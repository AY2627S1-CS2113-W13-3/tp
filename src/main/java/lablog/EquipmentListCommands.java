package lablog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Lists registered units with availability derived from current loans. */
public class EquipmentListCommands {
    private static final String SUPPORTED_SORT_FIELDS =
            "asset-id, name, category, model, location, availability";
    private static final Set<String> SORT_FIELDS = Set.of(
            "asset-id", "name", "category", "model", "location", "availability");
    private static final String USAGE = "Use: list [sort/FIELD]";

    private final Inventory inventory;
    private final LoanBook loans;

    public EquipmentListCommands(Inventory inventory, LoanBook loans) {
        this.inventory = inventory;
        this.loans = loans;
    }

    /**
     * Lists units in registration order or sorts a copied snapshot by one supported field.
     *
     * @param input list command with an optional sort field
     * @return formatted equipment rows
     * @throws IllegalArgumentException if the command syntax or sort field is invalid
     */
    public String execute(String input) {
        String sortField = parseSortField(input);
        List<Equipment> units = inventory.getEquipmentUnits();
        if (units.isEmpty()) {
            return "No equipment registered.";
        }
        if (sortField != null) {
            units = new ArrayList<>(units);
            units.sort(comparatorFor(sortField));
        }
        StringBuilder result = new StringBuilder("Asset ID | Name | Category | Model | Location | Availability");
        for (Equipment unit : units) {
            result.append("\n").append(unit.getAssetId()).append(" | ").append(unit.getName())
                    .append(" | ").append(unit.getCategory()).append(" | ").append(unit.getModel())
                    .append(" | ").append(unit.getLocation()).append(" | ")
                    .append(availabilityOf(unit));
        }
        return result.toString();
    }

    /** Validates the optional sort argument and returns its field name, if present. */
    private static String parseSortField(String input) {
        if (input == null) {
            throw new IllegalArgumentException(USAGE);
        }
        String[] parts = input.strip().split("\\s+");
        if (!parts[0].equals("list") || parts.length > 2) {
            throw new IllegalArgumentException(USAGE);
        }
        if (parts.length == 1) {
            return null;
        }
        if (!parts[1].startsWith("sort/")) {
            throw new IllegalArgumentException(USAGE);
        }
        String field = parts[1].substring("sort/".length());
        if (field.isEmpty()) {
            throw new IllegalArgumentException(USAGE);
        }
        if (!SORT_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Unsupported sort field: " + field
                    + ". Supported fields: " + SUPPORTED_SORT_FIELDS + ".");
        }
        return field;
    }

    /**
     * Builds an ascending, case-insensitive comparator with asset ID as the stable tie-breaker.
     */
    private Comparator<Equipment> comparatorFor(String field) {
        Function<Equipment, String> fieldValue = switch (field) {
        case "asset-id" -> Equipment::getAssetId;
        case "name" -> Equipment::getName;
        case "category" -> Equipment::getCategory;
        case "model" -> Equipment::getModel;
        case "location" -> Equipment::getLocation;
        case "availability" -> unit -> availabilityOf(unit);
        default -> throw new IllegalArgumentException("Unsupported sort field: " + field
                + ". Supported fields: " + SUPPORTED_SORT_FIELDS + ".");
        };
        return Comparator.comparing(fieldValue, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Equipment::getAssetId, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Equipment::getAssetId);
    }

    /** Returns the same availability label displayed in each equipment row. */
    private String availabilityOf(Equipment unit) {
        return loans.hasActiveLoan(unit.getAssetId()) ? "On loan" : "Available";
    }
}
