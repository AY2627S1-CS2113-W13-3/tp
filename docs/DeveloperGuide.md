# Developer Guide

## Acknowledgements

LabLog builds on the SE-EDU Java project template. Gradle manages compilation,
JUnit 5 runs tests, Checkstyle checks coding conventions, and Shadow builds the
executable JAR. See `build.gradle` for dependency versions.

## Design & implementation

### Entry points and shared records

`LabLog` is the Gradle application entry point. It holds an `Inventory` and
routes help, loan, history, and return commands to their handlers. Equipment
registration requires `add ASSET_ID` for the wizard or `add` with all four
prefixed fields for one-line registration. Unknown commands are rejected.
Borrower-console commands (`loans`, `add-borrower`) are rejected before
registration so their arguments cannot become equipment details.

`BorrowerCli` is a separate entry point backed by a persistent `BorrowerBook`
and an initially empty `LoanBook`. `BorrowerCommands` handles registration,
loan listings, and help. See [borrower integration notes](BorrowerImplementation.md)
for connecting it to shared application records.

`Equipment`, `Borrower`, and `Loan` model individual records. `Inventory`,
`BorrowerBook`, and `LoanBook` store collections. `LoanBook.findActiveLoan`
returns an `Optional<Loan>`; `hasActiveLoan` reports whether a matching active
loan exists. These are lookup helpers, not an enforcement mechanism:
`addLoan` still permits duplicate active loans, and the loan constructor does
not validate identifiers or dates. `LoanCommands` applies those checks before
storing a record; any other issuing path must repeat them.

### Help and command examples

`HelpCommands` contains shared guidance for both consoles and has no record
store dependencies. `help` returns a concise list; `help COMMAND` returns
syntax, fields, and explained examples. Invalid names and extra arguments
produce recoverable errors. The loan example notes that borrower IDs are not
checked against borrower records yet.

`HelpCommandsTest` checks parsing, examples against the real handlers, console
integration, and unchanged records/files when viewing help. `LabLogTest` checks
that unavailable commands and invalid initial input do not consume subsequent
commands as equipment details.

### Issuing loans

`LoanCommands` receives the shared inventory, loan book, and a `Clock`.
`loan ASSET_ID b/BORROWER_ID d/DUE_DATE` accepts the two prefixes in either
order, once each. It checks the syntax, that the unit is registered, that the
borrower ID is a single token, that the due date parses and is not in the past,
and last that the unit has no active loan. The record is stored only after every
check passes, so a rejected command never leaves a partial loan.

Availability is derived from `LoanBook.findActiveLoan` rather than a status field
on `Equipment`, so one place decides which units are out. The refusal names the
current borrower and due date, which is why the lookup returns the loan itself
rather than a boolean.

Borrower identifiers are accepted as given. `LabLog` holds no `BorrowerBook`, so
checking them belongs with the borrower-console integration.

`LoanCommandsTest` covers the accepted command, prefix order, due dates at and
before today, malformed dates, missing and repeated fields, unknown equipment,
the refusal for a unit already out, and lending again after a return.

### Equipment returns

`ReturnCommands` receives the shared inventory, loan book, and a `Clock`.
`return ASSET_ID [faults/DESCRIPTION]` finds exactly one active loan for the unit
and calls `Loan.markReturned(LocalDate.now(clock), faults)`. The loan validates
the date, fault text, and active state before assigning either return field.
The original one-argument method remains available and records no reported faults.

`LabLog` supplies `Clock.systemDefaultZone()` normally and accepts an explicit
clock for tests. Return handling shares the same loan book with history; no copied
return records are created. Fault descriptions appear on returned loan history
entries, and the existing borrower handler excludes the now-inactive loan.
There is no automatic fault-record creation, availability change, or persistence.

`ReturnCommandsTest` covers local-date boundaries, optional faults, invalid input,
repeat returns, multiple active loans, date validation, historical loans, borrower
listing updates, and the return/history console flow.

### Equipment history

`HistoryCommands.execute("history ASSET_ID")` validates the command, checks
equipment existence in the shared `Inventory`, and reads the shared `LoanBook`.
It includes returned loans and builds fresh descriptions on every request, so a
later return is visible without updating a separate history store.

