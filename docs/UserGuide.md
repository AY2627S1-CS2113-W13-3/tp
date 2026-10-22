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

The main console saves equipment, loans, return dates, and reported return faults
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

### Listing equipment: `list`

Enter `list` in the main console to show asset ID, name, category, model,
location, and availability for each registered unit. A unit is on loan when it
has an active loan; returning it makes it available in the next listing.
An empty inventory reports `No equipment registered.` No records are changed.

### Recording equipment history notes

Use `reservation ASSET_ID d/YYYY-MM-DD desc/DESCRIPTION`, `fault` with the same
fields, or `maintenance` with the same fields. For example:
`fault OSC-001 d/2026-10-14 desc/Cracked screen`.
The equipment must already be registered. Dates must be real calendar dates,
and descriptions must be non-empty and single-line. View recorded notes using
`history ASSET_ID`. Notes describe events only: they do not reserve availability
or prevent loans, and are lost when the session ends.

### Listing all active loans: `active-loans`

Enter `active-loans` to show every current loan, ordered by due date. Each row
includes asset ID, borrower ID, due date, and overdue status. Returned loans are
excluded; a loan due today is not overdue.

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

Detailed help covers `add`, history-note commands, `active-loans`, `list`,
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
lent twice; once it is returned it can be lent again.

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
`fault`, and `maintenance`. These notes last for the session and are not saved
across restarts.

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
| `list` | Main | List equipment and availability |
| `active-loans` | Main | List all active loans |
| `loan ASSET_ID b/BORROWER_ID d/DUE_DATE` | Main | Lend an available unit |
| `history ASSET_ID` | Main | View supplied equipment records |
| `reservation|fault|maintenance ASSET_ID d/DATE desc/DETAILS` | Main | Add a session history note |
| `return ASSET_ID [faults/DESCRIPTION]` | Main | Return one unit, optionally reporting new faults |
| `return ASSET_ID ASSET_ID ...` | Main | Return multiple units together, with no new faults reported |
| `exit` | Main | End the session |
| `add-borrower BORROWER_ID n/NAME p/PHONE e/EMAIL` | Both | Register and save a borrower |
| `loans BORROWER_ID` | Both | List a registered borrower's active loans |

Use `reservation`, `fault`, or `maintenance` with `ASSET_ID d/DATE desc/DETAILS`
to add a dated history note in the main console.

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

Format 2 contains equipment, borrowers, full loan and return details, and the
currently supported reservation, fault and maintenance notes. Rich reservation
and fault models and audit records are not yet implemented on develop. Their
owners must agree a new schema before those records can be transferred; they
are not represented by inventing data or treating display notes as full records.
