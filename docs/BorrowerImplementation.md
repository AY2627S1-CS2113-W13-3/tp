# Borrower commands (issues #10 and #11)

The borrower implementation uses the shared `Loan` and `LoanBook` models.
Registration and loan listings remain in a separate console; both consoles now
share `HelpCommands` for read-only command guidance and examples.

## Run locally

Use Java 25 from the repository root:

```powershell
.\gradlew.bat classes
java -cp build/classes/java/main lablog.BorrowerCli build/borrowers-demo.properties
```

Enter:

```text
add-borrower A0123456X n/Jamie Tan p/91234567 e/jamietan@u.nus.edu
loans A0123456X
help add-borrower
```

The console accepts commands until `exit` or EOF (Ctrl+Z, then Enter on Windows).
Borrowers persist in the specified file. The default is `data/borrowers.properties`.
Use a path under `build/` for disposable demonstrations to avoid tracking personal data.
The standalone console starts with an empty loan book; it does not implement the
loan command assigned to another team member. Active-loan behavior is exercised
using the real shared loan model in the tests.

Gradle `run` starts `LabLog`, the main equipment console. Use the explicit Java
command above for borrower registration and lookups.

## Connect to the shared application

Create one persistent `BorrowerBook`, one shared `LoanBook`, and one
`BorrowerCommands` instance at startup. Route full `add-borrower` and `loans`
command lines to `execute`, displaying its result and handling
`IllegalArgumentException` and `IOException` as shown in `BorrowerCli`.
Pass the same books to the loan/return commands so changes appear immediately.

The current inventory API supplies names without changes:

```java
BorrowerCommands borrowerCommands = new BorrowerCommands(
        borrowerBook, loanBook,
        assetId -> inventory.getEquipmentUnits().stream()
                .filter(equipment -> assetId.equals(equipment.getAssetId()))
                .map(Equipment::getName)
                .findFirst().orElse(null),
        Clock.systemDefaultZone());
```

Borrowers use the guide's example student-ID shape: an uppercase letter, seven
digits, and an uppercase letter. No check-digit verification is attempted.
Names contain 1-100 characters, phones eight ASCII digits, and email addresses
use `u.nus.edu`. Prefixes may appear in any order. Repeated, missing, empty, and
unknown prefixes are rejected. Surrounding command separator whitespace is
removed; letter case and internal name spaces are preserved.

Successful registration atomically saves the proposed borrower state before
publishing it in memory or displaying success. Invalid commands and failed saves
leave registered borrowers unchanged. Existing malformed data stops startup
instead of silently replacing records. The no-argument `BorrowerBook` is an
in-memory option for tests or callers with their own persistence.

Loan listings exclude returned loans and other borrowers, sort by due date then
asset ID, and treat only dates before today in the supplied clock's zone as
overdue. Missing equipment records display `Unknown equipment` without hiding
the loan. No separate loan status or copied loan list is maintained.

## Verification

For the current combined application checks and remaining limitations, see
[the verification report](../test/verification-report.md). The dated results
below describe the original borrower increment, not the current total test count.

```powershell
.\gradlew.bat test checkstyleMain checkstyleTest
```

JUnit coverage includes field validation, prefix order, duplicate IDs, persistence,
failed writes, corrupt records, exact console output, filtering, sorting,
same-day deadlines, local timezone boundaries, and newly returned loans.

Verified on 12 October 2026 with Temurin Java 25.0.4: all 20 tests (13 new)
and both Checkstyle tasks passed. The default OneDrive build directory had a
locked test-output folder, so verification used a temporary Gradle init script
to move build output outside OneDrive; no project build settings were changed.
Both fresh-process UI cases passed again on 13 October 2026. Run
`pwsh -File test/run-borrower-ui-tests.ps1` with PowerShell 7 to repeat them,
optionally supplying `-Classes` for an alternate compiled-class directory.
The exact session is recorded in `test/borrower-ui-transcript.md`.

An isolated Java integration check compiled against PR #16 at
`1887771e3ec9d3f2acff8fef334bfa2c7834126f`, including its unchanged entry point,
equipment, and inventory classes. It verified equipment-name lookup and immediate
removal of returned loans using the merged loan API from PR #17. No PR code was
merged or copied into the feature source tree. Compatibility is verified against
that revision; future shared-dispatcher changes still need integration review.

Chillguy's SE-EDU conventions are retained: four-space indentation, explicit
imports, descriptive Javadocs beginning with third-person verbs, and descriptive
`method_scenario_expectedOutcome` test names.

## Delivery scope

The work is split into four commits: borrower records and persistence, command
handlers and their JUnit tests, console regression checks, and documentation.
PR #18 was merged into `develop`. This increment provides a separately runnable borrower
console and reusable handlers; it does not complete the shared application flow.

Before closing issues #10 and #11 as complete, connect the handlers to the shared
prompt and verify a full register, issue, list, return, and list-again session
using the same borrower book, loan book, and inventory. Passing this increment's
tests and having no Git conflicts do not replace that integration check.
