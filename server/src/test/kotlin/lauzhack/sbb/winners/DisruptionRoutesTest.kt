package lauzhack.sbb.winners

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import lauzhack.sbb.winners.model.Disruption
import lauzhack.sbb.winners.replay.CsvReader
import lauzhack.sbb.winners.replay.RawData
import lauzhack.sbb.winners.replay.ReplayDisruptionSource
import lauzhack.sbb.winners.replay.TimestampParse
import lauzhack.sbb.winners.replay.ViaRow
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests Ktor des endpoints de replay, sur des fixtures INVENTÉES (server/src/test/resources/fixtures/).
 * Aucune dépendance au dossier `data/` : la source est bâtie à la main et injectée dans `module(...)`.
 */
class DisruptionRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun res(path: String): InputStream =
        checkNotNull(javaClass.getResourceAsStream(path)) { "fixture manquante: $path" }

    private fun ts(s: String): Long = checkNotNull(TimestampParse.publicToMillis(s)) { "ts illisible: $s" }

    /** Même construction que ReplayParsingTest : deux incidents factices FAKE_1 / FAKE_2. */
    private fun fixtureSource(): ReplayDisruptionSource {
        val incidents = res("/fixtures/incident.csv").use { CsvReader.incidents(it) } +
            res("/fixtures/incident_audit.csv").use { CsvReader.incidents(it) }
        val messages = res("/fixtures/message.csv").use { CsvReader.messages(it) } +
            res("/fixtures/message_audit.csv").use { CsvReader.messages(it) }
        val reasons = res("/fixtures/reason.csv").use { CsvReader.reasons(it) }
        val via = listOf(
            ViaRow("FAKE_1", ts("2026-01-01 08:30:00.000 +0100"), "IC1", "8500000", "", "EG_TEST"),
            ViaRow("FAKE_1", ts("2026-01-01 11:30:00.000 +0100"), "IC1, S2, S8", "8500000", "8500001", "EG_TEST"),
        )
        return ReplayDisruptionSource(RawData(incidents, messages, reasons, via), listOf("FAKE_1", "FAKE_2"))
    }

    private fun ApplicationTestBuilder.installModule() = application { module(fixtureSource()) }

    @Test
    fun listReturnsBothDisruptions() = testApplication {
        installModule()
        val response = client.get("/disruptions")
        assertEquals(HttpStatusCode.OK, response.status)
        val list = json.decodeFromString<List<Disruption>>(response.bodyAsText())
        assertEquals(listOf("FAKE_1", "FAKE_2"), list.map { it.id })
    }

    @Test
    fun missingAtReturnsLatestState() = testApplication {
        installModule()
        val response = client.get("/disruptions/FAKE_1")
        assertEquals(HttpStatusCode.OK, response.status)
        val state = json.decodeFromString<Disruption>(response.bodyAsText())
        // Dernière version connue = celle d'incident.csv (ENDED, last_modified 13:00).
        assertEquals("FAKE_1", state.id)
        assertEquals(ts("2026-01-01 13:00:00.000 +0100"), state.lastUpdateMillis)
    }

    @Test
    fun atSelectsStateAtInstant() = testApplication {
        installModule()
        // 09:30 -> v2 : fin estimée ~10:00.
        val mid = json.decodeFromString<Disruption>(
            client.get("/disruptions/FAKE_1?at=${ts("2026-01-01 09:30:00.000 +0100")}").bodyAsText(),
        )
        // 12:30 -> v3 : fin estimée ~12:00.
        val late = json.decodeFromString<Disruption>(
            client.get("/disruptions/FAKE_1?at=${ts("2026-01-01 12:30:00.000 +0100")}").bodyAsText(),
        )
        assertEquals(ts("2026-01-01 10:00:00.000 +0100"), mid.estimatedEndMillis)
        assertEquals(ts("2026-01-01 12:00:00.000 +0100"), late.estimatedEndMillis)
        assertTrue(mid.estimatedEndMillis != late.estimatedEndMillis, "l'état doit dépendre de 'at'")
    }

    @Test
    fun timelineReturnsSnapshotsInOrder() = testApplication {
        installModule()
        val response = client.get("/disruptions/FAKE_1/timeline")
        assertEquals(HttpStatusCode.OK, response.status)
        val snapshots = json.decodeFromString<List<Disruption>>(response.bodyAsText())
        assertEquals(4, snapshots.size) // 3 versions d'audit + 1 courante
        val updates = snapshots.map { it.lastUpdateMillis }
        assertEquals(updates.sorted(), updates, "les snapshots doivent être triés par date croissante")
    }

    @Test
    fun unknownIdReturns404() = testApplication {
        installModule()
        assertEquals(HttpStatusCode.NotFound, client.get("/disruptions/NOPE").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/disruptions/NOPE/timeline").status)
    }

    @Test
    fun stateBeforeFirstVersionReturns404() = testApplication {
        installModule()
        val response = client.get("/disruptions/FAKE_1?at=${ts("2026-01-01 07:00:00.000 +0100")}")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun invalidAtReturns400() = testApplication {
        installModule()
        assertEquals(HttpStatusCode.BadRequest, client.get("/disruptions/FAKE_1?at=notanumber").status)
        // 'at' présent mais vide -> invalide également.
        assertEquals(HttpStatusCode.BadRequest, client.get("/disruptions/FAKE_1?at=").status)
    }

    @Test
    fun responseIsJson() = testApplication {
        installModule()
        val response = client.get("/disruptions")
        assertNotNull(response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) })
        assertTrue(
            response.headers[HttpHeaders.ContentType]!!.startsWith("application/json"),
            "Content-Type attendu application/json, obtenu ${response.headers[HttpHeaders.ContentType]}",
        )
    }
}
