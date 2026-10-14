package lablog;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Lists outstanding loans across borrowers using the console's clock. */
public class ActiveLoanCommands {
    private final LoanBook loans;
    private final Clock clock;

    public ActiveLoanCommands(LoanBook loans, Clock clock) {
        this.loans = loans;
        this.clock = clock;
    }

    /** Shows active loans in due-date order; a loan due today is not overdue. */
    public String execute(String input) {
        if (input == null || !input.strip().equals("active-loans")) {
            throw new IllegalArgumentException("Use: active-loans");
        }
        List<Loan> active = loans.getLoans().stream().filter(Loan::isActive)
                .sorted(Comparator.comparing(Loan::getDueDate).thenComparing(Loan::getAssetId)).toList();
        if (active.isEmpty()) {
            return "No active loans.";
        }
        LocalDate today = LocalDate.now(clock);
        StringBuilder result = new StringBuilder("Asset ID | Borrower ID | Due date | Status");
        for (Loan loan : active) {
            result.append("\n").append(loan.getAssetId()).append(" | ").append(loan.getBorrowerId())
                    .append(" | ").append(loan.getDueDate()).append(" | ")
                    .append(loan.getDueDate().isBefore(today) ? "OVERDUE" : "On time");
        }
        return result.toString();
    }
}
