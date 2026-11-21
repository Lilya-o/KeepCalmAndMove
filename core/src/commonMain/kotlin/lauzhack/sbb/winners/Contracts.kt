package lauzhack.sbb.winners

import lauzhack.sbb.winners.model.*

/** Implémenté par B (core). Utilisé par C (app) et le serveur. */
interface OptionEngine {
    fun generateOptions(disruption: Disruption, journey: Journey): List<Option>
    fun rank(options: List<Option>, profile: Profile): List<Option>
}

/** Implémenté par A (server) côté serveur, et par un client HTTP côté app. */
interface DisruptionSource {
    suspend fun list(): List<Disruption>
    /** État connu de l'incident à l'instant donné (replay). */
    suspend fun stateAt(id: String, atMillis: Long): Disruption?
}