package lauzhack.sbb.winners.replay

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Configuration du replay (rôle A).
 *
 * Tous les chemins sont configurables ; par défaut ils pointent vers le dossier `data/` local
 * (jamais commité, cf. .gitignore). Aucune ressource réseau : on ne lit que des fichiers locaux.
 *
 * Les ids de démo sont une liste configurable — on ne les code pas en dur ailleurs.
 */
data class ReplayConfig(
    /** Racine des exports SBB. Par défaut `data/Daten/Daten` depuis la racine du dépôt. */
    val dataRoot: Path = Paths.get("data", "Daten", "Daten"),
    /** Incidents rejoués pour la démo (principal puis secondaire). */
    val incidentIds: List<String> = listOf("2195005188970", "2195005182293"),
    /**
     * Fuseau utilisé pour interpréter les dates du xlsx, qui sont des numéros de série Excel
     * SANS fuseau. Hypothèse d'équipe : heure locale CH, pour s'aligner sur l'offset des CSV publics.
     */
    val xlsxZone: String = "Europe/Zurich",
    // Noms de fichiers (surchargeables si un nouvel export change le suffixe de date).
    val incidentFile: String = "extern-liveticker/incident_202610121914.csv",
    val incidentAuditFile: String = "extern-liveticker/incident_audit_202610121914.csv",
    val messageFile: String = "extern-liveticker/message_202610121835.csv",
    val messageAuditFile: String = "extern-liveticker/message_audit_202610121835.csv",
    val reasonFile: String = "extern-liveticker/reason_202610121034.csv",
    val xlsxFile: String = "Ereignismeldungen/Ereignismeldungen Via.xlsx",
) {
    val incidentCsv: Path get() = dataRoot.resolve(incidentFile)
    val incidentAuditCsv: Path get() = dataRoot.resolve(incidentAuditFile)
    val messageCsv: Path get() = dataRoot.resolve(messageFile)
    val messageAuditCsv: Path get() = dataRoot.resolve(messageAuditFile)
    val reasonCsv: Path get() = dataRoot.resolve(reasonFile)
    val xlsxPath: Path get() = dataRoot.resolve(xlsxFile)

    /** True si les cinq CSV et le xlsx existent (sinon le replay ne peut pas tourner). */
    fun dataPresent(): Boolean = listOf(
        incidentCsv, incidentAuditCsv, messageCsv, messageAuditCsv, reasonCsv, xlsxPath,
    ).all { java.nio.file.Files.exists(it) }
}
