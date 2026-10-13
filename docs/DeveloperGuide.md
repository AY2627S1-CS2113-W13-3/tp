# Developer Guide

## Acknowledgements

{list here sources of all reused/adapted ideas, code, documentation, and third-party libraries -- include links to the original source as well}

## Design & implementation

### Equipment history

`HistoryCommands.execute("history ASSET_ID")` validates the command, checks
equipment existence in the shared `Inventory`, and reads the shared `LoanBook`.
It includes returned loans and builds fresh descriptions on every request, so a
later return is visible without updating a separate history store.

Reservation, fault, and maintenance models are not yet implemented. Their owners
can supply `HistoryEntry` snapshots through a `Supplier<List<HistoryEntry>>`.
The supplier should read the current source records on every call, include their
status and relevant end dates in the description, and retain closed/cancelled
records when they belong in history. It must return a non-null list and must not
include duplicate loan entries. Entries may cover multiple assets; the handler
filters by exact asset ID and sorts a new list without changing the source order.

Use loan date, reservation start date, fault report date, and maintenance date
as the entry dates. Entries are sorted oldest first, then by enum type order and
description. This is a record summary rather than a permanent event audit trail.

Pass the shared records to
`new LabLog(inventory, loanBook, () -> currentHistoryEntries)` or construct
`HistoryCommands` directly for a future shared dispatcher. The default application
uses an empty source until the remaining features are connected. No persistence
or reservation/fault/maintenance creation commands are introduced by this feature.

`HistoryCommandsTest` covers all four types, asset filtering, returned loans,
fresh source updates, date ordering, invalid input, empty/unknown equipment, and
the actual application prompt with injected shared data.

{Describe the design and implementation of the product. Use UML diagrams and short code snippets where applicable.}


## Product scope
### Target user profile

{Describe the target user profile}

### Value proposition

{Describe the value proposition: what problem does it solve?}

## User Stories

|Version| As a ... | I want to ... | So that I can ...|
|--------|----------|---------------|------------------|
|v1.0|new user|see usage instructions|refer to them when I forget how to use the application|
|v2.0|user|find a to-do item by name|locate a to-do without having to go through the entire list|

## Non-Functional Requirements

{Give non-functional requirements}

## Glossary

* *glossary item* - Definition

## Instructions for manual testing

{Give instructions on how to do a manual product testing e.g., how to load sample data to be used for testing}
