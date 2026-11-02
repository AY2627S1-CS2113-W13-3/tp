# Contribution evidence audit

Audited on 2 November 2026 against `develop` at
`daff5f9acd73693303d5fc22e827fcacc4053ccc` (PR #159), including #156 and #157.
`master` was `5c6c12d8656715a02b0240a9ba4ee005e2a21150`.
This is an evidence index, not a declaration that every course requirement has
been met. Commit authorship is recorded evidence, not independent proof of
personal work. Each member must confirm their own PPP and any disputed ownership.

## Personal documentation and automated-test evidence

Commit links identify changes, rather than assigning ownership from the latest
blame alone: subsequent restructuring and notation fixes can replace earlier
authors' lines without transferring credit for the original work.

| Member | UG and DG evidence | Personally added/modified UML types | Automated-test evidence |
| --- | --- | --- | --- |
| Doyeon (`doyk0215`) | Equipment search in [0f3213f](https://github.com/AY2627S1-CS2113-W13-3/tp/commit/0f3213f); registration, equipment maintenance and console output in [4fb2b0b](https://github.com/AY2627S1-CS2113-W13-3/tp/commit/4fb2b0b) | Class and sequence diagrams in [59bfdd6](https://github.com/AY2627S1-CS2113-W13-3/tp/commit/59bfdd6); also added the loan state machine there. This is significant redrawing, not merely formatting. | `EquipmentSearchCommandsTest` in 0f3213f; `RetireEquipmentTest` in 77ab969 |
| Lulu (`lulu-wen`) | Loan feature guides in [6313582](https://github.com/AY2627S1-CS2113-W13-3/tp/commit/6313582), UG revision f38b87d and DG restructuring 4e31d19 | Command-execution sequence and model class diagrams in [4e31d19](https://github.com/AY2627S1-CS2113-W13-3/tp/commit/4e31d19); additional loan sequences in feature commits. The flowchart-rendered audit object view is not needed to establish two types. | `LoanCorrectionCommandsTest` and persistence regression tests in 6313582 |
| Kwan Tze (`YamYamui`) | Reservation, conflict, availability and waiting-request UG/DG sections in [f9c9a35](https://github.com/AY2627S1-CS2113-W13-3/tp/commit/f9c9a35) through [13fe8c2](https://github.com/AY2627S1-CS2113-W13-3/tp/commit/13fe8c2) | **Gap:** no personal addition/significant modification of two UML types was found in the inspected DG history. Describing the shared booking policy in prose does not establish diagram authorship. Do not claim teammates' diagrams. | `ReservationTest` in f9c9a35; `WaitingRequestTest` in 13fe8c2; `IntegratedFeaturesTest` in e55b804 |
| Yuncong (`yuncongshen`, Git name `Shen Yuncong`) | Return/history guide expansion [5ca6336](https://github.com/AY2627S1-CS2113-W13-3/tp/commit/5ca6336); fault, repair and overview guides in feature commits 9ed4b22, 6521ee8 and 0d7b87c | Sequence diagrams in 5ca6336. Class-diagram corrections in [616e44c](https://github.com/AY2627S1-CS2113-W13-3/tp/commit/616e44c) change relationships, multiplicities, field labels and nested/static notation across five diagrams. **Confirm significance personally:** these are modifications of team diagrams, not original authorship of all five. | `FaultReportingTest` in 9ed4b22; `OverviewCommandsTest` in 0d7b87c |
| Zeph (`zephtzk`) | Borrower documentation [e707178](https://github.com/AY2627S1-CS2113-W13-3/tp/commit/e707178); export/import/recall guide sections in their feature commits; broader UG/DG updates c21ff85 and cbd57e1 | Borrower class and registration/listing sequence diagrams added in e707178 ([#123](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/123)). Later notation corrections by other members are not claimed as Zeph's work. | `BorrowerEditTest` in 2f57738; `SnapshotValidationTest` in 25b2516; UI regression tests in e44ae1f, bdf268f and 355491b |

## Team tasks and reviews

GitHub's PR/review, milestone and release APIs were checked on the audit date.
Zeph created milestones v1.0/v2.0/v2.1 and 44 issues at that time. His merges and
submitted approvals were confirmed for #59, #111-117, #127, #128, #151 and #153;
#128 has two submitted approvals. Approval records show a review occurred, not
that it contained substantive written feedback. PR #154 contains Zeph's release
preparation. The releases API returned v1.0 and v2.0 only: this audit does not
substantiate the local draft's claim that v2.1 was published.

Examples of other members' team work are visible in Git history: Doyeon's build
integration 087e6dd, Lulu's guide restructuring and test workflows 4e31d19/d08cdcc,
Kwan Tze's integrated feature tests e55b804 and Yuncong's diagram consistency
616e44c. Existing PPP review links distinguish comments from formal approvals.
The full numeric review/comment totals in teammates' PPPs were not independently
recounted in this audit; members should confirm them before personal submission.

## Attribution and RepoSense

Recorded author identities include `doyk0215` (two emails), `lulu-wen` and
`文芃儒` (same email), `YamYamui` / `Lee Kwan Tze`, `Shen Yuncong` /
`yuncongshen`, and `zephtzk`. These aliases must be compared with the course
dashboard's author configuration before claiming the published green lines are
complete. Do not add blanket `@@author` tags or a personal-only RepoSense author
list: shared files contain real contributions from multiple people.

The [October local audit](../RepoSense.md) is retained as historical evidence;
its line counts are not totals for this baseline. No code attribution was changed.

**Still required externally:** each member should open their linked course
dashboard code panel, verify the analyzed branch/revision and inspect highlighted
functional and test lines. This audit has not verified the live rendered dashboard
or its alias mapping. Missing personal UML evidence requires the member's own
work or a genuine historical change reference; it cannot be resolved by assigning
someone else's diagram to them.

## PPP PDFs

Run `python docs/team/export_ppp.py` with `reportlab` and `pypdf` installed to
export the five individual Markdown PPPs to `output/pdf/`. The exporter resolves
relative guide links to the team's hosted documentation and rejects output over
the two-page hard limit. Only Overview and Summary of Contributions are included;
there are no optional guide extracts. `contribution-evidence.md` is an audit, not
an individual's PPP, and is excluded. PDFs are generated artifacts, not a course
submission; confirm personal claims and filename requirements before uploading.
