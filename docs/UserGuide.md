# User Guide

## Introduction

LabLog helps laboratory technicians manage loanable electronics equipment and
borrowers. The main console shares equipment, loans, and borrowers for registration,
lending, returns, and loan lookups. A standalone borrower console remains available.
Both provide command help and examples.

## Quick Start

Use Java 25. From the repository root in PowerShell:

```powershell
java -version
.\gradlew.bat run --console=plain
```

The command summary appears once at startup. Subsequent input uses the short
`lablog> ` prompt so results remain easy to find.

Enter `help` to see commands, `help COMMAND` for examples, and `exit` to close
the main console. On macOS/Linux, replace `.\gradlew.bat` with `./gradlew`.

For the borrower console, compile and run:

```powershell
.\gradlew.bat classes
java -cp build/classes/java/main lablog.BorrowerCli
```

Exit the borrower console with `exit` or EOF: Ctrl+Z then Enter on Windows, or Ctrl+D
on macOS/Linux. Its default data file is `data/borrowers.properties`; pass a
different path after `lablog.BorrowerCli` to select another file.

The main console saves equipment, loans, return dates, return faults and structured fault reports
in `data/lablog.properties` after each successful change. It reloads them on
startup. To select another file, run
`java -jar build/libs/lablog.jar PATH/TO/lablog.properties`.
A missing file starts a new collection. Invalid existing data stops startup
without overwriting it. If saving fails, the console reports the failure and
stops; fix file access and restart with the previous saved records. Run only one
main console against a given data file at a time.

## Features 

### Registering equipment

Use `add ASSET_ID n/NAME c/CATEGORY m/MODEL l/LOCATION` to register in one line.
All four non-empty prefixes are required exactly once, in any order; values may
contain spaces. Duplicate IDs are rejected by this one-line command.

Alternatively, enter `add ASSET_ID`, then answer the four prompts:

```text
add OSC-001
Oscilloscope
Electronics
Scope Model 1
Lab A
```

Type `cancel` at any of the four detail prompts to discard the entry and return
to the main prompt. No partial equipment record is added.

LabLog confirms `Equipment added to inventory.` An incomplete entry at EOF is
discarded. Unknown commands and bare asset IDs are rejected with a help hint.
Registration begins only after an explicit `add` command.

Equipment is saved to the configured LabLog data file after each successful
registration and is available after restart.

### Copying equipment details: `copy`

Use `copy SOURCE_ASSET_ID NEW_ASSET_ID` to create another unit from a registered
unit. The new ID must be unique. The copy receives the source's name, category,
model, and location; the source remains unchanged. Loans and history stay with
the original ID and are not copied. A successful copy is saved to the configured
data file.

### Editing equipment details

Use `edit-equipment ASSET_ID [n/NAME] [c/CATEGORY] [m/MODEL] [l/LOCATION]` to
change one or more descriptive fields. For example:

```text
edit-equipment OSC-001 l/Lab 2 n/Digital oscilloscope
```

Supply each field at most once; values may contain spaces. Omitted fields stay
unchanged. The asset ID is stable, so loan and history references remain valid.
Invalid or incomplete edits change nothing. Successful edits are saved to the
configured LabLog data file.

### Moving several equipment units together

Use `move-equipment ASSET_ID[,ASSET_ID...] l/LOCATION` to move selected units
to the same location. For example:

```text
move-equipment OSC-001,MIC-002 l/Lab 3
```

Select distinct registered asset IDs, separated by commas, and provide one
non-empty location. LabLog validates the full selection and destination before
changing any unit, so an invalid ID or location leaves the whole batch
unchanged. A successful batch is saved to the configured LabLog data file.

### Listing, filtering, and sorting equipment: `list`

Enter `list` in the main console to show asset ID, name, category, model,
location, and availability for each registered unit. Add any combination of
category, location, and status filters:

```text
list [category/VALUE] [location/VALUE] [status/available|on-loan] [sort/FIELD]
```

For example, `list category/Electronics location/Lab A status/available` shows
electronics stored in Lab A that are not currently on loan. Category and
location values match exactly, ignoring case; when combined, all supplied
filters must match. Status is `on-loan` when a unit has an active loan and
`available` only when it has neither an active loan nor an unresolved severe
fault. Severe faults display `Unavailable: severe fault` and are excluded from
`status/available` results. Reservation and legacy fault notes do not block loans.
Add `sort/FIELD` to sort the matching units in ascending order by `asset-id`,
`name`, `category`, `model`, `location`, or `availability`. For example,
`list sort/name` sorts all units, while
`list category/Electronics location/Lab A status/available sort/name` filters
first and then sorts the matches. Options may appear in any order, once each.
Comparisons are case-insensitive. Equal values are ordered by asset ID, with
exact ID case as a final tie-breaker. Sorting only reorders the displayed
snapshot; it does not change inventory order or stored records.

