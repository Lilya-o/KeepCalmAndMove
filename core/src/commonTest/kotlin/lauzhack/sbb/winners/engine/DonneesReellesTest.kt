package lauzhack.sbb.winners.engine

import lauzhack.sbb.winners.engine.RuleBasedOptionEngine.CategorieCause
import lauzhack.sbb.winners.engine.ia.ExtracteurParRegles
import lauzhack.sbb.winners.model.Disruption
import lauzhack.sbb.winners.model.DisruptionMessage
import lauzhack.sbb.winners.model.Journey
import lauzhack.sbb.winners.model.MessageSource
import lauzhack.sbb.winners.model.OptionType
import lauzhack.sbb.winners.model.Profile
import lauzhack.sbb.winners.model.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests sur les VRAIS messages SBB fournis par le serveur (rôle A) :
 *  - incident 2195005188970 : panne de courant Renens VD – Morges, 17.09.2026 (14 snapshots) ;
 *  - incident 2195005182293 : ligne de contact Wädenswil – Pfäffikon SZ, 10.09.2026.
 * Les textes sont copiés tels quels depuis GET /disruptions/{id}/timeline.
 */
class DonneesReellesTest {

    private val moteur = RuleBasedOptionEngine()
    private val equilibre = Profile(urgency = 0.5, budgetSensitivity = 0.5, flexibility = 0.5)

    // --- Messages réels (incident Renens, 17.09.2026, heure suisse) ---
    private val m1752 = DisruptionMessage(
        1789660366440, MessageSource.PUBLIC_TICKER,
        "Interrupted service: Renens VD - Morges\nInterrupted train service on the Lausanne - Genève line between Renens VD and Morges.\nThis is due to a power failure.\nThe restriction lasts until approx. 18:30.\nLines IC1, IR15, IR57, IR90, IR95, RE33, R8 and R9 are affected.\nAllow for delays and cancellations.",
    )
    private val m1814 = DisruptionMessage(
        1789661675007, MessageSource.PUBLIC_TICKER,
        "Cancellation of rail services between Renens and Morges\nRail services are currently suspended on the Lausanne–Geneva line between Renens VD and Morges. Potential complete interruption from 6.30pm Lausanne–Geneva. The overhead contact lines have been torn off due to smoke. All trains will be cancelled. We thank you for your understanding and will keep you informed as the situation develops.",
    )
    private val m1830 = DisruptionMessage(
        1789662604084, MessageSource.PUBLIC_TICKER,
        "Interrupted service: Renens VD - Morges\nInterrupted train service on the Lausanne - Genève line between Renens VD and Morges.\nThis is due to a power failure.\nThe restriction lasts until approx. 20:30.\nLines TGV, EC, IC1, IR15, IR90, IR95, RE33, R8 and R9 are affected.\nAllow for delays and cancellations.",
    )
    private val m2029 = DisruptionMessage(
        1789669791722, MessageSource.PUBLIC_TICKER,
        "Interrupted service: Renens VD - Morges and Bussigny\nTrain service interrupted on the line Lausanne - Genève, between Renens VD and Morges, as well as on the line Lausanne - Yverdon-les-Bains, between Renens VD and Bussigny.\nThis is due to a power failure.\nThe restriction lasts until approx. 21:00.\nLines TGV, EC, IC1, IR15, IR90, IR95, RE33, R1, R2, R3, R4, R8 and R9 are affected.\nAllow for delays and cancellations.\nPassengers travelling between Morges and Renens VD, gare use the replacement bus (EV).",
    )
    private val m2105 = DisruptionMessage(
        1789671911744, MessageSource.PUBLIC_TICKER,
        "Limited service: Renens VD - Morges and Bussigny\nLimited train service on the line Lausanne - Genève, between Renens VD and Morges, as well as on the line Lausanne - Yverdon-les-Bains, between Renens VD and Bussigny.\nThis is due to a power failure.\nThe restriction lasts until approx. 23:59.\nLines TGV, EC, IC1, IR15, IR90, IR95, RE33, R1, R2, R3, R4, R8 and R9 are affected.\nAllow for delays and cancellations.\nPassengers travelling between Morges and Renens VD, gare use the replacement bus (EV).",
    )
    private val m0000 = DisruptionMessage(
        1789682459585, MessageSource.PUBLIC_TICKER,
        "Service resumed: Renens VD - Morges and Bussigny\nThe train service on the line Lausanne - Genève, between Renens VD and Morges, as well as on the line Lausanne - Yverdon-les-Bains, between Renens VD and Bussigny has been resumed.",
    )

