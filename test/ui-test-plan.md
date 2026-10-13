# Console UI tests

## Packaged application and borrower smoke checks

With Java 25 on `PATH`, run `.\gradlew.bat shadowJar`, then
`python test/run-cli-smoke-tests.py` (Python 3). This checks the executable JAR,
help examples, input recovery, registration, empty history, return rejection,
and borrower persistence across fresh processes. The runner uses temporary
files under `build/` and does not rewrite the recorded borrower transcript.

The PowerShell 7 cases below remain available for exact-output borrower checks.

Use Java 25. Build with `.\gradlew.bat classes` before testing. Each case runs in
a fresh process from the repository root. Cases 1 and 2 deliberately share the
same data file. Choose a new, nonexistent file under `build/` for each test run.

To run both cases automatically with PowerShell 7, use
`pwsh -File test/run-borrower-ui-tests.ps1`. The runner selects a unique data file,
stops on the first failure, and writes `test/borrower-ui-transcript.md`.
The final prompt's trailing space is checked exactly but omitted from the
Markdown code blocks below and in the transcript.

## Case 1: add, recover from a duplicate, and list

Command: `java -cp build/classes/java/main lablog.BorrowerCli build/ui-borrowers.properties`

Input (then EOF):

```text
add-borrower A0123456X n/Jamie Tan p/91234567 e/jamie@u.nus.edu
add-borrower A0123456X n/Other p/91234567 e/other@u.nus.edu
loans A0123456X
```

Expected stdout (last prompt has a trailing space and no newline):

```text
LabLog borrower console. Use add-borrower or loans. Send EOF to exit.
lablog> Added borrower A0123456X: Jamie Tan.
Phone: 91234567 | Email: jamie@u.nus.edu
lablog> Error: Borrower ID A0123456X already exists. Choose a different borrower ID.
No changes saved.
lablog> No active loans for A0123456X - Jamie Tan.
lablog>
```

## Case 2: reload the persisted borrower

Run the same command with the same file. Input: `loans A0123456X`, then EOF.

Expected stdout (same final-prompt rule):

```text
LabLog borrower console. Use add-borrower or loans. Send EOF to exit.
lablog> No active loans for A0123456X - Jamie Tan.
lablog>
```

Both cases must exit with code 0 and empty stderr. Normalize only line endings.
JUnit additionally verifies exact mixed active-loan output with a fixed local
clock and the real `Loan` and `LoanBook`, as the standalone console has no loan
creation command yet.
