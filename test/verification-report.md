# Current feature verification

Verified locally on 29 October 2026 against upstream develop revision 5691cc0
(v2.0 candidate), with the test-expectation and documentation updates on
chore/update-lulu-ppp-and-ui-tests. Runtime: OpenJDK 25.0.4.1, PowerShell 7.6.
Application code is unchanged by that branch.

## Results

| Check | Result |
| --- | --- |
| Gradle clean check shadowJar | Passed; 305 JUnit tests, no failures/errors/skips, Checkstyle main/test passed, executable JAR built |
| python test/run-cli-smoke-tests.py | Passed; eight packaged CLI checks |
| pwsh -File test/run-borrower-ui-tests.ps1 | Passed; two exact-output cases and restart persistence |
| git diff --check | Passed |

## Coverage

JUnit covers equipment copy/edit/move and validation; borrower registration and
contact edits; single/batch loans and returns; overdue boundaries, renewal, loss
and audit records; booking conflicts, reservation edits/cancellation, interval
availability and waiting demand; faults and repairs; snapshot migration and
failed-import preservation; command recall, help and console recovery.

The packaged checks query help for every supported command name. An
integrated workflow issues two loans, renews one, reports it lost, returns the
other, creates a reservation and waiting request, and reports/resolves a severe
fault. It exports to a path containing spaces, imports into a new data file,
restarts, and compares loan audit, history, bookings, waiting demand and loss
listings. Read-only queries must leave the saved bytes unchanged.

The borrower transcript records the current standalone console output, including
the `[OK]` prefix for successful changes; the runner's disposable data path is
shown as `<run-id>`. These local checks do not replace GitHub Actions. CI runs
Gradle check on Windows, macOS and Ubuntu; the Python and PowerShell runners are
explicit local checks.

## Documentation corrections

The User Guide was aligned with v2.0: Quick Start uses the released JAR, example
due dates are in the future, sample output includes `[OK]` and aligned tables,
and save-failure behaviour is described once in the FAQ. The Developer Guide was
regrouped after peer review, and its diagrams follow the course UML notation.

## Remaining limitations

- Equipment list/find labels omit reservations; use the available interval
  query to check bookings. Loans, losses and severe faults do affect labels.
- Lost-equipment recovery and automatic waiting-request allocation are absent.
- Renewals and loss reports cannot be reversed; `reverse-loan` and
  `reverse-return` correct only loans and returns.
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
