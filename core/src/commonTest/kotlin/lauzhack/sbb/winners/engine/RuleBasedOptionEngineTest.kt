
package lauzhack.sbb.winners.engine

import lauzhack.sbb.winners.model.Disruption
import lauzhack.sbb.winners.model.DisruptionMessage
import lauzhack.sbb.winners.model.MessageSource
import lauzhack.sbb.winners.model.Journey
import lauzhack.sbb.winners.model.Option
import lauzhack.sbb.winners.model.OptionType
import lauzhack.sbb.winners.model.Profile
import lauzhack.sbb.winners.model.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleBasedOptionEngineTest {

    private val engine = RuleBasedOptionEngine()

    private val min = 60_000L
    private val heure = 60 * min

    // ---------------------------------------------------------------------
    // Profils
    // ---------------------------------------------------------------------
    private val pressee = Profile(urgency = 1.0, budgetSensitivity = 0.0, flexibility = 0.5)
    private val econome = Profile(urgency = 0.0, budgetSensitivity = 1.0, flexibility = 1.0)
    private val equilibre = Profile(urgency = 0.5, budgetSensitivity = 0.5, flexibility = 0.5)

    // ---------------------------------------------------------------------
    // Options de l'exemple (chiffres inventés, uniquement pour les tests)
    // L'heure d'arrivée prévue est 0 : etaMillis = retard.
    // ---------------------------------------------------------------------
    private val attendre = Option(
        type = OptionType.WAIT, title = "Attendre", etaMillis = 80 * min,
        costToTravellerChf = 0.0, reimbursable = true, effort = 0.1, reliability = 0.6, explanation = "",
    )
    private val taxi = Option(
        type = OptionType.TAXI, title = "Taxi", etaMillis = 15 * min,
        costToTravellerChf = 60.0, reimbursable = false, effort = 0.3, reliability = 0.9, explanation = "",
    )
    private val bus = Option(
        type = OptionType.ALTERNATIVE_ROUTE, title = "Bus", etaMillis = 40 * min,
        costToTravellerChf = 0.0, reimbursable = false, effort = 0.6, reliability = 0.8, explanation = "",
    )
    private val exemple = listOf(attendre, taxi, bus)

    private fun List<Option>.position(type: OptionType) = indexOfFirst { it.type == type }

    // =====================================================================
    // Tests du classement (rank)
    // =====================================================================

    @Test
    fun personnePressee_prefereLeTaxiAAttendre() {
        val classement = engine.rank(exemple, pressee)
        assertEquals(OptionType.TAXI, classement.first().type)
        assertTrue(classement.position(OptionType.TAXI) < classement.position(OptionType.WAIT))
    }

    @Test
    fun personneEconomeEtFlexible_prefereAttendreAuTaxi() {
        val classement = engine.rank(exemple, econome)
        assertTrue(classement.position(OptionType.WAIT) < classement.position(OptionType.TAXI))
        assertEquals(OptionType.TAXI, classement.last().type)
    }

    @Test
    fun profilEquilibre_gardeToutesLesOptions_etNotesEntre0Et1() {
        val notes = engine.scored(exemple, equilibre)
        assertEquals(3, notes.size)
        notes.forEach { (_, note) -> assertTrue(note in 0.0..1.0, "note hors de [0,1] : $note") }

        val classement = engine.rank(exemple, equilibre)
        val notesTriees = classement.map { o -> notes.first { it.first == o }.second }
        assertEquals(notesTriees.sortedDescending(), notesTriees)
    }

    @Test
    fun lesPoidsSAdditionnentA1() {
        listOf(pressee, econome, equilibre).forEach { profil ->
            val p = engine.poids(profil)
            assertTrue(kotlin.math.abs(p.temps + p.cout + p.effort - 1.0) < 1e-9)
        }
    }

    @Test
    fun uneSeuleOption_pasDeDivisionParZero() {
        val notes = engine.scored(listOf(taxi), pressee)
        assertEquals(1, notes.size)
        assertFalse(notes.first().second.isNaN())
    }

    @Test
    fun listeVide_renvoieListeVide() {
        assertTrue(engine.rank(emptyList(), pressee).isEmpty())
    }

    // =====================================================================
    // Tests de la génération (generateOptions)
    // =====================================================================

    // 15 septembre 2026, 20:00 UTC = 22:00 en Suisse (heure d'été)
    private val depart = 1_789_502_400_000L
    private val trajet = Journey(
        origin = "Annemasse", destination = "Genève",
        departureMillis = depart, plannedArrivalMillis = depart + 30 * min,
    )

    private fun perturbation(finEstimee: Long?, cause: String = "Dérangement technique") = Disruption(
        id = "test-1",
        cause = cause,
        affectedLines = listOf("RL1"),
        affectedStations = listOf("Annemasse", "Genève"),
        startTimeMillis = depart - 10 * min,
        estimatedEndMillis = finEstimee,
        severity = Severity.MAJOR,
        confidence = 0.8,
        lastUpdateMillis = depart,
        messages = emptyList(),
    )

    @Test
    fun finInconnue_attenteAvecFiabiliteBasse() {
        val options = engine.generateOptions(perturbation(finEstimee = null), trajet)
        val attente = options.first { it.type == OptionType.WAIT }
        assertTrue(attente.reliability <= 0.3)
    }

    @Test
    fun petitRetard_pasDIndemnite_niHotel() {
        // Reprise 20 min après le départ → environ 20 min de retard
        val options = engine.generateOptions(perturbation(finEstimee = depart + 20 * min), trajet)
        assertTrue(options.none { it.type == OptionType.REFUND })
        assertTrue(options.none { it.type == OptionType.HOTEL })
    }

    @Test
    fun retardDePlusDUneHeure_indemniteProposee() {
        // Reprise 80 min après le départ → environ 80 min de retard
        val options = engine.generateOptions(perturbation(finEstimee = depart + 80 * min), trajet)
        val indemnite = options.firstOrNull { it.type == OptionType.REFUND }
        assertTrue(indemnite != null)
        assertTrue(indemnite.title.contains("25"))
    }

    @Test
    fun arriveeLeLendemain_hotelProposeEtTaxiGratuit() {
        // Reprise 3 h après le départ (22:00 → 01:00) : on n'arrive plus aujourd'hui
        val options = engine.generateOptions(perturbation(finEstimee = depart + 3 * heure), trajet)
        assertTrue(options.any { it.type == OptionType.HOTEL })
        val taxiGenere = options.first { it.type == OptionType.TAXI }
        assertTrue(taxiGenere.reimbursable)
        assertEquals(0.0, taxiGenere.costToTravellerChf)
    }

    // =====================================================================
    // Tests de la cause (tempête = pas la faute de la compagnie)
    // =====================================================================

    @Test
    fun tempete_arriveeLeLendemain_taxiEtHotelPasPayes() {
        // Même situation que le test précédent, mais à cause d'une tempête
        val options = engine.generateOptions(
            perturbation(finEstimee = depart + 3 * heure, cause = "Intempéries : arbre sur la voie"),
            trajet,
        )
        val taxiGenere = options.first { it.type == OptionType.TAXI }
        assertFalse(taxiGenere.reimbursable)
        assertTrue(taxiGenere.costToTravellerChf > 0.0)

        val hotel = options.first { it.type == OptionType.HOTEL }
        assertFalse(hotel.reimbursable)
        assertTrue(hotel.costToTravellerChf > 0.0)
    }

    @Test
    fun tempete_indemniteDeRetardToujoursProposee() {
        // L'indemnité de 25 % / 50 % est due même en cas de force majeure
        val options = engine.generateOptions(
            perturbation(finEstimee = depart + 3 * heure, cause = "Unwetter"),
            trajet,
        )
        assertTrue(options.any { it.type == OptionType.REFUND })
    }

    @Test
    fun reconnaissanceDeLaCause() {
        // Causes météo / naturelles → hors responsabilité
        assertTrue(Rules.causeHorsResponsabilite("Intempéries"))
        assertTrue(Rules.causeHorsResponsabilite("Tempête entre Lausanne et Genève"))
        assertTrue(Rules.causeHorsResponsabilite("Unwetter"))
        assertTrue(Rules.causeHorsResponsabilite("Starker Schneefall"))
        assertTrue(Rules.causeHorsResponsabilite("Maltempo"))
        // Causes techniques → la compagnie est responsable
        assertFalse(Rules.causeHorsResponsabilite("Dérangement technique"))
        assertFalse(Rules.causeHorsResponsabilite("Panne de signalisation à Geneve"))
        assertFalse(Rules.causeHorsResponsabilite("Baumassnahmen"))
    }

    // =====================================================================
    // Scénario réaliste : incendie entre Genève et Lausanne à 22h00,
    // voie interrompue, durée inconnue.
    // =====================================================================

    private val genevelausanne = Journey(
        origin = "Genève", destination = "Lausanne",
        departureMillis = depart, plannedArrivalMillis = depart + 40 * min,
    )

    private fun incendie(avecBus: Boolean = false) = Disruption(
        id = "feu",
        cause = "Incendie à proximité de la voie",
        affectedLines = listOf("IC1", "IR90"),
        affectedStations = listOf("Genève", "Lausanne"),
        startTimeMillis = depart - 5 * min,
        estimatedEndMillis = null,
        severity = Severity.CRITICAL,
        confidence = 0.5,
        lastUpdateMillis = depart,
        messages = listOf(
            DisruptionMessage(
                timestampMillis = depart,
                source = MessageSource.PUBLIC_TICKER,
                text = "Genève - Lausanne : trafic interrompu. Durée indéterminée." +
                        if (avecBus) " Des bus de remplacement (EV) circulent." else "",
            ),
        ),
    )

    @Test
    fun incendie22h_repriseCeSoirTresImprobable() {
        val analyse = engine.analyser(incendie(), genevelausanne)
        assertEquals(RuleBasedOptionEngine.NiveauChance.TRES_FAIBLE, analyse.niveauChance)
        assertEquals(Rules.Responsabilite.INCERTAINE, analyse.responsabilite)
        assertTrue(analyse.conseil.contains("personnel CFF"))
    }

    @Test
    fun incendie22h_pasDItineraireInvente() {
        val options = engine.generateOptions(incendie(avecBus = false), genevelausanne)
        assertTrue(options.none { it.type == OptionType.ALTERNATIVE_ROUTE })
    }

    @Test
    fun incendie22h_busAnnonce_busPropose() {
        val options = engine.generateOptions(incendie(avecBus = true), genevelausanne)
        assertTrue(options.any { it.type == OptionType.ALTERNATIVE_ROUTE })
    }

    @Test
    fun incendie22h_hotelPropose_taxiPasPromisGratuit() {
        val options = engine.generateOptions(incendie(), genevelausanne)
        assertTrue(options.any { it.type == OptionType.HOTEL })
        val taxiGenere = options.first { it.type == OptionType.TAXI }
        assertFalse(taxiGenere.reimbursable)          // responsabilité incertaine : on ne promet rien
        assertTrue(taxiGenere.costToTravellerChf > 100.0) // ~62 km : bien plus cher qu'un petit trajet
        assertTrue(taxiGenere.explanation.contains("bon taxi"))
        // L'attente n'est plus « attendre la reprise » mais une nuit en gare peu fiable
        val attente = options.first { it.type == OptionType.WAIT }
        assertTrue(attente.reliability <= 0.4)
    }

    @Test
    fun indemniteJamaisEnPremier() {
        val options = engine.generateOptions(incendie(), genevelausanne)
        listOf(pressee, econome, equilibre).forEach { profil ->
            val classement = engine.rank(options, profil)
            assertTrue(classement.first().type != OptionType.REFUND)
            assertEquals(OptionType.REFUND, classement.last().type)
        }
    }

    @Test
    fun distanceInconnue_pasDeTaxiInvente() {
        val trajetInconnu = Journey("Village A", "Village B", depart, depart + 30 * min)
        val options = engine.generateOptions(incendie(), trajetInconnu)
        assertTrue(options.none { it.type == OptionType.TAXI })
    }

    @Test
    fun panneTechniqueEnApresMidi_repriseProbable() {
        val quatorzeHeures = depart - 8 * heure // 14:00 en Suisse
        val trajet14h = Journey("Genève", "Lausanne", quatorzeHeures, quatorzeHeures + 40 * min)
        val panne = perturbation(finEstimee = null).copy(
            startTimeMillis = quatorzeHeures, lastUpdateMillis = quatorzeHeures,
        )
        val analyse = engine.analyser(panne, trajet14h)
        assertTrue(analyse.chanceRepriseCeSoir > 0.7)
        assertTrue(engine.generateOptions(panne, trajet14h).none { it.type == OptionType.HOTEL })
    }
    @Test
    fun milieuDeLaNuit_bloqueJusquAuMatin_hotelPropose() {
        // Comme SampleData avec T0 = 0 : on est vers 1 h du matin, reprise annoncée après le dernier train
        val uneHeure = depart + 3 * heure // 01:00 en Suisse
        val trajetNuit = Journey("Annemasse", "Genève", uneHeure + 10 * min, uneHeure + 35 * min)
        val panneNuit = perturbation(finEstimee = uneHeure + 80 * min).copy(
            startTimeMillis = uneHeure, lastUpdateMillis = uneHeure + 15 * min,
        )
        val options = engine.generateOptions(panneNuit, trajetNuit)
        val hotel = options.firstOrNull { it.type == OptionType.HOTEL }
        assertTrue(hotel != null, "l'hôtel doit être proposé quand on est bloqué jusqu'au matin")
        // Panne technique = faute de la compagnie, dernière correspondance perdue → hôtel et taxi payés
        assertTrue(hotel.reimbursable)
        assertTrue(options.first { it.type == OptionType.TAXI }.reimbursable)
    }
}
