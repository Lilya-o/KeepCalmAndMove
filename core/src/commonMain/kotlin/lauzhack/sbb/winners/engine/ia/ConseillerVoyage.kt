
package lauzhack.sbb.winners.engine.ia

import lauzhack.sbb.winners.engine.RuleBasedOptionEngine
import lauzhack.sbb.winners.engine.RuleBasedOptionEngine.Companion.HEURE_ETE_SUISSE_MILLIS
import lauzhack.sbb.winners.engine.RuleBasedOptionEngine.Companion.HEURE_MILLIS
import lauzhack.sbb.winners.engine.RuleBasedOptionEngine.Companion.JOUR_MILLIS
import lauzhack.sbb.winners.engine.RuleBasedOptionEngine.Companion.MINUTE_MILLIS
import lauzhack.sbb.winners.model.Disruption
import lauzhack.sbb.winners.model.Journey
import lauzhack.sbb.winners.model.Option
import lauzhack.sbb.winners.model.Profile

/** Tout ce que l'écran doit afficher. */
data class Recommandation(
    /** Le conseil principal (bandeau en haut de l'écran). */
    val conseil: String,
    /** Les options, de la meilleure à la moins bonne, avec des explications dans la langue choisie. */
    val options: List<Option>,
    val analyse: RuleBasedOptionEngine.Analyse,
    val faits: FaitsExtraits,
    /** Vrai si l'IA a complété les faits ou reformulé au moins un texte (sinon : règles seules). */
    val iaUtilisee: Boolean,
) {
    val recommandee: Option? get() = options.firstOrNull()
}

/**
 * Point d'entrée unique pour l'interface (C), le serveur (A) et l'assistant.
 *
 *   val conseiller = ConseillerVoyage(modele = KoogModeleIA(...))   // ou modele = null : sans IA
 *   val reco = conseiller.conseiller(disruption, journey, profile, Langue.FR)
 *
 * Les étapes :
 *  1. Faits : règles d'abord, IA seulement si nécessaire (cache, garde-fous).
 *  2. Si un message annonce une heure ou une durée de reprise que les données n'avaient pas, on l'utilise
 *     (avec une confiance réduite).
 *  3. Le moteur de règles décide : options, droits, prix, classement. L'IA n'intervient PAS ici.
 *  4. Les textes sont reformulés dans la langue choisie, en un seul appel, sous garde-fous.
 *
 * Sans IA (modele = null, pas de réseau, délai dépassé), tout fonctionne quand même en français.
 */
class ConseillerVoyage(
    private val moteur: RuleBasedOptionEngine = RuleBasedOptionEngine(),
    modele: ModeleIA? = null,
    private val decalageHoraireMillis: Long = HEURE_ETE_SUISSE_MILLIS,
) {
    val stats = StatistiquesIA()
    private val extracteur = ExtracteurHybride(modele, stats)
    private val redacteur = RedacteurExplications(modele, stats)

    suspend fun conseiller(
        disruption: Disruption,
        journey: Journey,
        profile: Profile,
        langue: Langue = Langue.FR,
    ): Recommandation {
        // 1. Comprendre les messages
        val faits = extracteur.extraire(disruption)

        // 2. Compléter l'heure de reprise si un message la donne
        val perturbation = completerFin(disruption, faits)

        // 3. Décider (règles uniquement)
        val analyse = moteur.analyser(perturbation, journey, faits)
        val options = moteur.rank(moteur.generateOptions(perturbation, journey, faits), profile)

        // 4. Rédiger : conseil + explications en un seul appel
        val (textes, reformule) = redacteur.reformuler(
            listOf(analyse.conseil) + options.map { it.explanation },
            langue,
        )
        val optionsRedigees = options.mapIndexed { i, o -> o.copy(explanation = textes[i + 1]) }

        return Recommandation(
            conseil = textes[0],
            options = optionsRedigees,
            analyse = analyse,
            faits = faits,
            iaUtilisee = reformule || faits.source == SourceDesFaits.REGLES_ET_IA,
        )
    }

    /** Si la fin n'est pas connue mais qu'un message donne une heure ou une durée, on la reprend. */
    internal fun completerFin(d: Disruption, faits: FaitsExtraits): Disruption {
        if (d.estimatedEndMillis != null) return d
        val derniereInfo = d.messages.maxOfOrNull { it.timestampMillis } ?: d.lastUpdateMillis

        val fin = faits.repriseHeure?.let { prochaineOccurrence(it, derniereInfo) }
            ?: faits.dureeMinutes?.let { minutes ->
                // la durée compte à partir du message qui l'annonce
                val annonce = d.messages.lastOrNull { Regex("(?<!\\d)$minutes(?!\\d)").containsMatchIn(it.text) }
                (annonce?.timestampMillis ?: derniereInfo) + minutes * MINUTE_MILLIS
            }
            ?: return d

        return d.copy(
            estimatedEndMillis = fin,
            confidence = minOf(d.confidence, CONFIANCE_FIN_LUE_DANS_UN_MESSAGE),
        )
    }

    /** « 18:45 » → le prochain 18:45 (heure suisse) après [reference] moins 1 h (une heure juste passée reste ce jour). */
    private fun prochaineOccurrence(hhmm: String, reference: Long): Long {
        val (h, m) = hhmm.split(':').map { it.toLong() }
        val debutJour = (reference + decalageHoraireMillis).floorDiv(JOUR_MILLIS) * JOUR_MILLIS - decalageHoraireMillis
        val candidat = debutJour + h * HEURE_MILLIS + m * MINUTE_MILLIS
        return if (candidat < reference - HEURE_MILLIS) candidat + JOUR_MILLIS else candidat
    }

    companion object {
        /** Une heure lue dans un message reste une annonce, pas une certitude. */
        const val CONFIANCE_FIN_LUE_DANS_UN_MESSAGE = 0.6
    }
}


