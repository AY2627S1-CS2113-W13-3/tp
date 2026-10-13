package lablog;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Records equipment returns against the same loan book used by history and borrower listings. */
public class ReturnCommands {
    private static final String USAGE = "Use: return ASSET_ID [faults/DESCRIPTION]";
    private final Inventory inventory;
    private final LoanBook loans;
    private final Clock clock;

    /**
     * Connects return handling to shared records and a clock that tests can fix.
     *
     * @param inventory registered equipment
     * @param loans shared loan records
     * @param clock clock and time zone used for the return date
     */
    public ReturnCommands(Inventory inventory, LoanBook loans, Clock clock) {
        this.inventory = Objects.requireNonNull(inventory);
        this.loans = Objects.requireNonNull(loans);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Returns the unit on today's local date, optionally recording faults reported at return.
     * All checks finish before changing the loan; existing equipment faults are not altered.
     *
     * @param input complete return command
     * @return confirmation including the return date and reported faults
     * @throws IllegalArgumentException if syntax, equipment, or active loan state is invalid
     */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+", 3);
        if (parts.length < 2 || !parts[0].equals("return")) {
            throw new IllegalArgumentException(USAGE);
        }
        String faults = "";
        if (parts.length == 3) {
            if (!parts[2].startsWith("faults/")) {
                throw new IllegalArgumentException(USAGE);
            }
            faults = parts[2].substring("faults/".length()).strip();
            if (faults.isEmpty() || faults.matches("(?s).*(?<!\\S)faults/.*")) {
                throw new IllegalArgumentException("Supply faults/ once with a non-empty description. " + USAGE);
            }
        }
        String assetId = parts[1];
        boolean exists = inventory.getEquipmentUnits().stream()
                .anyMatch(unit -> assetId.equals(unit.getAssetId()));
        if (!exists) {
            throw new IllegalArgumentException("Equipment " + assetId + " was not found.");
        }
        List<Loan> active = loans.getLoans().stream()
                .filter(loan -> assetId.equals(loan.getAssetId()) && loan.isActive()).toList();
        if (active.isEmpty()) {
            throw new IllegalArgumentException("Equipment " + assetId + " is not currently on loan.");
        }
        if (active.size() > 1) {
            throw new IllegalArgumentException("Multiple active loans found for " + assetId
                    + ". Resolve the loan records before returning it.");
        }
        LocalDate today = LocalDate.now(clock);
        active.get(0).markReturned(today, faults);
        return "Returned " + assetId + " on " + today + ". "
                + (faults.isEmpty() ? "No faults reported on return." : "Faults: " + faults);
    }
}
