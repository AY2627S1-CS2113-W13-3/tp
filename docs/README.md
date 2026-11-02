# LabLog

Current application version: **LabLog v2.1** (Java 25).

LabLog is a local command-line application for an individual laboratory technician
who manages loanable electronics equipment for NUS student borrowers, can type
fast, and prefers typed commands. One-line registration, batch loans and returns,
and searchable equipment and loan records help the technician record lending
and find outstanding items without repeatedly filling in forms.

Each technician maintains their own local records. LabLog is a single-user
application: staff must not take turns using the same installation or access
another technician's data file during regular work, even at different times.
Borrowers are records managed by the technician, not users who log in to LabLog.
Export/import supports backups and the same technician moving their own records
to another computer; it does not provide staff handovers, shared inventories,
synchronisation, or merging of different technicians' records.

The main console supports equipment and borrower
registration, loans, renewals, loss reports, single-unit and batch returns,
active-loan listings, borrowing/return audit queries, and equipment history.
Equipment copy/edit/move/retire, reservations and interval availability, waiting requests,
fault reports and repairs, snapshot import/export, and command recall are also
available. All domain records persist together in the main LabLog data file;
command recall is session-only. A standalone borrower console is also available.

Useful links:

* [User Guide](UserGuide.md)
* [Developer Guide](DeveloperGuide.md)
* [Manual testing instructions](DeveloperGuide.md#manual-test-cases)
* [Downloads and releases](https://github.com/AY2627S1-CS2113-W13-3/tp/releases)
* [About Us](AboutUs.md)
