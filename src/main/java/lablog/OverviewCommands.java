package lablog;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Builds a read-only daily view directly from the shared reservation, loan and fault records. */
public class OverviewCommands {
    private final ReservationBook reservations;
    private final LoanBook loans;
    private final FaultBook faults;
    private final Clock clock;

    public OverviewCommands(ReservationBook reservations, LoanBook loans, FaultBook faults, Clock clock) {
        this.reservations = reservations;
        this.loans = loans;
        this.faults = faults;
        this.clock = clock;
    }

    /** Uses one local date for all sections and never changes record order or saved state. */
    public String execute(String input) {
        if (input == null || !input.strip().equals("overview")) {
            throw new IllegalArgumentException("Use: overview");
        }
        LocalDate today = LocalDate.now(clock);
        StringBuilder output = new StringBuilder("Operational overview for " + today + "\n");
        appendReservations(output, today);
        appendOverdueLoans(output, today);
        appendSevereFaults(output);
        return output.append("\nMaintenance scheduling is not supported; no due-maintenance data is included.")
                .toString();
    }

    /** Includes bookings covering today, including both boundary dates, unless cancelled. */
    private void appendReservations(StringBuilder output, LocalDate today) {
        List<Reservation> current = reservations.getReservations().stream()
                .filter(item -> !item.isCancelled() && !item.window().start().isAfter(today)
                        && !item.window().end().isBefore(today))
                .sorted(Comparator.comparing(Reservation::assetId).thenComparing(Reservation::id)).toList();
        output.append("\nToday's reservations (" + current.size() + "):\n");
        if (current.isEmpty()) {
            output.append("No reservations for today.\n");
            return;
        }
        output.append("Reservation ID | Asset ID | Borrower ID | From | Through\n");
        for (Reservation item : current) {
            output.append(item.id()).append(" | ").append(item.assetId()).append(" | ")
                    .append(item.borrowerId()).append(" | ").append(item.window().start()).append(" | ")
                    .append(item.window().end()).append('\n');
        }
    }

    /** Uses the same active-state and strict due-date boundary as the overdue-loan listing. */
    private void appendOverdueLoans(StringBuilder output, LocalDate today) {
        List<Loan> overdue = loans.getLoans().stream()
                .filter(loan -> loan.isActive() && loan.getDueDate().isBefore(today))
                .sorted(Comparator.comparing(Loan::getDueDate).thenComparing(Loan::getAssetId)
                        .thenComparing(Loan::getId)).toList();
        output.append("\nOverdue loans (" + overdue.size() + "):\n");
        if (overdue.isEmpty()) {
            output.append("No overdue loans.\n");
            return;
        }
        output.append("Loan ID | Asset ID | Borrower ID | Due date | Status\n");
        for (Loan loan : overdue) {
            output.append(loan.getId()).append(" | ").append(loan.getAssetId()).append(" | ")
                    .append(loan.getBorrowerId()).append(" | ").append(loan.getDueDate()).append(" | ")
                    .append(loan.isLost() ? "LOST (overdue)" : "OVERDUE").append('\n');
        }
    }

    /** Includes every unresolved severe report, matching the shared lending restriction regardless of report date. */
    private void appendSevereFaults(StringBuilder output) {
        List<Fault> severe = faults.getUnresolvedSevereFaults().stream()
                .sorted(Comparator.comparing(Fault::getReportDate).thenComparing(Fault::getAssetId)
                        .thenComparing(Fault::getId)).toList();
        output.append("\nUnresolved severe faults (" + severe.size() + "):\n");
        if (severe.isEmpty()) {
            output.append("No unresolved severe faults.\n");
            return;
        }
        output.append("Fault ID | Asset ID | Report date | Description\n");
        for (Fault fault : severe) {
            output.append(fault.getId()).append(" | ").append(fault.getAssetId()).append(" | ")
                    .append(fault.getReportDate()).append(" | ").append(fault.getDescription()).append('\n');
        }
    }
}
