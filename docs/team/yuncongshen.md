# Yuncong Shen's Project Portfolio Page

### Project: LabLog

LabLog is a command-line application for laboratory technicians who lend electronics equipment to students. Written in Java, it tracks equipment, borrowers, loans, reservations and faults, and stores records in one validated local file.

Given below are my contributions to the project.

* **New Feature: Added fault reporting, severity and repair management** ([#107](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/107), [#108](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/108), [#109](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/109)).
  * **What it does:** Records persistent faults with stable IDs, descriptions and severity. Unresolved severe faults block lending. Repairs resolve individual faults while retaining the original report and dated repair history.
  * **Justification:** Helps technicians avoid lending faulty equipment and trace defects from reporting through resolution.
  * **Highlights:** Kept persistence, lending restrictions and history consistent across reporting, repair and restart. Tests cover severity validation, repair-date boundaries, repeated repair rejection, export/import and restored lending eligibility.

* **New Feature: Added equipment returns and history** ([#20](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/20), [#21](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/21)).
  * **What it does:** Records single-unit returns with optional damage descriptions and displays chronological equipment history.
  * **Highlights:** Used an injected clock to test return dates and a shared history-entry representation so other features can contribute records without coupling the history command to their models. Later team contributions extended this foundation with batch returns and audit records.

* **New Feature: Added unavailability explanations** ([#125](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/125)).
  * **What it does:** Lists every applicable loss, severe fault, reservation or loan blocker through `why-unavailable`.
  * **Highlights:** Reused the team's booking policy to keep explanations consistent with lending decisions. Tested simultaneous blockers, inclusive date boundaries, repairs, cancellations and queries after restart/import.

* **New Feature: Added a daily operational overview** ([#126](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/126)).
  * **What it does:** Combines today's reservations, overdue loans and unresolved severe faults in one read-only view.
  * **Highlights:** Uses one local date and deterministic ordering across sections. Tests cover date boundaries, cancelled reservations, returned/reversed loans, repaired faults and imported records.

* **Code contributed:** [Individual tP Code Dashboard](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=yuncongshen&sort=groupTitle&sortWithin=title&timeframe=commit&mergegroup=&groupSelect=groupByRepos&breakdown=true&checkedFileTypes=docs~functional-code~test-code~other).

* **Documentation:**
  * **User Guide:** Documented fault reporting/severity, repairs, unavailability explanations and overview, including syntax, examples and restrictions in the feature PRs above. Contributed the original return and history instructions.
  * **Developer Guide:** Wrote return/history walkthroughs, design trade-offs and sequence diagrams ([#60](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/60)), plus implementation guidance for faults, repairs, overview and unavailability explanations.
  * Converted search, return and history image diagrams to inline Mermaid ([#131](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/131)). Standardised class-diagram associations, multiplicities, nested type names and static-operation notation ([#141](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/141)).

* **Team-based tasks:**
  * Reconciled overlapping DG edits while preserving teammates' contributions ([merge resolution](https://github.com/AY2627S1-CS2113-W13-3/tp/commit/58b856531ba5bbcbe076c908f0f08f7d44fa7780)).
  * Applied #141 to the latest team guide, rendered all 16 diagrams with the site's Mermaid version and visually checked the five class diagrams.

* **Community:**
  * Reviewed correction dependencies, availability, persistence and preservation of audit evidence in [PR #122](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/122#issuecomment-6362308091). Independently ran Java 25 checks: 274 tests and both Checkstyle tasks passed.
  * Reminded the author of [PR #95](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/95#issuecomment-6313155974) to update command hints and Javadoc.
