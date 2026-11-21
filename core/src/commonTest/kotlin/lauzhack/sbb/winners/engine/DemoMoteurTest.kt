
package lauzhack.sbb.winners.engine

import lauzhack.sbb.winners.model.Disruption
import lauzhack.sbb.winners.model.DisruptionMessage
import lauzhack.sbb.winners.model.Journey
import lauzhack.sbb.winners.model.MessageSource
import lauzhack.sbb.winners.model.Profile
import lauzhack.sbb.winners.model.Severity
import kotlin.test.Test

/**
 * DÉMO : ce n'est pas un vrai test, ça sert juste à VOIR ce que fait le moteur.
 * Clique sur la flèche verte à côté de « fun demo() » et lis le texte en bas de l'écran.
 * Change les valeurs marquées ← et relance pour voir comment la réponse change.
 */
class DemoMoteurTest {

    @Test
    fun demo() {
        val moteur = RuleBasedOptionEngine()
        val minute = 60_000L
        val heure = 60 * minute

        // ===== LA SITUATION (tu peux changer ces valeurs) =====
        val vingtDeuxHeures = 1_789_502_400_000L        // 15 septembre 2026, 22:00 en Suisse
        val depart = vingtDeuxHeures                     // ← essaie vingtDeuxHeures - 8 * heure (= 14:00)
        val cause = "Incendie à proximité de la voie"   // ← essaie "Dérangement technique" ou "Tempête"
        val finAnnoncee: Long? = null                    // ← essaie depart + 45 * minute
        val busAnnonce = false                           // ← essaie true

        val trajet = Journey(
            origin = "Genève",
            destination = "Lausanne",
            departureMillis = depart,
            plannedArrivalMillis = depart + 40 * minute,
        )
        val perturbation = Disruption(
            id = "demo",
            cause = cause,
            affectedLines = listOf("IC1", "IR90"),
            affectedStations = listOf("Genève", "Lausanne"),
            startTimeMillis = depart - 5 * minute,
            estimatedEndMillis = finAnnoncee,
            severity = Severity.CRITICAL,
            confidence = 0.5,
            lastUpdateMillis = depart,
            messages = listOf(
                DisruptionMessage(
                    timestampMillis = depart,
                    source = MessageSource.PUBLIC_TICKER,
                    text = "Genève - Lausanne : trafic interrompu. Durée indéterminée." +
                            if (busAnnonce) " Des bus de remplacement (EV) circulent." else "",
                ),
            ),
        )

        // ===== LES VOYAGEURS (valeurs entre 0.0 et 1.0) =====
        val voyageurs = listOf(
            "🏃 PRESSÉE" to Profile(urgency = 1.0, budgetSensitivity = 0.0, flexibility = 0.5),
            "💰 ÉCONOME" to Profile(urgency = 0.0, budgetSensitivity = 1.0, flexibility = 1.0),
            "⚖️ ÉQUILIBRÉE" to Profile(urgency = 0.5, budgetSensitivity = 0.5, flexibility = 0.5),
        )

        // ===== CE QUE LE MOTEUR FAIT =====
        val analyse = moteur.analyser(perturbation, trajet)
        val options = moteur.generateOptions(perturbation, trajet)

        println()
        println("SITUATION : Genève → Lausanne | cause : $cause")
        println()
        println("ÉTAPE 0 — Ce que le moteur comprend :")
        println("  Type de cause      : ${analyse.categorie}")
        println("  Responsabilité     : ${analyse.responsabilite}")
        println("  Train encore ce soir ? ${analyse.niveauChance} (${(analyse.chanceRepriseCeSoir * 100).toInt()} %)")
        println("  Bus de remplacement annoncé : ${if (analyse.busDeRemplacementAnnonce) "oui" else "non"}")
        println()
        println("  📢 CONSEIL : ${analyse.conseil}")
        println()
        println("ÉTAPE 1 — Options réalistes :")
        options.forEach { o ->
            println("  • ${o.title}")
            println("      ${o.explanation}")
            println("      Coût pour le voyageur : ${o.costToTravellerChf.toInt()} CHF | effort ${o.effort} | fiabilité ${o.reliability}")
        }

        voyageurs.forEach { (nom, profil) ->
            val notes = moteur.scored(options, profil).toMap()
            println()
            println("ÉTAPE 2 — Classement pour $nom")
            moteur.rank(options, profil).forEachIndexed { i, option ->
                val etoile = if (i == 0) " ⭐ RECOMMANDÉ" else ""
                println("  ${i + 1}. ${option.title}  →  note ${((notes[option] ?: 0.0) * 100).toInt() / 100.0}$etoile")
            }
        }
        println()
    }
}

