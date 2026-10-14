# Current feature verification

Verified locally on 15 October 2026 from upstream `develop` revision
`53dc4c00a414cb3e405ca131c12dd3741333d132`, with the uncommitted changes on
`bugfix/integrated-develop-audit`. Runtime: Temurin Java 25.0.4.

## Results

| Check | Result |
| --- | --- |
| `./gradlew check` | Passed; 83 tests, Checkstyle main and test passed |
| `./gradlew shadowJar` | Passed; executable JAR built |
| `python3 test/run-cli-smoke-tests.py` | Passed; 6 packaged CLI checks |

The JUnit suite covers equipment registration and validation, loan and return
flows, borrower sharing and persistence, history, help, and recovery from
invalid console input. The packaged smoke checks cover both consoles, command
help, registration, loan and return persistence across restarts, and error
recovery.

## Changes prompted by the audit

The merged `LabLog` assigned its final history handler twice, preventing
compilation. Its main loop also printed a long prompt and did not route explicit
equipment registration to `EquipmentAddCommands`; that made valid registration
forms fail and broke the integrated history and loan flows. The loop now uses
the short prompt, handles both supported `add` forms, reports cancellation and
validation errors, and saves before showing success. Persistent main sessions
now load borrowers from `borrowers.properties` beside the selected LabLog data
file, matching the documented shared workflow.

The audit also updated the CLI smoke inputs and corrected stale command,
persistence, integration, and setup guidance across the Markdown documents.
The placeholder John Doe portfolio page and its fake links were removed.

## Remaining limitations

- Reservation, fault, and maintenance history notes are session-only.
- The standalone borrower console starts with an empty loan book and cannot
  issue loans; use the main console for the shared workflow.
- Concurrent writers to the same data files are unsupported. Direct callers of
  `LoanBook.addLoan` can bypass the validation in `LoanCommands`.
- These checks were run locally; they do not replace a fresh GitHub Actions run.

## Repeat the checks

With Java 25 on `PATH`, run from the repository root:

```sh
./gradlew check shadowJar
python3 test/run-cli-smoke-tests.py
```

On Windows, use `gradlew.bat` in place of `./gradlew`. The smoke runner requires
Python 3 and creates disposable data under `build/`.
