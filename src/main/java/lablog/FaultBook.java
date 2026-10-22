package lablog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Owns shared fault records for history and future operational consumers. */
public class FaultBook {
    private final Inventory inventory;
    private final List<Fault> faults = new ArrayList<>();

    public FaultBook(Inventory inventory) {
        this.inventory = inventory;
    }

    /** Allocates a stable identifier and validates the entire report before insertion. */
    public Fault report(String assetId, LocalDate date, String description) {
        Fault fault = new Fault("F-" + UUID.randomUUID(), assetId, date, description);
        add(fault);
        return fault;
    }

    /** Restores a record without regenerating its ID or accepting broken references. */
    public void add(Fault fault) {
        if (!inventory.containsAssetId(fault.getAssetId())) {
            throw new IllegalArgumentException("Equipment " + fault.getAssetId() + " was not found.");
        }
        if (faults.stream().anyMatch(existing -> existing.getId().equals(fault.getId()))) {
            throw new IllegalArgumentException("Duplicate fault ID: " + fault.getId());
        }
        faults.add(fault);
    }

    /** Supplies immutable records so other features cannot bypass validation. */
    public List<Fault> getFaults() {
        return List.copyOf(faults);
    }
}
