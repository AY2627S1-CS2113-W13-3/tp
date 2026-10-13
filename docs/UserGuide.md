# User Guide

## Introduction

{Give a product intro}

## Quick Start

{Give steps to get started quickly}

1. Ensure that you have Java 25 or above installed.
1. Down the latest version of `Duke` from [here](http://link.to/duke).

## Features 

### Viewing common commands and examples: `help`

Format: `help [COMMAND]`

Enter `help` for the concise Version 1 command list, or supply one command name
to see its syntax, fields, and explained usage examples. Help is available in both consoles
and does not change records. Command names are case-sensitive; unknown names
and extra arguments produce an error and let you try again.

For example, `help loan` displays:

```text
Issue a loan for an available equipment item.
Format: loan ASSET_ID b/BORROWER_ID d/DUE_DATE
Example: loan OSC-001 b/A0123456X d/2026-09-18
  Lend OSC-001 to borrower A0123456X with a due date of 18 September 2026.
  Substitute the actual asset ID, borrower ID, and due date when issuing a loan.
Fields: ASSET_ID identifies the equipment; b/ identifies the borrower; d/ gives the due date in YYYY-MM-DD format.
Loan creation is not yet connected to either console.
```

Detailed help covers `help`, `loan`, `return`, `history`, `add-borrower`, `loans`,
and `exit`. The list labels commands belonging to the main or borrower console;
viewing help does not make a command available in the other console.

Use `help return` to compare returning an item with and without new faults:

```text
Example: return OSC-001 faults/cracked screen
  Return the unit and record a cracked screen on its loan.
Example: return OSC-001
  Return the unit with no new faults reported.
```

These are alternatives: the unit must have an active loan, and a loan cannot be
returned twice. Use `help add-borrower` to see registrations with different field
orders, and `help loans` for lookups using those borrowers' IDs. Use `help history`
for a history lookup and its registration prerequisite, or `help help` to see how
to switch between the concise list and command-specific guidance.

Lines beginning with `Example:` contain commands you can type; the indented lines
explain their outcomes. Viewing an example never executes it. Replace sample IDs,
contact details, and dates with your own when entering a command.

### Recording a return: `return`

At the initial prompt, enter `return ASSET_ID [faults/DESCRIPTION]`:

```text
return OSC-001
return OSC-002 faults/cracked screen
```

The return date is today's date according to the computer's clock and local time
zone. Omit `faults/` if the unit is returned in its original condition with no new
damage to report. This records "No faults reported on return"; it does not clear
existing equipment faults. If supplied, `faults/` must appear once with a non-empty,
single-line description. Asset IDs are case-sensitive.

The equipment must exist and have exactly one active loan. Invalid commands,
repeated returns, multiple active loans, and returns before the loan date are
rejected without changing records. Returned loans remain in equipment history,
including their reported faults, and are excluded from active-loan listings.

Fault details are stored on the loan only; this command does not create a separate
fault record or certify that the equipment is available for lending. Loans and
return details are currently in memory and are not saved across restarts. The
default app still needs a loan-creation feature connected to its shared loan book
before returns can be exercised in a normal fresh session.

### Viewing equipment history: `history`

At LabLog's initial asset-ID prompt, enter `history ASSET_ID`, for example
`history OSC-001`. The identifier is case-sensitive and must be a single token.
Register the equipment first using the existing equipment prompts.

The display combines loan, reservation, fault, and maintenance records for that
unit, oldest first. Loans include both active and returned records, with borrower
ID, due date, and return date where available. Each row represents one record,
not every change made to that record. Reservation, fault, and maintenance details
are supplied by their respective features.

Dates mean loan date, reservation start date, fault report date, or maintenance
date. Future reservations are included. Same-day entries are ordered by type
(loan, reservation, fault, maintenance), then description.

Unknown equipment produces an error; registered equipment with no supplied records
produces `No history found`. Enter `exit` at the initial prompt to close LabLog.

**Current integration limit:** the default application starts with an empty loan
book and no reservation, fault, or maintenance source. Those record-creation
features are not yet connected. History can display all four types when shared
records are supplied, as exercised by the integration tests; an ordinary fresh
session can currently demonstrate registration and empty-history lookup only.

{Give detailed description of each feature}

### Adding a todo: `todo`
Adds a new item to the list of todo items.

Format: `todo n/TODO_NAME d/DEADLINE`

* The `DEADLINE` can be in a natural language format.
* The `TODO_NAME` cannot contain punctuation.  

Example of usage: 

`todo n/Write the rest of the User Guide d/next week`

`todo n/Refactor the User Guide to remove passive voice d/13/04/2020`

## FAQ

**Q**: How do I transfer my data to another computer? 

**A**: {your answer here}

## Command Summary

{Give a 'cheat sheet' of commands here}

* Add todo `todo n/TODO_NAME d/DEADLINE`
