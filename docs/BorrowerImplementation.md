# Borrower commands

Borrower registration and loan lookup are available in both the main LabLog
console and the standalone `BorrowerCli`. The main console uses the same
`BorrowerBook` and `LoanBook` as its loan and return handlers. The standalone
console starts with an empty loan book and cannot issue loans.

## Data files

The main console stores borrowers and all other supported records together in
the configured LabLog snapshot, defaulting to `data/lablog.properties` (format
11). Only legacy split-file installations read the sibling `borrowers.properties`;
the next successful save migrates to the complete snapshot. The old sibling
file is retained but ignored after migration.

The standalone borrower console stores borrower records in
`data/borrowers.properties` by default. Pass a path to use another file. Use a
location under `build/` for disposable demonstrations; Gradle removes that
directory during `clean`.

## Run the standalone console

Use Java 25 from the repository root:

```powershell
.\gradlew.bat classes
java -cp build/classes/java/main lablog.BorrowerCli build/borrowers-demo.properties
```

On macOS/Linux, use `./gradlew classes` for the build step. Enter commands such
as:

```text
add-borrower A0123456X n/Jamie Tan p/91234567 e/jamietan@u.nus.edu
loans A0123456X
help add-borrower
```

The console exits on `exit` or EOF. Restart with the same file to reload
borrowers. The Gradle `run` task starts the main LabLog console instead.

## Shared application behavior

`LabLog.load(Path)` validates an `ApplicationSnapshot`, then populates shared
books and connects borrower changes to the complete snapshot saver. Legacy
split-file data is handled through `LabLogStore` and the sibling borrower file. Injected `LabLog` constructors
use an in-memory borrower book for tests. Borrower registration saves the
proposed state before publishing it in memory or displaying success; malformed
saved data stops startup rather than replacing the file.

Borrower IDs use an uppercase letter, seven digits, and an uppercase letter.
Names contain 1-100 characters, phone numbers contain eight ASCII digits, and
email addresses use `u.nus.edu`. Registration requires all three field prefixes exactly once, in any order.
`edit-borrower` accepts a non-empty subset of those fields and keeps the ID
stable. Failed validation or saving preserves the previous contacts. No ID check-digit verification is
performed.

Loan lookups list only active loans for the selected borrower, ordered by due
date and then asset ID. Lost loans remain outstanding with status LOST; returned
loans are excluded. A loan becomes overdue only after its due date. The main
console resolves equipment names from its shared inventory; the standalone
console has no equipment records and can only show loan records supplied by its
own caller.

## Verification

See the [current verification report](../test/verification-report.md) for the
latest local checks and known limitations. The borrower tests cover field
validation, prefix order, duplicate IDs, persistence, failed writes, corrupt
records, filtering, sorting, due-date boundaries, and newly returned loans.