An empty inventory reports `No equipment registered.` A filter with no
matches reports `No equipment matches the supplied filters.`

### Finding one unit: `find`

Enter `find ASSET_ID` to show one registered unit's name, category, model,
location, and current availability. For example, `find OSC-001` performs an
exact, case-sensitive lookup. Unknown IDs produce an error, and the lookup does
not change equipment or loan records.

### Recording equipment history notes

Use `reservation ASSET_ID d/YYYY-MM-DD desc/DESCRIPTION`, `fault` with the same
fields, or `maintenance` with the same fields. For example:
`fault OSC-001 d/2026-10-14 desc/Cracked screen`.
The equipment must already be registered. Dates must be real calendar dates,
and descriptions must be non-empty and single-line. View recorded notes using
`history ASSET_ID`. Notes describe events only: they do not reserve availability
or prevent loans. Reservation and maintenance notes are saved in the snapshot.
Fault reports receive a unique `F-...` ID shown on success and in history. They
are saved across restarts and included in exports. Multiple reports for the same
equipment are allowed, each with its own ID. Past and future dates are accepted,
as with the original dated notes. Use `repair` to resolve an identified fault.

### Recording fault severity and description

Use `fault ASSET_ID d/YYYY-MM-DD [s/SEVERITY] desc/DESCRIPTION`, for example:

```text
fault OSC-001 d/2026-10-22 s/severe desc/Cracked screen
```

The optional severity goes between the date and description. Values ignore case:

| Severity | Meaning | Blocks new loans? |
| --- | --- | --- |
| `minor` | Small defect that does not prevent use | No |
| `moderate` | Significant defect needing attention | No |
| `severe` | Serious defect that makes equipment unsuitable for lending | Yes |
| Omitted | Not assessed; displayed as `unspecified` | No |

Descriptions must be non-empty and on one line. Supply each field only once;
standalone `s/`, `d/`, and `desc/` prefixes inside descriptions are rejected to
avoid silently accepting misplaced or repeated fields. Invalid input creates no
report. Confirmation and history include the severity, which survives restarts
and exports. Older fault records without a severity remain unspecified.

New fault reports are unresolved. Unresolved severe faults block new loans
immediately, including reports with future dates. Existing loans remain active
and can be returned, but returning a unit does not clear its severe faults.
Use the repair command below to resolve a fault after completing its repair.

### Recording repairs and resolving faults: `repair`

Enter `repair FAULT_ID d/YYYY-MM-DD desc/DETAILS` in the main console. For example:

```text
repair F-123 d/2026-10-22 desc/Replaced cracked screen
```

Replace `F-123` with the actual fault ID from the report confirmation or
`history ASSET_ID`. The ID identifies a single fault, not the equipment unit.
The repair date must be a real date on or after the report date and no later
than today in the application's local time zone. Same-day repairs are accepted.
Repair details must be non-empty and single-line. Put `d/` before `desc/` and
supply each field once; standalone `d/` and `desc/` prefixes inside the details
are rejected as repeated fields.

Unknown IDs, already resolved faults, invalid dates and invalid details leave
records unchanged. Successful repairs retain the original report and add a
dated `MAINTENANCE` history entry naming the resolved fault and repair details.
Both entries survive restarts and exports. Repairs cannot be undone or repeated;
report a new fault if a repaired problem occurs again.

Resolving the last unresolved severe fault removes that equipment's fault-based
lending restriction immediately. Other unresolved severe faults still block
lending, and an active loan still makes the unit unavailable. Repairs do not
return a loan. If saving fails, the session stops without reporting success;
the previous saved state remains unchanged.

### Listing active or overdue loans: `active-loans`

Format: `active-loans [overdue]`

Enter `active-loans` to show every current loan, or `active-loans overdue` to
show only active loans whose due date is before today's local date. For example:

```text
active-loans overdue
```

Both modes order results by due date, then asset ID when dates match. Each row
includes asset ID, borrower ID, due date, and overdue status. Returned loans
are excluded. The overdue-only view also excludes loans due today or later.
It reports `No overdue loans.` when there are no matches; the full view reports
`No active loans.` when no loans remain active.

`overdue` is an optional, case-sensitive keyword. Unknown or extra arguments
produce a usage error. Listing changes no records or files; a returned loan
disappears from subsequent listings automatically.

