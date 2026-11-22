package lauzhack.sbb.winners.replay

import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test d'intégration sur les VRAIS incidents. Les données `data/` ne sont pas dans Git, donc ce test
 * se SAUTE (assumption JUnit) quand le dossier est absent — il ne casse pas le build des coéquipières.
 *
 * Gradle lance les tests avec le répertoire de travail du module (`server/`) ; on remonte donc les
 * dossiers parents pour retrouver `data/Daten/Daten` à la racine du dépôt.
 */
class ReplayIntegrationTest {

    private fun ts(s: String): Long = checkNotNull(TimestampParse.publicToMillis(s))

    private fun locateDataRoot(): Path? {
        var dir: Path? = Paths.get("").toAbsolutePath()
        repeat(8) {
            val candidate = dir?.resolve("data")?.resolve("Daten")?.resolve("Daten")
            if (candidate != null && Files.isDirectory(candidate)) return candidate
            dir = dir?.parent
        }
        return null
    }

    @Test
    fun realStarIncidentStateChangesWithAt() {
        val dataRoot = locateDataRoot()
        val config = dataRoot?.let { ReplayConfig(dataRoot = it) }
        assumeTrue("data/ absent — test d'intégration sauté", config != null && config.dataPresent())
        config!!

        val src = ReplayDisruptionSource.fromConfig(config)
        val id = "2195005188970" // incident principal (Renens – Morges)

        // Avant la création -> inconnu.
        assertNull(src.stateAtSync(id, ts("2026-09-17 10:00:00.000 +0200")))

        // Deux instants encadrant un changement de duration_to (18:30 -> 23:59).
        val s1 = assertNotNull(src.stateAtSync(id, ts("2026-09-17 18:00:00.000 +0200")))
        val s2 = assertNotNull(src.stateAtSync(id, ts("2026-09-17 22:00:00.000 +0200")))

        assertNotNull(s1.estimatedEndMillis)
        assertNotNull(s2.estimatedEndMillis)
        assertTrue(s1.estimatedEndMillis != s2.estimatedEndMillis, "la fin estimée doit évoluer")
        assertTrue(s2.lastUpdateMillis >= s1.lastUpdateMillis)
        assertTrue(s2.messages.size >= s1.messages.size)

        // Les deux incidents configurés sont exposés.
        assertEquals(config.incidentIds.size, src.listSync().size)
    }
}
