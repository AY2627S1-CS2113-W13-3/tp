package lablog;

/** Looks up one registered equipment unit by its exact asset identifier. */
public class EquipmentFindCommands {
    private static final String USAGE = "Use: find ASSET_ID";
    private final Inventory inventory;
    private final LoanBook loans;

    public EquipmentFindCommands(Inventory inventory, LoanBook loans) {
        this.inventory = inventory;
        this.loans = loans;
    }

    /** Returns the selected unit's details without changing equipment or loan records. */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+");
        if (parts.length != 2 || !parts[0].equals("find")) {
            throw new IllegalArgumentException(USAGE);
        }
        String assetId = parts[1];
        Equipment equipment = inventory.getEquipmentUnits().stream()
                .filter(unit -> unit.getAssetId().equals(assetId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Equipment " + assetId + " not found."));
        String availability = loans.hasActiveLoan(assetId) ? "On loan" : "Available";
        return "Asset ID: " + equipment.getAssetId() + "\n"
                + "Name: " + equipment.getName() + "\n"
                + "Category: " + equipment.getCategory() + "\n"
                + "Model: " + equipment.getModel() + "\n"
                + "Location: " + equipment.getLocation() + "\n"
                + "Availability: " + availability;
    }
}
