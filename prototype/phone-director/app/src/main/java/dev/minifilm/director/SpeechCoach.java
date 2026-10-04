package dev.minifilm.director;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
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
    private final Context context;
    private final TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private boolean ready;
    private boolean closed;
    private boolean listening;
    private String pendingSpeech;
    private int utterance;
    private int recognitionSession;

    public SpeechCoach(Context context) {
        this.context = context.getApplicationContext();
        tts = new TextToSpeech(this.context, status -> initializeVoice(status));
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
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        tts.setSpeechRate(.94f);
        ready = true;
        if (pendingSpeech != null) { String cue = pendingSpeech; pendingSpeech = null; speak(cue); }
    }

    public boolean speak(String text) {
        if (closed || listening || text == null || text.trim().isEmpty()) return false;
        if (!ready) { pendingSpeech = text; return false; }
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cue-" + (++utterance)) == TextToSpeech.SUCCESS;
    }
    public void stop() { pendingSpeech = null; tts.stop(); }
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

    public void close() { closed = true; stopListening(); stop(); tts.shutdown(); }
}
