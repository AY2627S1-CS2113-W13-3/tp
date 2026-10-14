# User Guide

## Introduction

LabLog helps laboratory technicians manage loanable electronics equipment and
borrowers. The current build has two consoles: the main console for equipment,
history, and returns, and a separate borrower console for registration and loan
lookups. Both provide command help and examples.

## Quick Start

Use Java 25. From the repository root in PowerShell:

```powershell
java -version
.\gradlew.bat run --console=plain
```

Enter `help` to see commands, `help COMMAND` for examples, and `exit` to close
the main console. On macOS/Linux, replace `.\gradlew.bat` with `./gradlew`.

For the borrower console, compile and run:

```powershell
.\gradlew.bat classes
java -cp build/classes/java/main lablog.BorrowerCli
```

Exit the borrower console with EOF: Ctrl+Z then Enter on Windows, or Ctrl+D
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

At the main prompt, enter a single asset ID, then answer the four prompts:

```text
OSC-001
Oscilloscope
Electronics
Scope Model 1
Lab A
```

LabLog confirms `Equipment added to inventory.` An incomplete entry at EOF is
discarded. Blank or multi-word asset IDs and commands belonging to the borrower
console are rejected at the initial prompt. Other single tokens are treated as
asset IDs; use `help` to check command spelling.

Equipment registration currently does not enforce unique IDs or validate the
four detail fields. Persistent sessions reject duplicate asset IDs. Choose
meaningful details. Completed registrations are saved before confirmation.

### Registering borrowers: `add-borrower`

In the borrower console:

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

In the borrower console, enter `loans A0123456X` for a registered borrower.
Active loans are ordered by due date, then asset ID. Rows include the equipment
name, issue date, due date, and whether the loan is overdue. Returned loans are
excluded; a loan becomes overdue after its due date, not on that date.

The standalone console has no loan-creation command, so after registration it
currently reports `No active loans for A0123456X - Jamie Tan.` Unknown borrowers
produce an error.

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
Borrower IDs are not checked against borrower records yet.
```

Detailed help covers `help`, `loan`, `return`, `history`, `add-borrower`, `loans`,
and `exit`. The list labels commands belonging to the main or borrower console;
viewing help does not make a command available in the other console.

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

Borrower IDs are accepted as entered. The main console holds no borrower records,
so an ID that was never registered is not detected here. Loans are currently in
memory and are not saved across restarts.

### Recording a return: `return`

At the initial prompt, enter `return ASSET_ID [faults/DESCRIPTION]`:

```text
return OSC-001
return OSC-002 faults/cracked screen
```

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
fault record or certify that the equipment is available for lending. Loans and
return details are currently in memory and are not saved across restarts.

### Viewing equipment history: `history`

At LabLog's initial asset-ID prompt, enter `history ASSET_ID`, for example
`history OSC-001`. The identifier is case-sensitive and must be a single token.
Register the equipment first using the existing equipment prompts.

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

**Current integration limit:** the default application starts with no reservation,
fault, or maintenance source, so those entries appear only when shared records are
supplied, as exercised by the integration tests. A fresh session can register a
unit, lend it, return it, and see the resulting loan history.

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
| `ASSET_ID`, followed by prompts | Main | Register equipment |
| `loan ASSET_ID b/BORROWER_ID d/DUE_DATE` | Main | Lend an available unit |
| `history ASSET_ID` | Main | View supplied equipment records |
| `return ASSET_ID [faults/DESCRIPTION]` | Main | Close an existing active loan |
| `exit` | Main | End the session |
| `add-borrower BORROWER_ID n/NAME p/PHONE e/EMAIL` | Borrower | Register and save a borrower |
| `loans BORROWER_ID` | Borrower | List a registered borrower's active loans |

There are no reservation, maintenance, or standalone fault-creation commands in
this build.