`HistoryRecordCommands` creates reservation, fault, and maintenance notes.
Other features can also supply snapshots through a `Supplier<List<HistoryEntry>>`.
The supplier should read the current source records on every call, include their
status and relevant end dates in the description, and retain closed/cancelled
records when they belong in history. It must return a non-null list and must not
include duplicate loan entries. Entries may cover multiple assets; the handler
filters by exact asset ID and sorts a new list without changing the source order.

Use loan date, reservation start date, fault report date, and maintenance date
as the entry dates. Entries are sorted oldest first, then by enum type order and
description. This is a record summary rather than a permanent event audit trail.

Pass the shared records to
`new LabLog(inventory, loanBook, () -> currentHistoryEntries)` or construct
`HistoryCommands` directly for a future shared dispatcher. The default application
combines caller-supplied entries with session notes from `HistoryRecordCommands`.
The notes do not change equipment availability and are not persisted.

`HistoryCommandsTest` covers all four types, asset filtering, returned loans,
fresh source updates, date ordering, invalid input, empty/unknown equipment, and
the actual application prompt with injected shared data.

## Product scope
### Target user profile

Laboratory technicians managing individual electronics equipment units and
student borrowers through a text interface.

### Value proposition

Keep equipment identity, borrower contacts, and loan history accessible from
the command line. The current handlers support parts of this workflow; the
shared loan-issuing workflow remains unfinished.

## User Stories

|Version| As a ... | I want to ... | So that I can ...|
|--------|----------|---------------|------------------|
|v1.0|new user|see usage instructions|refer to them when I forget how to use the application|
|v1.0|laboratory technician|register borrowers|retain validated contact details|
|v1.0|laboratory technician|view active loans and equipment history|track who has equipment and its past loans|
|v1.0|laboratory technician|record returns and reported damage|close loans while retaining their history|

## Non-Functional Requirements

Build and run with Java 25. Keep help read-only and reject invalid borrower and
return commands before changing records. Save borrower additions atomically
before updating memory. These behaviors are covered by automated tests.

Equipment rejects blank detail fields, and Inventory rejects duplicate asset IDs.
Current limitations: equipment, loans, and return details are not persisted;
the consoles are not unified; the loan command does not check the borrower
against registered borrowers; history notes do not enforce reservations or
maintenance availability. Passing tests does not imply these planned features are
complete.

## Glossary

* **Asset ID:** identifier of one physical equipment unit.
* **Active loan:** a loan whose return date is absent.
* **Overdue loan:** an active loan with a due date before today's local date.
* **History entry:** a display snapshot of a record, not an immutable event log.

## Instructions for manual testing

### Assertions and diagnostic logging

`LabLog` uses Java `assert` statements to check internal assumptions: only
supported record commands reach its dispatch helper, and equipment detail
collection receives an already validated asset ID. User input errors still use
normal validation, which works even when assertions are disabled.

`HelpCommands` and `LabLog` use `java.util.logging` at `FINE` level to trace help
requests, rejected input, and incomplete registration. Logs omit raw input and
borrower details. Enable assertions and console diagnostics from the repository
root after building the JAR:

```powershell
java -ea -Djava.util.logging.config.file=config/logging.properties -jar build/libs/lablog.jar
```

Enter `help`, `help unknown`, a blank line, and `exit` to see diagnostics on
standard error alongside the usual console output. Without the logging option,
the default Java logging configuration hides `FINE` messages. Gradle's test task
enables assertions by default.

### Automated and manual checks

From the repository root with Java 25:

```powershell
.\gradlew.bat test checkstyleMain checkstyleTest shadowJar
python test/run-cli-smoke-tests.py
```

The Python 3 runner checks the packaged JAR and separate borrower processes with
temporary data. The existing PowerShell 7 runner also verifies exact borrower
output: `pwsh -File test/run-borrower-ui-tests.ps1`. See the
[console test plan](../test/ui-test-plan.md) and
[verification report](../test/verification-report.md).

For a manual main-console check, run `java -jar build/libs/lablog.jar`, enter
`help`, `help return`, then enter `add OSC-001` with four detail lines. Check
`history OSC-001`, `return OSC-001`, and `exit`. In this fresh session, history
is empty and return reports that the unit is not on loan. Automated integration
tests supply active loans to verify successful return and history updates.
