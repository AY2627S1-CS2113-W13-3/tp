package lablog;

import java.time.LocalDate;

/**
 * Represents a single loan of one equipment unit to one borrower.
 *
 * <p>A loan stays active from the day it is recorded until it is closed with
 * a return date. The borrower is referenced by identifier rather than by
 * object so that loans can be recorded before borrower records exist.
 */
public class Loan {
    private final String assetId;
    private final String borrowerId;
    private final LocalDate loanDate;
    private final LocalDate dueDate;
    private LocalDate returnDate;

    /**
     * Creates an active loan of one equipment unit to one borrower.
     *
     * @param assetId asset identifier of the unit being lent out
     * @param borrowerId identifier of the borrower taking the unit
     * @param loanDate date the unit was handed over
     * @param dueDate date the unit is expected back
     */
    public Loan(String assetId, String borrowerId, LocalDate loanDate, LocalDate dueDate) {
        this.assetId = assetId;
        this.borrowerId = borrowerId;
        this.loanDate = loanDate;
        this.dueDate = dueDate;
        this.returnDate = null;
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

    public LocalDate getReturnDate() {
        return returnDate;
    }

    /**
     * Returns whether this unit is still out with the borrower.
     *
     * @return true if the unit has not been returned yet
     */
    public boolean isActive() {
        return returnDate == null;
    }

    /**
     * Closes this loan by recording when the unit came back.
     *
     * @param returnDate date the unit was returned
     */
    public void markReturned(LocalDate returnDate) {
        this.returnDate = returnDate;
    }
}
