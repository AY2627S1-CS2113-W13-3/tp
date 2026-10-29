package lablog;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Lists active or overdue-only loans across borrowers using the console's local date. */
public class ActiveLoanCommands {
    private static final String USAGE = "Use: active-loans [overdue]";
    private final LoanBook loans;
    private final Clock clock;

    public ActiveLoanCommands(LoanBook loans, Clock clock) {
        this.loans = loans;
        this.clock = clock;
    }

    /**
     * Shows active loans, optionally restricted to due dates before today's local date.
     *
     * @param input active-loans command with an optional overdue keyword
     * @return loans ordered by due date and asset ID, or a mode-specific empty message
     * @throws IllegalArgumentException if the command or optional keyword is invalid
     */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+");
        if (parts.length == 0 || parts.length > 2 || !parts[0].equals("active-loans")
                || (parts.length == 2 && !parts[1].equals("overdue"))) {
            throw new IllegalArgumentException(USAGE);
        }
        boolean overdueOnly = parts.length == 2;
        LocalDate today = LocalDate.now(clock);
        List<Loan> active = loans.getLoans().stream().filter(Loan::isActive)
                .filter(loan -> !overdueOnly || loan.getDueDate().isBefore(today))
                .sorted(Comparator.comparing(Loan::getDueDate).thenComparing(Loan::getAssetId)).toList();
        if (active.isEmpty()) {
            return overdueOnly ? "No overdue loans." : "No active loans.";
        }
        List<List<String>> rows = new ArrayList<>();
        for (Loan loan : active) {
            rows.add(List.of(loan.getAssetId(), loan.getBorrowerId(), loan.getDueDate().toString(),
                    loan.isLost() ? "LOST" : loan.getDueDate().isBefore(today) ? "OVERDUE" : "On time"));
        }
        return TableFormatter.format(List.of("Asset ID", "Borrower ID", "Due date", "Status"), rows);
    }
}
