package lablog;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Lists registered units with availability derived from current loans. */
public class EquipmentListCommands {
    private static final String USAGE = "Use: list [category/VALUE] [location/VALUE] [status/available|on-loan]";
    private static final Set<String> FILTER_FIELDS = Set.of("category", "location", "status");

    private final Inventory inventory;
    private final LoanBook loans;

    public EquipmentListCommands(Inventory inventory, LoanBook loans) {
        this.inventory = inventory;
        this.loans = loans;
    }

    /** Lists matching units in registration order without changing inventory or loans. */
    public String execute(String input) {
        String command = input == null ? "" : input.strip();
        if (!command.equals("list")
                && !(command.startsWith("list") && command.length() > 4
                        && Character.isWhitespace(command.charAt(4)))) {
            throw new IllegalArgumentException(USAGE);
        }
        Map<String, String> filters = parseFilters(command.length() == 4 ? "" : command.substring(4).strip());
        List<Equipment> equipment = inventory.getEquipmentUnits();
        if (equipment.isEmpty()) {
            return "No equipment registered.";
        }
        List<Equipment> matches = equipment.stream().filter(unit -> matches(unit, filters)).toList();
        if (matches.isEmpty()) {
            return "No equipment matches the supplied filters.";
        }
        StringBuilder result = new StringBuilder("Asset ID | Name | Category | Model | Location | Availability");
        for (Equipment unit : matches) {
            result.append("\n").append(unit.getAssetId()).append(" | ").append(unit.getName())
                    .append(" | ").append(unit.getCategory()).append(" | ").append(unit.getModel())
                    .append(" | ").append(unit.getLocation()).append(" | ")
                    .append(loans.hasActiveLoan(unit.getAssetId()) ? "On loan" : "Available");
        }
        return result.toString();
    }

    /** Parses optional field filters and rejects unsupported or repeated fields. */
    private Map<String, String> parseFilters(String input) {
        if (input.isEmpty()) {
            return Map.of();
        }
        Map<String, String> filters = new HashMap<>();
        for (String clause : input.split("\\s+(?=\\S+/)")) {
            int separator = clause.indexOf('/');
            if (separator <= 0) {
                throw new IllegalArgumentException(USAGE);
            }
            String field = clause.substring(0, separator).toLowerCase(Locale.ROOT);
            String value = clause.substring(separator + 1).strip();
            if (!FILTER_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unsupported filter '" + field
                        + "'. Choose category, location, or status. " + USAGE);
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Filter values must not be empty. " + USAGE);
            }
            if (filters.containsKey(field)) {
                throw new IllegalArgumentException("Use each filter at most once. " + USAGE);
            }
            if (field.equals("status")) {
                value = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
                if (!Set.of("available", "on-loan").contains(value)) {
                    throw new IllegalArgumentException("Status must be available or on-loan. " + USAGE);
                }
            }
            filters.put(field, value);
        }
        return Map.copyOf(filters);
    }

    /** Applies exact, case-insensitive category/location filters and the current loan status. */
    private boolean matches(Equipment unit, Map<String, String> filters) {
        String category = filters.get("category");
        String location = filters.get("location");
        String status = filters.get("status");
        return (category == null || unit.getCategory().equalsIgnoreCase(category))
                && (location == null || unit.getLocation().equalsIgnoreCase(location))
                && (status == null || status.equals(loans.hasActiveLoan(unit.getAssetId()) ? "on-loan" : "available"));
    }
}
