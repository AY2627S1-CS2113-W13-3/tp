# Zeph Tang Zhenkai - Project Portfolio Page

## Overview

LabLog is a command-line application for laboratory technicians who lend electronics equipment to students. It tracks equipment, borrowers, loans, reservations and faults, and keeps every record in one validated local file.

## Summary of Contributions

**Code contributed:** [RepoSense dashboard](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=zephtzk&sort=groupTitle&sortWithin=title&timeframe=commit&mergegroup=&groupSelect=groupByRepos&breakdown=true&checkedFileTypes=docs~functional-code~test-code~other)

### Main contribution: v2.1 UI overhaul

Redesigned the CLI to make commands easier to enter and results easier to read in both the main and borrower consoles ([PR #156](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/156)).

* **Input and navigation (historical):** originally added a boxed command prompt and used JLine for command editing. The dependency-free replacement uses labelled line prompts and Enter-based pagination. Added a paged results view with arrow-key scrolling and Enter to close, so long results can be read without losing their place. Kwan Tze later extended pager navigation in [PR #159](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/159).
* **Clearer output:** reorganised help into sections and used colour labels to distinguish headings, statuses and feedback. Kept the result title and current section visible while scrolling.
* **Terminal layout:** added word wrapping and made wide tables switch to numbered, labelled records when they cannot fit. All columns and values remain available. The original implementation adjusted on resize; the dependency-free replacement uses dimensions configured at startup.
* **Shared behaviour:** applied the presentation to both consoles, supported `NO_COLOR`, and kept plain input/output for redirected or unsupported sessions. Existing command syntax and saved data remain compatible.

### Other enhancements implemented

* **Borrower registration and loan listing** ([PR #18](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/18)): added `add-borrower` and `loans`, with checks on borrower details. Borrowers are saved before being added to the current records, so a failed save does not leave unsaved changes in memory.
* **Borrower contact editing** ([PR #95](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/95)): added `edit-borrower` to update selected contact details. The borrower ID stays the same, keeping existing loans linked. Invalid changes and failed saves leave the original details unchanged.
* **Export and import** ([PR #96](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/96), [PR #97](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/97)): added `export FILE` and `import FILE --replace`, and brought the main console's records into one data file. Import checks and saves the complete replacement before switching to the new records.
* **Invalid-import protection** ([PR #98](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/98)): added checks for duplicate keys, unknown fields, oversized files and invalid UTF-8. Rejected imports leave the current records and saved file unchanged.
* **Command recall** ([PR #99](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/99)): added `commands` and `recall INDEX` to both consoles. Users can retrieve the last 20 submitted commands for copying or editing. Recall does not run the command again.

### Contributions to the UG

* Wrote the sections on editing borrowers, export, import, invalid-import
  protection and command recall.
* Wrote the Current limitations section, updated the existing FAQ, and completed
  the Command Summary ([#124](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/124),
  [#132](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/132)).

### Contributions to the DG

* Wrote the borrower registration and loan listing section, with its class and
  sequence diagrams and design alternatives ([#123](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/123)).
* Wrote the sections on contact editing, the export schema, import, import
  validation and command recall.
* Expanded the user stories to the 33 documented v2.0 stories ([#132](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/132)).

### Testing contributions

* Added tests for borrower editing, export/import, invalid-file handling and command recall. Checked successful operations as well as failed saves and rejected inputs ([borrower tests](https://github.com/AY2627S1-CS2113-W13-3/tp/blob/develop/src/test/java/lablog/BorrowerEditTest.java), [import tests](https://github.com/AY2627S1-CS2113-W13-3/tp/blob/develop/src/test/java/lablog/SnapshotValidationTest.java), [recall tests](https://github.com/AY2627S1-CS2113-W13-3/tp/blob/develop/src/test/java/lablog/CommandHistoryTest.java)).
* Added UI tests for scrolling, resizing, wrapping, table layouts and fixed section headings. Recorded the PR #156 UI refresh checks on Java 25: 342 JUnit tests, Checkstyle, packaged smoke tests and presentation checks with and without colour ([verification report](https://github.com/AY2627S1-CS2113-W13-3/tp/blob/1d5b7af/test/verification-report.md)). The JUnit total includes the team's existing tests.

### Contributions to team-based tasks

* **Issue tracking:** created the [v1.0](https://github.com/AY2627S1-CS2113-W13-3/tp/milestone/1), [v2.0](https://github.com/AY2627S1-CS2113-W13-3/tp/milestone/2) and [v2.1](https://github.com/AY2627S1-CS2113-W13-3/tp/milestone/3) milestones, and opened [44 issues](https://github.com/AY2627S1-CS2113-W13-3/tp/issues?q=is%3Aissue%20author%3Azephtzk) to track features, user stories and UI work.
* **Integration:** merged teammates' changes for overdue loans, reservations, availability, waiting requests, search and status filtering into `develop` ([PRs #111-117](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/111), [#127](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/127), [#128](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/128)). Also merged the console integration fix, terminal safety and retirement changes, and resolved the retirement conflict while keeping both validation rules ([#59](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/59), [#151](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/151), [#153](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/153)).
* **Library and build setup (historical):** integrated JLine and its native terminal support into Gradle. Configured native access for application runs and the packaged JAR, and shared the terminal input and output handling across both consoles ([PR #156](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/156)).
* **Release management:** prepared the v2.1 release changes, aligned the startup banner, build version and JAR manifest, and recorded the packaged application checks ([PR #154](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/154)).

### Review contributions

* Reviewed PRs: [#59](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/59), [#111](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/111), [#112](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/112), [#113](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/113), [#114](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/114), [#115](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/115), [#116](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/116), [#117](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/117), [#127](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/127), [#128](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/128), [#151](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/151), [#153](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/153).
