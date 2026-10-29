# Zeph - Project Portfolio Page

## Overview

LabLog is a command-line application for laboratory technicians who lend
electronics equipment to students. It tracks equipment, borrowers, loans,
reservations and faults, and keeps every record in one validated local file.

### Summary of Contributions

**Code contributed:** [RepoSense link](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=zephtzk&sort=groupTitle&sortWithin=title&timeframe=commit&mergegroup=&groupSelect=groupByRepos&breakdown=true&checkedFileTypes=docs~functional-code~test-code~other)

**Enhancements implemented:**

* **Borrower registration and loan listing** ([#18](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/18)):
  `add-borrower` and `loans`, with validated, immutable `Borrower` records. Changes
  are saved before they are published, so a failed save never leaves a borrower
  only in memory.
* **Borrower contact editing** ([#95](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/95)):
  `edit-borrower` updates any subset of fields. Borrower IDs and loan references
  stay stable, and a failure keeps the original contacts.
* **Export and import** ([#96](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/96),
  [#97](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/97)): `export FILE` and
  `import FILE --replace`. Import validates a detached snapshot, saves it
  atomically, then replaces the shared books so every handler sees the new data.
  This also moved the main console to one complete data file.
* **Invalid-import protection** ([#98](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/98)):
  rejects duplicate keys, unknown fields, oversized files and invalid UTF-8
  before any record is replaced.
* **Command recall** ([#99](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/99)):
  `commands` and `recall INDEX` in both consoles. A recalled line is displayed for
  editing but never run automatically.

**Contributions to the UG:**

* Wrote the sections on editing borrowers, export, import, invalid-import
  protection and command recall.
* Wrote the Current limitations and FAQ sections, and completed the Command
  Summary ([#124](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/124),
  [#132](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/132)).

**Contributions to the DG:**

* Wrote the borrower registration and loan listing section, with its class and
  sequence diagrams and design alternatives ([#123](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/123)).
* Wrote the sections on contact editing, the export schema, import, import
  validation and command recall.
* Expanded the user stories to all 33 approved v2.0 stories ([#132](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/132)).

**Contributions to team-based tasks:**

* Created the v1.0, v2.0 and v2.1 milestones.
* Opened 43 issues for planned features and user stories.
* Aligned the README and guides with the implemented features
  ([#124](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/124),
  [#132](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/132)).

**Review/mentoring contributions:**

* Reviewed PRs [#59](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/59),
  [#111](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/111),
  [#112](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/112) to
  [#117](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/117),
  [#127](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/127) and
  [#128](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/128) (11 reviews).
