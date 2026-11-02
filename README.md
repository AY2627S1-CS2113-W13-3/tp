# LabLog

Current application version: **LabLog v2.1** (Java 25).

LabLog is a local command-line application for an individual laboratory technician
who manages loanable electronics equipment for NUS student borrowers, can type
fast, and prefers typed commands. One-line registration, batch loans and returns,
and searchable equipment and loan records help the technician record lending
and find outstanding items without repeatedly filling in forms.

Each technician maintains their own local records. LabLog is a single-user
application: staff must not take turns using the same installation or access
another technician's data file during regular work, even at different times.
Borrowers are records managed by the technician, not users who log in to LabLog.
Export/import supports backups and the same technician moving their own records
to another computer; it does not provide staff handovers, shared inventories,
synchronisation, or merging of different technicians' records.

It supports equipment and borrower registration,
equipment copying, editing, relocation and retirement; single-unit and batch lending/returns;
renewals, loss reports, overdue listings and loan audit queries; reservations,
waiting requests, fault reports and repairs; validated import/export; and command recall.

## Command help

Enter `help` in either the main LabLog console or the standalone borrower console
for a concise list of current LabLog commands. Enter `help COMMAND`, such as
`help loan`, for its syntax, fields, and explained usage examples. `help return`
shows returns with and without reported faults; `help add-borrower` demonstrates
both the usual and reordered fields. Help displays these examples without
executing them or changing records.
The list identifies which console supports each command. Equipment registration
uses either a complete `add ASSET_ID n/NAME c/CATEGORY m/MODEL l/LOCATION`
command or the prompted `add ASSET_ID` form.

## Borrower features

The borrower console provides:

* `add-borrower BORROWER_ID n/NAME p/PHONE e/EMAIL` registers a borrower, rejects duplicate IDs, and saves validated contact details.
* `edit-borrower BORROWER_ID [n/NAME] [p/PHONE] [e/EMAIL]` updates validated contacts without changing the borrower ID.
* `commands` and `recall INDEX` display recent session input for explicit resubmission.
* `loans BORROWER_ID` lists a registered borrower's active loans, ordered by due date and then asset ID. Returned loans are excluded, and a loan is overdue only after its due date.

Borrower IDs follow the form `A0123456X`: an uppercase letter, seven digits, and an uppercase letter. Names contain 1-100 characters, phone numbers contain exactly eight digits, and email addresses use `u.nus.edu` (domain matching ignores case; spelling is preserved). All three prefixes are required, may appear in any order, and must occur only once.

The standalone borrower console remains available for borrower registration and
lookup, and starts with its own empty loan book. In the main console, borrower
records are shared with loan commands and persist in the complete LabLog snapshot.

## Setting up in IntelliJ IDEA

Prerequisite: JDK 25. Configure both the project SDK and Gradle JVM to use Java 25.

