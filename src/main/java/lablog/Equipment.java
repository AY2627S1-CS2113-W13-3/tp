package lablog;

import java.time.LocalDate;

/**
 * Represents an equipment unit and its identifying details.
 */
public class Equipment {
    private final String assetId;
    private final String name;
    private final String category;
    private final String model;
    private final String location;
    private final LocalDate retiredOn;
    private final String retirementReason;

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
        this(assetId, name, category, model, location, null, "");
    }

    /** Restores the retirement date and reason alongside the equipment details. */
    public Equipment(String assetId, String name, String category, String model, String location,
            LocalDate retiredOn, String retirementReason) {
        if (assetId == null || assetId.isBlank()
                || assetId.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw new IllegalArgumentException("Asset ID must be a single non-empty word without control characters.");
        }
        // These characters delimit prefixed fields and batch selections in equipment commands.
        if (assetId.indexOf('/') >= 0 || assetId.indexOf(',') >= 0) {
            throw new IllegalArgumentException("Asset ID must not contain '/' or ',' (reserved command separators).");
        }
        validateDetail(name, "Name");
        validateDetail(category, "Category");
        validateDetail(model, "Model");
        validateDetail(location, "Location");
        if (retirementReason == null || (retiredOn == null && !retirementReason.isEmpty())
                || (retiredOn != null && (retirementReason.isBlank()
                        || retirementReason.chars().anyMatch(Character::isISOControl)))) {
            throw new IllegalArgumentException("Retirement needs a date and a single-line reason.");
        }
        this.retiredOn = retiredOn;
        this.retirementReason = retirementReason;
        this.assetId = assetId;
        this.name = name;
        this.category = category;
        this.model = model;
        this.location = location;
    }

    /** Rejects missing or multiline details before constructing a record. */
    private static void validateDetail(String value, String label) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + " must be non-empty and on one line.");
        }
    }

    public boolean isRetired() {
        return retiredOn != null;
    }

    public LocalDate getRetiredOn() {
        return retiredOn;
    }

    public String getRetirementReason() {
        return retirementReason;
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
