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
        if (equipmentUnits.stream().anyMatch(unit -> unit.getAssetId().equals(equipment.getAssetId()))) {
            throw new IllegalArgumentException("Equipment " + equipment.getAssetId() + " already exists.");
        }
        equipmentUnits.add(equipment);
    }

    /**
     * Returns an immutable snapshot of the equipment units in this inventory.
     *
     * @return equipment units currently stored
     */
    public List<Equipment> getEquipmentUnits() {
        return List.copyOf(equipmentUnits);
    }
}
