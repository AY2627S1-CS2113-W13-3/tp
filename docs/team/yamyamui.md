# Kwan Tze - Project Portfolio Page

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
  shared by reservations, lending and renewal.
* **Waiting requests** ([#117](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/117)):
  `wait-request` and `waiting-requests` record category-level demand only when no
  suitable unit is free, without allocating equipment automatically.
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
* **Assertions and logging** ([#42](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/42)).

**Contributions to the UG:**

* Wrote the Quick Start and the sections on registering equipment, history notes,
  `add-borrower`, `loans`, `help`, reservations, availability and waiting requests.

**Contributions to the DG:**

* Wrote the sections on entry points and shared records, help, reservations, the
  shared conflict policy, availability queries and waiting requests.
* Wrote the assertions and logging and automated checks sections, and drafted the
  acknowledgements, non-functional requirements and glossary.

**Contributions to team-based tasks:**

* Prepared the v1.1 update ([#94](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/94)).
* Integrated overlapping feature PRs in one validated merge ([#110](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/110)).

**Review/mentoring contributions:**

* Reviewed PRs [#97](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/97) to
  [#100](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/100) and
  [#106](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/106) to
  [#109](https://github.com/AY2627S1-CS2113-W13-3/tp/pull/109)
  (8 reviews, 8 comments).
