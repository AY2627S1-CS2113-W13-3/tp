package lablog;

/**
 * Represents an equipment unit and its identifying details.
 */
public class Equipment {
    private final String assetId;
    private final String name;
    private final String category;
    private final String model;
    private final String location;

    /**
     * Creates an equipment unit with the given details.
     *
     * @param assetId unique asset identifier
     * @param name equipment name
     * @param category equipment category
     * @param model equipment model
     * @param location equipment location
     */
    public Equipment(String assetId, String name, String category, String model, String location) {
        this.assetId = assetId;
        this.name = name;
        this.category = category;
        this.model = model;
        this.location = location;
    }

    public String getAssetId() {
        return assetId;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getModel() {
        return model;
    }

    public String getLocation() {
        return location;
    }
}
