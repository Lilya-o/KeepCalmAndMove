package lauzhack.sbb.winners.audio

import java.io.IOException

private var speech: Process? = null

@Synchronized
actual fun lire(texte: String, langue: String) {
    if (texte.isBlank() || !System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) return
    val voice = when (langue.substringBefore('-').lowercase()) {
        "fr" -> "Thomas"
        "de" -> "Anna"
        "en" -> "Samantha"
        else -> return
    }
    speech?.destroy()
    speech = try {
        Runtime.getRuntime().exec(arrayOf("/usr/bin/say", "-v", voice, "--", texte))
    } catch (_: IOException) {
        null
    }
}
