# Lee Kwan Tze (YamYamui) - Project Portfolio Page

## Overview

LabLog is a command-line application for laboratory technicians who lend
electronics equipment to students. It tracks equipment, borrowers, loans,
reservations and faults, and keeps every record in one validated local file.

### Summary of Contributions

**Code contributed:** [RepoSense link](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=YamYamui&sort=groupTitle&sortWithin=title&timeframe=commit&mergegroup=&groupSelect=groupByRepos&breakdown=true&checkedFileTypes=docs~functional-code~test-code~other)

**Enhancements implemented:**

* **Reservations and availability** ([#112](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/112)
  to [#116](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/116)): `reserve`,
  `reservations`, `edit-reservation`, `cancel-reservation` and `available`.
  Added `BookingAvailability`, the single inclusive-interval conflict policy now
  shared by reservations, lending and renewal to prevent double-booking.
  Edits preserve booking IDs; cancellations retain the original booking and reason.
  Changes are saved before taking effect, so failed saves leave bookings unchanged.
* **Waiting requests** ([#117](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/117)):
  `wait-request` and `waiting-requests` record category-level demand only when no
  operationally suitable unit is free for the whole period, without allocating
  equipment automatically. Duplicate demand is rejected, and requests survive
  restarts and later capacity changes.
* **Persistence** ([#55](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/55)):
  saved equipment and complete loan history in one versioned snapshot, replaced
  atomically, rejecting corrupt data instead of overwriting it.
* **Shared consoles** ([#54](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/54)):
  routed borrower commands through the main console so they share its inventory
  and loan records.
* **Help and console usability** ([#22](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/22),
  [#23](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/23),
  [#44](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/44) to
  [#48](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/48)): `help` and
  `help COMMAND` with explained examples, `exit` in the borrower console,
  cancelling equipment registration, and a consistent prompt and welcome.
* **Input validation** ([#24](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/24),
  [#49](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/49),
  [#53](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/53)): unknown words no
  longer start registration, and duplicate or incomplete units are rejected.
* **Listings** ([#50](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/50),
  [#51](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/51)): equipment list with
  live availability, and `active-loans` with overdue status.
* **Equipment history notes** ([commit](https://github.com/AY2627S1-CS2113-W13-3/tp/commit/cfbcd31cfe30dbe8389ba54b1603d127ca06cb5d)):
  added commands to record dated reservation, fault and maintenance notes after
  validating the equipment, date and description. These initial notes were
  separate from the later structured booking and fault features.
* **Assertions and logging** ([#42](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/42)):
  added internal checks and diagnostic logging to support debugging.
* **Regression tests**: covered booking date boundaries, conflicting edits,
  cancellation, failed saves, restart/export/import and older snapshot formats
  alongside the reservation and waiting-request features above.

**Contributions to the UG:**

* Wrote the initial Quick Start ([#24](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/24))
  and contributed command guidance for registering equipment, history notes,
  `add-borrower`, `loans` and `help`. These sections were subsequently revised
  by the team.
* Documented reservations, booking conflicts, editing, cancellation, availability
  and waiting requests, including validation rules and persistence behaviour
  ([#112](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/112)
  to [#117](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/117)).

**Contributions to the DG:**

* Wrote the sections on entry points and shared records, help, reservations, the
  shared conflict policy, availability queries and waiting requests. Explained
  saving proposed changes before updating live records and compatibility with
  older snapshot formats.
* Wrote the assertions and logging and automated checks sections, and drafted the
  acknowledgements, non-functional requirements and glossary.

**Contributions to team-based tasks:**

* Prepared the v1.1 update ([#94](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/94)).
* Integrated overlapping feature PRs ([#110](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/110)),
  reconciled shared persistence and filtering behaviour, and added
  `IntegratedFeaturesTest` to check interactions among imports, repairs, lending
  and sorted filters.

**Review/mentoring contributions:**

* Reviewed and approved PRs
  [#97](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/97#pullrequestreview-5617136591),
  [#98](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/98#pullrequestreview-5617137315),
  [#99](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/99#pullrequestreview-5617138298),
  [#100](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/100#pullrequestreview-5617139201),
  [#106](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/106#pullrequestreview-5617139967),
  [#107](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/107#pullrequestreview-5617140690),
  [#108](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/108#pullrequestreview-5617141403)
  and [#109](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/109#pullrequestreview-5617142285)
  (8 reviews), with integration verification handled separately in #110.
