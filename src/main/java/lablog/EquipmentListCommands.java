package lablog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Lists and sorts registered units with operational status derived from live records. */
public class EquipmentListCommands {
    private static final String SUPPORTED_SORT_FIELDS =
            "asset-id, name, category, model, location, availability";
    private static final Set<String> SORT_FIELDS = Set.of(
            "asset-id", "name", "category", "model", "location", "availability");
    private static final String USAGE = "Use: list [category/VALUE] [location/VALUE]"
            + " [status/available|on-loan|unavailable|lost] [sort/FIELD]";
    private static final Set<String> OPTION_FIELDS = Set.of("category", "location", "status", "sort");

    private final Inventory inventory;
    private final LoanBook loans;
    private final FaultBook faults;

    public EquipmentListCommands(Inventory inventory, LoanBook loans) {
        this(inventory, loans, new FaultBook(inventory));
    }

    /** Shares the fault book used by lending, repair and snapshot replacement. */
    public EquipmentListCommands(Inventory inventory, LoanBook loans, FaultBook faults) {
        this.inventory = inventory;
        this.loans = loans;
        this.faults = faults;
    }

    /**
     * Filters the inventory snapshot and optionally sorts a copy of the matching units.
     *
     * @param input list command with optional category, location, status, and sort options
     * @return matching equipment rows or an empty-result message
     * @throws IllegalArgumentException if the command syntax or any option is invalid
     */
    public String execute(String input) {
        String command = input == null ? "" : input.strip();
        if (!command.equals("list")
                && !(command.startsWith("list") && command.length() > 4
                        && Character.isWhitespace(command.charAt(4)))) {
            throw new IllegalArgumentException(USAGE);
        }
        Map<String, String> options = parseOptions(command.length() == 4 ? "" : command.substring(4).strip());
        List<Equipment> equipment = inventory.getEquipmentUnits();
        if (equipment.isEmpty()) {
            return "No equipment registered.";
        }
        List<Equipment> units = equipment.stream().filter(unit -> matches(unit, options)).toList();
        if (units.isEmpty()) {
            return "No equipment matches the supplied filters.";
        }
        String sortField = options.get("sort");
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

    /** Parses filters and the sort option together, preserving spaces inside filter values. */
    private Map<String, String> parseOptions(String input) {
        if (input.isEmpty()) {
            return Map.of();
        }
        Map<String, String> options = new HashMap<>();
        for (String clause : input.split("\\s+(?=\\S+/)")) {
            int separator = clause.indexOf('/');
            if (separator <= 0) {
                throw new IllegalArgumentException(USAGE);
            }
            String field = clause.substring(0, separator).toLowerCase(Locale.ROOT);
            String value = clause.substring(separator + 1).strip();
            if (!OPTION_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unsupported option '" + field
                        + "'. Choose category, location, status, or sort. " + USAGE);
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Option values must not be empty. " + USAGE);
            }
            if (options.containsKey(field)) {
                throw new IllegalArgumentException("Use each option at most once. " + USAGE);
            }
            if (field.equals("status")) {
                value = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
                if (!Set.of("available", "on-loan", "unavailable", "lost").contains(value)) {
                    throw new IllegalArgumentException(
                            "Status must be available, on-loan, unavailable or lost. " + USAGE);
                }
            }
            if (field.equals("sort") && !SORT_FIELDS.contains(value)) {
                throw new IllegalArgumentException("Unsupported sort field: " + value
                        + ". Supported fields: " + SUPPORTED_SORT_FIELDS + ".");
            }
            options.put(field, value);
        }
        return Map.copyOf(options);
    }

    /** Applies exact, case-insensitive category/location filters and the current loan status. */
    private boolean matches(Equipment unit, Map<String, String> options) {
        String category = options.get("category");
        String location = options.get("location");
        String status = options.get("status");
        return (category == null || unit.getCategory().equalsIgnoreCase(category))
                && (location == null || unit.getLocation().equalsIgnoreCase(location))
                && (status == null || status.equals(statusOf(unit)));
    }

    /** Builds an ascending, case-insensitive comparator with asset ID as the stable tie-breaker. */
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
        return loans.hasLostLoan(unit.getAssetId()) ? "Lost"
                : loans.hasActiveLoan(unit.getAssetId()) ? "On loan"
                : faults.blocksLending(unit.getAssetId()) ? "Unavailable: severe fault" : "Available";
    }
    /** Keeps filters consistent with lending restrictions and displayed availability. */
    private String statusOf(Equipment unit) {
        return loans.hasLostLoan(unit.getAssetId()) ? "lost"
                : loans.hasActiveLoan(unit.getAssetId()) ? "on-loan"
                : faults.blocksLending(unit.getAssetId()) ? "unavailable" : "available";
    }
}
