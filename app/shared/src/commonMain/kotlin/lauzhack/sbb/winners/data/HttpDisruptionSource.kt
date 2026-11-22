package lauzhack.sbb.winners.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import lauzhack.sbb.winners.DisruptionSource
import lauzhack.sbb.winners.model.Disruption

/** Config JSON commune aux deux moteurs HTTP (tolère les champs inconnus). */
fun HttpClientConfig<*>.configurerJson() {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

/**
 * Implémentation côté app du contrat [DisruptionSource] de `core`, via HTTP vers le serveur de A.
 *
 * Endpoints (voir server/Application.kt) :
 *  - GET /disruptions                         -> liste
 *  - GET /disruptions/{id}?at=<epoch ms UTC>  -> état connu à cet instant
 *  - GET /disruptions/{id}/timeline           -> tous les snapshots, du plus ancien au plus récent
 */
class HttpDisruptionSource(
    private val baseUrl: String = urlServeurParDefaut,
    private val client: HttpClient = creerClientHttp(),
) : DisruptionSource {

    override suspend fun list(): List<Disruption> =
        client.get("$baseUrl/disruptions").body()

    override suspend fun stateAt(id: String, atMillis: Long): Disruption? =
        client.get("$baseUrl/disruptions/$id") { parameter("at", atMillis) }.body()

    /** Toute l'historique d'un incident, pour alimenter le curseur de replay localement. */
    suspend fun timeline(id: String): List<Disruption> =
        client.get("$baseUrl/disruptions/$id/timeline").body()
}
