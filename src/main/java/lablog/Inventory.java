package lablog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * Finds an equipment unit by its stable asset identifier.
     *
     * @param assetId asset identifier to find
     * @return matching equipment unit
     */
    public Equipment getEquipment(String assetId) {
        return equipmentUnits.stream().filter(equipment -> equipment.getAssetId().equals(assetId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Equipment " + assetId + " not found."));
    }

    /** Replaces an existing unit while preserving its asset ID and inventory position. */
    public void updateEquipment(Equipment replacement) {
        assert replacement != null : "replacement must not be null";
        for (int index = 0; index < equipmentUnits.size(); index++) {
            if (equipmentUnits.get(index).getAssetId().equals(replacement.getAssetId())) {
                equipmentUnits.set(index, replacement);
                return;
            }
        }
        throw new IllegalArgumentException("Equipment " + replacement.getAssetId() + " not found.");
    }

    /** Validates an entire location batch before replacing any selected equipment records. */
    public void updateLocations(List<String> assetIds, String location) {
        if (assetIds == null || assetIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one equipment asset ID.");
        }
        Set<String> selectedIds = new HashSet<>();
        List<Equipment> replacements = new ArrayList<>();
        for (String assetId : assetIds) {
            if (assetId == null || assetId.isBlank() || !selectedIds.add(assetId)) {
                throw new IllegalArgumentException("Asset IDs must be non-empty and unique.");
            }
            Equipment current = getEquipment(assetId);
            replacements.add(new Equipment(assetId, current.getName(), current.getCategory(),
                    current.getModel(), location));
        }
        replacements.forEach(this::updateEquipment);
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
