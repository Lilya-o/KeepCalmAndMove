package lauzhack.sbb.winners.replay

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

/**
 * Conversions de dates vers epoch millis.
 *
 * - Les CSV publics portent un offset explicite, ex. `2026-09-17 17:52:46.440 +0200`.
 * - Le xlsx VIA stocke des numéros de série Excel (jours depuis le 1899-12-30), sans fuseau.
 */
object TimestampParse {

    // La fraction de seconde est optionnelle (certaines lignes n'en ont pas).
    private val PUBLIC: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS] Z")

    /** Parse un horodatage public (`… ±HHmm`). Renvoie null si vide/illisible. */
    fun publicToMillis(raw: String?): Long? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        return try {
            OffsetDateTime.parse(s, PUBLIC).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    private const val MILLIS_PER_DAY = 86_400_000.0

    /**
     * Convertit un numéro de série Excel en epoch millis, en interprétant la date « naïve » dans le
     * fuseau donné (hypothèse d'équipe : Europe/Zurich, pour s'aligner sur les CSV publics).
     * Époque Excel = 1899-12-30 (gère le bug d'année bissextile 1900 pour les dates usuelles).
     */
    fun excelSerialToMillis(serial: Double?, zone: String): Long? {
        if (serial == null) return null
        val base = LocalDateTime.of(1899, 12, 30, 0, 0)
        val dt = base.plusNanos((serial * MILLIS_PER_DAY).roundToLong() * 1_000_000L)
        return dt.atZone(ZoneId.of(zone)).toInstant().toEpochMilli()
    }

    /** Variante tolérante : la cellule xlsx peut contenir une chaîne. */
    fun excelSerialToMillis(raw: String?, zone: String): Long? =
        excelSerialToMillis(raw?.trim()?.toDoubleOrNull(), zone)
}
