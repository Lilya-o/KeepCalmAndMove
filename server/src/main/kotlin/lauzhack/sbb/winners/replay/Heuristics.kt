package lauzhack.sbb.winners.replay

import lauzhack.sbb.winners.model.Severity

/**
 * Heuristiques d'équipe pour dériver `severity` et `confidence`, ABSENTES des données sources.
 *
 * ATTENTION : ce ne sont PAS des règles officielles SBB. Ce sont des approximations choisies par
 * l'équipe pour la démo ; à ne jamais présenter à l'utilisateur comme un fait vérifié.
 */
object Heuristics {

    /**
     * Confiance dérivée du `duration_type` annoncé par VIA : plus l'annonce de fin est précise,
     * plus on fait confiance à l'estimation.
     */
    fun confidence(durationType: String): Double = when {
        durationType.isBlank() || durationType == "UNKNOWN" || durationType == "NONE" -> 0.3
        durationType.contains("AT_LEAST") -> 0.6
        durationType.contains("APPROX") -> 0.7
        else -> 0.9 // UNTIL, UNTIL_DATE, FROM_TO…
    }

    /**
     * Sévérité : un incident clos est purement informatif ; sinon on l'estime par le nombre de
     * lignes impactées (proxy grossier de l'ampleur).
     */
    fun severity(closed: Boolean, affectedLineCount: Int): Severity = when {
        closed -> Severity.INFO
        affectedLineCount >= 5 -> Severity.CRITICAL
        affectedLineCount >= 2 -> Severity.MAJOR
        else -> Severity.MINOR
    }
}
