package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class LoanBookTest {
    private static final LocalDate LOAN_DATE = LocalDate.of(2026, 10, 9);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 10, 23);
    private static final LocalDate RETURN_DATE = LocalDate.of(2026, 10, 20);

    @Test
    public void getLoans_newLoanBook_isEmpty() {
        LoanBook loanBook = new LoanBook();

        assertTrue(loanBook.getLoans().isEmpty());
    }

    @Test
    public void addLoan_oneLoan_isStored() {
        LoanBook loanBook = new LoanBook();
        Loan loan = new Loan("A1001", "B001", LOAN_DATE, DUE_DATE);

        loanBook.addLoan(loan);

        assertEquals(1, loanBook.getLoans().size());
        assertEquals(loan, loanBook.getLoans().get(0));
    }

    @Test
    public void getLoans_modifySnapshot_doesNotAffectLoanBook() {
        LoanBook loanBook = new LoanBook();
        loanBook.addLoan(new Loan("A1001", "B001", LOAN_DATE, DUE_DATE));

        List<Loan> snapshot = loanBook.getLoans();

        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertEquals(1, loanBook.getLoans().size());
    }

    @Test
    public void hasActiveLoan_unitNeverLentOut_returnsFalse() {
        LoanBook loanBook = new LoanBook();

        assertFalse(loanBook.hasActiveLoan("A1001"));
        assertTrue(loanBook.findActiveLoan("A1001").isEmpty());
    }

    @Test
    public void hasActiveLoan_unitCurrentlyOut_returnsTrue() {
        LoanBook loanBook = new LoanBook();
        Loan loan = new Loan("A1001", "B001", LOAN_DATE, DUE_DATE);
        loanBook.addLoan(loan);

        assertTrue(loanBook.hasActiveLoan("A1001"));
        assertEquals(Optional.of(loan), loanBook.findActiveLoan("A1001"));
    }

    @Test
    public void hasActiveLoan_otherUnitOut_returnsFalse() {
        LoanBook loanBook = new LoanBook();
        loanBook.addLoan(new Loan("A1001", "B001", LOAN_DATE, DUE_DATE));

        assertFalse(loanBook.hasActiveLoan("A1002"));
    }

    @Test
    public void hasActiveLoan_unitAlreadyReturned_returnsFalse() {
        LoanBook loanBook = new LoanBook();
        Loan loan = new Loan("A1001", "B001", LOAN_DATE, DUE_DATE);
        loanBook.addLoan(loan);

        loan.markReturned(RETURN_DATE);

        assertFalse(loanBook.hasActiveLoan("A1001"));
        assertTrue(loanBook.findActiveLoan("A1001").isEmpty());
    }

    @Test
    public void findActiveLoan_unitLentAgainAfterReturn_returnsLatestLoan() {
        LoanBook loanBook = new LoanBook();
        Loan firstLoan = new Loan("A1001", "B001", LOAN_DATE, DUE_DATE);
        firstLoan.markReturned(RETURN_DATE);
        Loan secondLoan = new Loan("A1001", "B002", RETURN_DATE, DUE_DATE);
        loanBook.addLoan(firstLoan);
        loanBook.addLoan(secondLoan);

        assertTrue(loanBook.hasActiveLoan("A1001"));
        assertEquals(Optional.of(secondLoan), loanBook.findActiveLoan("A1001"));
        assertEquals(2, loanBook.getLoans().size());
    }
}
