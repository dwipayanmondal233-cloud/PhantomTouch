package com.example.phantomtouch

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceControl(
    private val ctx: Context,
    private val onPhrase: (String) -> Unit,
    private val onProblem: (String) -> Unit
) {

    private val h = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var on = false
    private var fails = 0

    @Volatile
    var listening = false
        private set

    private val intent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    // Returns null if started, or a short problem text.
    fun start(): String? {
        if (Build.VERSION.SDK_INT < 33) return "Voice needs Android 13 or newer"
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
            return "No offline speech found. Install the offline English pack in Google settings."
        }
        val r: SpeechRecognizer = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        } catch (e: Exception) {
            return "Voice failed: " + e.message
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                listening = false
            }

            override fun onError(error: Int) {
                listening = false
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart(400L)
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restart(1200L)
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                        stop()
                        onProblem("Offline English speech is not installed")
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        stop()
                        onProblem("Microphone permission missing")
                    }
                    else -> {
                        fails++
                        if (fails >= 6) {
                            stop()
                            onProblem("Voice stopped (error $error)")
                        } else {
                            restart(1000L)
                        }
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                fails = 0
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (list != null && list.isNotEmpty()) {
                    val first = list[0]
                    if (first.isNotBlank()) onPhrase(first)
                }
                restart(300L)
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        rec = r
        on = true
        fails = 0
        listen()
        return null
    }

    private fun listen() {
        if (!on) return
        try {
            rec?.startListening(intent)
        } catch (e: Exception) {
            fails++
            if (fails >= 6) {
                stop()
                onProblem("Voice stopped")
            } else {
                restart(1000L)
            }
        }
    }

    private fun restart(delay: Long) {
        if (!on) return
        h.postDelayed({ listen() }, delay)
    }

    fun stop() {
        on = false
        listening = false
        h.removeCallbacksAndMessages(null)
        try {
            rec?.cancel()
            rec?.destroy()
        } catch (e: Exception) {
        }
        rec = null
    }
}