package lablog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses explicit equipment registration without interpreting typos as asset IDs. */
public class EquipmentAddCommands {
    private static final String USAGE = "Use: add ASSET_ID n/NAME c/CATEGORY m/MODEL l/LOCATION";
    private static final Pattern PREFIX = Pattern.compile("(?<!\\S)([^\\s/]+)/");
    private final Inventory inventory;

    public EquipmentAddCommands(Inventory inventory) {
        this.inventory = inventory;
    }

    /** Validates all fields before adding one equipment unit. */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+", 3);
        if (parts.length != 3 || !parts[0].equals("add")) {
            throw new IllegalArgumentException(USAGE);
        }
        Matcher matcher = PREFIX.matcher(parts[2]);
        if (!matcher.find() || matcher.start() != 0) {
            throw new IllegalArgumentException(USAGE);
        }
        Map<String, String> fields = new HashMap<>();
        String prefix = matcher.group(1);
        int start = matcher.end();
        while (matcher.find()) {
            addField(fields, prefix, parts[2].substring(start, matcher.start()).strip());
            prefix = matcher.group(1);
            start = matcher.end();
        }
        addField(fields, prefix, parts[2].substring(start).strip());
        if (fields.size() != 4) {
            throw new IllegalArgumentException(USAGE);
        }
        if (inventory.getEquipmentUnits().stream().anyMatch(unit -> unit.getAssetId().equals(parts[1]))) {
            throw new IllegalArgumentException("Equipment " + parts[1] + " already exists.");
        }
        inventory.addEquipment(new Equipment(parts[1], fields.get("n"), fields.get("c"),
                fields.get("m"), fields.get("l")));
        return "Equipment added to inventory.";
    }

    /** Rejects unknown, repeated, blank, or multiline fields before mutation. */
    private void addField(Map<String, String> fields, String prefix, String value) {
        if (!List.of("n", "c", "m", "l").contains(prefix) || fields.containsKey(prefix)
                || value.isBlank() || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(USAGE);
        }
        fields.put(prefix, value);
    }
}
