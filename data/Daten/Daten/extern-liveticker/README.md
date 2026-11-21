# Liveticker data export

CSV exports from the liveticker database (PostgreSQL), taken on 2026-10-12.
The relationships between the tables are in [`liveticker_erd.puml`](liveticker_erd.puml).

| File                              | Table            | Rows  | What it holds                        |
|-----------------------------------|------------------|-------|--------------------------------------|
| `incident_202610121914.csv`       | `incident`       | 523   | Current state of each incident       |
| `incident_audit_202610121914.csv` | `incident_audit` | 4,782 | Every earlier version of an incident |
| `message_202610121835.csv`        | `message`        | 3,234 | Current state of each message        |
| `message_audit_202610121835.csv`  | `message_audit`  | 4,722 | Every earlier version of a message   |
| `reason_202610121034.csv`         | `reason`         | 93    | Lookup table of disruption causes    |

## Background: what is a VIA incident?

The liveticker shows passengers the current state of unplanned disruptions
(strikes, track damage, vehicle incidents, etc.).

- **VIA** is the upstream system that records unplanned disruptions
  ("Ereignisse"). The backend receives them as messages on a Kafka topic.
- An **incident** is one disruption. Its `id` is the VIA event id (a long
  numeric string, for example `2195004439194`). Each time VIA sends a new
  version, the existing row is updated.
- A **message** is a short update that traffic control center (TCC) staff publish for an
  incident, for example "Replacement buses are running". An incident has usually several messages.
- A **reason** is a standardised cause (`EG_xxx` codes). Each incident points
  to exactly one reason. `EG_140` ("No reason") is the fallback.
- Almost all text exists in four languages (`_de`, `_fr`, `_it`, `_en`).
  `origin_language` says which language the incident was originally written in.
  The other languages are machine translations.

## Column notes

### incident
- `lifecycle` is the state as reported by VIA:
    - `INFO`: ongoing.
    - `ENDED`: the disruption is over.
    - `DELETED`: withdrawn, for example because it was entered by mistake.
- `duration_type` says how to read the expected end of the disruption.
  This data is received from VIA as part of the incident Kafka event:

  | Value                      | Meaning                                              |
  |----------------------------|------------------------------------------------------|
  | `UNKNOWN`                  | end not known                                        |
  | `UNTIL`                    | until a time of day (`duration_to`)                  |
  | `UNTIL_AT_LEAST`           | until at least `duration_at_least` (time of day)     |
  | `UNTIL_DATE`               | until a date and time                                |
  | `UNTIL_APPROX_DATE_TIME`   | until approximately a date and time                  |
  | `UNTIL_AT_LEAST_DATE_TIME` | until at least a date and time                       |
  | `FROM_TO`                  | between `duration_from` and `duration_to`            |
  | `NONE`                     | no duration (also used if VIA sends an unknown type) |

  `duration_from` is almost always empty. `duration_at_least` is only filled
  for the `*_AT_LEAST*` types.
- `created_at_source` and `updated_at_source` are timestamps from VIA.
  `created_date` and `last_modified_date` are when the liveticker wrote the row.
- `version` is the VIA version number, not a counter in the liveticker (versioning the VIA incident itself).
- `author_id_fk` points to the `employee` table (not exported). It is empty
  for incidents that came straight from VIA do not have a related liveticker.

### message
- `status` is `DRAFT`, `REJECTED`, `PUBLISHED`, `DONE` or `HIDDEN`. In this
  export only `PUBLISHED` and `HIDDEN` appear.
- `*_translated` is `true` if that language version is a machine translation
  and `false` if it is the original text. In the data, the original language
  is the one that is `false`.
- `author_id_fk` points to `employee` (not exported).

## History (the `_audit` tables)

A database trigger runs before every UPDATE or DELETE on `incident` and
`message`. It copies the old row into the matching `_audit` table.

- The audit tables hold only the old versions. The current version is in the
  main table, so the full history of an incident is its `incident_audit` rows
  plus its `incident` row.
- Order versions by `last_modified_date`. `(id, last_modified_date)` is unique.
  Do not use `version`, because it is not unique within an incident.
- The audit tables have no foreign keys. They also hold rows for incidents
  that no longer exist in `incident` (27 ids in this export). A nightly job
  deletes incidents that were never worked on, and the trigger keeps their
  history.
- Because audit rows are old copies, `last_modified_date` can be earlier than
  the row's `created_date` in a few places. Treat it as the version timestamp,
  not a creation time.

## Columns not in the exports

These columns exist in the database but are not in the CSV files:

| Table                        | Missing columns                                                              |
|------------------------------|------------------------------------------------------------------------------|
| `reason`                     | `created_date`                                                               |
| `incident`, `incident_audit` | `status`, `source`, `has_been_opened`                                        |
| `message`, `message_audit`   | `media_id_fk`, `verifier_id_fk`, `creator`, `last_modifier`, `verified_date` |

`incident.status` (`NEW`, `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`,
`DELETED`, `ARCHIVED`) is the liveticker's own workflow state. It is separate
from the VIA `lifecycle`.

Other tables (`employee`, `media`, `link`, `link_audit`, `rating`) are not
part of this export.

## Reading the files

- Text fields can contain line breaks and emoji, so use a real CSV parser, not
  line-based tools.
- Timestamps include a UTC offset (`+0100` or `+0200`).
- Empty values are NULL. Only `incident_audit` and `message_audit` have
  mostly nullable columns.