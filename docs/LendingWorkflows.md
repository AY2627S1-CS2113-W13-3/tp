# Lending handovers and recovery

After `return ASSET_ID`, the equipment can be loaned again on the same day.
The earlier loan and return remain in history. Outstanding loans, severe faults,
retirement and other borrowers' reservations still block lending.

A borrower can collect reserved equipment with the existing command:
`loan ASSET_ID b/BORROWER_ID d/YYYY-MM-DD`. Today and the due date must both
fall within that borrower's confirmed reservation. The reservation remains
recorded and continues to protect the booked period if equipment is returned
early. A loan extending outside the booking is rejected; edit or cancel the
reservation explicitly when changing the arrangement. All units in a batch
must pass validation before any loan is issued.

Use `recover ASSET_ID` when lost equipment is physically recovered, including
when correcting a mistaken loss report. It closes the outstanding lost loan
on today's date, retains its loss date, borrower and renewal history, and adds
return audit evidence. Ordinary `return` still rejects lost equipment. Recovery
requires one outstanding lost loan and cannot precede its loss report. A
recovered loan cannot be reopened with `reverse-return`; its loss evidence is
retained. Existing faults, retirement and reservations continue to apply before
the unit can be lent again. Recovery and subsequent loans survive restart and
export/import.
