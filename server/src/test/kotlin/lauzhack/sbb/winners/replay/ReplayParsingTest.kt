package lauzhack.sbb.winners.replay

import lauzhack.sbb.winners.model.Severity
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests de parsing et de timeline sur des fixtures INVENTÉES (server/src/test/resources/fixtures/).
 * Aucune donnée SBB réelle n'est requise : ces tests tournent chez tout le monde, même sans `data/`.
 */
class ReplayParsingTest {

    private fun res(path: String): InputStream =
        checkNotNull(javaClass.getResourceAsStream(path)) { "fixture manquante: $path" }

    private fun ts(s: String): Long = checkNotNull(TimestampParse.publicToMillis(s)) { "ts illisible: $s" }

    private fun source(): ReplayDisruptionSource {
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

    @Test
    fun csvParsingMergesCurrentAndAuditVersions() {
        val src = source()
        // 4 versions pour FAKE_1 (3 d'audit + 1 courante) -> timeline de 4 snapshots.
        assertEquals(4, src.timeline("FAKE_1").size)
        // Les deux incidents configurés sont exposés par list().
        assertEquals(2, src.listSync().size)
    }

    @Test
    fun stateIsNullBeforeFirstVersion() {
        val src = source()
        assertNull(src.stateAtSync("FAKE_1", ts("2026-01-01 07:00:00.000 +0100")))
        // id inconnu -> null également.
        assertNull(src.stateAtSync("NOPE", ts("2026-01-01 09:00:00.000 +0100")))
    }

    @Test
    fun estimatedEndChangesWithAt() {
        val src = source()
        val early = assertNotNull(src.stateAtSync("FAKE_1", ts("2026-01-01 08:10:00.000 +0100")))
        val mid = assertNotNull(src.stateAtSync("FAKE_1", ts("2026-01-01 09:30:00.000 +0100")))
        val late = assertNotNull(src.stateAtSync("FAKE_1", ts("2026-01-01 12:30:00.000 +0100")))

        assertNull(early.estimatedEndMillis) // v1 = UNKNOWN -> pas de fin
        assertEquals(ts("2026-01-01 10:00:00.000 +0100"), mid.estimatedEndMillis)
        assertEquals(ts("2026-01-01 12:00:00.000 +0100"), late.estimatedEndMillis)
        // Le cœur du replay : l'état dépend bien de `at`.
        assertTrue(mid.estimatedEndMillis != late.estimatedEndMillis)
    }

    @Test
    fun confidenceFollowsDurationType() {
        val src = source()
        val unknown = assertNotNull(src.stateAtSync("FAKE_1", ts("2026-01-01 08:10:00.000 +0100")))
        val approx = assertNotNull(src.stateAtSync("FAKE_1", ts("2026-01-01 09:30:00.000 +0100")))
        assertEquals(0.3, unknown.confidence) // UNKNOWN
        assertEquals(0.7, approx.confidence)  // *_APPROX_*
    }

    @Test
    fun messagesAccumulateOverTime() {
        val src = source()
        fun count(at: String) = assertNotNull(src.stateAtSync("FAKE_1", ts(at))).messages.size
        val s0810 = count("2026-01-01 08:10:00.000 +0100") // v1
        val s0930 = count("2026-01-01 09:30:00.000 +0100") // + v2, M1
        val s1230 = count("2026-01-01 12:30:00.000 +0100") // + v3, M2
        val s1330 = count("2026-01-01 13:30:00.000 +0100") // + v4
        assertTrue(s0810 < s0930 && s0930 < s1230 && s1230 < s1330, "messages: $s0810 $s0930 $s1230 $s1330")
    }

    @Test
    fun affectedLinesAndSeverityTrackViaAndClosure() {
        val src = source()
        val mid = assertNotNull(src.stateAtSync("FAKE_1", ts("2026-01-01 09:30:00.000 +0100")))
        assertEquals(listOf("IC1"), mid.affectedLines)       // via loggée à 08:30
        assertEquals(Severity.MINOR, mid.severity)           // 1 ligne

        val late = assertNotNull(src.stateAtSync("FAKE_1", ts("2026-01-01 12:30:00.000 +0100")))
        assertEquals(listOf("IC1", "S2", "S8"), late.affectedLines) // via loggée à 11:30
        assertEquals(Severity.MAJOR, late.severity)                 // 3 lignes

        // Avant toute ligne VIA loggée -> union des lignes (repli documenté).
        val union = assertNotNull(src.stateAtSync("FAKE_1", ts("2026-01-01 08:10:00.000 +0100")))
        assertContains(union.affectedLines, "S8")

        // Dernière version = ENDED -> incident clos : severity INFO, fin = dernière mise à jour.
        val closed = assertNotNull(src.stateAtSync("FAKE_1", ts("2026-01-01 13:30:00.000 +0100")))
        assertEquals(Severity.INFO, closed.severity)
        assertEquals(ts("2026-01-01 13:00:00.000 +0100"), closed.estimatedEndMillis)
    }

    @Test
    fun causeIsResolvedFromReasonTable() {
        val src = source()
        val d = assertNotNull(src.stateAtSync("FAKE_1", ts("2026-01-01 09:30:00.000 +0100")))
        assertEquals("Test cause", d.cause)
    }

    // --- Parsing XLSX (fonctions pures, petits XML inline : pas besoin d'un vrai fichier) ---

    @Test
    fun xlsxSharedStringsAndSheetParse() {
        val shared = XlsxReader.parseSharedStrings(
            """
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <si><t>alea_fall_id</t></si>
              <si><t>stoerungs_liniennummer</t></si>
              <si><t>logging_date_time</t></si>
              <si><t>FAKE_1</t></si>
              <si><t>IC1, S2</t></si>
            </sst>
            """.trimIndent(),
        )
        assertEquals(5, shared.size)
        assertEquals("IC1, S2", shared[4]) // garde la virgule interne

        val sheet = """
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
              <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
              <row r="2"><c r="A2" t="s"><v>3</v></c><c r="B2" t="s"><v>4</v></c><c r="C2"><v>46266.5</v></c></row>
            </sheetData></worksheet>
        """.trimIndent()
        val rows = XlsxReader.parseSheet(sheet, shared)
        assertEquals(1, rows.size)
        assertEquals("FAKE_1", rows[0]["alea_fall_id"])
        assertEquals("IC1, S2", rows[0]["stoerungs_liniennummer"])
        assertEquals("46266.5", rows[0]["logging_date_time"])
    }

    @Test
    fun excelSerialConvertsAndIsMonotonic() {
        val a = TimestampParse.excelSerialToMillis("46266", "Europe/Zurich")
        val b = TimestampParse.excelSerialToMillis("46266.5", "Europe/Zurich")
        val aMillis = assertNotNull(a)
        val bMillis = assertNotNull(b)
        assertTrue(bMillis > aMillis) // +0.5 jour = +12 h
    }
}