    private fun renens(fin: Long?, maj: Long, severity: Severity, vararg messages: DisruptionMessage) = Disruption(
        id = "2195005188970",
        cause = "Power failure",
        affectedLines = listOf("IC1", "IR15", "IR90", "IR95", "RE33", "R8", "R9"),
        affectedStations = listOf("8501118", "8501037"),
        startTimeMillis = 1789658549896,
        estimatedEndMillis = fin,
        severity = severity,
        confidence = 0.7,
        lastUpdateMillis = maj,
        messages = messages.toList(),
    )

    private fun lausanneGeneve(depart: Long) = Journey("Lausanne", "Genève", depart, depart + 36 * 60_000L)

    // ---------------------------------------------------------------------

    @Test
    fun messageEnAnglais_causeEtHeureReconnues() {
        val faits = ExtracteurParRegles.extraire(renens(1789662600000, m1752.timestampMillis, Severity.CRITICAL, m1752))
        assertEquals(CategorieCause.PANNE_TECHNIQUE, faits.categorie) // « power failure »
        assertEquals("18:30", faits.repriseHeure)                     // « until approx. 18:30 »
    }

    @Test
    fun ligneDeContactArrachee_infrastructureEndommagee() {
        val faits = ExtracteurParRegles.extraire(renens(1789662600000, m1814.timestampMillis, Severity.CRITICAL, m1752, m1814))
        assertEquals(CategorieCause.INFRASTRUCTURE_ENDOMMAGEE, faits.categorie) // « overhead contact lines torn off »
    }

    @Test
    fun heureDeRepriseRepousseeDeuxFois_estComptee() {
        // 18:30 → 20:30 → 21:00
        val faits = ExtracteurParRegles.extraire(renens(1789671600000, m2029.timestampMillis, Severity.CRITICAL, m1752, m1830, m2029))
        assertEquals(2, faits.reportsDeLaFin)
        assertTrue(faits.busDeRemplacement) // « use the replacement bus (EV) »
    }

    @Test
    fun a21h05_leBusDeRemplacementEstRecommande() {
        // Situation réelle à 21:05 : reprise repoussée 3 fois (→ 23:59), bus de remplacement en place
        val d = renens(1789682340000, m2105.timestampMillis, Severity.CRITICAL, m1752, m1814, m1830, m2029, m2105)
        val j = lausanneGeneve(m2105.timestampMillis)
        val classement = moteur.rank(moteur.generateOptions(d, j), equilibre)
        assertEquals(OptionType.ALTERNATIVE_ROUTE, classement.first().type)
        assertTrue(moteur.analyser(d, j).conseil.contains("repoussée 3 fois"))
    }

    @Test
    fun serviceResumed_uneSeuleOption_prendreSonTrain() {
        val d = renens(1789682340000, m0000.timestampMillis, Severity.INFO, m1752, m2105, m0000)
        val j = lausanneGeneve(m0000.timestampMillis)
        val options = moteur.generateOptions(d, j)
        assertEquals(1, options.size)
        assertEquals(OptionType.WAIT, options.first().type)
        assertTrue(moteur.analyser(d, j).conseil.contains("a repris"))
    }

    @Test
    fun interruptionEnCours_nEstPasConfondueAvecUneReprise() {
        // « until approx. 18:30 » annonce une reprise future, pas une reprise effective
        assertFalse(ExtracteurParRegles.extraire(renens(1789662600000, m1752.timestampMillis, Severity.CRITICAL, m1752)).resolue)
    }

    @Test
    fun wadenswil_dureeInconnue_etBusAnnonces() {
        val d = Disruption(
            id = "2195005182293",
            cause = "Problem with the overhead line",
            affectedLines = listOf("IC3", "S2", "S25"),
            affectedStations = listOf("8503206", "8503209"),
            startTimeMillis = 1789011794435,
            estimatedEndMillis = null,
            severity = Severity.CRITICAL,
            confidence = 0.6,
            lastUpdateMillis = 1789015745942,
            messages = listOf(
                DisruptionMessage(
                    1789015745942, MessageSource.PUBLIC_TICKER,
                    "Interrupted service: Wädenswil - Pfäffikon SZ\nInterrupted train service between Wädenswil and Pfäffikon SZ.\nThis is due to a defect in the overhead line.\nThe duration of the restriction is not known.\nLines IC3, IR35, S2, S8 and S25 are affected.\nReplacement buses (EV) are being organized between Wädenswil and Pfäffikon SZ. Please allow for longer waiting times.",
                ),
            ),
        )
        val faits = ExtracteurParRegles.extraire(d)
        assertTrue(faits.dureeIndeterminee) // « is not known »
        assertTrue(faits.busDeRemplacement) // « Replacement buses (EV) »
        assertEquals(CategorieCause.INFRASTRUCTURE_ENDOMMAGEE, faits.categorie) // « overhead line »
    }
}
