package lablog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
     * Finds the loan that currently has the given unit out.
     *
     * <p>Returns the first matching active loan. Callers that issue loans must
     * prevent duplicate active loans; this lookup does not enforce uniqueness.
     *
     * @param assetId asset identifier of the unit to look up
     * @return the active loan for that unit, or empty if the unit is available
     */
    public Optional<Loan> findActiveLoan(String assetId) {
        for (Loan loan : loans) {
            if (loan.isActive() && loan.getAssetId().equals(assetId)) {
                return Optional.of(loan);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns whether the given unit is already out with a borrower.
     *
     * @param assetId asset identifier of the unit to check
     * @return true if the unit cannot be lent out again until it is returned
     */
    public boolean hasActiveLoan(String assetId) {
        return findActiveLoan(assetId).isPresent();
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
