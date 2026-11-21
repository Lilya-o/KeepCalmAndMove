
package lauzhack.sbb.winners.engine.ia

import lauzhack.sbb.winners.engine.RuleBasedOptionEngine.CategorieCause
import lauzhack.sbb.winners.engine.Rules
import lauzhack.sbb.winners.engine.Rules.Responsabilite
import lauzhack.sbb.winners.engine.contientUnMot
import lauzhack.sbb.winners.model.Disruption

/** Comment la situation évolue d'après les messages. */
enum class Tendance { AMELIORATION, STABLE, AGGRAVATION, INCONNUE }

/** Qui a trouvé ces faits. */
enum class SourceDesFaits { REGLES, REGLES_ET_IA }

/**
 * Ce que l'on a compris des messages de perturbation (texte libre → faits structurés).
 * Le moteur de décision ne lit QUE ces faits, jamais le texte brut.
 */
data class FaitsExtraits(
    val categorie: CategorieCause,
    val responsabilite: Responsabilite,
    val busDeRemplacement: Boolean,
    val dureeIndeterminee: Boolean,
    val tendance: Tendance,
    /** Heure de reprise ÉCRITE dans un message, au format « HH:MM » (heure suisse), sinon null. */
    val repriseHeure: String? = null,
    /** Durée annoncée en minutes (ex. « pronostic 60' »), sinon null. */
    val dureeMinutes: Int? = null,
    val source: SourceDesFaits = SourceDesFaits.REGLES,
) {
    /** Les règles ont-elles compris l'essentiel ? Si oui, inutile d'appeler l'IA. */
    val suffisant: Boolean
        get() = categorie != CategorieCause.INCONNUE &&
                (dureeIndeterminee || repriseHeure != null || dureeMinutes != null)
}

/**
 * Extraction par RÈGLES (mots-clés FR/DE/IT/EN) : instantanée, gratuite, prévisible.
 * C'est toujours la première étape ; l'IA ne complète que ce qui manque.
 */
object ExtracteurParRegles {

    fun extraire(disruption: Disruption): FaitsExtraits {
        val texte = texteComplet(disruption)
        val minuscule = texte.lowercase()

        val categorie = when {
            contientUnMot(texte, MOTS_INFRASTRUCTURE_ENDOMMAGEE) -> CategorieCause.INFRASTRUCTURE_ENDOMMAGEE
            EXPRESSIONS_ACCIDENT_DE_PERSONNE.any { minuscule.contains(it) } -> CategorieCause.ACCIDENT_DE_PERSONNE
            contientUnMot(texte, MOTS_PANNE_TECHNIQUE) -> CategorieCause.PANNE_TECHNIQUE
            else -> CategorieCause.INCONNUE
        }

        return FaitsExtraits(
            categorie = categorie,
            responsabilite = responsabiliteConservatrice(Rules.responsabilite(texte), categorie),
            busDeRemplacement = contientUnMot(texte, MOTS_BUS_REMPLACEMENT) ||
                    EXPRESSIONS_BUS_REMPLACEMENT.any { minuscule.contains(it) },
            dureeIndeterminee = EXPRESSIONS_DUREE_INDETERMINEE.any { minuscule.contains(it) },
            tendance = when {
                EXPRESSIONS_AGGRAVATION.any { minuscule.contains(it) } -> Tendance.AGGRAVATION
                EXPRESSIONS_AMELIORATION.any { minuscule.contains(it) } -> Tendance.AMELIORATION
                else -> Tendance.INCONNUE
            },
            repriseHeure = heureDeReprise(minuscule),
        )
    }

    /**
     * Pour promettre de l'argent (taxi, hôtel), il faut être SÛR que la compagnie est responsable :
     * seule une panne technique reconnue par les règles le permet. Sinon → INCERTAINE.
     */
    internal fun responsabiliteConservatrice(r: Responsabilite, categorie: CategorieCause): Responsabilite =
        if (r == Responsabilite.COMPAGNIE && categorie != CategorieCause.PANNE_TECHNIQUE) Responsabilite.INCERTAINE else r

    /** La cause + les messages, du plus ancien au plus récent. */
    internal fun texteComplet(d: Disruption): String =
        (listOf(d.cause) + d.messages.sortedBy { it.timestampMillis }.map { it.text }).joinToString("\n")

    /** « reprise prévue vers 18:45 », « wieder ab 18.45 Uhr », « ripresa alle 18h45 » → « 18:45 ». */
    internal fun heureDeReprise(texteMinuscule: String): String? {
        val m = REGEX_REPRISE.findAll(texteMinuscule).lastOrNull() ?: return null
        val h = m.groupValues[2].toInt()
        val min = m.groupValues[3].toInt()
        if (h > 23 || min > 59) return null
        return "${h.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}"
    }

    /** Vrai si l'heure « HH:MM » est réellement écrite dans le texte (18:45, 18h45, 18.45, 18 h 45). */
    internal fun heureEcriteDans(heure: String, texte: String): Boolean {
        val (h, m) = heure.split(':').map { it.toInt() }
        val regex = Regex("(?<![\\d.])0?$h\\s*[:h.]\\s*${m.toString().padStart(2, '0')}(?!\\d|\\.\\d)")
        return regex.containsMatchIn(texte.lowercase())
    }

    // Mot déclencheur, puis au plus 40 caractères sans chiffre, puis l'heure. Pas une date (06.09.2026).
    private val REGEX_REPRISE = Regex(
        "(reprise|reprend|rétabli|retabli|wieder|ripres|riprende|resum|jusqu'à|jusqu’à|bis|fino|until)" +
                "[^0-9]{0,40}(\\d{1,2})\\s*[:h.]\\s*(\\d{2})(?!\\d|\\.\\d)",
    )

    // --- Mots-clés (FR, DE, IT, EN) ---
    val MOTS_INFRASTRUCTURE_ENDOMMAGEE = listOf(
        "incendie", "feu", "brand", "fire", "incendio",
        "éboulement", "glissement", "felssturz", "erdrutsch", "murgang", "frana", "landslide",
        "déraillement", "entgleisung", "deragliamento", "derailment",
        "caténaire", "fahrleitung", "inondation", "überschwemmung", "hochwasser", "alluvione", "flood",
        "avalanche", "lawine", "valanga", "dégâts", "schaden",
    )
    val EXPRESSIONS_ACCIDENT_DE_PERSONNE = listOf(
        "accident de personne", "personenunfall", "incidente a una persona", "person struck",
    )
    val MOTS_PANNE_TECHNIQUE = listOf(
        "dérangement", "panne", "störung", "guasto", "défaut", "defekt", "technique", "technisch",
        "technical", "signal", "aiguillage", "weiche", "stellwerk", "enclenchement",
    )
    val MOTS_BUS_REMPLACEMENT = listOf("ersatzbus", "bahnersatz", "ev")
    val EXPRESSIONS_BUS_REMPLACEMENT = listOf(
        "bus de remplacement", "bus sostitutiv", "replacement bus", "ersatzbusse",
    )
    val EXPRESSIONS_DUREE_INDETERMINEE = listOf(
        "indéterminé", "indetermine", "durée inconnue", "unbestimmt", "unbekannt", "indeterminat", "unknown",
    )
    val EXPRESSIONS_AGGRAVATION = listOf(
        "prolong", "s'aggrave", "aggrav", "verlänger", "verschlechter", "prolungat", "extended",
    )
    val EXPRESSIONS_AMELIORATION = listOf(
        "reprise progressive", "rétabli", "retabli", "wieder in betrieb", "normalisier", "ripristinat", "restored",
    )
}


