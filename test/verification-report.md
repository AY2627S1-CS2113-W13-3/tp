# Current feature verification

Verified locally on 23 October 2026 against upstream develop revision
2f9c2f6 with the documentation/help/test changes on
chore/update-feature-documentation. Runtime: Temurin Java 25.0.4.
Before publication, the branch was rebased onto 6c119a2, which adds only borrower
design documentation. The new section was also checked and its lost-loan and
console save-failure descriptions aligned with the implementation; application
and test code are unchanged by that base update.

## Results

| Check | Result |
| --- | --- |
| Gradle clean check shadowJar, then check shadowJar after help corrections | Passed; 251 JUnit tests, no failures/errors/skips, Checkstyle main/test passed, executable JAR built |
| python test/run-cli-smoke-tests.py | Passed; eight packaged CLI checks |
| pwsh -File test/run-borrower-ui-tests.ps1 | Passed; two exact-output cases and restart persistence |
| git diff --check | Passed |

## Coverage

JUnit covers equipment copy/edit/move and validation; borrower registration and
contact edits; single/batch loans and returns; overdue boundaries, renewal, loss
and audit records; booking conflicts, reservation edits/cancellation, interval
availability and waiting demand; faults and repairs; snapshot migration and
failed-import preservation; command recall, help and console recovery.

The packaged checks now query help for all 33 supported command names. An
integrated workflow issues two loans, renews one, reports it lost, returns the
other, creates a reservation and waiting request, and reports/resolves a severe
fault. It exports to a path containing spaces, imports into a new data file,
restarts, and compares loan audit, history, bookings, waiting demand and loss
listings. Read-only queries must leave the saved bytes unchanged.

The borrower transcript was regenerated from the current standalone console.
These local checks do not replace GitHub Actions. CI currently runs Gradle check
on Windows, macOS and Ubuntu; the Python and PowerShell runners are explicit
local checks.

## Documentation corrections

README, both guides and borrower implementation notes now distinguish the
complete format-11 main snapshot from the standalone borrower file and legacy
migration. History notes persist; only command recall is session-only. The
command table includes implemented equipment, booking, transfer and recall
commands. Help uses a version-neutral heading and includes a waiting-request
listing example. Current limitations are stated without implying missing
commands are available.

## Remaining limitations

- Equipment list/find labels omit reservations; use the available interval
  query to check bookings. Loans, losses and severe faults do affect labels.
- Partial-text equipment search and the combined daily overview are absent.
- The audit model stores/displays correction records, but there is no user
  command to reverse an erroneous loan or return.
- Lost-equipment recovery and automatic waiting-request allocation are absent.
- The standalone borrower console has its own borrower file and empty loan
  book; it does not edit contacts in the current main snapshot.
- One main-console process per data file is supported.

## Repeat the checks

Use Java 25 and Python 3 from the repository root:

~~~powershell
.\gradlew.bat clean check shadowJar --console=plain
python test/run-cli-smoke-tests.py
pwsh -File test/run-borrower-ui-tests.ps1
~~~

On macOS/Linux use ./gradlew instead of .\gradlew.bat. The PowerShell runner
requires PowerShell 7. Test data under build/ is disposable; use another
location for real records. See ui-test-plan.md for console inputs.
