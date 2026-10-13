# Current feature verification

Verified on 13 October 2026 on Windows with Java 25.0.4, from upstream revision
`5f1c6ae` plus the changes on `chore/verify-features-and-docs`.

## Results

| Check | Result |
| --- | --- |
| Gradle JUnit suite | 53 tests, 0 failures, 0 errors, 0 skipped |
| `checkstyleMain` and `checkstyleTest` | Passed |
| `shadowJar` | Built `build/libs/lablog.jar` |
| `python test/run-cli-smoke-tests.py` | 5 tests passed |

The JUnit count includes the inherited `DukeTest` sample. Feature tests cover
borrower validation, persistence and failed saves; active-loan lookup; return
validation and faults; history ordering and updates; help examples and unchanged
records; and main-console input recovery.

The smoke runner starts the packaged main application and borrower console in
fresh Java processes. It verifies help and error recovery, registration, empty
history, rejecting a return with no active loan, incomplete registration,
exact borrower output, persistence across restarts, and unchanged borrower files
after help or rejected input.

PowerShell 7 was not available, so `run-borrower-ui-tests.ps1` was not executed.
Its two exact-output cases are also exercised by the Python runner against the
packaged JAR. The historical `borrower-ui-transcript.md` was left unchanged.
The smoke tests initially hit Windows sandbox permissions on temporary files;
all five passed when rerun with the required filesystem permissions.

## Issue fixed

The main prompt previously interpreted unavailable commands such as
`loan OSC-001 b/A0123456X d/2026-09-18` as asset IDs and consumed subsequent input
as equipment details. It now rejects `loan`, `loans`, and `add-borrower` before
registration, along with blank or multi-token initial input. Regression tests
verify that the next help command or valid registration still works.

## Remaining limitations

- Neither console creates loans; successful returns and populated loan/history
  displays are verified with records supplied programmatically in JUnit tests.
- Borrower commands remain separate from the main prompt.
- Only borrowers persist across restarts; equipment, loans, and return details
  are in memory.
- Equipment registration does not reject duplicate IDs or validate detail fields.
- `LoanBook.addLoan` does not enforce one active loan per asset, and the `Loan`
  constructor does not validate IDs or date order. Return commands reject
  ambiguous multiple active loans. A future issuing handler needs these checks.
- Reservation, maintenance, and separate fault-record creation are not implemented.
- These results cover the local Windows environment, not a fresh CI run on every OS.

## Repeat the checks

With Java 25 on `PATH`, run from the repository root:

```powershell
.\gradlew.bat test checkstyleMain checkstyleTest shadowJar
python test/run-cli-smoke-tests.py
```

Use `--rerun-tasks` for a fresh Gradle baseline. On macOS/Linux, replace
`.\gradlew.bat` with `./gradlew`. The Python runner needs Python 3 and creates
disposable borrower files under `build/`.
