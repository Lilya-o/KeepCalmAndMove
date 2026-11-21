package lauzhack.sbb.winners

import lauzhack.sbb.winners.model.*

object SampleData {
    private const val T0 = 0L
    private fun min(m: Int) = T0 + m * 60_000L

    val disruption = Disruption(
        id = "demo-1",
        cause = "Difficultés techniques sur l'infrastructure",
        affectedLines = listOf("RE33", "RL1", "RL3", "RL4", "TERL2"),
        affectedStations = listOf("Annemasse", "Genève"),
        startTimeMillis = min(0),
        estimatedEndMillis = min(80),
        severity = Severity.MAJOR,
        confidence = 0.6,
        lastUpdateMillis = min(15),
        messages = listOf(
            DisruptionMessage(min(0), MessageSource.PUBLIC_TICKER, "Trafic limité entre Annemasse et Genève."),
        ),
    )

    val journey = Journey("Annemasse", "Genève", min(10), min(35))
    val profile = Profile(urgency = 0.8, budgetSensitivity = 0.3, flexibility = 0.2)

    val options = listOf(
        Option(OptionType.WAIT, "Attendre la reprise", min(120), 0.0, false, 0.2, 0.5, "Reprise estimée vers 12:00."),
        Option(OptionType.TAXI, "Taxi jusqu'à Genève", min(50), 60.0, true, 0.3, 0.9, "Plus rapide, remboursement à vérifier."),
    )
}