1. **Ensure JDK 25 is defined as an SDK**, as described [here](https://www.jetbrains.com/help/idea/sdk.html#set-up-jdk).
1. **Import the project _as a Gradle project_**, as described [here](https://se-education.org/guides/tutorials/intellijImportGradleProject.html).
1. **Verify the setup**: Run the Gradle `classes` task, then run `lablog.LabLog.main()` using the repository root as the working directory. Set its program argument to `build/lablog-demo.properties` for a disposable demonstration.

**Warning:** Keep the `src\main\java` folder as the root folder for Java files (i.e., don't rename those folders or move Java files to another folder outside of this folder path), as this is the default location some tools (e.g., Gradle) expect to find Java files.

## Running the main console

Use Java 25 and run from the repository root:

```powershell
java -version
.\gradlew.bat run --console=plain
```

On macOS/Linux, use `./gradlew run --console=plain`. Enter `help` for the command
list or `help return` for explained examples. To register equipment, enter
`add OSC-001`, then answer the name, category, model, and location prompts. Add
a borrower with `add-borrower` before using
`loan OSC-001 b/A0123456X d/2030-12-31` to lend it. Use `history OSC-001` to
check its history, or `exit` to close.

To extend an active loan, use `renew OSC-001 d/2031-01-15`. The new date must
be later than its current deadline and no earlier than today. Shared booking
checks apply, and `history OSC-001` retains each successful deadline change.

To report loaned equipment lost, use `lost OSC-001`. It remains outstanding
with status `LOST` and stays unavailable; ordinary return and renewal are blocked.
Use `list status/lost` to find lost units and `history OSC-001` for the report.

Use `loan-audit` or `loan-audit OSC-001` to inspect recorded borrowing/return
events and their stable IDs. Older files retain their loans without invented
historical events; `history` continues to show those records.

To correct a mistaken transaction, copy its full event ID from `loan-audit` and
use `reverse-loan EVENT_ID reason/TEXT` or `reverse-return EVENT_ID reason/TEXT`.
Both retain the original evidence. Dependent actions and shared availability
checks can block a correction; see the User Guide for the rules.

To return multiple loaned units together, enter `return OSC-001 OSC-002`.
All selected units must pass validation before any loan is changed; duplicate
IDs or an invalid selection reject the entire batch. To report damage, return
the affected unit individually, for example `return OSC-003 faults/cracked screen`.
Batch returns do not accept `faults/`. See the [User Guide](docs/UserGuide.md)
for full return rules and examples.

To build and run the executable JAR:

```powershell
.\gradlew.bat shadowJar
java -jar build/libs/lablog.jar
```

Run the packaged JAR in a terminal to use interactive paging; Gradle `run` may
provide plain input instead. Long results, including `help`, start at the top.
Press Enter for the next page, type `b` then Enter for the previous page,
and type `q` then Enter to close. Enter on the final page also closes it.
Recognised sections in help, detailed help and overview start on new pages.
Each page repeats its result/section heading; unused body rows are blank so
paged results keep a consistent height. Short unpaged results remain compact.
Redirected input and unsupported terminals print the complete result without paging.
Layout uses startup `COLUMNS` (at least 20) and `LINES` (at least 10), defaulting
to 100 columns and 30 rows. Restart after changing these values; resizing is not detected.

The main console supports registration, help, loans, history, active-loan lists,
returns, and borrower commands, so a fresh session can complete a loan/return
workflow. The main console saves equipment, borrowers, loans, returns, faults, repairs and history
notes, renewal events, loss reports, borrowing/return audit events, reservations and waiting requests together in `data/lablog.properties` (format 12). Legacy format-1 installations
load the sibling borrower file and migrate on their next successful save. The
old borrower file is retained as a backup and ignored after migration. Pass another data-file
path after the JAR name to select the complete application snapshot. Use only one
main-console process per data file.

## Running the borrower console

From the repository root in PowerShell:

```powershell
java -version
.\gradlew.bat shadowJar
java -cp build/libs/lablog.jar lablog.BorrowerCli build/borrowers-demo.properties
```

On macOS or Linux, use `./gradlew shadowJar` for the build step; the Java command is the same. Ensure `java -version` reports Java 25.
No terminal library or native-access flag is required.

Example input:

```text
add-borrower A0123456X n/Jamie Tan p/91234567 e/jamietan@u.nus.edu
loans A0123456X
```

Expected responses, following the `lablog>` prompts:

```text
[OK] Added borrower A0123456X: Jamie Tan.
Phone: 91234567 | Email: jamietan@u.nus.edu
No active loans for A0123456X - Jamie Tan.
```

End input with Ctrl+Z followed by Enter on Windows, or Ctrl+D on macOS/Linux. Restart with the same file to reload borrowers. Repeating the registration command will report a duplicate ID.

The file argument is optional; the default is `data/borrowers.properties`. The example uses `build/`, which Git ignores and Gradle `clean` removes. Use a location outside `build/` for records you want to retain, and do not commit borrower data. Invalid commands and failed saves do not register a borrower.

Use this explicit Java command for the borrower console. The existing Gradle `run` task has not been redirected to it.

## Build automation using Gradle

* This project uses Gradle for build automation and dependency management. It includes a basic build script as well (i.e. the `build.gradle` file).
* If you are new to Gradle, refer to the [Gradle Tutorial at se-education.org/guides](https://se-education.org/guides/tutorials/gradle.html).

## Testing

### JUnit and Checkstyle

Run the JUnit and Checkstyle checks with Java 25:

```powershell
.\gradlew.bat test checkstyleMain checkstyleTest
```

Tests cover equipment copy/edit/move and validation, borrower edits, batch loans
and returns, renewals, losses, audit events, overdue boundaries, reservations and
conflicts, waiting demand, fault severity and repairs, snapshot migration and
invalid-import preservation, command recall, help and console recovery. Return/history tests supply shared loan records
directly, which keeps them independent of the loan command.
Batch-return tests check successful updates, duplicate selections, rejected fault
descriptions, and unchanged records when a later selection is invalid. A
main-console persistence test verifies that both returns and their history remain
available after reloading the saved data.
See the [JUnit tutorial](https://se-education.org/guides/tutorials/junit.html) for background.

### Console tests

After compiling, run the recorded console cases with PowerShell 7:

```powershell
pwsh -File test/run-borrower-ui-tests.ps1
```

The runner uses a fresh demonstration file under `build/`, verifies exact output, and checks persistence across two separate Java processes. It stops on the first failure and writes the results to [the recorded transcript](test/borrower-ui-transcript.md). See the [test plan](test/ui-test-plan.md) for inputs and expected output. The optional `-Jar` argument selects an alternate packaged JAR.

Run these console checks explicitly: the Gradle checks and GitHub Actions workflow do not invoke the PowerShell runner. Each run updates the transcript, so review that change before including it in a commit.

For packaged-JAR checks and borrower persistence checks without PowerShell 7,
use Python 3 with Java 25 on `PATH`:

```powershell
.\gradlew.bat shadowJar
python test/run-cli-smoke-tests.py
```

This runner uses temporary application and borrower files under `build/`. It
checks help for every supported command, registration, rejected commands,
recall, and persistence across fresh processes. The integrated scenario also
checks batch lending, renewal, loss, return audit, reservations, waiting requests,
fault repairs and snapshot export/import. See the
[verification report](test/verification-report.md) for the checked revision and results.

## Checkstyle

* A sample CheckStyle rule configuration is provided in this project.
* If you are new to Checkstyle, refer to the [Checkstyle Tutorial at se-education.org/guides](https://se-education.org/guides/tutorials/checkstyle.html).

## CI using GitHub Actions

The project uses [GitHub actions](https://github.com/features/actions) for CI. When you push a commit to this repo or PR against it, GitHub actions will run automatically to build and verify the code as updated by the commit/PR.

## Documentation

See the [User Guide](docs/UserGuide.md) for current commands and limitations,
the [Developer Guide](docs/DeveloperGuide.md) for architecture and verification,
and [borrower implementation notes](docs/BorrowerImplementation.md) for integration.

Final merged-version verification on Windows Java 25 passed all 356 JUnit tests,
Checkstyle, 10 packaged CLI workflows and both borrower-console cases. Interactive
component probes passed with and without colour. Manual terminal checks passed as reported by the project author; see the
[verification report](test/verification-report.md).
