package lablog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds units free for an entire date interval without reserving or modifying them. */
public class AvailableEquipmentCommands {
    private static final String USAGE = "Use: available from/START to/END [category/CATEGORY]";
    private final BookingAvailability availability;

    public AvailableEquipmentCommands(BookingAvailability availability) {
        this.availability = availability;
    }

    /** Validates the whole query before reporting an empty or non-empty result. */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+", 2);
        if (parts.length != 2 || !parts[0].equals("available")) {
            throw new IllegalArgumentException(USAGE);
        }
        Map<String, String> fields = BookingArguments.fields(parts[1], Set.of("from", "to", "category"));
        if (!fields.containsKey("from") || !fields.containsKey("to")) {
            throw new IllegalArgumentException(USAGE);
        }
        DateWindow window = DateWindow.parse(fields.get("from"), fields.get("to"));
        List<Equipment> units = availability.availableUnits(window, fields.get("category"));
        if (units.isEmpty()) {
            return "No equipment available throughout " + window.start() + " through " + window.end() + ".";
        }
        List<List<String>> rows = new ArrayList<>();
        for (Equipment unit : units) {
            rows.add(List.of(unit.getAssetId(), unit.getName(), unit.getCategory(), unit.getModel(),
                    unit.getLocation()));
        }
        return "Available throughout " + window.start() + " through " + window.end() + " (inclusive):\n"
                + TableFormatter.format(List.of("Asset ID", "Name", "Category", "Model", "Location"), rows);
    }
}
