package lauzhack.sbb.winners.engine

/**
 * Distances par la route entre quelques gares, pour estimer le prix et la durée d'un taxi.
 *
 * ESTIMATIONS approximatives (pas des données officielles) : à vérifier,
 * puis à remplacer par une vraie source (avec A).
 * Si une distance est inconnue, le moteur NE propose PAS de taxi plutôt que d'inventer un prix.
 */
object Distances {

    private val table: Map<Set<String>, Double> = mapOf(
        paire("geneve", "lausanne") to 62.0,
        paire("annemasse", "geneve") to 9.0,
        paire("geneve", "nyon") to 25.0,
        paire("lausanne", "nyon") to 40.0,
        paire("lausanne", "morges") to 12.0,
        paire("geneve", "morges") to 52.0,
        paire("lausanne", "renens") to 5.0,
        paire("geneve", "renens") to 58.0,
    )

    /** Distance en km, ou null si on ne la connaît pas. */
    fun kmEntre(origine: String, destination: String): Double? {
        val a = normaliser(origine)
        val b = normaliser(destination)
        if (a == b) return 0.0
        return table[paire(a, b)]
    }

    private fun paire(a: String, b: String): Set<String> = setOf(a, b)

    /** « Genève Cornavin » → « geneve », « Genf » → « geneve », « Lausanne » → « lausanne ». */
    private fun normaliser(nom: String): String {
        val sansAccents = nom.lowercase()
            .replace('é', 'e').replace('è', 'e').replace('ê', 'e')
            .replace('à', 'a').replace('â', 'a').replace('ä', 'a')
            .replace('ü', 'u').replace('ö', 'o').replace('ô', 'o')
            .replace('î', 'i').replace('ç', 'c')
        val premierMot = sansAccents.split(' ', ',', '-', '/').first { it.isNotBlank() }
        return when (premierMot) {
            "genf", "ginevra", "geneva" -> "geneve"
            "losanna" -> "lausanne"
            else -> premierMot
        }
    }
}
