package lablog;

/** Copies descriptive equipment details under a new, unique asset ID. */
public class EquipmentCopyCommands {
    private static final String USAGE = "Use: copy SOURCE_ASSET_ID NEW_ASSET_ID";

    private final Inventory inventory;

    public EquipmentCopyCommands(Inventory inventory) {
        this.inventory = inventory;
    }

    /** Adds a unit with the source's descriptive fields and no associated loan or history records. */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+");
        if (parts.length != 3 || !parts[0].equals("copy")) {
            throw new IllegalArgumentException(USAGE);
        }
        Equipment source = inventory.getEquipmentUnits().stream()
                .filter(unit -> unit.getAssetId().equals(parts[1]))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown equipment: " + parts[1] + "."));
        if (inventory.containsAssetId(parts[2])) {
            throw new IllegalArgumentException("Equipment " + parts[2] + " already exists.");
        }
        inventory.addEquipment(new Equipment(parts[2], source.getName(), source.getCategory(),
                source.getModel(), source.getLocation()));
        return "Copied " + parts[1] + " to " + parts[2] + ".";
    }
}
