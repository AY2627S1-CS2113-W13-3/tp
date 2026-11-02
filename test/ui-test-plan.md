# Console UI tests

## Packaged application and borrower smoke checks

With Java 25 on `PATH`, run `.\gradlew.bat shadowJar`, then
`python test/run-cli-smoke-tests.py` (Python 3). This checks the executable JAR,
help examples, input recovery, registration, empty history, return rejection,
recall, and borrower persistence across fresh processes. It also checks help for
every supported command and a combined batch-loan, renewal, loss, return-audit,
reservation, waiting-request and fault-repair workflow through export/import
and restart, including paths containing spaces. The runner uses temporary
files under `build/` and does not rewrite the recorded borrower transcript.
It also checks that missing required snapshot counts stop startup without
rewriting the file, and that reserved equipment-ID separators are rejected
during registration/copy and loading.

For complete manual feature workflows, expected results and restart checks, see
[Developer Guide: manual test cases](../docs/DeveloperGuide.md#manual-test-cases).
The [overdue sample](../docs/testdata/overdue.properties) provides a historical
loan without changing the system clock. Use a disposable copy of that file.

## Interactive refresh checks

In a supported native terminal, launch the fat JAR with disposable data. Confirm
that Enter acknowledges the startup banner, then shows the compact welcome and
boxed prompt. Redirected input must begin executing commands immediately.

- Run help: it starts at the first line in the pager. Down/Up moves one displayed
  line, and the sticky heading follows the nearest preceding help category.
- Resize width and height while the pager is idle: wrapping and footer ranges
  update without dropping table columns or cell values. Wide/Unicode tables
  become complete numbered records; fitting ASCII tables remain aligned.
- Space/Page Down advances a page; b/Page Up goes back a page. Enter advances
  one line. Navigation stops at the first/last page, including after resizing.
- q closes the pager. Ctrl-C, Ctrl-D and EOF restore terminal settings.
- Repeat with NO_COLOR set: labels lose colour but user-supplied controls remain
  escaped. Redirected output uses no pager or interactive acknowledgement.
- Repeat help/paging in the standalone borrower console using the fat JAR and
  --enable-native-access=ALL-UNNAMED.

Automated TerminalInputTest covers JLine stream-terminal navigation, live resize
and terminal-mode cleanup; it does not establish native appearance on all OSes.
ConsolePresentationTest covers control escaping and unchanged visible widths.

The PowerShell 7 cases below remain available for exact-output borrower checks.

Use Java 25. Build with `.\gradlew.bat shadowJar` before testing. Each case runs in
a fresh process from the repository root. Cases 1 and 2 deliberately share the
same data file. Choose a new, nonexistent file under `build/` for each test run.

To run both cases automatically with PowerShell 7, use
`pwsh -File test/run-borrower-ui-tests.ps1`. The runner selects a unique data file,
stops on the first failure, and writes `test/borrower-ui-transcript.md`.
The final prompt's trailing space is checked exactly but omitted from the
Markdown code blocks below and in the transcript.

## Case 1: add, recover from a duplicate, and list

Command: `java --enable-native-access=ALL-UNNAMED -cp build/libs/lablog.jar lablog.BorrowerCli build/ui-borrowers.properties`

Input (then EOF):

```text
add-borrower A0123456X n/Jamie Tan p/91234567 e/jamie@u.nus.edu
add-borrower A0123456X n/Other p/91234567 e/other@u.nus.edu
loans A0123456X
```

Expected stdout (last prompt has a trailing space and no newline):

```text
LabLog borrower console. Use add-borrower or loans. Type exit or send EOF to exit.
lablog> [OK] Added borrower A0123456X: Jamie Tan.
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
LabLog borrower console. Use add-borrower or loans. Type exit or send EOF to exit.
lablog> No active loans for A0123456X - Jamie Tan.
lablog>
```

Both cases must exit with code 0 and empty stderr. Normalize only line endings.
JUnit additionally verifies exact mixed active-loan output with a fixed local
clock and the real `Loan` and `LoanBook`, as the standalone console has no loan
creation command yet.
