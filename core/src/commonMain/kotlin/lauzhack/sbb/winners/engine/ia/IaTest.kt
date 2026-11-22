
package lauzhack.sbb.winners.engine.ia

import lauzhack.sbb.winners.engine.RuleBasedOptionEngine
import lauzhack.sbb.winners.engine.RuleBasedOptionEngine.CategorieCause
import lauzhack.sbb.winners.engine.Rules.Responsabilite
import lauzhack.sbb.winners.model.Disruption
import lauzhack.sbb.winners.model.DisruptionMessage
import lauzhack.sbb.winners.model.Journey
import lauzhack.sbb.winners.model.MessageSource
import lauzhack.sbb.winners.model.Profile
import lauzhack.sbb.winners.model.Severity
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lance une fonction suspend dans un test (le faux modèle ne suspend jamais vraiment). */
private fun <T> lancer(bloc: suspend () -> T): T {
    var resultat: Result<T>? = null
    bloc.startCoroutine(Continuation(EmptyCoroutineContext) { resultat = it })
    return resultat!!.getOrThrow()
}

/** Faux modèle : répond selon la consigne reçue, et compte ses appels. */
private class FauxModele(
    val extraction: String? = null,
    val redaction: ((String) -> String?)? = null,
) : ModeleIA {
    var appels = 0
    override suspend fun repondre(consigneSysteme: String, message: String): String? {
        appels++
        return if (consigneSysteme == ExtracteurHybride.CONSIGNE_EXTRACTION) extraction else redaction?.invoke(message)
    }
}

class IaTest {

    private val min = 60_000L
    private val depart = 1_789_502_400_000L - 4 * 60 * min // 18:00 en Suisse

    private val trajet = Journey("Lausanne", "Genève", depart, depart + 36 * min)
    private val equilibre = Profile(urgency = 0.5, budgetSensitivity = 0.5, flexibility = 0.5)

    private fun perturbation(cause: String, vararg messages: Pair<MessageSource, String>) = Disruption(
        id = "t", cause = cause,
        affectedLines = listOf("IR90"), affectedStations = listOf("Lausanne", "Genève"),
        startTimeMillis = depart - 10 * min, estimatedEndMillis = null,
        severity = Severity.MAJOR, confidence = 0.7, lastUpdateMillis = depart,
        messages = messages.mapIndexed { i, (s, t) -> DisruptionMessage(depart - (10 - i) * min, s, t) },
    )

    // Un message interne en jargon, que les règles ne comprennent pas
    private val jargon = perturbation(
        "Ereignis Morges",
        MessageSource.INTERNAL_TICKER to "ZB: Stw-Stoerg. Morges, Gl. 1+2 gesperrt, Prognose 60'",
    )

    private val reponseJargon = """
        CATEGORIE: PANNE_TECHNIQUE
        REPRISE: INCONNUE
        DUREE: 60
        BUS: NON
        TENDANCE: STABLE
    """.trimIndent()

    // ---------------------------------------------------------------------
    // Efficacité
    // ---------------------------------------------------------------------

    @Test
    fun reglesSuffisent_aucunAppelIA() {
        val ia = FauxModele(extraction = reponseJargon)
        val extracteur = ExtracteurHybride(ia)
        val d = perturbation("Dérangement technique", MessageSource.PUBLIC_TICKER to "Lausanne - Genève : durée indéterminée.")
        val faits = lancer { extracteur.extraire(d) }
        assertEquals(0, ia.appels)
        assertEquals(CategorieCause.PANNE_TECHNIQUE, faits.categorie)
        assertEquals(1, extracteur.stats.evitesGraceAuxRegles)
    }

    @Test
    fun replay_memeMessages_unSeulAppelIA() {
        val ia = FauxModele(extraction = reponseJargon)
        val extracteur = ExtracteurHybride(ia)
        repeat(30) { lancer { extracteur.extraire(jargon) } } // 30 étapes de replay
        assertEquals(1, ia.appels)
        assertEquals(29, extracteur.stats.reponsesDuCache)
    }

    @Test
    fun conseilComplet_auPlusDeuxAppelsIA() {
        val ia = FauxModele(
            extraction = reponseJargon,
            redaction = { message -> message.lines().filter { it.contains(" | ") }.joinToString("\n") { it } },
        )
        val conseiller = ConseillerVoyage(modele = ia)
        val reco = lancer { conseiller.conseiller(jargon, trajet, equilibre, Langue.DE) }
        assertTrue(ia.appels <= 2, "une extraction + une rédaction pour TOUTES les options")
        assertTrue(reco.options.isNotEmpty())
    }

    // ---------------------------------------------------------------------
    // L'IA complète les règles
    // ---------------------------------------------------------------------

    @Test
    fun jargonInterne_lIAComplete_categorieEtDuree() {
        val faits = lancer { ExtracteurHybride(FauxModele(extraction = reponseJargon)).extraire(jargon) }
        assertEquals(CategorieCause.PANNE_TECHNIQUE, faits.categorie)
        assertEquals(60, faits.dureeMinutes)
        assertEquals(SourceDesFaits.REGLES_ET_IA, faits.source)

        // La durée lue dans le message devient une heure de fin estimée, avec une confiance réduite
        val complete = ConseillerVoyage().completerFin(jargon, faits)
        assertEquals(jargon.messages.last().timestampMillis + 60 * min, complete.estimatedEndMillis)
        assertTrue(complete.confidence <= ConseillerVoyage.CONFIANCE_FIN_LUE_DANS_UN_MESSAGE)
    }

