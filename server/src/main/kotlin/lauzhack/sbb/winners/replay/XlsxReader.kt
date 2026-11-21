package lauzhack.sbb.winners.replay

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/**
 * Lecture du xlsx VIA SANS dépendance externe : dézippage (java.util.zip) + parsing XML en flux
 * (StAX du JDK). On lit la table des chaînes partagées puis l'onglet des données VIA.
 *
 * Les fonctions pures `parseSharedStrings` / `parseSheet` sont testables avec de petits XML inline
 * (fixtures), sans avoir besoin d'un vrai fichier xlsx.
 */
object XlsxReader {

    private val factory: XMLInputFactory = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
    }

    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

    /** Table des chaînes partagées (`xl/sharedStrings.xml`), concatène les runs `<t>` d'un `<si>`. */
    fun parseSharedStrings(xml: String): List<String> {
        val reader = factory.createXMLStreamReader(ByteArrayInputStream(xml.toByteArray()))
        val out = ArrayList<String>()
        val current = StringBuilder()
        var inSi = false
        var inT = false
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                    "si" -> { inSi = true; current.setLength(0) }
                    "t" -> inT = true
                }
                XMLStreamConstants.CHARACTERS -> if (inSi && inT) current.append(reader.text)
                XMLStreamConstants.END_ELEMENT -> when (reader.localName) {
                    "t" -> inT = false
                    "si" -> { out.add(current.toString()); inSi = false }
                }
            }
        }
        reader.close()
        return out
    }

    /**
     * Parse un onglet (`xl/worksheets/sheetN.xml`). La première ligne est l'en-tête ; chaque ligne
     * suivante est rendue sous forme de Map<colonne, valeur>. Les cellules `t="s"` sont résolues via
     * la table des chaînes partagées ; les cellules vides/absentes valent "".
     */
    fun parseSheet(xml: String, sharedStrings: List<String>): List<Map<String, String>> {
        val reader = factory.createXMLStreamReader(ByteArrayInputStream(xml.toByteArray()))
        val rawRows = ArrayList<Map<Int, String>>() // index de colonne -> valeur

        var cells = HashMap<Int, String>()
        var colIdx = -1
        var cellType: String? = null
        var inV = false
        val value = StringBuilder()

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                    "row" -> cells = HashMap()
                    "c" -> {
                        val ref = reader.getAttributeValue(null, "r")
                        cellType = reader.getAttributeValue(null, "t")
                        colIdx = ref?.let { columnIndex(it) } ?: (cells.size)
                    }
                    "v" -> { inV = true; value.setLength(0) }
                }
                XMLStreamConstants.CHARACTERS -> if (inV) value.append(reader.text)
                XMLStreamConstants.END_ELEMENT -> when (reader.localName) {
                    "v" -> {
                        inV = false
                        val resolved =
                            if (cellType == "s") sharedStrings.getOrElse(value.toString().toInt()) { "" }
                            else value.toString()
                        if (colIdx >= 0) cells[colIdx] = resolved
                    }
                    "row" -> rawRows.add(cells)
                }
            }
        }
        reader.close()

        if (rawRows.isEmpty()) return emptyList()
        val header = rawRows.first()
        val headerByIdx = header.toSortedMap()
        val cols = headerByIdx.keys.toList()
        return rawRows.drop(1).map { row ->
            cols.associate { idx -> (headerByIdx[idx] ?: "col$idx") to (row[idx] ?: "") }
        }
    }

    /** "A1"/"AB12" -> index de colonne 0-based (A=0). */
    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break
        }
        return n - 1
    }

    /** Lit les lignes VIA du xlsx et les filtre sur les incidents demandés. */
    fun readViaRows(path: Path, incidentIds: Set<String>, zone: String): List<ViaRow> {
        val entries = readZipEntries(
            Files.newInputStream(path),
            setOf("xl/sharedStrings.xml"),
            prefix = "xl/worksheets/",
        )
        val shared = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        // Les en-têtes sont des chaînes partagées (la feuille ne contient que des indices) : on parse
        // chaque feuille puis on retient celle dont l'en-tête résolu contient `alea_fall_id`.
        val rows = entries.entries
            .filter { it.key.startsWith("xl/worksheets/") && it.key.endsWith(".xml") }
            .map { parseSheet(it.value, shared) }
            .firstOrNull { sheet -> sheet.firstOrNull()?.containsKey("alea_fall_id") == true }
            ?: error("Onglet VIA introuvable dans $path")
        return rows
            .filter { it["alea_fall_id"].orEmpty() in incidentIds }
            .map { r ->
                ViaRow(
                    aleaFallId = r["alea_fall_id"].orEmpty(),
                    loggingDateMillis = TimestampParse.excelSerialToMillis(r["logging_date_time"], zone),
                    liniennummer = r["stoerungs_liniennummer"].orEmpty(),
                    ort1 = r["stoerungs_ort1"].orEmpty(),
                    ort2 = r["stoerungs_ort2"].orEmpty(),
                    ereignisgrund = r["ereignisgrund"].orEmpty(),
                )
            }
    }

    private fun readZipEntries(
        input: InputStream,
        exact: Set<String>,
        prefix: String,
    ): Map<String, String> {
        val out = HashMap<String, String>()
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name in exact || name.startsWith(prefix)) {
                    out[name] = zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        return out
    }
}
