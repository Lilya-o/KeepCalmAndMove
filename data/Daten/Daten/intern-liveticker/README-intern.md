# KULT – Teilexport (September 2026)

## Dateien und Spalten

| Datei | Inhalt | Spalten |
|---|---|---|
| `1__Ereignisfaelle.csv` | Ereignisfälle (Störungen/Vorfälle), 01.09.–30.09.2026 | `id`, `created_at`, `datum`, `active`, `archived`, `zug_nr_strecke`, `beschreibung`, `ereigniskategorie_id` |
| `2__Ereigniskategorien.csv` | Lookup der Kategorien (DE/FR/IT) | `id`, `name` (Code), `name_de`, `name_fr`, `name_it` |
| `3__Liveticker_messages.csv` | Liveticker-Meldungen zu Ereignisfällen | `id`, `ereignisfall_id`, `timestamp`, `sender`, `message` (HTML), `name`, `vorname`, `telefonnummer` |
| `4__Logs.csv` |  Audit-Log aller Änderungen, 01.09.–30.09.2026 | `id`, `ereignisfall_id`, `created_at`, `user_name`, `action`, `entity`, `data` (Objekt-Dump) |

## Was sich damit über einen Monat auswerten lässt

- **Aufkommen:** Anzahl Ereignisfälle pro Tag/Woche/Stunde (Spitzenzeiten, Wochentagsmuster).
- **Ursachen:** Verteilung nach Kategorie (z. B. Personenunfall, Fahrleitungs-, Barrierenstörung, Bauarbeiten) via Datei 2.
- **Orte/Strecken:** Häufung nach Betriebspunkt bzw. Strecke (`zug_nr_strecke` in Datei 1).
- **Dauer & Verlauf:** Zeit von `created_at` bis `ACTIVATE`/`ARCHIVE` in den Logs; Zeit bis zur ersten Liveticker-Meldung.
- **Kommunikationsaufwand:** Meldungen pro Ereignis, Absender-Anteile (TCC vs. Bahnhof/Personen), Tickerverlauf (z. B. «Konzept in Bearbeitung» → «Konzept aktiv»), Anzahl `DEACTIVATE` (zurückgezogene Meldungen).
- **Bearbeitungsprozess:** Aus den Logs Aktionen (`CREATE`, `UPDATE`, `SEND`, `ACTIVATE`, `ARCHIVE`, …) und Objekttypen (`Ereignisfall`, `InfoboardModule`, `LivetickerMessage`, `Kubealarm`, `Kubeeinsatz`) – z. B. Updates je Infoboard-Modul, Anteil Ereignisse mit Alarm/Einsatz.
- **Arbeitslast:** Aktivität pro Benutzer und Tageszeit.
- **Datenqualität:** Leere Textfelder, Ereignisse ohne Ticker-Meldung, fehlende Archivierung, Platzhalter wie `XXX` in Infoboard-Texten.
