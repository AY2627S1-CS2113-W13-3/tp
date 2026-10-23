package lablog;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Records equipment returns against the same loan book used by history and borrower listings. */
public class ReturnCommands {
    private static final String USAGE =
            "Use: return ASSET_ID [ASSET_ID ...] [faults/DESCRIPTION]"
            + " (faults/ is allowed only for a single unit)";
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
     * Returns one or more units on today's local date, with optional faults for a single unit.
     * All selected loans are validated before any are changed; existing equipment faults are not altered.
     *
     * @param input complete return command
     * @return confirmation including the return date and reported faults
     * @throws IllegalArgumentException if syntax, equipment, or active loan state is invalid
     */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+", 2);
        if (parts.length < 2 || !parts[0].equals("return")) {
            throw new IllegalArgumentException(USAGE);
        }
        // Separate the equipment selection from optional fault details.
        String[] arguments = parts[1].split("\\s+faults/", 2);
        List<String> assetIds = readAssetIds(arguments[0]);

        String faults = "";
        if (arguments.length == 2) {
            if (assetIds.size() > 1) {
                throw new IllegalArgumentException(
                        "Return units with reported faults individually.");
            }

            faults = arguments[1].strip();
            if (faults.isEmpty() || faults.matches("(?s).*(?<!\\S)faults/.*")) {
                throw new IllegalArgumentException(
                        "Supply faults/ once with a non-empty description. " + USAGE);
            }
        }

        List<Loan> selectedLoans = assetIds.stream()
                .map(this::findActiveLoan)
                .toList();
        LocalDate today = LocalDate.now(clock);

        // The book validates all selections and prepares evidence before changing any loan.
        loans.returnLoans(selectedLoans, today, faults);

        return "Returned " + String.join(", ", assetIds) + " on " + today + ". "
                + (faults.isEmpty() ? "No faults reported on return." : "Faults: " + faults);
    }

    /**
     * Finds the only active loan for registered equipment.
     *
     * @param assetId selected equipment identifier
     * @return the equipment's active loan
     * @throws IllegalArgumentException if the equipment is missing or does not
     *     have exactly one active loan
     */
    private Loan findActiveLoan(String assetId) {
        boolean exists = inventory.getEquipmentUnits().stream()
                .anyMatch(unit -> assetId.equals(unit.getAssetId()));
        if (!exists) {
            throw new IllegalArgumentException("Equipment " + assetId + " was not found.");
        }
        List<Loan> active = loans.getLoans().stream()
                .filter(loan -> assetId.equals(loan.getAssetId()) && loan.isActive())
                .toList();
        if (active.isEmpty()) {
            throw new IllegalArgumentException("Equipment " + assetId + " is not currently on loan.");
        }
        if (active.size() > 1) {
            throw new IllegalArgumentException("Multiple active loans found for " + assetId
                    + ". Resolve the loan records before returning it.");
        }
        return active.get(0);
    }

    /**
     * Parses the equipment selection and rejects repeated asset IDs.
     *
     * @param selection whitespace-separated asset IDs, excluding fault details
     * @return selected asset IDs in the user's input order
     * @throws IllegalArgumentException if the selection is empty or contains duplicates
     */
    private List<String> readAssetIds(String selection) {
        if (selection.isBlank()) {
            throw new IllegalArgumentException("Supply at least one asset ID.");
        }

        List<String> assetIds = List.of(selection.strip().split("\\s+"));
        Set<String> seen = new HashSet<>();

        for (String assetId : assetIds) {
            if (!seen.add(assetId)) {
                throw new IllegalArgumentException("Duplicate asset ID: " + assetId + ".");
            }
        }

        return assetIds;
    }
}
