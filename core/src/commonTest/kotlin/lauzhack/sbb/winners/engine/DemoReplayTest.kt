package lauzhack.sbb.winners.engine

import lauzhack.sbb.winners.engine.ia.ConseillerVoyage
import lauzhack.sbb.winners.engine.ia.Recommandation
import lauzhack.sbb.winners.model.Disruption
import lauzhack.sbb.winners.model.DisruptionMessage
import lauzhack.sbb.winners.model.Journey
import lauzhack.sbb.winners.model.MessageSource
import lauzhack.sbb.winners.model.OptionType
import lauzhack.sbb.winners.model.Severity
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test

/**
 * DÉMO « REPLAY » : rejoue la VRAIE perturbation du 17.09.2026 (panne de courant Renens VD – Morges)
 * moment par moment, et affiche ce que l'app conseille à chaque fois.
 *
 * Ce n'est pas un vrai test : clique sur la flèche verte à côté de « fun replay() » (incident réel)
 * ou de « fun scenarios() » (situations de nuit), et lis le résultat en bas de l'écran.
 * L'affichage reprend l'écran de l'app (C) : pas de profil, un titre, un conseil, une recommandation
 * et des alternatives.
 *
 * Messages copiés tels quels depuis le serveur (GET /disruptions/2195005188970/timeline).
 */
class DemoReplayTest {

    // ---- Les messages réels, dans l'ordre (heure suisse) ----
    private val m1752 = msg(1789660366440, "Interrupted service: Renens VD - Morges\nInterrupted train service on the Lausanne - Genève line between Renens VD and Morges.\nThis is due to a power failure.\nThe restriction lasts until approx. 18:30.\nLines IC1, IR15, IR57, IR90, IR95, RE33, R8 and R9 are affected.\nAllow for delays and cancellations.")
    private val m1814 = msg(1789661675007, "Cancellation of rail services between Renens and Morges\nRail services are currently suspended on the Lausanne–Geneva line between Renens VD and Morges. Potential complete interruption from 6.30pm Lausanne–Geneva. The overhead contact lines have been torn off due to smoke. All trains will be cancelled. We thank you for your understanding and will keep you informed as the situation develops.")
    private val m1830 = msg(1789662604084, "Interrupted service: Renens VD - Morges\nInterrupted train service on the Lausanne - Genève line between Renens VD and Morges.\nThis is due to a power failure.\nThe restriction lasts until approx. 20:30.\nLines TGV, EC, IC1, IR15, IR90, IR95, RE33, R8 and R9 are affected.\nAllow for delays and cancellations.")
    private val m2002 = msg(1789668159871, "Interrupted service: Renens VD - Morges and Bussigny\nTrain service interrupted on the line Lausanne - Genève, between Renens VD and Morges, as well as on the line Lausanne - Yverdon-les-Bains, between Renens VD and Bussigny.\nThis is due to a power failure.\nThe restriction lasts until approx. 20:30.\nLines TGV, EC, IC1, IR15, IR90, IR95, RE33, R1, R2, R3, R4, R8 and R9 are affected.\nAllow for delays and cancellations.\nPassengers travelling between Morges and Renens VD, gare use the replacement bus (EV).")
    private val m2029 = msg(1789669791722, "Interrupted service: Renens VD - Morges and Bussigny\nTrain service interrupted on the line Lausanne - Genève, between Renens VD and Morges, as well as on the line Lausanne - Yverdon-les-Bains, between Renens VD and Bussigny.\nThis is due to a power failure.\nThe restriction lasts until approx. 21:00.\nLines TGV, EC, IC1, IR15, IR90, IR95, RE33, R1, R2, R3, R4, R8 and R9 are affected.\nAllow for delays and cancellations.\nPassengers travelling between Morges and Renens VD, gare use the replacement bus (EV).")
    private val m2105 = msg(1789671911744, "Limited service: Renens VD - Morges and Bussigny\nLimited train service on the line Lausanne - Genève, between Renens VD and Morges, as well as on the line Lausanne - Yverdon-les-Bains, between Renens VD and Bussigny.\nThis is due to a power failure.\nThe restriction lasts until approx. 23:59.\nLines TGV, EC, IC1, IR15, IR90, IR95, RE33, R1, R2, R3, R4, R8 and R9 are affected.\nAllow for delays and cancellations.\nPassengers travelling between Morges and Renens VD, gare use the replacement bus (EV).")
    private val m0000 = msg(1789682459585, "Service resumed: Renens VD - Morges and Bussigny\nThe train service on the line Lausanne - Genève, between Renens VD and Morges, as well as on the line Lausanne - Yverdon-les-Bains, between Renens VD and Bussigny has been resumed.")

    /** Un moment du replay : ce que l'on savait à cette heure-là. */
    private class Moment(val fin: Long, val severity: Severity, val messages: List<DisruptionMessage>)

    // Fins estimées telles que le serveur les donne (18:30, 20:30, 21:00, 23:59)
    private val f1830 = 1789662600000
    private val f2030 = 1789669800000
    private val f2100 = 1789671600000
    private val f2359 = 1789682340000

    private val moments = listOf(
        Moment(f1830, Severity.CRITICAL, listOf(m1752)),
        Moment(f1830, Severity.CRITICAL, listOf(m1752, m1814)),
        Moment(f2030, Severity.CRITICAL, listOf(m1752, m1814, m1830)),
        Moment(f2030, Severity.CRITICAL, listOf(m1752, m1814, m1830, m2002)),
        Moment(f2100, Severity.CRITICAL, listOf(m1752, m1814, m1830, m2002, m2029)),
        Moment(f2359, Severity.CRITICAL, listOf(m1752, m1814, m1830, m2002, m2029, m2105)),
        Moment(f2359, Severity.INFO, listOf(m1752, m1814, m1830, m2002, m2029, m2105, m0000)),
    )