### Registering borrowers: `add-borrower`

In either console:

```text
add-borrower A0123456X n/Jamie Tan p/91234567 e/jamietan@u.nus.edu
```

The ID must be an uppercase letter, seven digits, and an uppercase letter.
Names contain 1-100 characters and no control characters; phone numbers contain
eight digits; email addresses use `u.nus.edu`. All three prefixes are required
exactly once and may appear in any order. Duplicate IDs and invalid fields are
rejected. Registration saves the borrower before reporting success; a failed
save leaves the borrower book unchanged.

### Viewing a borrower's loans: `loans`

In the main console, enter `loans A0123456X` for a registered borrower.
Active loans are ordered by due date, then asset ID. Rows include the equipment
name, issue date, due date, and whether the loan is overdue. Returned loans are
excluded; a loan becomes overdue after its due date, not on that date.

The standalone console has no loan-creation command, so after registration it
reports `No active loans for A0123456X - Jamie Tan.` Unknown borrowers produce
an error. In the main console, active loans are shared with the loan and return
commands.

### Viewing common commands and examples: `help`

Format: `help [COMMAND]`

Enter `help` for the concise Version 1 command list, or supply one command name
to see its syntax, fields, and explained usage examples. Help is available in both consoles
and does not change records. Command names are case-sensitive; unknown names
and extra arguments produce an error and let you try again.

For example, `help loan` displays:

```text
Issue a loan for an available equipment item.
Format: loan ASSET_ID b/BORROWER_ID d/DUE_DATE
Example: loan OSC-001 b/A0123456X d/2026-09-18
  Lend OSC-001 to borrower A0123456X with a due date of 18 September 2026.
  Substitute the actual asset ID, borrower ID, and due date when issuing a loan.
Fields: ASSET_ID identifies the equipment; b/ identifies the borrower; d/ gives the due date in YYYY-MM-DD format.
The main console checks borrower IDs against registered borrowers before lending.
```

Detailed help covers `add`, `copy`, `edit-equipment`, `move-equipment`, history-note commands,
`active-loans`, `list`, `find`,
`loan`, `return`, `history`, `add-borrower`, `loans`, and `exit`. The list labels
commands belonging to the main or borrower console; viewing help does not make
a command available in the other console.

Use `help return` to compare returning an item with and without new faults:

```text
Example: return OSC-001 faults/cracked screen
  Return the unit and record a cracked screen on its loan.
Example: return OSC-001
  Return the unit with no new faults reported.
```

These are alternatives: the unit must have an active loan, and a loan cannot be
returned twice. Use `help add-borrower` to see registrations with different field
orders, and `help loans` for lookups using those borrowers' IDs. Use `help history`
for a history lookup and its registration prerequisite, or `help help` to see how
to switch between the concise list and command-specific guidance.

Lines beginning with `Example:` contain commands you can type; the indented lines
explain their outcomes. Viewing an example never executes it. Replace sample IDs,
contact details, and dates with your own when entering a command.

### Issuing a loan: `loan`

At the initial prompt, enter `loan ASSET_ID b/BORROWER_ID d/DUE_DATE`:

```text
loan OSC-001 b/A0123456X d/2026-10-30
```

LabLog confirms `Loaned OSC-001 (Oscilloscope) to A0123456X on 2026-10-13. Due
2026-10-30.` The loan date is today's date according to the computer's clock and
local time zone. The due date uses `YYYY-MM-DD` and must not be earlier than
today. Both `b/` and `d/` are required exactly once and may appear in either
order. Asset IDs are case-sensitive.

The equipment must be registered and must not already be out. Unknown equipment,
malformed or past due dates, and missing, repeated, or unknown prefixes are
rejected without recording anything. A unit that someone still has is refused with
a message naming the current borrower and due date, so the same unit cannot be
lent twice. Unresolved severe faults also prevent new loans, including after a
return. Minor, moderate and unspecified faults do not block lending.

Borrower IDs must already exist in the shared borrower book. The main console
rejects unknown borrower IDs. Equipment and loan history are saved in the
configured LabLog file; borrower records are saved in the sibling
`borrowers.properties` file. Both are reloaded on restart.

### Recording a return: `return`

At the initial prompt, use either format:

```text
return ASSET_ID [faults/DESCRIPTION]
return ASSET_ID ASSET_ID ...
```

For example, return two units together, then return a third with reported damage:

```text
return OSC-001 OSC-002
return OSC-003 faults/cracked screen
```

