package lablog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Stores the loan records managed by LabLog.
 *
 * <p>Whether a unit is available is derived from these records rather than
 * stored on each equipment unit, so there is only one place that knows which
 * units are currently out.
 */
public class LoanBook {
    private final List<Loan> loans = new ArrayList<>();
    private final List<LoanAuditEvent> audit = new ArrayList<>();

    /**
     * Records a new loan and immutable issue evidence. Restore paths use replaceAll instead.
     *
     * @param loan loan to store
     */
    public void addLoan(Loan loan) {
        LoanAuditEvent event = LoanAuditEvent.issued(loan);
        loans.add(loan);
        audit.add(event);
    }

    /** Returns events in acceptance order as an immutable collection of immutable values. */
    public List<LoanAuditEvent> getAuditEvents() {
        return List.copyOf(audit);
    }

    /** Validates a whole return batch and prepares all evidence before publishing either state or events. */
    public void returnLoans(List<Loan> selected, LocalDate date, String faults) {
        Set<String> ids = new HashSet<>();
        for (Loan loan : selected) {
            if (!loans.contains(loan) || !ids.add(loan.getId())) {
                throw new IllegalArgumentException("Return selection must contain distinct loans from this book.");
            }
            loan.validateReturn(date, faults);
        }
        List<LoanAuditEvent> pending = selected.stream()
                .map(loan -> LoanAuditEvent.returned(loan, date, faults)).toList();
        selected.forEach(loan -> loan.markReturned(date, faults));
        audit.addAll(pending);
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

    /** Keeps every availability consumer aware of unresolved loss reports in the shared records. */
    public boolean hasLostLoan(String assetId) {
        return loans.stream().anyMatch(loan -> loan.isLost() && loan.getAssetId().equals(assetId));
    }

    /**
     * Returns an immutable snapshot of every loan recorded so far.
     *
     * @return all loans, including ones that are already closed
     */
    public List<Loan> getLoans() {
        return List.copyOf(loans);
    }
    /** Installs records that have already passed complete validation and persistence. */
    void replaceAll(List<Loan> replacement) {
        replaceAll(replacement, List.of());
    }

    /** Restores existing records and evidence together without inventing fresh issue/return events. */
    void replaceAll(List<Loan> replacement, List<LoanAuditEvent> events) {
        LoanAuditCodec.validate(replacement, events);
        loans.clear();
        loans.addAll(replacement);
        audit.clear();
        audit.addAll(events);
    }
}
