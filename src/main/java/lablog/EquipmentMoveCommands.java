package lablog;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Moves a selected group of equipment units to one validated storage location. */
public class EquipmentMoveCommands {
    private static final String USAGE = "Use: move-equipment ASSET_ID[,ASSET_ID...] l/LOCATION";
    private static final Pattern LOCATION_PREFIX = Pattern.compile("\\s+l/");
    private final Inventory inventory;

    public EquipmentMoveCommands(Inventory inventory) {
        this.inventory = inventory;
    }

    /** Validates the selection and location before applying the complete batch. */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+", 2);
        if (parts.length != 2 || !parts[0].equals("move-equipment")) {
            throw new IllegalArgumentException(USAGE);
        }
        Matcher locationMatcher = LOCATION_PREFIX.matcher(parts[1]);
        if (!locationMatcher.find()) {
            throw new IllegalArgumentException(USAGE);
        }
        List<String> assetIds = Arrays.stream(parts[1].substring(0, locationMatcher.start())
                .split(",", -1)).map(String::strip).toList();
        String location = parts[1].substring(locationMatcher.end()).strip();
        inventory.updateLocations(assetIds, location);
        return "Moved " + assetIds.size() + " equipment unit(s) to " + location + ".";
    }
}
