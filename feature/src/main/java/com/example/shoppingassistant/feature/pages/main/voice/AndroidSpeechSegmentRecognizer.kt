// Last synced: 2025-12-22
package com.example.shoppingassistant.feature.pages.main.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

enum class VoiceRecognizerError {
    NoMatch,
    Timeout,
    Network,
    Busy,
    InsufficientPermissions,
    Unknown,
}

/**
 * Сегментный распознаватель:
 * - start() -> слушаем один сегмент
 * - stop()  -> получаем финальный текст сегмента
 * - cancel() -> отмена текущего сегмента
 *
 * Сегменты собираются выше (в VM/host логике) для pause/undo.
 */
class AndroidSpeechSegmentRecognizer(
    private val context: Context,
    private val locale: Locale = Locale.getDefault(),
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (VoiceRecognizerError) -> Unit,
) : RecognitionListener {

    private var recognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false

    fun start() {
        if (isListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError(VoiceRecognizerError.Unknown)
            return
        }
        val r = (recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it })
        r.setRecognitionListener(this)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        isListening = true
        r.startListening(intent)
    }

    fun stop() {
        if (!isListening) return
        recognizer?.stopListening()
        // финальный результат придёт в onResults / onError
    }

    fun cancel() {
        if (!isListening) return
        recognizer?.cancel()
        isListening = false
    }

    fun release() {
        isListening = false
        recognizer?.setRecognitionListener(null)
        recognizer?.destroy()
        recognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (text.isNotBlank()) onPartial(text)
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (text.isNotBlank()) onFinal(text) else onError(VoiceRecognizerError.NoMatch)
    }

    override fun onError(error: Int) {
        isListening = false
        onError(
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> VoiceRecognizerError.NoMatch
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceRecognizerError.Timeout
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceRecognizerError.Network
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceRecognizerError.Busy
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceRecognizerError.InsufficientPermissions
                else -> VoiceRecognizerError.Unknown
            }
        )
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
