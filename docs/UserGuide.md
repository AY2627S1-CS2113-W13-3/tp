# User Guide

## Introduction

LabLog helps laboratory technicians manage loanable electronics equipment and
borrowers. The main console shares equipment, borrowers, loans, reservations,
waiting requests, faults, repairs and audit records across its commands. A standalone borrower console remains available.
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

The main console saves all equipment, borrower, loan, renewal, loss, audit,
reservation, waiting-request, fault, repair and history-note records together
in `data/lablog.properties` (snapshot format 11) after successful changes. It reloads them on
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
list [category/VALUE] [location/VALUE] [status/available|on-loan|unavailable|lost] [sort/FIELD]
```

For example, `list category/Electronics location/Lab A status/available` shows
electronics stored in Lab A that are currently available. Category and
location values match exactly, ignoring case; when combined, all supplied
filters must match. This status is current operational status, not availability
for a requested date range. Status precedence is `lost`, `on-loan`,
`unavailable`, then `available`: lost equipment is `lost`; equipment with an
active loan is `on-loan`; equipment with an unresolved severe fault and no
higher-priority status is `unavailable`; otherwise it is `available`.
Unavailable equipment displays `Unavailable: severe fault`. A reservation does
not change this undated operational status; reservations block loans only when
their date ranges overlap.
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

### Searching equipment by name or model: `search`

Enter `search TEXT` to find equipment whose name or model contains the supplied
text. Matching ignores case and collapses repeated spaces, so `search scope`
matches both `Digital Oscilloscope` and a model such as `ScopeMate`. Results
remain in inventory order and show current availability. Search only checks
names and models; it does not search asset IDs, categories, or locations.

An empty inventory reports `No equipment registered.` No matches reports the
query that was searched. Search is read-only and does not change inventory or
saved records.

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
includes asset ID, borrower ID, due date, and status. Lost loans remain listed
as `LOST`, including in overdue-only mode when their due date is before today.
Returned loans are excluded. The overdue-only view also excludes loans due today or later.
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
name, issue date, due date, whether the loan is overdue, and its status.
Outstanding lost loans remain listed with status `LOST`. Returned loans are
excluded; a loan becomes overdue after its due date, not on that date.

The standalone console has no loan-creation command, so after registration it
reports `No active loans for A0123456X - Jamie Tan.` Unknown borrowers produce
an error. In the main console, active loans are shared with the loan and return
commands.

### Viewing common commands and examples: `help`

Format: `help [COMMAND]`

Enter `help` for the concise LabLog command list, or supply one command name
to see its syntax, fields, and explained usage examples. Help is available in both consoles
and does not change records. Command names are case-sensitive; unknown names
and extra arguments produce an error and let you try again.

For example, `help loan` includes the following format and examples:

```text
Format: loan ASSET_ID [ASSET_ID ...] b/BORROWER_ID d/DUE_DATE
Example: loan OSC-001 b/A0123456X d/2026-09-18
Example: loan OSC-001 MIC-002 b/A0123456X d/2026-09-18
```

Detailed help covers `add`, `copy`, `edit-equipment`, `move-equipment`, history-note commands,
`active-loans`, `loan-audit`, `reverse-loan`, `reverse-return`, `list`, `find`,
`loan`, `renew`, `lost`, `return`, `history`, `add-borrower`, `loans`, and `exit`. The list labels
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

### Issuing one or more loans: `loan`

At the initial prompt, enter
`loan ASSET_ID [ASSET_ID ...] b/BORROWER_ID d/DUE_DATE`:

```text
loan OSC-001 b/A0123456X d/2026-10-30
loan OSC-001 MIC-002 b/A0123456X d/2026-10-30
```

These are alternative examples: use one unit or select several distinct units
separated by spaces. All selected units share the borrower, loan date, and due
date. A batch confirmation lists every selected asset ID, for example:
`Loaned OSC-001, MIC-002 to A0123456X on 2026-10-23. Due 2026-10-30.`

The loan date is today's date according to the computer's clock and local time
zone. The due date uses `YYYY-MM-DD` and must not be earlier than today. Both
`b/` and `d/` are required exactly once and may appear in either order after the
asset IDs. Asset IDs are case-sensitive; repeated IDs are rejected.

The equipment must be registered and must not already be out. Unknown equipment,
malformed or past due dates, and missing, repeated, or unknown prefixes are
rejected without recording anything. A unit that someone still has is refused with
a message naming the current borrower and due date, so the same unit cannot be
lent twice. Unresolved severe faults also prevent new loans, including after a
return. Minor, moderate and unspecified faults do not block lending.

The inclusive period from the loan date through its due date must also pass
the shared booking checks. A reservation overlapping even the due date blocks
the loan, including reservations made for the same borrower. Historical loans
are checked using the existing shared availability policy.

LabLog validates the entire selection before adding any loan. If any unit,
borrower, date, or field is invalid, no loans from that command are recorded.
In particular, an unavailable unit later in the selection does not cause the
earlier units to be lent out. Existing loans and their history remain intact.

Borrower IDs must already exist in the shared borrower book. The main console
rejects unknown borrower IDs. A successful batch is saved once, together with
equipment, borrowers, and loan history, in the complete LabLog snapshot and is
reloaded on restart. If saving fails, the session stops and the previous saved
snapshot remains available; restart after fixing file access, as described in
Quick Start.

### Renewing an active loan: `renew`

Format: `renew ASSET_ID d/YYYY-MM-DD`

```text
renew OSC-001 d/2026-11-05
```

Use one case-sensitive asset ID and exactly one `d/` date. The equipment must
have an active, non-lost loan; overdue loans can also be renewed. The new due date must
be later than its current due date and no earlier than today in the computer's
local time zone. For an overdue loan, today is an acceptable new deadline.
Renewal keeps the borrower and original loan date unchanged.

LabLog checks the entire proposed loan-to-new-due period against shared
availability rules, excluding only the loan being renewed. Reservations,
including those for the same borrower, still conflict at shared endpoints.
Cancelled reservations are ignored; unresolved severe faults block renewal.
An unknown asset, returned loan, invalid date or conflict leaves both the loan
and its renewal records unchanged.

Each successful renewal records its date and previous/new due dates. View these
entries with `history OSC-001`; active-loan and borrower-loan listings immediately
use the new deadline. Renewal records persist through restart, export and import,
and remain attached to the loan after return or a later loan of the same unit.
If saving fails, LabLog reports the failure and stops without a success message;
restart from the previous saved state before continuing.

### Viewing borrowing and return evidence: `loan-audit`

```text
loan-audit
loan-audit OSC-001
```

Omit the asset ID to show all recorded borrowing/return events, or supply one
case-sensitive registered asset ID to filter the view. Each numbered event uses
separate lines for the date, type, equipment, borrower, deadline and complete
event/loan IDs. Empty Faults, Related event and Reason fields are omitted.
Events are ordered oldest first; same-day events keep their acceptance
order, including the selection order of a batch return. The query never saves
or changes records.

For example, a return with reported damage appears as:

```text
Loan audit for OSC-001:

