package lablog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses edits to descriptive equipment fields while keeping asset identity stable. */
public class EquipmentEditCommands {
    private static final String USAGE = "Use: edit-equipment ASSET_ID [n/NAME] [c/CATEGORY] [m/MODEL] [l/LOCATION]";
    private static final Pattern PREFIX = Pattern.compile("(?<!\\S)([^\\s/]+)/");
    private final Inventory inventory;

    public EquipmentEditCommands(Inventory inventory) {
        this.inventory = inventory;
    }

    /** Validates every supplied field before replacing the matching equipment record. */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+", 2);
        if (parts.length != 2 || !parts[0].equals("edit-equipment")) {
            throw new IllegalArgumentException(USAGE + ". Supply at least one field.");
        }
        Matcher matcher = PREFIX.matcher(parts[1]);
        if (!matcher.find()) {
            throw new IllegalArgumentException(USAGE + ". Supply at least one field.");
        }
        String assetId = parts[1].substring(0, matcher.start()).strip();
        Equipment current = inventory.getEquipment(assetId);
        Map<String, String> fields = new HashMap<>();
        do {
            String prefix = matcher.group(1);
            int start = matcher.end();
            boolean next = matcher.find();
            String value = parts[1].substring(start, next ? matcher.start() : parts[1].length()).strip();
            if (!List.of("n", "c", "m", "l").contains(prefix) || value.isEmpty()
                    || value.chars().anyMatch(Character::isISOControl) || fields.containsKey(prefix)) {
                throw new IllegalArgumentException("Repeated, empty or unknown field. " + USAGE);
            }
            fields.put(prefix, value);
            if (!next) {
                break;
            }
        } while (true);

        Equipment updated = new Equipment(assetId, fields.getOrDefault("n", current.getName()),
                fields.getOrDefault("c", current.getCategory()), fields.getOrDefault("m", current.getModel()),
                fields.getOrDefault("l", current.getLocation()));
        inventory.updateEquipment(updated);
        return "Updated equipment " + assetId + ".";
    }
}
