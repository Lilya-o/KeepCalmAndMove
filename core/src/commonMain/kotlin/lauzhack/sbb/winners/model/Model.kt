package lauzhack.sbb.winners.model

import kotlinx.serialization.Serializable

@Serializable
enum class Severity { INFO, MINOR, MAJOR, CRITICAL }

@Serializable
enum class MessageSource { PUBLIC_TICKER, INTERNAL_TICKER, TIMETABLE }

@Serializable
enum class OptionType { ALTERNATIVE_ROUTE, WAIT, TAXI, HOTEL, REFUND }

@Serializable
data class DisruptionMessage(
    val timestampMillis: Long,
    val source: MessageSource,
    val text: String,
)

@Serializable
data class Disruption(
    val id: String,
    val cause: String,
    val affectedLines: List<String>,
    val affectedStations: List<String>,
    val startTimeMillis: Long,
    val estimatedEndMillis: Long?,   // null = inconnu
    val severity: Severity,
    val confidence: Double,          // 0.0 à 1.0
    val lastUpdateMillis: Long,
    val messages: List<DisruptionMessage>,
)

@Serializable
data class Journey(
    val origin: String,
    val destination: String,
    val departureMillis: Long,
    val plannedArrivalMillis: Long,
)

/** Toutes les valeurs entre 0.0 et 1.0 */
@Serializable
data class Profile(
    val urgency: Double,
    val budgetSensitivity: Double,
    val flexibility: Double,
)

@Serializable
data class Option(
    val type: OptionType,
    val title: String,
    val etaMillis: Long?,            // null = arrivée impossible ou inconnue
    val costToTravellerChf: Double,  // coût avant remboursement éventuel
    val reimbursable: Boolean,
    val effort: Double,              // 0.0 (facile) à 1.0 (pénible)
    val reliability: Double,         // 0.0 à 1.0
    val explanation: String,
)