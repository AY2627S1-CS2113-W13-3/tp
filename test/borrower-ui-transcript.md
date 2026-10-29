# Borrower console test results
The command path below uses <run-id> for the disposable file selected by the runner.
Output comparisons preserve the final prompt space; code blocks omit it for Markdown formatting.
## Case 1: add, duplicate rejection, and list

Command: java -cp build/classes/java/main lablog.BorrowerCli build/ui-borrowers-<run-id>.properties

Input:
```text
add-borrower A0123456X n/Jamie Tan p/91234567 e/jamie@u.nus.edu
add-borrower A0123456X n/Other p/91234567 e/other@u.nus.edu
loans A0123456X
```

Actual output:
```text
LabLog borrower console. Use add-borrower or loans. Type exit or send EOF to exit.
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

Command: java -cp build/classes/java/main lablog.BorrowerCli build/ui-borrowers-<run-id>.properties

Input:
```text
loans A0123456X
```

Actual output:
```text
LabLog borrower console. Use add-borrower or loans. Type exit or send EOF to exit.
lablog> No active loans for A0123456X - Jamie Tan.
lablog>
```
Exit code: 0. Stderr: (empty)
PASS: exact match after normalizing line endings.
