package dev.minifilm.director;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/**
 * Read-only runtime capability diagnostic, not recognition accuracy or AirPods routing evidence.
 * The availability snapshot creates no recognizer. The separate language query creates only
 * a dedicated on-device recognizer to ask for metadata; it never starts recognition or downloads.
 * No microphone is opened and no sound is played. Output types are
 * reduced to booleans; names, addresses and hardware identifiers are never read or logged.
 * Unavailable recognition or headphones are valid observations, not failing readiness gates.
 */
@RunWith(AndroidJUnit4.class)
public final class SpeechCapabilityTest {
    @Test(timeout = 10_000)
    public void snapshotAvailabilityWithoutOpeningMicrophoneOrChangingPermission() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        boolean permissionBefore = microphoneGranted(context);
        int sdk = Build.VERSION.SDK_INT;
        boolean recognizerAvailable = sdk >= 31
                && SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        assertNotNull("AudioManager must be available for the capability query", audio);
        boolean bluetooth = false;
        boolean wired = false;
        for (AudioDeviceInfo output : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int type = output.getType();
            bluetooth |= type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    || (sdk >= 31 && (type == AudioDeviceInfo.TYPE_BLE_HEADSET
                            || type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
                    || (sdk >= 33 && type == AudioDeviceInfo.TYPE_BLE_BROADCAST);
            wired |= type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    || type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    || type == AudioDeviceInfo.TYPE_USB_HEADSET;
        }
        boolean permissionAfter = microphoneGranted(context);
        assertEquals("The read-only query must not change microphone permission",
                permissionBefore, permissionAfter);
        JSONObject snapshot = new JSONObject()
                .put("sdkInt", sdk)
                .put("onDeviceRecognizerAvailable", recognizerAvailable)
                .put("bluetoothOutputAvailable", bluetooth)
                .put("wiredOutputAvailable", wired)
                .put("microphonePermissionGranted", permissionBefore)
                .put("permissionUnchanged", permissionBefore == permissionAfter)
                .put("microphoneOpened", false);
        JSONObject parsed = new JSONObject(snapshot.toString());
        assertEquals(7, parsed.length());
        assertEquals(sdk, parsed.getInt("sdkInt"));
        for (String key : new String[] {"onDeviceRecognizerAvailable", "bluetoothOutputAvailable",
                "wiredOutputAvailable", "microphonePermissionGranted", "permissionUnchanged",
                "microphoneOpened"}) {
            assertTrue("Snapshot field must remain boolean: " + key, parsed.get(key) instanceof Boolean);
        }
        assertTrue(parsed.getBoolean("permissionUnchanged"));
        assertFalse(parsed.getBoolean("microphoneOpened"));
        Log.i("MiniFilmReadiness", snapshot.toString());
    }

    /** Service errors/timeouts are observations, not proof that en-US recognition works. */
    @Test(timeout = 25_000)
    public void queryEnUsMetadataWithoutStartingRecognitionOrDownloadingModels() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        boolean permissionBefore = microphoneGranted(context);
        AtomicReference<SpeechRecognizer> owned = new AtomicReference<>();
        AtomicReference<LanguageOutcome> outcome = new AtomicReference<>();
        AtomicBoolean active = new AtomicBoolean(true);
        CountDownLatch complete = new CountDownLatch(1);
        try {
            if (Build.VERSION.SDK_INT < 33) {
                outcome.set(new LanguageOutcome("unsupported_api"));
            } else {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    try {
                        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                            finishLanguageQuery(active, outcome, complete,
                                    new LanguageOutcome("on_device_service_unavailable"));
                            return;
                        }
                        SpeechRecognizer recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
                        owned.set(recognizer);
                        recognizer.setRecognitionListener(NOOP_RECOGNITION_LISTENER);
                        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
                        recognizer.checkRecognitionSupport(intent, context.getMainExecutor(), new RecognitionSupportCallback() {
                            @Override public void onSupportResult(RecognitionSupport support) {
                                LanguageOutcome result = new LanguageOutcome("support_result");
                                result.installed = containsEnUs(support.getInstalledOnDeviceLanguages());
                                result.pending = containsEnUs(support.getPendingOnDeviceLanguages());
                                // Official API: this list means supported but still needing a download.
                                result.supported = containsEnUs(support.getSupportedOnDeviceLanguages());
                                finishLanguageQuery(active, outcome, complete, result);
                            }
                            @Override public void onError(int error) {
                                LanguageOutcome result = new LanguageOutcome("service_error");
                                result.errorCode = error;
                                finishLanguageQuery(active, outcome, complete, result);
                            }
                        });
                    } catch (RuntimeException error) {
                        // No exception messages/classes are logged: they may identify a component.
                        LanguageOutcome result = new LanguageOutcome("query_exception");
                        result.exceptionKind = error instanceof SecurityException ? "security"
                                : error instanceof UnsupportedOperationException ? "unsupported" : "runtime";
                        finishLanguageQuery(active, outcome, complete, result);
                    }
                });
                if (!complete.await(12, TimeUnit.SECONDS)) {
                    outcome.compareAndSet(null, new LanguageOutcome("timeout"));
                }
            }
        } finally {
            active.set(false);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                SpeechRecognizer recognizer = owned.getAndSet(null);
                if (recognizer != null) recognizer.destroy();
            });
            assertEquals("Metadata queries must leave microphone permission unchanged",
                    permissionBefore, microphoneGranted(context));
        }
        LanguageOutcome result = outcome.get();
        assertNotNull("Diagnostic must report a result, error, or timeout", result);
        JSONObject snapshot = new JSONObject()
                .put("sdkInt", Build.VERSION.SDK_INT)
                .put("status", result.status)
                .put("permissionUnchanged", true)
                .put("microphoneOpened", false);
        if ("support_result".equals(result.status)) {
            snapshot.put("installedEnUS", result.installed)
                    .put("pendingEnUS", result.pending)
                    .put("supportedEnUS", result.supported);
        } else if ("service_error".equals(result.status)) {
            snapshot.put("errorCode", result.errorCode);
        } else if ("query_exception".equals(result.status)) {
            snapshot.put("exceptionKind", result.exceptionKind);
        } else if ("timeout".equals(result.status)) {
            snapshot.put("timeout", true);
        }
        JSONObject parsed = new JSONObject(snapshot.toString());
        assertEquals(result.status, parsed.getString("status"));
        assertTrue(parsed.getBoolean("permissionUnchanged"));
        assertFalse(parsed.getBoolean("microphoneOpened"));
        Log.i("MiniFilmLanguageReadiness", snapshot.toString());
    }

    private static void finishLanguageQuery(AtomicBoolean active, AtomicReference<LanguageOutcome> outcome,
            CountDownLatch complete, LanguageOutcome result) {
        if (active.get() && outcome.compareAndSet(null, result)) complete.countDown();
    }

    private static boolean containsEnUs(List<String> languages) {
        for (String language : languages) {
            if (language != null && "en-US".equalsIgnoreCase(language.replace('_', '-'))) return true;
        }
        return false;
    }

    private static final class LanguageOutcome {
        final String status;
        boolean installed, pending, supported;
        int errorCode;
        String exceptionKind;
        LanguageOutcome(String status) { this.status = status; }
    }

    private static final RecognitionListener NOOP_RECOGNITION_LISTENER = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) { }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onError(int error) { }
        @Override public void onResults(Bundle results) { }
        @Override public void onPartialResults(Bundle partialResults) { }
        @Override public void onEvent(int eventType, Bundle params) { }
    };

    private static boolean microphoneGranted(Context context) {
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }
}