Separate asset IDs with spaces to return multiple units in one operation.
Each ID must appear only once, identify registered equipment, and have exactly
one active loan. All selected loans are checked before any are changed. If
any selection or return date is invalid, the entire batch is rejected.

Batch returns record no new faults. To report damage, return the affected
unit individually using `faults/DESCRIPTION`. Supplying `faults/` with multiple
asset IDs is rejected without changing any loan. All units in a successful
batch receive the same return date. Their loan records remain available in
history and are saved together with the LabLog data.

The return date is today's date according to the computer's clock and local time
zone. Omit `faults/` if the unit is returned in its original condition with no new
damage to report. This records "No faults reported on return"; it does not clear
existing equipment faults. If supplied, `faults/` must appear once with a non-empty,
single-line description. Asset IDs are case-sensitive.

The equipment must exist and have exactly one active loan. Invalid commands,
repeated returns, multiple active loans, and returns before the loan date are
rejected without changing records. Returned loans remain in equipment history,
including their reported faults, and are excluded from active-loan listings.

Fault details are stored on the loan only; this command does not create a separate
fault record or certify that the equipment is available for lending. The updated
loan and return details are saved with the LabLog data.

### Viewing equipment history: `history`

At the main prompt, enter `history ASSET_ID`, for example `history OSC-001`.
The identifier is case-sensitive and must be a single token. Register the
equipment first using `add`.

The display combines loan, reservation, fault, and maintenance records for that
unit, oldest first. Loans include both active and returned records, with borrower
ID, due date, and return date where available. Each row represents one record,
not every change made to that record. Reservation, fault, and maintenance details
are supplied by their respective features.

Dates mean loan date, reservation start date, fault report date, or maintenance
date. Future reservations are included. Same-day entries are ordered by type
(loan, reservation, fault, maintenance), then description.

Unknown equipment produces an error; registered equipment with no supplied records
produces `No history found`. Enter `exit` at the initial prompt to close LabLog.

The application combines loan history with notes recorded through `reservation`,
`fault`, and `maintenance`. Fault records survive restarts; reservation and
maintenance notes last only for the session.

## FAQ

**Q**: How do I transfer my data to another computer? 

**A**: Close both consoles and copy `data/lablog.properties` for equipment and
loan history, plus `data/borrowers.properties` for borrower records. Start the
main JAR or `BorrowerCli` with the corresponding file path on the other computer.
Files under `build/` are disposable and are removed by Gradle `clean`.

## Command Summary

| Input | Console | Purpose |
| --- | --- | --- |
| `help [COMMAND]` | Both | View commands and explained examples |
| `add ASSET_ID [n/NAME c/CATEGORY m/MODEL l/LOCATION]` | Main | Register equipment |
| `copy SOURCE_ASSET_ID NEW_ASSET_ID` | Main | Copy descriptive equipment details |
| `list` | Main | List equipment and availability |
| `active-loans [overdue]` | Main | List all active loans or only overdue loans |
| `loan ASSET_ID b/BORROWER_ID d/DUE_DATE` | Main | Lend an available unit |
| `history ASSET_ID` | Main | View supplied equipment records |
| `return ASSET_ID [faults/DESCRIPTION]` | Main | Return one unit, optionally reporting new faults |
| `return ASSET_ID ASSET_ID ...` | Main | Return multiple units together, with no new faults reported |
| `reservation|maintenance ASSET_ID d/DATE desc/DETAILS` | Main | Save a dated history note |
| `fault ASSET_ID d/DATE [s/SEVERITY] desc/DETAILS` | Main | Save an identifiable equipment fault |
| `repair FAULT_ID d/DATE desc/DETAILS` | Main | Resolve a fault and retain its repair history |
| `exit` | Main | End the session |
| `add-borrower BORROWER_ID n/NAME p/PHONE e/EMAIL` | Both | Register and save a borrower |
| `loans BORROWER_ID` | Both | List a registered borrower's active loans |

Use `reservation`, `fault`, or `maintenance` with `ASSET_ID d/DATE desc/DETAILS`
to record a dated event in the main console. Fault reports are persistent records.

## Edit borrower contacts (US21)

Use edit-borrower BORROWER_ID [n/NAME] [p/PHONE] [e/EMAIL] in either console.
For example, edit-borrower A0123456X p/92345678 e/new@u.nus.edu.
Supply at least one non-empty field; prefixes may occur once each, in any order.
Omitted values stay unchanged. Existing name, phone and email validation applies.
The borrower ID and loan references remain stable. Invalid input or a failed save
leaves the original contacts unchanged.

## Export application data (US61)

