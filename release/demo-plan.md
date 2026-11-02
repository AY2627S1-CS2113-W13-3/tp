# LabLog demo runbook

Target **17 minutes**, leaving one minute below the five-person 18-minute cap.
Due **5 November 2026, 14:00 Singapore**; confirm Canvas assignment locally.
Use `[CS2113-W13-3][LabLog].mp4`, preferably below 200 MB, with legible CLI text
and audio. Assign the five speaking slots during rehearsal; slots do not imply
feature authorship. No member introduction or implementation lecture is needed.

## Prepare the exact submitted executable

After final merges and release verification, copy the submitted JAR to a writable
local folder outside OneDrive. Record its SHA-256 alongside the submission
receipt. Every recording segment must use that same JAR. The baseline seed was
verified with daff5f9's JAR; repeat after final merge, rather than treating that
run as final release evidence.

```powershell
python release/prepare-demo.py 'PATH/[CS2113-W13-3][LabLog].jar' 'LOCAL/demo.properties'
java -jar 'PATH/[CS2113-W13-3][LabLog].jar' 'LOCAL/demo.properties'
```

Choose an absent local snapshot path. The generator refuses to overwrite files;
it creates 20 realistic equipment units (ten kinds, two copies each) and five
fictional borrowers. The NUS-shaped IDs and email addresses are demo data only;
do not contact them. Assets LAB-001 through LAB-020 include oscilloscopes,
generators, meters, supplies, logic analysers, soldering stations, cameras,
microscopes and spectrum analysers. Borrower IDs are A0123450X to A0123454X.
Take a local backup of the seeded snapshot for rehearsal resets.

Use today's date T for historical notes and faults, T+7 as due date, T+10 as
renewal date, and T+14/T+15 for bookings. Replace date placeholders before
recording. Capture returned event/fault/reservation IDs from output; never
assume fixed IDs. Reset to the seed between rehearsals, not between segments
of the final story. Rehearse every command with `help COMMAND` and the final UG.

## Cohesive story and five speaking slots

| Time | Slot | Story and commands to show |
| --- | --- | --- |
| 0:00-3:00 | 1 | Explain LabLog for a single lab coordinator who prefers typing: find, lend and track equipment without losing records. Show populated `overview`, `list`, pager arrows/Enter, `search oscilloscope`, `find LAB-001`. Register a new unit with one-line `add`, show guided add/cancel briefly, `copy`, `edit-equipment`, `move-equipment` and filtered/sorted list using final help syntax. |
| 3:00-6:15 | 2 | Register one new fictional borrower, `edit-borrower`, then `loan LAB-001 LAB-002 b/A0123450X d/DUE`. Show `loans A0123450X`, `active-loans`, `renew LAB-001 d/RENEW`, and `why-unavailable LAB-001`. Return LAB-002, show its audit, demonstrate `reverse-return EVENT_ID reason/Entered in error`, then return it correctly. Loan and immediately `reverse-loan` another available unit; show retained audit evidence. |
| 6:15-9:30 | 3 | Book LAB-003 with `reserve ... b/A0123451X from/START to/END`, show `reservations`, `available` and conflict rejection, then edit/cancel the captured reservation ID. For waiting demand, book both LAB-019 and LAB-020 over the same interval; `wait-request b/A0123452X category/Spectrum analyser from/START to/END`, then `waiting-requests`. Explain that demand does not allocate automatically. |
| 9:30-12:45 | 4 | Record `fault LAB-005 d/T s/severe desc/Display flickers`, show blocking state and `overview`, then `repair FAULT_ID d/T desc/Replaced display cable`. Add `maintenance` and `reservation` history notes (the latter is a note, not a booking). `lost LAB-001` retains its outstanding loan; show `history` and `why-unavailable`. Retire a different idle unit with `retire LAB-018 reason/End of service`; show retired list and preserved history. |
| 12:45-17:00 | 5 | Show a batch loan/return of available LAB-007/LAB-008, `commands` and `recall INDEX` without automatic reexecution. Query help for recovery from one invalid command. `export LOCAL/backup.properties`; restart with the same snapshot and show retained records, then import that export into a separate empty snapshot with `import FILE --replace`. Demonstrate `active-loans overdue` with the DG's disposable overdue sample in a separate process; explain sample dates. End on overview with the operational benefits. |

Allocate 30-45 seconds within each slot to command transitions and output.
Use the final UG command summary to tick every user-testable command during
rehearsal, including loan-audit, history, availability and waiting demand.
The plan groups related commands so the result is a lab workflow rather than a
list of unrelated features. Advanced syntax can be shown in brief prepared
segments; show the common workflow first. BorrowerCli is an alternate entry
point with separate data: if demonstrating it, use a separate snapshot and
explain that boundary within slot 5's time budget.

## Rehearsal and upload gates

- Verify no unexpected Error output, unresolved placeholders or guessed IDs.
- Confirm bookings use future dates, due dates are current, and all dependent
  units are available when needed. Record actual IDs in a rehearsal cue sheet.
- Never reuse the live seed after a failed run; restart from its backup.
- If using short captured segments, preserve the coherent state and final JAR
  hash across segments. Do not substitute a developer build.
- Check all five speakers, sound, readable text and total duration <=18:00.
  This plan is a draft; no recording or full timed rehearsal is claimed here.
- Watch the exported MP4 end to end, verify filename/size, then have a human
  upload to Canvas and check the receipt. Canvas settings/upload are unverified.