    private val conseiller = ConseillerVoyage() // sans IA : règles seules, profil par défaut

    @Test
    fun replay() {
        println()
        println("REPLAY — panne de courant Renens VD – Morges, jeudi 17 septembre 2026")
        println("Trajet : Lausanne → Genève. La voyageuse ouvre l'app au moment indiqué.")
        moments.forEach { moment ->
            val maintenant = moment.messages.last().timestampMillis
            val perturbation = Disruption(
                id = "2195005188970",
                cause = "Power failure",
                affectedLines = listOf("IC1", "IR15", "IR90", "IR95", "RE33", "R8", "R9"),
                affectedStations = listOf("8501118", "8501037"),
                startTimeMillis = 1789658549896,
                estimatedEndMillis = moment.fin,
                severity = moment.severity,
                confidence = 0.7,
                lastUpdateMillis = maintenant,
                messages = moment.messages,
            )
            val trajet = Journey("Lausanne", "Genève", maintenant, maintenant + 36 * 60_000L)
            ecran(
                "${heure(maintenant)} · SBB : « ${moment.messages.last().text.lineSequence().first()} »",
                trajet,
                lancer { conseiller.conseiller(perturbation, trajet) },
            )
        }
        println()
    }

    /** Situations de nuit inventées (pas des données réelles) pour montrer hôtel, taxi et « plus de train ». */
    @Test
    fun scenarios() {
        val min = 60_000L
        val vingtDeuxHeures = 1_789_502_400_000L // 15.09.2026, 22:00 en Suisse
        fun situation(titre: String, cause: String, a: Long, fin: Long?, texte: String) {
            val d = Disruption(
                id = "demo", cause = cause, affectedLines = listOf("IC1"), affectedStations = listOf("Lausanne", "Genève"),
                startTimeMillis = a - 20 * min, estimatedEndMillis = fin, severity = Severity.CRITICAL,
                confidence = 0.75, lastUpdateMillis = a, messages = listOf(msg(a, texte)),
            )
            val trajet = Journey("Lausanne", "Genève", a, a + 36 * min)
            ecran("${heure(a)} · $titre", trajet, lancer { conseiller.conseiller(d, trajet) })
        }
        println()
        println("SCÉNARIOS DE NUIT (inventés) — trajet Lausanne → Genève")
        situation(
            "Panne technique, reprise annoncée à 02:00", "Dérangement technique",
            vingtDeuxHeures + 75 * min, vingtDeuxHeures + 4 * 60 * min,
            "Lausanne – Genève : trafic interrompu (dérangement technique). Reprise prévue vers 02:00.",
        )
        situation(
            "Accident de personne, durée inconnue", "Accident de personne",
            vingtDeuxHeures + 90 * min, null,
            "Lausanne – Genève : trafic interrompu suite à un accident de personne. Durée indéterminée.",
        )
        situation(
            "Tempête, reprise annoncée à 01:30", "Intempéries",
            vingtDeuxHeures + 30 * min, vingtDeuxHeures + 210 * min,
            "Lausanne – Genève : trafic interrompu en raison d'une tempête. Reprise prévue vers 01:30.",
        )
        println()
    }

    /** Affiche une recommandation comme l'écran de l'app. */
    private fun ecran(entete: String, trajet: Journey, reco: Recommandation) {
        println()
        println("┌──────────────────────────────────────────────────────────────")
        println("│ $entete")
        println("│")
        println("│ ${reco.titre.uppercase()}")
        reco.retardSiAttenteMinutes(trajet)?.let { if (it > 0) println("│ Si vous attendez : +$it min") }
        if (!reco.faits.resolue) println("│ Chance qu'un train circule encore ce soir : ${reco.chanceTrainCeSoirPourcent} %")
        println("│")
        println("│ ${reco.conseil}")
        println("│")
        reco.recommandee?.let { println("│ ⭐ RECOMMANDÉ : ${it.title}${prix(it)}") ; println("│    ${it.explanation}") }
        if (reco.alternatives.isNotEmpty()) {
            println("│")
            println("│ Autres possibilités :")
            reco.alternatives.forEach { println("│  • ${it.title}${prix(it)}") }
        }
        reco.indemnite?.let { println("│"); println("│ 💶 ${it.title}") }
        println("└──────────────────────────────────────────────────────────────")
    }

    private fun prix(o: lauzhack.sbb.winners.model.Option): String = when {
        o.type == OptionType.WAIT || o.type == OptionType.ALTERNATIVE_ROUTE -> ""
        o.reimbursable && o.costToTravellerChf == 0.0 -> " — pris en charge"
        else -> " — environ ${o.costToTravellerChf.toInt()} CHF"
    }

    private fun <T> lancer(bloc: suspend () -> T): T {
        var resultat: Result<T>? = null
        bloc.startCoroutine(Continuation(EmptyCoroutineContext) { resultat = it })
        return resultat!!.getOrThrow()
    }

    private fun msg(t: Long, texte: String) = DisruptionMessage(t, MessageSource.PUBLIC_TICKER, texte)

    /** Heure suisse (septembre = UTC+2) au format « 20:29 ». */
    private fun heure(millis: Long): String {
        val minutesDansJour = ((millis + 2 * 3_600_000L).mod(86_400_000L)) / 60_000L
        return "${(minutesDansJour / 60).toString().padStart(2, '0')}:${(minutesDansJour % 60).toString().padStart(2, '0')}"
    }
}
