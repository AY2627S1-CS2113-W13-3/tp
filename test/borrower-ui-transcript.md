# Borrower console test results

Verified on 2 November 2026 against master 2227f7f, Java 25.0.4, Windows.
Both cases use redirected input; this is not a physical-terminal appearance check.
Output comparisons preserve the final prompt space; code blocks omit it for Markdown formatting.
## Case 1: add, duplicate rejection, and list

Command: java -cp build/libs/lablog.jar lablog.BorrowerCli C:\Users\zepht\OneDrive\Documents\ChatGPT\CS2113 tP\tmp\final-version-verification\build\ui-borrowers-<run-id>.properties

Input:
```text
add-borrower A0123456X n/Jamie Tan p/91234567 e/jamie@u.nus.edu
add-borrower A0123456X n/Other p/91234567 e/other@u.nus.edu
loans A0123456X
```

Actual output:
```text
LabLog borrower console. Use add-borrower or loans. Type exit or send EOF to exit.
LabLog works best in a terminal at least 100 columns wide and 30 rows high.
lablog> [OK] Added borrower A0123456X: Jamie Tan.
Phone: 91234567 | Email: jamie@u.nus.edu
lablog> Error: Borrower ID A0123456X already exists. Choose a different borrower ID.
No changes saved.
lablog> No active loans for A0123456X - Jamie Tan.
lablog>
```
Exit code: 0. Stderr: (empty)
PASS: exact match after normalizing line endings.
## Case 2: reload persisted borrower

Command: java -cp build/libs/lablog.jar lablog.BorrowerCli C:\Users\zepht\OneDrive\Documents\ChatGPT\CS2113 tP\tmp\final-version-verification\build\ui-borrowers-<run-id>.properties

Input:
```text
loans A0123456X
```

Actual output:
```text
LabLog borrower console. Use add-borrower or loans. Type exit or send EOF to exit.
LabLog works best in a terminal at least 100 columns wide and 30 rows high.
lablog> No active loans for A0123456X - Jamie Tan.
lablog>
```
Exit code: 0. Stderr: (empty)
PASS: exact match after normalizing line endings.
