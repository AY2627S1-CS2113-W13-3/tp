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
        return report(assetId, date, description, Fault.Severity.UNSPECIFIED);
    }

    /** Records a severity assessment using the same validation as restored records. */
    public Fault report(String assetId, LocalDate date, String description, Fault.Severity severity) {
        Fault fault = new Fault("F-" + UUID.randomUUID(), assetId, date, description, severity);
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

    /** Replaces exactly one unresolved fault after validating all repair details. */
    public Fault resolve(String faultId, LocalDate date, String details) {
        for (int index = 0; index < faults.size(); index++) {
            Fault fault = faults.get(index);
            if (fault.getId().equals(faultId)) {
                Fault resolved = fault.resolve(date, details);
                faults.set(index, resolved);
                return resolved;
            }
        }
        throw new IllegalArgumentException("Fault " + faultId + " was not found.");
    }

    /** Supplies only unresolved severe reports to the operational overview. */
    public List<Fault> getUnresolvedSevereFaults() {
        return faults.stream().filter(fault -> fault.getSeverity() == Fault.Severity.SEVERE && !fault.isResolved())
                .toList();
    }

    /** Severe reports block new lending immediately, regardless of their recorded date. */
    public boolean blocksLending(String assetId) {
        return getUnresolvedSevereFaults().stream().anyMatch(fault -> fault.getAssetId().equals(assetId));
    }
}