[1] RETURN — 2026-10-23
    Asset: OSC-001
    Borrower: A0123456X
    Due: 2026-10-30
    Faults: cracked screen
    Event ID: E-bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb
    Loan ID: L-11111111-1111-1111-1111-111111111111
```

Each accepted loan creates a `LOAN` event and each accepted return creates a
`RETURN` event. Their stored facts do not change when a loan is renewed, returned
or marked lost. Different loans of the same equipment have different loan IDs;
the related loan/return events share that loan ID. Rejected commands add no events.
Renewal, loss, fault and maintenance records remain available through `history`.

Correction evidence uses `REVERSE_LOAN` or `REVERSE_RETURN`, with the original
event ID and a reason. Both events remain visible; this query does not apply a
correction. Use `reverse-loan` or `reverse-return` to apply an explicit correction.

Older saved files have no complete event log, so LabLog does not invent past
operations when loading them. `history ASSET_ID` still shows those loans. The
first successful save retains their newly assigned loan IDs; later borrowing
and return operations create actual events. An old loan returned after migration
may therefore have a return event without a recorded issue event.

Events and IDs survive restart, export and replacement import. Saving a failed
operation does not produce a success message: LabLog stops and the previous
snapshot remains intact, as with loan/return storage failures.

### Correcting a mistaken loan or return

First run `loan-audit [ASSET_ID]` and copy the complete **Event ID** of the original
`LOAN` or `RETURN` event. Use that event ID rather than the asset ID or loan ID:

```text
reverse-loan E-123 reason/Equipment was not handed over
reverse-return E-456 reason/Equipment has not been returned
```

The example IDs are placeholders; replace them with IDs from your own audit output.
Both commands require one case-sensitive event ID and one non-empty, single-line
`reason/` field. The correction uses today's local date and must not precede that
loan's recorded actions. Each original event can be reversed only once.

* `reverse-loan` cancels the effect of an erroneous issue. The loan remains in
  `history` as `REVERSED` and no longer blocks availability or appears in active
  loan lists. A return must be reversed first; renewed or lost loans cannot be
  cancelled by this command.
* `reverse-return` reopens the same loan, retaining its borrower, deadline and
  renewal history. Current return date/fault details are cleared, while their
  original audit evidence stays intact. The equipment becomes outstanding again.
  A later genuine return creates a new `RETURN` event.

A later effective loan of the same equipment blocks either correction, even if
that later loan has since been returned. Correct later mistakes first; do not
reverse genuine transactions just to bypass this protection. Reopening also
checks shared availability for the original loan-to-due interval, or indefinitely
if overdue. Overlapping reservations (including endpoints), other loans and
unresolved severe faults block reopening. Other reservations and fault records
are never automatically cancelled or cleared.

Successful output identifies the correction event and echoes the reason.
`loan-audit` retains both the original and corrective events with their related
IDs; restart, export and import preserve them. Older loans without an original
`LOAN` event cannot be cancelled using `reverse-loan`, but a return recorded after
migration can be reversed. Legacy same-day loan ordering without issue evidence
is treated conservatively as a possible dependency.

Rejected corrections change no records. If saving fails, the session stops without
a success message and the previous saved snapshot remains intact. Restart from
that saved state before continuing.

### Reporting loaned equipment lost: `lost`

Format: `lost ASSET_ID`

```text
lost OSC-001
```

Supply one case-sensitive asset ID with exactly one active loan. LabLog records
today's local date as the loss report date. The loan remains outstanding, with
its borrower, issue date, original/current deadlines and renewal history intact.
Returned equipment, duplicate reports and malformed input are rejected without
changes. A report date cannot precede the loan or its latest renewal.

`list` and `find` show `Lost`; `list status/lost` selects only lost units.
`list status/on-loan` selects ordinary outstanding loans, excluding lost units.
`active-loans` and `loans BORROWER_ID` retain the borrower association and display
`LOST`. The overdue-only view includes a lost loan only when its due date is
before today; its status still reads `LOST`. `history` includes a dated loss row
alongside the original loan and any renewal events.

Lost units stay unavailable for all periods, including after the original due
date. New lending, reservations, ordinary returns and renewals are rejected.
A batch return containing a lost unit changes none of the selected loans.
Existing reservations remain recorded and must be handled explicitly; reporting
a loss does not cancel them. A category with only lost or severely faulty units
has no operationally suitable capacity for a waiting request.

Loss reports survive restart, export and import. There is no recovery command
in this version. If saving fails, the session stops without reporting success;
the previous saved snapshot remains intact.

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
repeated returns, multiple active loans, and returns before the loan date or latest renewal are
rejected without changing records. Returned loans remain in equipment history,
including their reported faults, and are excluded from active-loan listings.
Lost loans cannot be returned using this command.

Fault details are stored on the loan only; this command does not create a separate
fault record or certify that the equipment is available for lending. The updated
loan and return details are saved with the LabLog data.

### Viewing equipment history: `history`

At the main prompt, enter `history ASSET_ID`, for example `history OSC-001`.
The identifier is case-sensitive and must be a single token. Register the
equipment first using `add`.

The display combines loan, reservation, fault, and maintenance records for that
unit, oldest first. Loans include both active and returned records, with borrower
ID, due date, and return date where available. Renewals add separate rows with
the previous and new due dates; loss reports add dated `LOST` rows. Other rows represent current records rather
than every change made to them. Reservation, fault, and maintenance details
are supplied by their respective features.

Dates mean loan date, reservation start date, fault report date, maintenance date,
renewal date, or loss report date. Future reservations are included. Same-day entries are ordered by type
(loan, reservation, fault, maintenance, renewal, lost), then description. Renewals on
the same day retain the order in which they were accepted.

Unknown equipment produces an error; registered equipment with no supplied records
produces `No history found`. Enter `exit` at the initial prompt to close LabLog.

Use `loan-audit [ASSET_ID]` for the separate borrowing/return event log.
The application combines loan history with notes recorded through `reservation`,
`fault`, and `maintenance`. These records and notes survive restarts and are
included in snapshot exports. Only command recall history is session-only.

## Current limitations

- `list` and `find` derive their labels from loans, losses and severe faults;
  they do not yet account for structured reservations. Use `available from/START
  to/END` to check bookings for the entire intended interval before lending.
- `loan-audit` displays stored events, including correction records. Use
  `reverse-loan` or `reverse-return` with an event ID and reason to correct a
  mistaken issue or return; dependency and availability checks may reject a
  correction.
- There is no lost-equipment recovery or automatic waiting-request allocation.

## FAQ

**Q**: How do I transfer my data to another computer? 

**A**: Export a snapshot with `export FILE`, or close the main console and copy
its configured `data/lablog.properties` file. That single file includes borrowers
and all main-console records. Start the main JAR with that path on the other
computer, or use `import FILE --replace` to replace an existing collection.
If you use the standalone `BorrowerCli` separately, close it and copy its own
borrower file too; it is independent of the current main snapshot. The sibling
borrower file is used by the main console only when migrating legacy split-file data.
Files under `build/` are disposable and are removed by Gradle `clean`.

## Daily operational overview

Enter `overview` in the main console to view three sections for the application's
current local date:

```text
overview
```

- **Today's reservations:** non-cancelled bookings whose inclusive period covers
  today, including bookings that started earlier or end today. Rows show booking
  ID, equipment ID, borrower ID and dates, ordered by equipment ID then booking ID.
- **Overdue loans:** outstanding loans due strictly before today, ordered by due
  date, equipment ID then loan ID. Loans due today, returned loans and reversed
  issues are excluded. Outstanding lost loans remain included when overdue and
  are labelled `LOST (overdue)`.
- **Unresolved severe faults:** each blocking severe report, with fault ID,
  equipment ID, report date and description, ordered by report date, equipment ID
  then fault ID. Repaired, minor, moderate and unspecified faults are excluded.
  Future-dated severe reports are included because they already block lending
  under the shared fault rules.

Each section includes a record count and a clear message when empty. Equipment
can appear in multiple sections, and multiple severe reports are shown separately.
Reservations remain listed even if equipment later becomes lost or develops a
fault; the overview does not cancel bookings or certify availability.

The command takes no arguments and does not change or save records. Each call
reflects current bookings, returns, renewals, reversals, repairs and imports.
Maintenance scheduling is outside the approved scope: maintenance history notes
are not treated as due-maintenance tasks, and no maintenance schedule is invented.

## Explaining unavailable equipment: `why-unavailable`

Use `why-unavailable ASSET_ID` to check one registered unit for today in the
application's local time zone, or supply both dates to check a future interval:

```text
why-unavailable OSC-001
why-unavailable OSC-001 from/2099-01-01 to/2099-01-02
```

Asset IDs are exact and case-sensitive. `from/` and `to/` may appear in either
order, once each. Both are required when specifying dates; the start must be
today or later and the end must be on or after the start. Invalid dates, unknown
equipment, extra fields and duplicate fields produce an error without changes.

The result shows every applicable reason: loss, each unresolved severe fault
(with ID, date and description), overlapping reservations (with ID, borrower and
dates), and overlapping loans (with ID, borrower, due date and return state).
Multiple reasons can apply simultaneously. Resolved faults, cancelled bookings
and records for other equipment are excluded. No blockers produces an explicit
available-throughout-the-interval message.

These are the same inclusive interval rules used by `available` and lending:
the return day remains occupied, an overdue unreturned loan blocks future dates
indefinitely, and losses or blocking faults apply regardless of the query dates.
Consequently `list`/`find` may show a returned unit as available while this query
explains its occupied return day or a reservation. Use this command for a full
booking explanation. Queries do not reserve equipment or rewrite saved data.
Repairs, cancellations, returns and imports are reflected on the next query.

## Command Summary

| Input | Console | Purpose |
| --- | --- | --- |
| `help [COMMAND]` | Both | View commands and explained examples |
| `add ASSET_ID [n/NAME c/CATEGORY m/MODEL l/LOCATION]` | Main | Register equipment |
| `copy SOURCE_ASSET_ID NEW_ASSET_ID` | Main | Copy descriptive equipment details |
| `list` | Main | List equipment and availability |
| `active-loans [overdue]` | Main | List all active loans or only overdue loans |
| `overview` | Main | Show today's reservations, overdue loans and unresolved severe faults |
| `loan ASSET_ID [ASSET_ID ...] b/BORROWER_ID d/DUE_DATE` | Main | Lend one or more available units together |
| `renew ASSET_ID d/YYYY-MM-DD` | Main | Extend an active loan and retain the change |
| `loan-audit [ASSET_ID]` | Main | View stored borrowing/return events and related corrections |
| `reverse-loan EVENT_ID reason/TEXT` | Main | Correct a mistaken loan and retain its original evidence |
| `reverse-return EVENT_ID reason/TEXT` | Main | Correct a mistaken return after dependency and availability checks |
| `lost ASSET_ID` | Main | Record a loss while retaining the outstanding loan |
| `history ASSET_ID` | Main | View equipment records, renewal events and loss reports |
| `why-unavailable ASSET_ID [from/START to/END]` | Main | Explain all applicable booking blockers |
| `return ASSET_ID [faults/DESCRIPTION]` | Main | Return one unit, optionally reporting new faults |
| `return ASSET_ID ASSET_ID ...` | Main | Return multiple units together, with no new faults reported |
| `reservation|maintenance ASSET_ID d/DATE desc/DETAILS` | Main | Save a dated history note |
| `fault ASSET_ID d/DATE [s/SEVERITY] desc/DETAILS` | Main | Save an identifiable equipment fault |
| `repair FAULT_ID d/DATE desc/DETAILS` | Main | Resolve a fault and retain its repair history |
| `edit-equipment ASSET_ID [n/NAME] [c/CATEGORY] [m/MODEL] [l/LOCATION]` | Main | Edit descriptive fields |
| `move-equipment ASSET_ID[,ASSET_ID...] l/LOCATION` | Main | Move selected units together |
| `find ASSET_ID` | Main | Look up one exact asset ID |
| `reserve ASSET_ID b/USER from/START to/END` | Main | Create a reservation |
| `reservations` | Main | List bookings and their IDs |
| `edit-reservation ID [b/USER] [from/START] [to/END]` | Main | Edit a booking |
| `cancel-reservation ID reason/TEXT` | Main | Cancel and retain its history |
| `available from/START to/END [category/CATEGORY]` | Main | Query the full requested interval |
| `wait-request b/USER category/CATEGORY from/START to/END` | Main | Record unallocated demand |
| `waiting-requests` | Main | List waiting demand |
| `edit-borrower ID [n/NAME] [p/PHONE] [e/EMAIL]` | Both | Edit contacts |
| `export FILE` | Main | Save a snapshot to a new file |
| `import FILE --replace` | Main | Validate and replace all records |
| `commands` / `recall INDEX` | Both | Display recent input without executing it |
| `exit` | Both | End the session |
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

Format 11 contains equipment, borrowers, complete loans and returns, structured
renewal events, loss reports, stable loan IDs, borrowing/return audit events,
history notes, faults with severity and repair details, reservations and waiting
requests. Formats 2 through 10 remain readable. Formats before 10 load without
loss reports; formats before 9 have no renewal events and treat the saved due
date as the original deadline. Formats before 11 have no event log. Legacy fault notes remain notes. Unknown future fields
are rejected instead of silently discarded.

## Import application data (US62)

Use import FILE --replace in the main console. This replaces all current records,
rather than merging them. Export a backup first if the current records are needed.
For example: import backups/lablog.properties --replace. Paths may contain spaces
and may be enclosed in double quotes. A successful import takes effect immediately
and survives restart. Missing --replace or invalid records cause an error.

The main console now saves one format-11 file containing equipment, borrowers,
loans, returns, renewal events, loss reports, stable IDs and borrowing/return audit
events, structured faults, repairs, history notes, reservations and waiting
requests. It also loads legacy equipment/loan data with
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
records per section. Borrowing/return audit events and stable IDs are supported.
Duplicate IDs, invalid event ownership and broken correction references are rejected. Invalid renewal chains or dates, and loss reports on returned
loans or before the latest renewal, are rejected.

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

Use `list [category/VALUE] [location/VALUE] [status/available|on-loan|unavailable|lost] [sort/FIELD]`.
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

These are structured records introduced in snapshot format 6, preserved by restart, export
and replacement import. Older formats load with no bookings. The existing
`reservation ... d/DATE desc/TEXT` command remains a history note and cannot
reserve a unit. Overlapping reservations and loans are rejected.

## Booking conflicts (US35)

Reservations and loans occupy inclusive start/end dates. A booking ending on
Friday conflicts with another starting Friday; starting Saturday is valid.
Returned loans occupy their actual loan-to-return dates. An active loan occupies
its loan-to-due dates; once overdue, it blocks future periods until returned.
Lost equipment also blocks every period without an assumed recovery date.
Unresolved severe faults block reservations and lending for every period because
there is no known repair completion date. Errors identify the conflicting booking
or loan. The same rule applies even for the same borrower: cancel a reservation
before issuing its loan. Legacy notes are not booking constraints.

## Edit a reservation (US36)

Use `edit-reservation RESERVATION_ID [b/BORROWER_ID] [from/START] [to/END]`.
Find IDs with `reservations`. Supply at least one field, once each, in any order.
Omitted fields stay unchanged; the unit and reservation ID remain stable.
The intended borrower must be registered. Changed start dates cannot be past;
an unchanged start may be past for an ongoing booking, but the new end must be
today or later. The full proposed period is checked against all other bookings,
loans and severe faults. A rejected edit or failed save leaves the original intact.

## Cancel a reservation (US37)

Use `cancel-reservation RESERVATION_ID reason/TEXT`. A non-empty, single-line
reason is required. Cancellation releases that booking's interval immediately
but does not alter loans, faults or other bookings. The original user and period
remain in `reservations` with cancelled status; `history ASSET_ID` also shows
the cancellation date and reason. Unknown or already-cancelled IDs are errors.
Cancelled records cannot be edited or reactivated. Failed saves leave the booking
confirmed. Snapshot format 7 preserves cancellations; format-6 bookings load as
confirmed, and earlier formats have no structured bookings.

## Find equipment for a period (US38)

Use `available from/START to/END [category/CATEGORY]`, for example
`available from/2099-01-01 to/2099-01-02 category/Electronics`. The interval is
inclusive and starts today or later. Options may appear in any order, once each;
category matches exactly ignoring case and may contain spaces. Results are
ordered by asset ID. Every listed unit is free for the entire interval under the
same loan, reservation and severe-fault rules used by booking. Cancelled bookings
are ignored. This is a read-only query, not a reservation or a guarantee against
later changes. Invalid queries are rejected even when inventory is empty.


## Record waiting demand (US39)

Use `wait-request b/A0123456X category/Lab Electronics from/2099-01-01 to/2099-01-02`
when every operationally suitable unit in that category is booked for at least
part of the requested interval. Categories match exactly ignoring case and can
contain spaces. The borrower must be registered. Dates use YYYY-MM-DD, include
both endpoints, allow a same-day request and must start today or later.

`waiting-requests` lists IDs, users, categories, periods and WAITING status.
These requests do not reserve a unit, change loans, or block availability.
They remain waiting if capacity becomes available; there is no automatic
allocation or conversion. Check `available` and explicitly create a reservation
when appropriate. Resolving/removing waiting requests is outside this version.

Missing or repeated fields, invalid dates, an unknown borrower, duplicate demand
(same user, category and dates), unknown categories and available capacity are
rejected without changes. A category containing only severely faulty units is
not considered fully booked: it has no operationally suitable units.

Format 8 saves waiting requests separately from reservations and preserves both
through restart, export and import. Formats 2 through 7 remain readable with an
empty waiting list. Validation or save failure leaves previous records intact.
