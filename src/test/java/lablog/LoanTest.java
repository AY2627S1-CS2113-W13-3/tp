package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Verifies the details a loan keeps and when it stops counting as active. */
class LoanTest {
    private static final LocalDate LOAN_DATE = LocalDate.of(2026, 10, 9);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 10, 23);

    @Test
    public void constructor_validDetails_keepsDetails() {
        Loan loan = new Loan("A1001", "B001", LOAN_DATE, DUE_DATE);

        assertEquals("A1001", loan.getAssetId());
        assertEquals("B001", loan.getBorrowerId());
        assertEquals(LOAN_DATE, loan.getLoanDate());
        assertEquals(DUE_DATE, loan.getDueDate());
    }

    @Test
    public void isActive_newLoan_returnsTrue() {
        Loan loan = new Loan("A1001", "B001", LOAN_DATE, DUE_DATE);

        assertTrue(loan.isActive());
        assertNull(loan.getReturnDate());
    }

    @Test
    public void markReturned_returnedLoan_becomesInactive() {
        Loan loan = new Loan("A1001", "B001", LOAN_DATE, DUE_DATE);
        LocalDate returnDate = LocalDate.of(2026, 10, 20);

        loan.markReturned(returnDate);

        assertFalse(loan.isActive());
        assertEquals(returnDate, loan.getReturnDate());
    }
}
