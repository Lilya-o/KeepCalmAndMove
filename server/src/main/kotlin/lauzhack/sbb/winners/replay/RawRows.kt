package lauzhack.sbb.winners.replay

/**
 * Structures brutes, proches des colonnes des exports. On ne garde que les champs utilisés par le
 * replay (les colonnes non utilisées — author_id_fk, media, etc. — sont volontairement ignorées).
 * Les valeurs sont des chaînes telles que lues ; la conversion en millis se fait plus tard.
 */

/** Une version d'incident (ligne de `incident` ou `incident_audit`). */
data class IncidentRow(
    val id: String,
    val durationType: String,
    val durationTo: String,
    val durationAtLeast: String,
    val reasonIdFk: String,
    val createdAtSource: String,
    val lastModifiedDate: String,
    val version: String,
    val lifecycle: String,
    val originLanguage: String,
    val summaryEn: String,
    val summaryDe: String,
    val summaryFr: String,
    val summaryIt: String,
    val descriptionEn: String,
    val descriptionDe: String,
    val descriptionFr: String,
    val descriptionIt: String,
)

/** Une version de message public (ligne de `message` ou `message_audit`). */
data class MessageRow(
    val id: String,
    val incidentIdFk: String,
    val lastModifiedDate: String,
    val status: String,
    val titleEn: String,
    val messageEn: String,
)

/** Cause normalisée (`reason`). */
data class ReasonRow(
    val id: String,
    val en: String,
    val de: String,
    val fr: String,
    val it: String,
)

/** Une ligne de l'onglet VIA du xlsx (lignes/gares affectées). */
data class ViaRow(
    val aleaFallId: String,
    val loggingDateMillis: Long?,
    val liniennummer: String,
    val ort1: String,
    val ort2: String,
    val ereignisgrund: String,
)