    // ---------------------------------------------------------------------
    // Garde-fous
    // ---------------------------------------------------------------------

    @Test
    fun lIANePeutPasRendreLaCompagnieResponsable() {
        // L'IA dit « panne technique », mais seules les RÈGLES peuvent déclencher des promesses d'argent
        val faits = lancer { ExtracteurHybride(FauxModele(extraction = reponseJargon)).extraire(jargon) }
        assertEquals(Responsabilite.INCERTAINE, faits.responsabilite)
    }

    @Test
    fun heureInventeeParLIA_rejetee() {
        val extracteur = ExtracteurHybride(FauxModele(extraction = reponseJargon.replace("REPRISE: INCONNUE", "REPRISE: 19:30")))
        val faits = lancer { extracteur.extraire(jargon) }
        assertNull(faits.repriseHeure) // 19:30 n'est écrit nulle part
        assertEquals(1, extracteur.stats.reponsesRejetees)
    }

    @Test
    fun meteoSelonLIA_rendPlusPrudent() {
        val d = perturbation("Ereignis", MessageSource.INTERNAL_TICKER to "Baum auf Fahrbahn nach Gewitter")
        val faits = lancer { ExtracteurHybride(FauxModele(extraction = reponseJargon.replace("PANNE_TECHNIQUE", "METEO"))).extraire(d) }
        assertEquals(Responsabilite.PAS_LA_COMPAGNIE, faits.responsabilite)
    }

    @Test
    fun reponseIllisible_onGardeLesRegles() {
        val faits = lancer { ExtracteurHybride(FauxModele(extraction = "Désolé, je ne sais pas.")).extraire(jargon) }
        assertEquals(CategorieCause.INCONNUE, faits.categorie)
        assertNull(faits.dureeMinutes)
    }

    @Test
    fun gardeFou_refuseChiffreOuPromesseAjoutes() {
        val original = "Arrivée estimée vers 23:03. Prix estimé : environ 224 CHF."
        assertTrue(GardeFou.accepte(original, "Ankunft gegen 23:03. Preis etwa 224 CHF."))
        assertFalse(GardeFou.accepte(original, "Ankunft gegen 23:30. Preis etwa 224 CHF.")) // heure changée
        assertFalse(GardeFou.accepte(original, "Ankunft gegen 23:03, etwa 224 CHF, wird erstattet.")) // promesse
    }

    @Test
    fun redaction_texteRefuse_onGardeLOriginal() {
        val ia = FauxModele(redaction = { "1 | Le taxi est remboursé, arrivée 22:10." })
        val (textes, _) = lancer { RedacteurExplications(ia).reformuler(listOf("Taxi : environ 224 CHF, à tes frais."), Langue.FR) }
        assertEquals("Taxi : environ 224 CHF, à tes frais.", textes[0])
    }

    // ---------------------------------------------------------------------
    // Sans IA, tout marche
    // ---------------------------------------------------------------------

    @Test
    fun iaEnPanne_leConseilEstQuandMemeDonne() {
        val ia = FauxModele(extraction = null, redaction = { null })
        val conseiller = ConseillerVoyage(modele = ia)
        val reco = lancer { conseiller.conseiller(jargon, trajet, equilibre) }
        assertTrue(reco.options.isNotEmpty())
        assertFalse(reco.iaUtilisee)
        assertTrue(conseiller.stats.echecsIA >= 1)
    }

    @Test
    fun sansIA_memeClassementQueLeMoteur() {
        val moteur = RuleBasedOptionEngine()
        val d = perturbation("Dérangement technique", MessageSource.PUBLIC_TICKER to "Durée indéterminée.")
        val attendu = moteur.rank(moteur.generateOptions(d, trajet), equilibre).map { it.type }
        val reco = lancer { ConseillerVoyage(moteur, modele = null).conseiller(d, trajet, equilibre) }
        assertEquals(attendu, reco.options.map { it.type })
    }

    // ---------------------------------------------------------------------
    // Règles de lecture des heures
    // ---------------------------------------------------------------------

    @Test
    fun heureDeReprise_lueDansLesMessages() {
        assertEquals("18:45", ExtracteurParRegles.heureDeReprise("reprise prévue vers 18h45."))
        assertEquals("18:45", ExtracteurParRegles.heureDeReprise("verkehr wieder ab 18.45 uhr"))
        assertNull(ExtracteurParRegles.heureDeReprise("restriction jusqu'au 06.09.2026")) // une date, pas une heure
    }

    @Test
    fun heureDeRepriseDansUnMessage_devientLaFinEstimee() {
        val d = perturbation("Dérangement technique", MessageSource.PUBLIC_TICKER to "Reprise prévue vers 18:45.")
        val reco = lancer { ConseillerVoyage().conseiller(d, trajet, equilibre) }
        assertTrue(reco.conseil.contains("18:45"))
    }
}


