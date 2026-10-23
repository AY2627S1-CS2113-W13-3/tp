package lablog;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;

/** Shared interval conflict rules for booking, lending, renewal and availability queries. */
public class BookingAvailability {
    private final Inventory inventory;
    private final LoanBook loans;
    private final ReservationBook reservations;
    private final FaultBook faults;
    private final Clock clock;

    public BookingAvailability(Inventory inventory, LoanBook loans, ReservationBook reservations,
            FaultBook faults, Clock clock) {
        this.inventory = inventory;
        this.loans = loans;
        this.reservations = reservations;
        this.faults = faults;
        this.clock = clock;
    }

    /**
     * Explains the first conflict for an inclusive interval without mutating records.
     * Editing excludes its own reservation ID; renewal excludes only the original loan object.
     * Unreturned overdue loans have no known end, so they block future intervals until returned.
     */
    public Optional<String> conflict(String assetId, DateWindow window, String ignoredReservationId, Loan ignoredLoan) {
        return reasons(assetId, window, ignoredReservationId, ignoredLoan).stream().findFirst();
    }

    /** Collects all applicable blockers from the shared records, in loss/fault/reservation/loan order. */
    public List<String> reasons(String assetId, DateWindow window, String ignoredReservationId, Loan ignoredLoan) {
        if (!inventory.containsAssetId(assetId)) {
            throw new IllegalArgumentException("Equipment " + assetId + " was not found.");
        }
        List<String> reasons = new ArrayList<>();
        if (loans.hasLostLoan(assetId)) {
            reasons.add("Equipment " + assetId + " is marked lost and is unavailable.");
        }
        for (Fault fault : faults.getUnresolvedSevereFaults()) {
            if (fault.getAssetId().equals(assetId)) {
                reasons.add("Equipment " + assetId + " has an unresolved severe fault. "
                        + fault.getId() + " reported " + fault.getReportDate() + ": " + fault.getDescription());
            }
        }
        for (Reservation item : reservations.getReservations()) {
            if (!item.isCancelled() && !item.id().equals(ignoredReservationId) && item.assetId().equals(assetId)
                    && item.window().overlaps(window)) {
                reasons.add("Conflicts with reservation " + item.id() + " for " + item.borrowerId()
                        + " from " + item.window().start() + " through " + item.window().end() + ".");
            }
        }
        for (Loan loan : loans.getLoans()) {
            if (loan == ignoredLoan || !loan.getAssetId().equals(assetId)) {
                continue;
            }
            LocalDate end = loan.isActive() ? loan.getDueDate() : loan.getReturnDate();
            if (loan.isActive() && end.isBefore(LocalDate.now(clock))) {
                end = LocalDate.MAX;
            }
            if (new DateWindow(loan.getLoanDate(), end).overlaps(window)) {
                reasons.add("Conflicts with loan to " + loan.getBorrowerId() + " from " + loan.getLoanDate()
                        + (end.equals(LocalDate.MAX) ? " (overdue and not returned)." : " through " + end + ".")
                        + " Loan " + loan.getId() + (loan.isActive() ? "; outstanding; due " + loan.getDueDate()
                                : "; returned " + loan.getReturnDate()) + ".");
            }
        }
        return List.copyOf(reasons);
    }

    /** Distinguishes operational equipment from units unavailable indefinitely through loss or severe faults. */
    public boolean isOperational(String assetId) {
        return !loans.hasLostLoan(assetId) && !faults.blocksLending(assetId);
    }

    /** Returns an immutable, deterministic snapshot of units free throughout the requested future period. */
    public List<Equipment> availableUnits(DateWindow window, String category) {
        window.requireNotPast(LocalDate.now(clock));
        if (category != null && (category.isBlank() || category.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Supply a non-empty single-line category.");
        }
        return inventory.getEquipmentUnits().stream()
                .filter(unit -> category == null || unit.getCategory().equalsIgnoreCase(category))
                .filter(unit -> conflict(unit.getAssetId(), window, null, null).isEmpty())
                .sorted(Comparator.comparing(Equipment::getAssetId, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Equipment::getAssetId))
                .toList();
    }

    /** Rejects a new or edited transaction before its caller saves or publishes any change. */
    public void requireAvailable(String assetId, DateWindow window, String ignoredReservationId, Loan ignoredLoan) {
        conflict(assetId, window, ignoredReservationId, ignoredLoan).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
    }
}
