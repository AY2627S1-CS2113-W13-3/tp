package lablog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a single loan of one equipment unit to one borrower.
 *
 * <p>A loan stays active until returned or its issue is explicitly reversed.
 * The borrower is referenced by identifier rather than by
 * object so that loans can be recorded before borrower records exist.
 */
public class Loan {
    private final String id;
    private final String assetId;
    private final String borrowerId;
    private final LocalDate loanDate;
    private LocalDate dueDate;
    // Events belong to this specific loan, even when an asset is lent again on the same day.
    private final List<LoanRenewal> renewals = new ArrayList<>();
    private LocalDate returnDate;
    // Rebuilt from REVERSE_LOAN evidence on load; the original loan remains in history.
    private boolean reversed;
    // Lost equipment remains outstanding; a loss is never treated as a return.
    private LocalDate lostDate;
    // Empty means no faults were reported on return; it does not clear equipment faults.
    private String returnFaults = "";

    /**
     * Creates an active loan of one equipment unit to one borrower.
     *
     * @param assetId asset identifier of the unit being lent out
     * @param borrowerId identifier of the borrower taking the unit
     * @param loanDate date the unit was handed over
     * @param dueDate date the unit is expected back
     */
    public Loan(String assetId, String borrowerId, LocalDate loanDate, LocalDate dueDate) {
        this("L-" + UUID.randomUUID(), assetId, borrowerId, loanDate, dueDate);
    }

    /** Restores a stable identity; older files receive one when first loaded and retain it on save. */
    Loan(String id, String assetId, String borrowerId, LocalDate loanDate, LocalDate dueDate) {
        if (id == null || !id.startsWith("L-") || id.length() < 3
                || id.chars().anyMatch(character -> Character.isWhitespace(character)
                        || Character.isISOControl(character))) {
            throw new IllegalArgumentException("Loan ID must be a non-empty L- identifier without whitespace.");
        }
        this.id = id;
        this.assetId = assetId;
        this.borrowerId = borrowerId;
        this.loanDate = loanDate;
        this.dueDate = dueDate;
        this.returnDate = null;
    }

    public String getId() {
        return id;
    }

    public String getAssetId() {
        return assetId;
    }

    public String getBorrowerId() {
        return borrowerId;
    }

    public LocalDate getLoanDate() {
        return loanDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    /** Returns the deadline at issue time, before any renewals. */
    public LocalDate getOriginalDueDate() {
        return renewals.isEmpty() ? dueDate : renewals.getFirst().previousDueDate();
    }

    /** Returns immutable renewal events in the order they were accepted. */
    public List<LoanRenewal> getRenewals() {
        return List.copyOf(renewals);
    }

    /**
     * Extends an active loan and appends an event only after all date checks pass.
     * Callers must check shared availability before invoking this method.
     *
     * @param newDueDate strictly later deadline, no earlier than the renewal date
     * @param renewedOn local date on which the change is recorded
     * @throws IllegalArgumentException if inactive or any date would contradict the loan's history
     */
    public void renew(LocalDate newDueDate, LocalDate renewedOn) {
        requireNotLost();
        if (!isActive()) {
            throw new IllegalArgumentException("Only an active loan can be renewed.");
        }
        if (renewedOn == null || renewedOn.isBefore(loanDate)
                || (!renewals.isEmpty() && renewedOn.isBefore(renewals.getLast().date()))) {
            throw new IllegalArgumentException("Renewal date must not precede the loan or its previous renewal.");
        }
        LoanRenewal event = new LoanRenewal(renewedOn, dueDate, newDueDate);
        renewals.add(event);
        dueDate = newDueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public String getReturnFaults() {
        return returnFaults;
    }

    public LocalDate getLostDate() {
        return lostDate;
    }

    /** Returns whether this outstanding loan has been reported lost. */
    public boolean isLost() {
        return lostDate != null;
    }

    /**
     * Records a loss without changing the borrower, deadline, issue date or renewal history.
     *
     * @param date local date of the loss report, no earlier than the loan or latest renewal
     * @throws IllegalArgumentException if returned, already lost or the date contradicts existing events
     */
    public void markLost(LocalDate date) {
        requireNotLost();
        if (!isActive()) {
            throw new IllegalArgumentException("Only an active loan can be marked lost.");
        }
        if (date == null || date.isBefore(loanDate)
                || (!renewals.isEmpty() && date.isBefore(renewals.getLast().date()))) {
            throw new IllegalArgumentException("Loss date must not precede the loan or its latest renewal.");
        }
        lostDate = date;
    }

    /** Stops ordinary lending transitions from silently clearing a loss report. */
    private void requireNotLost() {
        if (isLost()) {
            throw new IllegalArgumentException("Equipment " + assetId
                    + " is marked lost; it cannot be returned or renewed, or marked lost again.");
        }
    }

    /**
     * Returns whether this unit is still out with the borrower.
     *
     * @return true if neither returned nor reversed, including outstanding lost equipment
     */
    public boolean isActive() {
        return returnDate == null && !reversed;
    }

    public boolean isReversed() {
        return reversed;
    }

    /** Cancels an erroneous issue only when no renewal, return or loss still depends on it. */
    void reverseIssue() {
        if (!isActive() || isLost() || !renewals.isEmpty()) {
            throw new IllegalArgumentException("Reverse the return first; renewed or lost loans cannot be reversed.");
        }
        reversed = true;
    }

    /** Restores an outstanding loan; its original return evidence remains in the owning book. */
    void reverseReturn() {
        if (returnDate == null) {
            throw new IllegalArgumentException("Only a returned, non-reversed loan can be reopened.");
        }
        returnDate = null;
        returnFaults = "";
    }

    /** Builds a detached candidate so corrections can be fully validated before changing shared state. */
    Loan copy() {
        Loan copy = new Loan(id, assetId, borrowerId, loanDate, getOriginalDueDate());
        renewals.forEach(event -> copy.renew(event.newDueDate(), event.date()));
        if (returnDate != null) {
            copy.markReturned(returnDate, returnFaults);
        }
        if (lostDate != null) {
            copy.markLost(lostDate);
        }
        if (reversed) {
            copy.reverseIssue();
        }
        return copy;
    }

    /**
     * Closes this loan by recording when the unit came back.
     *
     * @param returnDate date the unit was returned
     */
    public void markReturned(LocalDate returnDate) {
        markReturned(returnDate, "");
    }

    /**
     * Records a return and optional faults together after validating all details.
     *
     * @param returnDate date the unit was returned, no earlier than the loan date
     * @param faults single-line fault description, or an empty string for no reported faults
     * @throws IllegalArgumentException if already returned or the details are invalid
     */
    public void markReturned(LocalDate returnDate, String faults) {
        validateReturn(returnDate, faults);
        this.returnFaults = faults.strip();
        this.returnDate = returnDate;
    }

    /**
     * Validates return details without changing this loan.
     * Batch returns can validate every selected loan before updating any of them.
     *
     * @param returnDate proposed return date
     * @param faults proposed fault description
     * @throws IllegalArgumentException if the loan or return details are invalid
     */
    void validateReturn(LocalDate returnDate, String faults) {
        requireNotLost();
        if (!isActive()) {
            throw new IllegalArgumentException("This loan has already been returned.");
        }
        if (returnDate == null || returnDate.isBefore(loanDate)) {
            throw new IllegalArgumentException("Return date must not be before the loan date.");
        }
        if (!renewals.isEmpty() && returnDate.isBefore(renewals.getLast().date())) {
            throw new IllegalArgumentException("Return date must not be before the latest renewal.");
        }
        if (faults == null || faults.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Fault details must be on one line.");
        }
    }
}
