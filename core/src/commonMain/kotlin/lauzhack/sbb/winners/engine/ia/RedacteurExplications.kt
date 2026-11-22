package lauzhack.sbb.winners.engine.ia

/**
 * Rend les textes du moteur plus naturels et les traduit (FR, DE, EN), en UN SEUL appel IA
 * pour le conseil + toutes les options (au lieu d'un appel par option).
 *
 * Les textes de départ sont construits par le code (heures, prix, droits exacts).
 * L'IA ne fait que les reformuler, et chaque phrase proposée passe par [GardeFou] :
 * si elle ajoute un chiffre ou une promesse d'argent, on garde le texte d'origine.
 */
class RedacteurExplications(
    private val modele: ModeleIA?,
    val stats: StatistiquesIA = StatistiquesIA(),
) {
    private val cache = CacheLimite<String, List<String>>(TAILLE_CACHE)

    /** Renvoie les textes reformulés, dans le même ordre (le texte d'origine si un texte est refusé). */
    suspend fun reformuler(textes: List<String>, langue: Langue): Pair<List<String>, Boolean> {
        if (modele == null || textes.isEmpty()) return textes to false

        val message = buildString {
            append("Langue : ").append(langue.nom).append('\n')
            textes.forEachIndexed { i, t -> append(i + 1).append(" | ").append(t.replace('\n', ' ')).append('\n') }
        }
        cache.get(message)?.let { stats.reponsesDuCache++; return it to true }

        stats.appelsIA++
        val reponse = modele.repondre(CONSIGNE_REDACTION, message)
        if (reponse == null) {
            stats.echecsIA++
            return textes to false
        }

        val proposes = lireListeNumerotee(reponse)
        var auMoinsUnAccepte = false
        val resultat = textes.mapIndexed { i, origine ->
            val propose = proposes[i + 1]
            if (propose != null && GardeFou.accepte(origine, propose)) {
                auMoinsUnAccepte = true
                propose
            } else {
                if (propose != null) stats.reponsesRejetees++
                origine
            }
        }
        cache.put(message, resultat)
        return resultat to auMoinsUnAccepte
    }

    /** « 1 | texte » ou « 1. texte » → {1: "texte"} */
    internal fun lireListeNumerotee(reponse: String): Map<Int, String> =
        reponse.lines().mapNotNull { ligne ->
            Regex("^\\s*(\\d+)\\s*[|.):-]\\s*(.+)$").find(ligne)?.let { it.groupValues[1].toInt() to it.groupValues[2].trim() }
        }.toMap()

    companion object {
        const val TAILLE_CACHE = 200

        val CONSIGNE_REDACTION = """
            Tu aides des voyageurs pendant une perturbation ferroviaire en Suisse.
            Réécris chaque texte numéroté dans la langue demandée : phrases courtes et simples, ton calme, tutoiement en français et en allemand.
            Règles strictes :
            - garde EXACTEMENT tous les chiffres, heures, prix et pourcentages ;
            - n'ajoute aucune information, aucun chiffre, aucune promesse de remboursement ;
            - garde les conditions (« si », « avant de payer », « estimation ») ;
            - au plus 35 mots par texte.
            Réponds uniquement avec les textes, une ligne chacun, sous la forme « numéro | texte ».
        """.trimIndent()
    }
}

/**
 * Vérifie qu'un texte reformulé par l'IA ne dit rien de plus que l'original.
 */
object GardeFou {

    /** Accepté seulement si : pas de chiffre nouveau, pas de promesse d'argent nouvelle, longueur raisonnable. */
    fun accepte(original: String, propose: String): Boolean =
        propose.isNotBlank() &&
            propose.length <= original.length * 2 + 120 &&
            chiffresAjoutes(original, propose).isEmpty() &&
            !promesseAjoutee(original, propose)

    /** Les nombres présents dans le texte proposé mais absents de l'original (« 23:03 » → 23 et 03). */
    fun chiffresAjoutes(original: String, propose: String): Set<String> =
        nombres(propose) - nombres(original)

    /** Le texte proposé parle de remboursement / gratuité alors que l'original n'en parlait pas. */
    fun promesseAjoutee(original: String, propose: String): Boolean {
        val o = original.lowercase()
        val p = propose.lowercase()
        val originalPromet = MOTS_PROMESSE.any { o.contains(it) }
        return !originalPromet && MOTS_PROMESSE.any { p.contains(it) }
    }

    private fun nombres(texte: String): Set<String> =
        Regex("\\d+").findAll(texte).map { it.value.trimStart('0').ifEmpty { "0" } }.toSet()

    private val MOTS_PROMESSE = listOf(
        // FR
        "rembours", "gratuit", "pris en charge", "prise en charge", "indemn", "offert",
        // DE
        "erstatt", "kostenlos", "gratis", "übernomm", "entschädig", "vergüt",
        // EN
        "refund", "reimburs", "free of charge", "for free", "covered", "compensat",
    )
}
