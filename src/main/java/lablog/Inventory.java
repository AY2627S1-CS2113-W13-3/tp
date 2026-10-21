package lablog;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores equipment units managed by LabLog.
 */
public class Inventory {
    private final List<Equipment> equipmentUnits = new ArrayList<>();

    /**
     * Adds an equipment unit to this inventory.
     *
     * @param equipment equipment unit to store
     */
    public void addEquipment(Equipment equipment) {
        assert equipment != null : "equipment must not be null";
        if (containsAssetId(equipment.getAssetId())) {
            throw new IllegalArgumentException("Equipment with asset ID " + equipment.getAssetId()
                    + " already exists.");
        }
        equipmentUnits.add(equipment);
    }

    /**
     * Checks whether this inventory already contains an equipment unit with the given asset ID.
     *
     * @param assetId asset identifier to check
     * @return true if the asset ID is already registered
     */
    public boolean containsAssetId(String assetId) {
        return equipmentUnits.stream().anyMatch(equipment -> equipment.getAssetId().equals(assetId));
    }

    /**
     * Returns an immutable snapshot of the equipment units in this inventory.
     *
     * @return equipment units currently stored
     */
    public List<Equipment> getEquipmentUnits() {
        return List.copyOf(equipmentUnits);
    }
    /** Installs records that have already passed complete validation and persistence. */
    void replaceAll(List<Equipment> replacement) {
        equipmentUnits.clear();
        equipmentUnits.addAll(replacement);
    }
}
