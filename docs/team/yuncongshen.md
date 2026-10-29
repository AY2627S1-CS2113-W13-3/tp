# Yuncong Shen - Project Portfolio Page

## Overview

LabLog is a command-line application for laboratory technicians who lend
electronics equipment to students. It tracks equipment, borrowers, loans,
reservations and faults, and keeps every record in one validated local file.

### Summary of Contributions

**Code contributed:** [RepoSense link](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=yuncongshen&sort=groupTitle&sortWithin=title&timeframe=commit&mergegroup=&groupSelect=groupByRepos&breakdown=true&checkedFileTypes=docs~functional-code~test-code~other)

**Enhancements implemented:**

* **Equipment history** ([#20](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/20)):
  `history ASSET_ID` shows one unit's loans and other records in date order. Other
  features contribute entries through a shared history format, so the command does
  not depend on their models.
* **Returns** ([#21](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/21)):
  `return ASSET_ID [faults/DESCRIPTION]` records the return date from an injectable
  clock, which makes date boundaries testable.
* **Fault reports and severity** ([#107](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/107),
  [#108](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/108)): turned faults from
  session notes into persistent records with stable IDs, and added optional
  severity. An unresolved severe fault blocks new loans.
* **Repairs** ([#109](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/109)):
  `repair FAULT_ID` resolves one fault and keeps the original report plus a dated
  repair entry in history.
* **Unavailability explanations** ([#125](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/125)):
  `why-unavailable` lists every blocker (loss, severe fault, reservation or loan)
  by reusing the shared booking policy, so explanations always match lending.
* **Daily overview** ([#126](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/126)):
  `overview` shows today's reservations, overdue loans and unresolved severe
  faults, using one local date across all sections.

**Contributions to the UG:**

* Wrote the sections on fault severity, `repair`, `overview` and `why-unavailable`.
* Contributed to the `return` and `history` sections.

**Contributions to the DG:**

* Wrote the equipment returns and equipment history sections, with walkthroughs,
  design trade-offs and sequence diagrams ([#60](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/60)).
* Converted the remaining diagrams to inline Mermaid and documented the shared
  diagram convention ([#131](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/131)).
* Wrote the sections on fault reports, severity, repairs, the daily overview and
  unavailability explanations.

**Contributions to team-based tasks:**

* Standardised the diagram style across the Developer Guide
  ([#131](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/131)).

**Review/mentoring contributions:**

* Commented on PRs [#95](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/95)
  and [#122](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/122).
