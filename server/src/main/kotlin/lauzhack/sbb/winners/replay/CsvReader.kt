package lauzhack.sbb.winners.replay

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Lecture des CSV publics via kotlin-csv (gère guillemets, champs multi-lignes et emoji, comme le
 * demande le README des données). Les clés d'en-tête sont nettoyées d'un éventuel BOM.
 */
object CsvReader {

    private fun rows(input: InputStream): List<Map<String, String>> =
        csvReader().readAllWithHeader(input).map { row ->
            row.mapKeys { (k, _) -> k.removePrefix("﻿").trim() }
        }

    private fun Map<String, String>.s(col: String): String = this[col]?.trim().orEmpty()

    fun incidents(input: InputStream): List<IncidentRow> = rows(input).map { r ->
        IncidentRow(
            id = r.s("id"),
            durationType = r.s("duration_type"),
            durationTo = r.s("duration_to"),
            durationAtLeast = r.s("duration_at_least"),
            reasonIdFk = r.s("reason_id_fk"),
            createdAtSource = r.s("created_at_source"),
            lastModifiedDate = r.s("last_modified_date"),
            version = r.s("version"),
            lifecycle = r.s("lifecycle"),
            originLanguage = r.s("origin_language"),
            summaryEn = r.s("summary_en"),
            summaryDe = r.s("summary_de"),
            summaryFr = r.s("summary_fr"),
            summaryIt = r.s("summary_it"),
            descriptionEn = r.s("description_en"),
            descriptionDe = r.s("description_de"),
            descriptionFr = r.s("description_fr"),
            descriptionIt = r.s("description_it"),
        )
    }

    fun messages(input: InputStream): List<MessageRow> = rows(input).map { r ->
        MessageRow(
            id = r.s("id"),
            incidentIdFk = r.s("incident_id_fk"),
            lastModifiedDate = r.s("last_modified_date"),
            status = r.s("status"),
            titleEn = r.s("title_en"),
            messageEn = r.s("message_en"),
        )
    }

    fun reasons(input: InputStream): List<ReasonRow> = rows(input).map { r ->
        ReasonRow(
            id = r.s("id"),
            en = r.s("reason_en"),
            de = r.s("reason_de"),
            fr = r.s("reason_fr"),
            it = r.s("reason_it"),
        )
    }

    // Variantes sur chemin de fichier.
    fun incidents(path: Path): List<IncidentRow> = read(path) { incidents(it) }
    fun messages(path: Path): List<MessageRow> = read(path) { messages(it) }
    fun reasons(path: Path): List<ReasonRow> = read(path) { reasons(it) }

    private fun <T> read(path: Path, block: (InputStream) -> T): T =
        Files.newInputStream(path).use(block)
}
