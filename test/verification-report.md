# Current feature verification

Verified locally on 2 November 2026 for the v2.1 preparation branch based on
upstream develop at 6f20236. Runtime: Temurin OpenJDK 25.0.4 on Windows.

## Results

| Check | Result |
| --- | --- |
| Gradle clean check shadowJar | Passed; 320 JUnit tests, Checkstyle main/test passed, executable JAR built |
| python test/run-cli-smoke-tests.py | Passed; ten packaged CLI checks, including retirement command help |
| Packaged v2.1 probe | Passed; startup banner and JAR manifest identify 2.1; retirement, restart, find, active and retired lists verified |
| Local documentation link targets | Passed for README and all Markdown files under docs; external URLs and anchors not checked |
| git diff --check | Passed |

The existing JUnit suite includes retirement conflicts, persistence, format-11
migration, terminal safety and invalid snapshot preservation. The packaged smoke
suite covers cross-feature workflows, export/import and restart. The separate
borrower UI transcript and every manual Developer Guide scenario were not rerun
for this version update; their older evidence is historical.

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
listings. Read-only queries must leave the saved bytes unchanged. Additional packaged
checks reject incomplete startup snapshots and reserved equipment-ID separators
without overwriting the rejected file.

The borrower transcript records the current standalone console output, including
the `[OK]` prefix for successful changes; the runner's disposable data path is
shown as `<run-id>`. These local checks do not replace GitHub Actions. CI runs
Gradle check on Windows, macOS and Ubuntu; the Python and PowerShell runners are
explicit local checks.

## Documentation corrections

The Developer Guide now includes independent manual workflows, a disposable
overdue-loan sample, explicit dynamic-ID instructions, and expected rejection
and restart results. The User Guide documents reserved asset-ID separators and
startup rejection of incomplete snapshots. Storage and return-audit descriptions
match the implementation; dependency acknowledgements link to their sources.


Earlier v2.0 documentation corrections: Quick Start uses the released JAR, example
due dates are in the future, sample output includes `[OK]` and aligned tables,
and save-failure behaviour is described once in the FAQ. The Developer Guide was
regrouped after peer review. This update does not change the diagram sources or
verify their browser rendering.

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
