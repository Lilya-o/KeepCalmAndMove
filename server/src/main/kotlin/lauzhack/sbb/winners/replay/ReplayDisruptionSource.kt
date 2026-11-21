package lauzhack.sbb.winners.replay

import lauzhack.sbb.winners.DisruptionSource
import lauzhack.sbb.winners.model.Disruption
import lauzhack.sbb.winners.model.DisruptionMessage
import lauzhack.sbb.winners.model.MessageSource

/** Données brutes déjà parsées, de quoi construire les incidents de démo. */
data class RawData(
    val incidents: List<IncidentRow>,      // `incident` + `incident_audit`
    val messages: List<MessageRow>,         // `message` + `message_audit`
    val reasons: List<ReasonRow>,
    val via: List<ViaRow>,
)

/**
 * Source de replay (rôle A) : rejoue l'historique d'un petit nombre d'incidents vedettes.
 *
 * Reconstruit la timeline à partir de TOUTES les versions (audit + courante), triées par
 * `last_modified_date` (jamais par `version`, qui n'est pas unique). `stateAt` renvoie l'état connu
 * à un instant donné. Un incident `lifecycle = ENDED | DELETED` est traité comme clos.
 */
class ReplayDisruptionSource(
    raw: RawData,
    private val incidentIds: List<String>,
    private val xlsxZone: String = "Europe/Zurich",
) : DisruptionSource {

    /** Ids d'incidents configurés pour la démo (ordre de config). Sert à distinguer un id inconnu. */
    val configuredIds: List<String> get() = incidentIds

    private val reasonById: Map<String, ReasonRow> = raw.reasons.associateBy { it.id }

    // Versions d'incident par id, triées chronologiquement (croissant).
    private val versionsById: Map<String, List<IncidentRow>> =
        raw.incidents
            .filter { it.id in incidentIds }
            .groupBy { it.id }
            .mapValues { (_, rows) -> rows.sortedBy { TimestampParse.publicToMillis(it.lastModifiedDate) ?: Long.MIN_VALUE } }

    // Lignes VIA par id, triées par date de log (croissant).
    private val viaById: Map<String, List<ViaRow>> =
        raw.via
            .filter { it.aleaFallId in incidentIds }
            .groupBy { it.aleaFallId }
            .mapValues { (_, rows) -> rows.sortedBy { it.loggingDateMillis ?: Long.MIN_VALUE } }

    // Tous les messages d'un incident (ticker public + résumé/description de chaque version),
    // triés par horodatage croissant. Construits une fois.
    private val messagesById: Map<String, List<DisruptionMessage>> = buildMessages(raw.messages)

    private fun buildMessages(messageRows: List<MessageRow>): Map<String, List<DisruptionMessage>> {
        val out = HashMap<String, MutableList<DisruptionMessage>>()
        incidentIds.forEach { out[it] = mutableListOf() }

        // (a) Messages du ticker public : chaque version devient un message daté.
        for (m in messageRows) {
            val list = out[m.incidentIdFk] ?: continue
            val ts = TimestampParse.publicToMillis(m.lastModifiedDate) ?: continue
            val text = listOf(m.titleEn, m.messageEn).filter { it.isNotBlank() }.joinToString("\n")
            if (text.isNotBlank()) list.add(DisruptionMessage(ts, MessageSource.PUBLIC_TICKER, text))
        }

        // (b) Résumé + description de chaque version d'incident, comme message de timeline.
        for ((id, versions) in versionsById) {
            val list = out[id] ?: continue
            for (v in versions) {
                val ts = TimestampParse.publicToMillis(v.lastModifiedDate) ?: continue
                val text = listOf(v.summaryEn, v.descriptionEn).filter { it.isNotBlank() }.joinToString("\n")
                if (text.isNotBlank()) list.add(DisruptionMessage(ts, MessageSource.PUBLIC_TICKER, text))
            }
        }

        return out.mapValues { (_, l) -> l.sortedBy { it.timestampMillis } }
    }

    // L'interface commune est suspend ; nos accès sont purement locaux (fichiers déjà lus), donc on
    // délègue à des méthodes synchrones, aussi utilisées directement par les tests (sans coroutines).
    override suspend fun list(): List<Disruption> = listSync()

    override suspend fun stateAt(id: String, atMillis: Long): Disruption? = stateAtSync(id, atMillis)

    fun listSync(): List<Disruption> = incidentIds.mapNotNull { stateAtSync(it, Long.MAX_VALUE) }

    fun stateAtSync(id: String, atMillis: Long): Disruption? {
        val versions = versionsById[id] ?: return null
        // Dernière version connue à cet instant.
        val chosen = versions.lastOrNull { (TimestampParse.publicToMillis(it.lastModifiedDate) ?: Long.MIN_VALUE) <= atMillis }
            ?: return null  // instant antérieur à la première version : incident pas encore connu

        val chosenLmd = TimestampParse.publicToMillis(chosen.lastModifiedDate) ?: atMillis
        val closed = chosen.lifecycle == "ENDED" || chosen.lifecycle == "DELETED"

        val (lines, stations) = affectedAt(id, atMillis)

        val estimatedEndRaw = estimatedEnd(chosen)
        val estimatedEnd = estimatedEndRaw ?: if (closed) chosenLmd else null

        val startTime = TimestampParse.publicToMillis(chosen.createdAtSource)
            ?: TimestampParse.publicToMillis(versions.first().createdAtSource)
            ?: chosenLmd

        val cause = reasonById[chosen.reasonIdFk]?.en?.takeIf { it.isNotBlank() } ?: chosen.reasonIdFk

        val messages = (messagesById[id] ?: emptyList()).filter { it.timestampMillis <= atMillis }

        return Disruption(
            id = id,
            cause = cause,
            affectedLines = lines,
            affectedStations = stations,
            startTimeMillis = startTime,
            estimatedEndMillis = estimatedEnd,
            severity = Heuristics.severity(closed, lines.size),
            confidence = Heuristics.confidence(chosen.durationType),
            lastUpdateMillis = chosenLmd,
            messages = messages,
        )
    }

    /** Snapshots de la timeline (un par version d'incident), du plus ancien au plus récent. */
    fun timeline(id: String): List<Disruption> {
        val versions = versionsById[id] ?: return emptyList()
        return versions.mapNotNull { v ->
            val ts = TimestampParse.publicToMillis(v.lastModifiedDate) ?: return@mapNotNull null
            stateAtSync(id, ts)
        }
    }

    /** Fin estimée : null si le type ne porte pas de date exploitable (`UNKNOWN`/`NONE`). */
    private fun estimatedEnd(v: IncidentRow): Long? = when {
        v.durationType.isBlank() || v.durationType == "UNKNOWN" || v.durationType == "NONE" -> null
        else -> TimestampParse.publicToMillis(v.durationTo)
    }

    /**
     * Lignes et gares connues à `atMillis` : on prend la dernière ligne VIA loggée avant l'instant ;
     * à défaut (aucune encore loggée), on retombe sur l'union de toutes les lignes VIA de l'incident.
     * Codes BPUIC bruts (la résolution en noms de gares n'est pas disponible dans le jeu de données).
     */
    private fun affectedAt(id: String, atMillis: Long): Pair<List<String>, List<String>> {
        val via = viaById[id] ?: return emptyList<String>() to emptyList()
        val current = via.lastOrNull { (it.loggingDateMillis ?: Long.MIN_VALUE) <= atMillis }
        return if (current != null) {
            splitLines(current.liniennummer) to stationsOf(current)
        } else {
            via.flatMap { splitLines(it.liniennummer) }.distinct() to
                via.flatMap { stationsOf(it) }.distinct()
        }
    }

    private fun splitLines(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotBlank() }

    private fun stationsOf(v: ViaRow): List<String> =
        listOf(v.ort1, v.ort2).map { it.trim() }.filter { it.isNotBlank() }.distinct()

    companion object {
        /** Construit la source en lisant les fichiers décrits par la config (aucun accès réseau). */
        fun fromConfig(config: ReplayConfig): ReplayDisruptionSource {
            val incidents = CsvReader.incidents(config.incidentCsv) + CsvReader.incidents(config.incidentAuditCsv)
            val messages = CsvReader.messages(config.messageCsv) + CsvReader.messages(config.messageAuditCsv)
            val reasons = CsvReader.reasons(config.reasonCsv)
            val via = XlsxReader.readViaRows(config.xlsxPath, config.incidentIds.toSet(), config.xlsxZone)
            return ReplayDisruptionSource(
                RawData(incidents, messages, reasons, via),
                config.incidentIds,
                config.xlsxZone,
            )
        }
    }
}
