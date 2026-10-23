package lablog;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Marks one outstanding equipment loan lost while retaining its borrower and complete history. */
public class LostCommands {
    private static final String USAGE = "Use: lost ASSET_ID";
    private final Inventory inventory;
    private final LoanBook loans;
    private final Clock clock;

    /** Uses shared live records and the same local clock as loan and return commands. */
    public LostCommands(Inventory inventory, LoanBook loans, Clock clock) {
        this.inventory = Objects.requireNonNull(inventory);
        this.loans = Objects.requireNonNull(loans);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Validates one exact asset and its unique active loan before recording the loss date.
     *
     * @param input complete lost command
     * @return confirmation with the report date and borrower
     * @throws IllegalArgumentException if syntax, equipment, loan state or report date is invalid
     */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+");
        if (parts.length != 2 || !parts[0].equals("lost")) {
            throw new IllegalArgumentException(USAGE);
        }
        String assetId = parts[1];
        if (!inventory.containsAssetId(assetId)) {
            throw new IllegalArgumentException("Equipment " + assetId + " was not found.");
        }
        List<Loan> active = loans.getLoans().stream()
                .filter(loan -> loan.getAssetId().equals(assetId) && loan.isActive()).toList();
        if (active.size() != 1) {
            throw new IllegalArgumentException("Equipment " + assetId + " must have exactly one active loan.");
        }
        LocalDate today = LocalDate.now(clock);
        Loan loan = active.getFirst();
        loan.markLost(today);
        return "Marked " + assetId + " as lost on " + today + ". Borrower: " + loan.getBorrowerId()
                + ". Original loan and renewal history retained.";
    }
}
