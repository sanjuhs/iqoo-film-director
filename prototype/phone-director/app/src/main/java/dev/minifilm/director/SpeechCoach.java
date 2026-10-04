package dev.minifilm.director;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.UtteranceProgressListener;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/** No default/cloud recognizer fallback. Listening is always explicitly requested. */
public final class SpeechCoach {
    public interface Listener { void onText(String text); void onError(String message); }
    /** Only the focus boundary is injectable; tests never need to play a spoken cue. */
    interface FocusControl {
        int request(AudioFocusRequest request, AudioManager.OnAudioFocusChangeListener listener);
        void abandon(AudioFocusRequest request);
    }
    private static final AudioAttributes SPEECH_AUDIO = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private final Context context;
    private final TextToSpeech tts;
    private final FocusControl focusControl;
    private AudioFocusRequest activeFocus;
    private boolean noisyReceiverRegistered;
    private SpeechRecognizer recognizer;
    private boolean ready;
    private boolean closed;
    private boolean listening;
    private String pendingSpeech;
    private int utterance;
    private int recognitionSession;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String activeUtterance;
    private Runnable afterSpeech, speechFailed, speechTimeout;
    private Runnable audioInterruptionListener;
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            if (intent == null || !AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) return;
            // Context-registered delivery uses the main thread. Also marshal synthetic delivery.
            if (Looper.myLooper() == Looper.getMainLooper()) interruptSpeech();
            else main.post(() -> interruptSpeech());
        }
    };

    public SpeechCoach(Context context) {
        this(context, null);
    }

    SpeechCoach(Context context, FocusControl focusControl) {
        this.context = context.getApplicationContext();
        AudioManager audio = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        this.focusControl = focusControl != null ? focusControl : new FocusControl() {
            @Override public int request(AudioFocusRequest request, AudioManager.OnAudioFocusChangeListener listener) {
                return audio == null ? AudioManager.AUDIOFOCUS_REQUEST_FAILED : audio.requestAudioFocus(request);
            }
            @Override public void abandon(AudioFocusRequest request) {
                if (audio != null) audio.abandonAudioFocusRequest(request);
            }
        };
        tts = new TextToSpeech(this.context, status -> initializeVoice(status));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { main.post(() -> finishSpeech(id, true)); }
            @Override public void onError(String id) { main.post(() -> finishSpeech(id, false)); }
            @Override public void onError(String id, int error) { main.post(() -> finishSpeech(id, false)); }
            @Override public void onStop(String id, boolean interrupted) { main.post(() -> finishSpeech(id, false)); }
        });
        // This protected action is sent by system-server audio, not a Bluetooth app UID.
        // NOT_EXPORTED still accepts system-UID delivery and excludes unrelated apps.
        IntentFilter noisy = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) this.context.registerReceiver(noisyReceiver, noisy, Context.RECEIVER_NOT_EXPORTED);
        else this.context.registerReceiver(noisyReceiver, noisy);
        noisyReceiverRegistered = true;
    }

    private void initializeVoice(int status) {
        if (closed || status != TextToSpeech.SUCCESS) return;
        Set<Voice> voices = tts.getVoices();
        Voice selected = null;
        if (voices != null) for (Voice voice : voices) {
            if (!voice.isNetworkConnectionRequired() && voice.getLocale().getLanguage().equals("en")) {
                if (selected == null || voice.getLocale().equals(Locale.getDefault())) selected = voice;
            }
        }
        if (selected == null || tts.setVoice(selected) == TextToSpeech.ERROR) return;
        tts.setAudioAttributes(SPEECH_AUDIO);
        tts.setSpeechRate(.94f);
        ready = true;
        if (pendingSpeech != null) { String cue = pendingSpeech; pendingSpeech = null; speak(cue); }
    }

    public boolean speak(String text) {
        if (!ready && !closed && !listening && text != null && !text.trim().isEmpty()) {
            pendingSpeech = text;
            return false;
        }
        return speakThen(text, null, null);
    }

    /** Completion means the engine finished playback, not merely accepted text. */
    public boolean speakThen(String text, Runnable completed, Runnable failed) {
        if (closed || listening || !ready || text == null || text.trim().isEmpty()
                || text.length() > TextToSpeech.getMaxSpeechInputLength()) {
            if (failed != null) main.post(failed);
            return false;
        }
        stop();
        String id = "cue-" + (++utterance);
        activeUtterance = id;
        afterSpeech = completed;
        speechFailed = failed;
        // No delayed focus or automatic resume: a denied request fails this cue before playback.
        if (!requestSpeechFocus(id)) {
            main.post(() -> finishSpeech(id, false));
            return false;
        }
        speechTimeout = () -> {
            if (!id.equals(activeUtterance)) return;
            tts.stop();
            finishSpeech(id, false);
        };
        int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
        if (result != TextToSpeech.SUCCESS) {
            main.post(() -> finishSpeech(id, false));
            return false;
        }
        main.postDelayed(speechTimeout, 45_000);
        return true;
    }

    private void finishSpeech(String id, boolean success) {
        if (id == null || !id.equals(activeUtterance)) return;
        Runnable callback = success ? afterSpeech : speechFailed;
        clearSpeechCompletion();
        if (!closed && callback != null) callback.run();
    }

    private void clearSpeechCompletion() {
        if (speechTimeout != null) main.removeCallbacks(speechTimeout);
        speechTimeout = null;
        activeUtterance = null;
        afterSpeech = null;
        speechFailed = null;
        abandonSpeechFocus();
    }

    private boolean requestSpeechFocus(String id) {
        AudioManager.OnAudioFocusChangeListener listener = change -> {
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                Runnable loss = () -> { if (id.equals(activeUtterance)) interruptSpeech(); };
                if (Looper.myLooper() == Looper.getMainLooper()) loss.run(); else main.post(loss);
            }
        };
        AudioFocusRequest request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(SPEECH_AUDIO)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(listener, main).build();
        int result;
        try { result = focusControl.request(request, listener); }
        catch (RuntimeException failure) { result = AudioManager.AUDIOFOCUS_REQUEST_FAILED; }
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            if (result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) abandonFocus(request);
            return false;
        }
        // A reentrant loss/cancel must not leave a just-granted focus lease behind.
        if (closed || !id.equals(activeUtterance)) {
            abandonFocus(request);
            return false;
        }
        activeFocus = request;
        return true;
    }

    private void abandonSpeechFocus() {
        AudioFocusRequest request = activeFocus;
        activeFocus = null;
        if (request != null) abandonFocus(request);
    }

    private void abandonFocus(AudioFocusRequest request) {
        try { focusControl.abandon(request); }
        catch (RuntimeException ignored) { /* Never revive a stopped cue if the service is unavailable. */ }
    }

    private void interruptSpeech() {
        if (closed) return;
        pendingSpeech = null;
        Runnable failed = speechFailed;
        clearSpeechCompletion();
        tts.stop();
        if (failed != null) failed.run();
        if (!closed && audioInterruptionListener != null) audioInterruptionListener.run();
    }

    /** Main-thread shoot preparation hook, including noisy output between spoken cues. */
    public void setAudioInterruptionListener(Runnable listener) {
        if (!closed) audioInterruptionListener = listener;
    }

    public void stop() {
        pendingSpeech = null;
        clearSpeechCompletion();
        tts.stop();
    }
    public boolean isOfflineVoiceReady() { return ready && !closed; }

    public boolean isOfflineRecognitionAvailable() {
        return !closed && Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
    }

    public void listen(Listener listener) {
        if (!isOfflineRecognitionAvailable()) { listener.onError("On-device speech recognition isn't available. Type your brief instead."); return; }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.onError("Allow microphone access using the app's permission prompt, or type your brief."); return;
        }
        stopListening();
        stop();
        try {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            listening = true;
            final int session = recognitionSession;
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float value) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onPartialResults(Bundle results) { }
                @Override public void onEvent(int type, Bundle params) { }
                @Override public void onError(int error) {
                    if (closed || session != recognitionSession) return;
                    listening = false;
                    listener.onError("On-device recognition stopped (" + error + "). Type your brief or try again.");
                }
                @Override public void onResults(Bundle results) {
                    if (closed || session != recognitionSession) return;
                    listening = false;
                    ArrayList<String> words = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (words != null && !words.isEmpty()) listener.onText(words.get(0));
                    else listener.onError("No speech was recognized. Try again or type your brief.");
                }
            });
            Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            recognizer.startListening(request);
        } catch (RuntimeException error) {
            stopListening();
            listener.onError("On-device speech couldn't start. Type your brief instead.");
        }
    }

    public void stopListening() {
        recognitionSession++;
        listening = false;
        if (recognizer != null) { recognizer.cancel(); recognizer.destroy(); recognizer = null; }
    }

    public String describeAudioRoute() {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return "Audio output unavailable";
        for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int type = device.getType();
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLE_HEADSET)
                return "Bluetooth audio available · playback follows Android's selected route";
            if (type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_USB_HEADSET)
                return "Headphones available · playback follows Android's selected route";
        }
        return "Phone audio · connect earbuds for private direction";
    }

    public void close() {
        if (closed) return;
        closed = true;
        audioInterruptionListener = null;
        stopListening();
        stop();
        if (noisyReceiverRegistered) {
            noisyReceiverRegistered = false;
            context.unregisterReceiver(noisyReceiver);
        }
        tts.shutdown();
    }
}
