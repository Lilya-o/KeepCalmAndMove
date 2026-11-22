package lauzhack.sbb.winners.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

private val mainHandler = Handler(Looper.getMainLooper())
private var appContext: Context? = null
private var engine: TextToSpeech? = null
private var ready = false
private var pending: Utterance? = null

private data class Utterance(val text: String, val language: String)

fun initialiserVoix(context: Context) {
    appContext = context.applicationContext
}

actual fun lire(texte: String, langue: String) {
    if (texte.isBlank()) return
    mainHandler.post {
        val context = appContext ?: return@post
        val utterance = Utterance(texte, langue)
        val currentEngine = engine
        if (ready && currentEngine != null) {
            speak(currentEngine, utterance)
        } else {
            pending = utterance
            if (currentEngine == null) {
                engine = TextToSpeech(context) { status ->
                    mainHandler.post { finishInitialization(status) }
                }
            }
        }
    }
}

private fun finishInitialization(status: Int) {
    val initialized = engine ?: return
    ready = status == TextToSpeech.SUCCESS
    if (ready) {
        pending?.let { speak(initialized, it) }
    } else {
        initialized.shutdown()
        engine = null
    }
    pending = null
}

private fun speak(engine: TextToSpeech, utterance: Utterance) {
    val locale = Locale.forLanguageTag(utterance.language)
    if (engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE) {
        engine.speak(utterance.text, TextToSpeech.QUEUE_FLUSH, null, "recommendation")
    }
}
