package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class LoanBookTest {
    private static final LocalDate LOAN_DATE = LocalDate.of(2026, 10, 9);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 10, 23);

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
}
