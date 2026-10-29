# Lulu Wen - Project Portfolio Page

## Overview

LabLog is a command-line application for laboratory technicians who lend
electronics equipment to students. It tracks equipment, borrowers, loans,
reservations and faults, and keeps every record in one validated local file.

### Summary of Contributions

**Code contributed:** [RepoSense link](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=lulu-wen&sort=groupTitle&sortWithin=title&timeframe=commit&mergegroup=&groupSelect=groupByRepos&breakdown=true&checkedFileTypes=docs~functional-code~test-code~other)

**Enhancements implemented:**

* **Loan records and lending** ([#17](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/17),
  [#19](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/19),
  [#25](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/25)): added `Loan`,
  `LoanBook` and the `loan` command. Every check runs before a loan is stored, and
  availability is derived from active loans instead of a status field.
* **Batch lending and returns** ([#118](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/118),
  [#106](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/106)): `loan` and `return`
  accept several units. The whole selection is validated first, so one invalid
  unit leaves every loan unchanged.
* **Overdue listing** ([#111](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/111)):
  `active-loans overdue` lists only loans due before today, sorted by due date.
* **Renewals** ([#119](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/119)):
  `renew` extends an active loan and keeps each deadline change as an immutable
  `LoanRenewal`, checked against the shared booking policy.
* **Lost equipment** ([#120](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/120)):
  `lost` keeps the loan outstanding with status `LOST` instead of treating the
  loss as a return, so the borrower stays responsible.
* **Loan audit trail and corrections** ([#121](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/121),
  [#122](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/122)): stable loan IDs,
  immutable `LoanAuditEvent` records, `loan-audit`, and `reverse-loan` /
  `reverse-return`, which add correction events instead of deleting evidence.
* **Build and documentation tooling**: enabled Java assertions for `run` and `test`
  ([#129](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/129)), and made Mermaid
  diagrams render on the GitHub Pages site ([#133](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/133)).

**Contributions to the UG:**

* Wrote the sections on `loan`, `active-loans`, `renew`, `return`, `lost`,
  `loan-audit`, loan corrections and `history`.

**Contributions to the DG:**

* Wrote the sections on issuing loans, active and overdue listings, renewals, the
  audit trail, corrections and lost equipment, with their sequence diagrams.
* In review ([#137](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/137)):
  regrouped the guide into Design and Implementation; added the command-execution,
  Model and Storage sections and an object diagram; aligned the diagrams with the
  course UML notation; added manual test cases.

**Contributions to team-based tasks:**

* Prepared and published the v1.0 release ([#26](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/26)).
* Synced `master` into `develop` ([#56](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/56)).
* Opened 11 issues for the loan features.

**Review/mentoring contributions:**

* Reviewed PRs [#44](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/44) to
  [#46](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/46),
  [#48](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/48) to
  [#55](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/55),
  [#100](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/100) and
  [#105](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/105)
  (11 reviews, 15 inline comments, 4 other comments).
