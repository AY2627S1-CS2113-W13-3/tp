package lablog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Searches registered equipment names and models without changing inventory records. */
public class EquipmentSearchCommands {
    private static final String USAGE = "Use: search TEXT";

    private final Inventory inventory;
    private final LoanBook loans;
    private final FaultBook faults;

    /** Uses the shared equipment, loan, and fault records for read-only search results. */
    public EquipmentSearchCommands(Inventory inventory, LoanBook loans, FaultBook faults) {
        this.inventory = inventory;
        this.loans = loans;
        this.faults = faults;
    }

    /** Returns all equipment whose name or model contains the query, ignoring letter case. */
    public String execute(String input) {
        String command = input == null ? "" : input.strip();
        if (!command.startsWith("search") || command.length() == 6
                || !Character.isWhitespace(command.charAt(6))) {
            throw new IllegalArgumentException(USAGE);
        }
        String query = normalize(command.substring(6));
        if (query.isEmpty()) {
            throw new IllegalArgumentException("Search text must not be empty. " + USAGE);
        }
        List<Equipment> equipment = inventory.getEquipmentUnits();
        if (equipment.isEmpty()) {
            return "No equipment registered.";
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        List<Equipment> matches = equipment.stream()
                .filter(unit -> normalize(unit.getName()).toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || normalize(unit.getModel()).toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .toList();
        if (matches.isEmpty()) {
            return "No equipment matches '" + query + "'.";
        }
        List<List<String>> rows = new ArrayList<>();
        for (Equipment unit : matches) {
            rows.add(List.of(unit.getAssetId(), unit.getName(), unit.getCategory(), unit.getModel(),
                    unit.getLocation(), availabilityOf(unit)));
        }
        return TableFormatter.format(
                List.of("Asset ID", "Name", "Category", "Model", "Location", "Availability"), rows);
    }

    /** Trims surrounding whitespace and treats runs of spaces as one for matching. */
    private String normalize(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }

    /** Reports the same live status as equipment listing and exact lookup. */
    private String availabilityOf(Equipment unit) {
        return loans.hasLostLoan(unit.getAssetId()) ? "Lost"
                : loans.hasActiveLoan(unit.getAssetId()) ? "On loan"
                : faults.blocksLending(unit.getAssetId()) ? "Unavailable: severe fault" : "Available";
    }
}
