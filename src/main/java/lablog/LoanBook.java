package lablog;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores the loan records managed by LabLog.
 *
 * <p>Whether a unit is available is derived from these records rather than
 * stored on each equipment unit, so there is only one place that knows which
 * units are currently out.
 */
public class LoanBook {
    private final List<Loan> loans = new ArrayList<>();

    /**
     * Records a loan.
     *
     * @param loan loan to store
     */
    public void addLoan(Loan loan) {
        loans.add(loan);
    }

    /**
     * Returns an immutable snapshot of every loan recorded so far.
     *
     * @return all loans, including ones that are already closed
     */
    public List<Loan> getLoans() {
        return List.copyOf(loans);
    }
}
