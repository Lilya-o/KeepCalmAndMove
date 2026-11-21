package lauzhack.sbb.winners

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import lauzhack.sbb.winners.replay.ReplayConfig
import lauzhack.sbb.winners.replay.ReplayDisruptionSource

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

/**
 * Module serveur (rôle A).
 *
 * La source de replay est injectable : par défaut on la construit au démarrage depuis la config
 * (`ReplayDisruptionSource.fromConfig`, qui lit `data/`). Les tests passent une source bâtie sur des
 * fixtures factices, donc ils ne dépendent jamais de `data/`.
 *
 * Les endpoints renvoient directement les modèles de `core` (déjà `@Serializable`) :
 *  - `GET /disruptions`                         liste des incidents de démo
 *  - `GET /disruptions/{id}?at=<epoch ms UTC>`  état connu à cet instant (`at` absent = dernier état)
 *  - `GET /disruptions/{id}/timeline`           tous les snapshots, du plus ancien au plus récent
 *
 * 404 si l'id est inconnu ; 400 si `at` n'est pas un entier (millis) valide.
 */
fun Application.module(
    source: ReplayDisruptionSource = ReplayDisruptionSource.fromConfig(ReplayConfig()),
) {
    install(ContentNegotiation) {
        json(Json { encodeDefaults = true })
    }

    routing {
        get("/disruptions") {
            call.respond(source.list())
        }

        get("/disruptions/{id}") {
            val id = call.parameters["id"]!!
            val atParam = call.request.queryParameters["at"]

            // `at` absent -> dernier état connu (Long.MAX_VALUE). Présent mais non entier -> 400.
            val at: Long = when (atParam) {
                null -> Long.MAX_VALUE
                else -> atParam.toLongOrNull()
                    ?: return@get call.respondText(
                        "Paramètre 'at' invalide: '$atParam' (attendu: epoch millis UTC)",
                        status = HttpStatusCode.BadRequest,
                    )
            }

            val state = source.stateAt(id, at)
                ?: return@get call.respondText(
                    "Incident inconnu ou sans état connu à cet instant: '$id'",
                    status = HttpStatusCode.NotFound,
                )
            call.respond(state)
        }

        get("/disruptions/{id}/timeline") {
            val id = call.parameters["id"]!!
            if (id !in source.configuredIds) {
                return@get call.respondText(
                    "Incident inconnu: '$id'",
                    status = HttpStatusCode.NotFound,
                )
            }
            call.respond(source.timeline(id))
        }
    }
}
