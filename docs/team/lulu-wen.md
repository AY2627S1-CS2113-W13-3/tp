# Lulu Wen - Project Portfolio Page

## Overview

LabLog is a command-line application for laboratory technicians who lend
electronics equipment to students. It tracks equipment, borrowers, loans,
reservations and faults in one validated local file.

## Summary of Contributions

**Code contributed:** [tP Code Dashboard / RepoSense](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=lulu-wen&sort=groupTitle&sortWithin=title&timeframe=commit&mergegroup=&groupSelect=groupByRepos&breakdown=true&checkedFileTypes=docs~functional-code~test-code~other)

**Enhancements implemented:**

* **Loan records and lending** ([#17](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/17),
  [#19](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/19),
  [#25](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/25)): implemented `Loan`,
  `LoanBook` and console integration. Availability is derived from active loans,
  avoiding a separate status field that could become inconsistent.
* **Batch operations and loan lifecycle**:
  [batch lending](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/118) and
  [returns](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/106) validate every
  selection before mutation, preventing partial batches.
  [Overdue listings](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/111),
  [renewals](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/119) and
  [loss reports](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/120) retain
  deadlines, renewal history and borrower responsibility through state changes.
* **Audit trail and corrections** ([#121](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/121),
  [#122](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/122)): added stable loan
  IDs and immutable events so mistakes can be corrected without erasing evidence.
  Reopening a return checks later loans and shared booking constraints; snapshot
  validation keeps current state consistent with the audit log.
* **Persistence and ID validation fixes** ([#148](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/148)):
  prevented incomplete modern snapshots from falling back to legacy loading and
  losing records on the next save. Rejected reserved asset-ID separators across
  registration, copy, load and import. Added five Java regression tests and two
  packaged CLI checks for these cases.
* **Console and tooling**: added the ASCII
  [startup banner](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/140), enabled
  [Java assertions](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/129), and
  integrated [Mermaid rendering](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/133).

**Contributions to the UG:**

* Documented [lending](../UserGuide.md#issuing-one-or-more-loans-loan),
  [batch returns](../UserGuide.md#recording-a-return-return),
  [renewals](../UserGuide.md#renewing-an-active-loan-renew),
  [loss reports](../UserGuide.md#reporting-loaned-equipment-lost-lost), and
  [audit corrections](../UserGuide.md#correcting-a-mistaken-loan-or-return).
* Aligned Quick Start, examples, output and save-failure explanations with v2.0
  ([#139](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/139)). Updated asset-ID
  and startup-validation guidance in
  [#149](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/149).

**Contributions to the DG:**

* Documented [loan implementation](../DeveloperGuide.md#loans), including
  [renewal](../DeveloperGuide.md#renewing-active-loans),
  [audit](../DeveloperGuide.md#borrowing-and-return-audit-trail) and
  [correction](../DeveloperGuide.md#correcting-erroneous-loans-and-returns)
  sequence diagrams.
* Restructured Design/Implementation after peer review; added command-execution,
  Model and Storage overviews, an audit object diagram and manual tests
  ([#137](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/137)). Added 12
  [manual workflows](../DeveloperGuide.md#manual-test-cases), an overdue sample
  and clearer responsibility descriptions in #149.

**Contributions to team-based tasks:**

* Coordinated [v1.0](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/26) and
  [v2.0 release integration](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/146),
  and [branch synchronization](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/56).
* Opened [11 planning issues](https://github.com/AY2627S1-CS2113-W13-3/tp/issues?q=is%3Aissue%20author%3Alulu-wen),
  drafted members' PPPs and linked them from About Us (#139), and updated
  [CLI test expectations and verification records](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/145).

**Review/mentoring contributions:**

* Submitted 11 teammate PR reviews. Examples discuss
  [registration cancellation](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/45#pullrequestreview-5519414376),
  [help output responsibilities](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/48#pullrequestreview-5519415770),
  [input validation](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/53#pullrequestreview-5519419089)
  and [save-failure handling](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/55#pullrequestreview-5519420496).

**Contributions beyond the project team:**

* Reviewed another team's
  [Developer Guide](https://github.com/NUS-CS2113-AY2627-S1/tp/pull/5#pullrequestreview-5678163234),
  commenting on [object creation lifelines](https://github.com/NUS-CS2113-AY2627-S1/tp/pull/5#discussion_r4397180709),
  [incomplete activity flows](https://github.com/NUS-CS2113-AY2627-S1/tp/pull/5#discussion_r4397185207)
  and [persistence-format documentation](https://github.com/NUS-CS2113-AY2627-S1/tp/pull/5#discussion_r4397230669).
