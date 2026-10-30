package lablog;

import java.time.LocalDate;

/** Immutable renewal event owned by one loan, retaining both sides of the due-date change. */
public record LoanRenewal(LocalDate date, LocalDate previousDueDate, LocalDate newDueDate) {
    /** Rejects incomplete events, shortened periods and a new deadline already past on the renewal date. */
    public LoanRenewal {
        if (date == null || previousDueDate == null || newDueDate == null
                || !newDueDate.isAfter(previousDueDate) || newDueDate.isBefore(date)) {
            throw new IllegalArgumentException("A renewal must extend the due date to the renewal date or later.");
        }
    }
}