Use export FILE in the main console, for example export backups/lablog.properties.
FILE must not already exist. Paths containing spaces may be enclosed in double
quotes. Export does not change current records. Store backups securely because
they include borrower contact details.

Format 6 contains equipment, borrowers, complete loans and returns, history
notes, and structured faults with severity, resolution dates and repair details.
Formats 2, 3 and 4 remain readable. Legacy fault notes remain notes; format-3
faults have unspecified severity, and older faults load as unresolved.
Reservation domain records and audit records require future schema extensions.

## Import application data (US62)

Use import FILE --replace in the main console. This replaces all current records,
rather than merging them. Export a backup first if the current records are needed.
For example: import backups/lablog.properties --replace. Paths may contain spaces
and may be enclosed in double quotes. A successful import takes effect immediately
and survives restart. Missing --replace or invalid records cause an error.

The main console now saves one format-6 file containing equipment, borrowers,
loans, returns, structured faults, repairs and history notes. It also loads legacy equipment/loan data with
the sibling borrowers.properties and migrates on the next successful save.
That old borrower file is retained, but is no longer read after migration.
The standalone borrower console continues to use its own borrower file; it no
longer edits the main console's contacts after migration. Use edit-borrower in
the main console for those records.

## Invalid import protection (US63)

Imports reject unsupported versions, missing or unknown fields, duplicate
property keys or equipment/borrower IDs, broken references, impossible dates,
multiple active loans for one unit, and invalid contact details. Files must be
valid UTF-8 regular files no larger than 10 MiB. Counts are bounded at 100,000
records per section. Audit records are unsupported in the current schema.

Any validation or save failure reports an error and preserves current live and
saved records. Fix the reported problem and submit the import again. Unknown
fields are rejected so a newer feature's data cannot be silently discarded.


### Unknown snapshot fields

Each supported version has a closed schema: an unrecognized property is rejected, even if all
required fields are valid. This includes extra record fields and indexed fields
outside the declared counts. For example, equipment.0.serialNumber produces
"Invalid snapshot: Unknown snapshot field: equipment.0.serialNumber."
The source and current saved records remain unchanged. Unsupported fields are
never accepted and then discarded by re-exporting. Use a compatible application
version when a file contains data from a newer feature.

## Recall and adapt commands (US64)

Use commands to list the last 20 submitted lines, oldest first, then recall INDEX
to display one. For example, recall 1 displays the oldest retained command.
Recall never executes a command. Copy the displayed line, edit it if needed,
and submit it as a new command to execute it.

History is scoped to this console session and is not saved or exported.
It includes invalid commands, but excludes blank input, exit, commands, recall,
and answers to equipment-registration prompts. Indices shift when the oldest
line is discarded. Both consoles support this feature. Recent input may contain
contact details, so avoid displaying it when sharing your screen.

### Sorting filtered equipment

Use `list [category/VALUE] [location/VALUE] [status/available|on-loan] [sort/FIELD]`.
Filtering and sorting can be combined in any order. Sort fields are `asset-id`,
`name`, `category`, `model`, `location`, and `availability`. For example,
`list category/Electronics sort/name` sorts only matching units. Comparisons
are ascending and case-insensitive, with asset ID and exact ID case as tie-breakers.
Sorting a copied snapshot preserves the stored registration order.

## Reserve equipment (US34)

Use `reserve ASSET_ID b/BORROWER_ID from/YYYY-MM-DD to/YYYY-MM-DD`.
Both the equipment and intended borrower must already be registered. Supply each
field once, in any order. Dates are inclusive: a one-day reservation is valid,
and a shared endpoint is occupied by both periods. Start dates cannot be before
today. `reservations` lists stable reservation IDs; `history ASSET_ID` shows the
period and intended borrower. Invalid input or a failed save changes no bookings.

These are structured records in snapshot format 6, preserved by restart, export
and replacement import. Older formats load with no bookings. The existing
`reservation ... d/DATE desc/TEXT` command remains a history note and cannot
reserve a unit. Overlapping reservations and loans are rejected.

## Booking conflicts (US35)

Reservations and loans occupy inclusive start/end dates. A booking ending on
Friday conflicts with another starting Friday; starting Saturday is valid.
Returned loans occupy their actual loan-to-return dates. An active loan occupies
its loan-to-due dates; once overdue, it blocks future periods until returned.
Unresolved severe faults block reservations and lending for every period because
there is no known repair completion date. Errors identify the conflicting booking
or loan. The same rule applies even for the same borrower: cancel a reservation
before issuing its loan. Legacy notes are not booking constraints.
