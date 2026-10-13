# User Guide

## Introduction

{Give a product intro}

## Quick Start

{Give steps to get started quickly}

1. Ensure that you have Java 25 or above installed.
1. Down the latest version of `Duke` from [here](http://link.to/duke).

## Features 

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
