package com.spinecare.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/** Voice guidance (TTS cues) + voice control (start/stop/next/repeat).
 *
 *  TTS uses the app locale (en-US / ro-RO) when the voice pack is installed,
 *  falling back to default. Commands are recognized in English on purpose:
 *  "start/stop/next/repeat" are short, robust across accents, and Romanian
 *  STT is not guaranteed on-device; the UI says so when the app is in Romanian.
 *  All recognition is on-device (EXTRA_PREFER_OFFLINE) when available.
 */
class VoiceGuide(private val ctx: Context) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var sr: android.speech.SpeechRecognizer? = null
    private var listening = false
    var onCommand: ((String) -> Unit)? = null
    var onState: ((String?) -> Unit)? = null   // null = idle, else status text

    init {
        if (ServerConfig.voiceGuidance(ctx)) {
            tts = TextToSpeech(ctx) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) setLanguage()
            }
        }
    }

    private fun appLocale(): Locale = when (ServerConfig.lang(ctx)) {
        "ro" -> Locale("ro", "RO")
        "en" -> Locale.US
        else -> Locale.getDefault()
    }

    private fun setLanguage() {
        val loc = appLocale()
        val r = tts?.setLanguage(loc)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.US)
        }
    }

    fun speak(text: String) {
        if (!ServerConfig.voiceGuidance(ctx) || !ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cue")
    }

    fun startListening() {
        if (!ServerConfig.voiceControl(ctx)) return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            onState?.invoke(null); return
        }
        if (listening) return
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            if (android.os.Build.VERSION.SDK_INT >= 23)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        sr = android.speech.SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true; onState?.invoke(ctx.getString(R.string.voice_listening)) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    listening = false; onState?.invoke(null)
                    if (error == android.speech.SpeechRecognizer.ERROR_CLIENT) return
                    // Auto-restart the listener loop unless we were stopped on purpose.
                    if (ServerConfig.voiceControl(ctx) && sr != null) {
                        try { sr?.startListening(intent) } catch (_: Exception) {}
                    }
                }
                override fun onResults(results: Bundle?) {
                    listening = false; onState?.invoke(null)
                    val text = results
                        ?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.lowercase()?.trim() ?: return
                    val cmd = listOf("start", "stop", "next", "repeat")
                        .firstOrNull { text.contains(it) }
                    if (cmd != null) {
                        Log.i("VoiceGuide", "command=$cmd from \"$text\"")
                        onCommand?.invoke(cmd)
                    }
                    if (ServerConfig.voiceControl(ctx) && sr != null) {
                        try { sr?.startListening(intent) } catch (_: Exception) {}
                    }
                }
                override fun onPartialResults(partial: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        sr?.startListening(intent)
    }

    fun stopListening() {
        sr?.cancel(); sr?.destroy(); sr = null; listening = false
    }

    fun shutdown() {
        stopListening()
        tts?.stop(); tts?.shutdown(); tts = null
    }
}
