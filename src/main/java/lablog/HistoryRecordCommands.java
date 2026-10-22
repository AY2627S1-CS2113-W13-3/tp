package lablog;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Records structured faults and dated reservation/maintenance notes for registered equipment. */
public class HistoryRecordCommands {
    private final Inventory inventory;
    private final FaultBook faults;
    private final List<HistoryEntry> entries = new ArrayList<>();

    public HistoryRecordCommands(Inventory inventory) {
        this(inventory, new FaultBook(inventory));
    }

    /** Connects commands to the shared persistent fault records. */
    public HistoryRecordCommands(Inventory inventory, FaultBook faults) {
        this.inventory = inventory;
        this.faults = faults;
    }

    /** Validates a complete note before adding it to the history source. */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+", 4);
        if (parts.length != 4 || !parts[2].startsWith("d/") || !parts[3].startsWith("desc/")) {
            throw new IllegalArgumentException(
                    "Use: reservation|fault|maintenance ASSET_ID d/YYYY-MM-DD desc/DESCRIPTION");
        }
        HistoryEntry.Type type;
        switch (parts[0]) {
        case "reservation":
            type = HistoryEntry.Type.RESERVATION;
            break;
        case "fault":
            type = HistoryEntry.Type.FAULT;
            break;
        case "maintenance":
            type = HistoryEntry.Type.MAINTENANCE;
            break;
        default:
            throw new IllegalArgumentException("Use reservation, fault, or maintenance.");
        }
        if (inventory.getEquipmentUnits().stream().noneMatch(unit -> unit.getAssetId().equals(parts[1]))) {
            throw new IllegalArgumentException("Equipment " + parts[1] + " was not found.");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(parts[2].substring(2));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Date must be a real date in YYYY-MM-DD format.");
        }
        HistoryEntry entry = new HistoryEntry(parts[1], date, type, parts[3].substring(5).strip());
        if (type == HistoryEntry.Type.FAULT) {
            Fault fault = faults.report(entry.getAssetId(), date, entry.getDescription());
            return "Recorded fault for " + parts[1] + ". Fault ID: " + fault.getId() + ".";
        }
        entries.add(entry);
        return "Recorded " + parts[0] + " for " + parts[1] + ".";
    }

    /** Supplies only notes, excluding derived fault history, for export. */
    public List<HistoryEntry> getNotes() {
        return List.copyOf(entries);
    }

    /** Supplies an immutable snapshot to the combined history display. */
    public List<HistoryEntry> getEntries() {
        List<HistoryEntry> combined = new ArrayList<>(entries);
        faults.getFaults().stream().map(Fault::toHistoryEntry).forEach(combined::add);
        return List.copyOf(combined);
    }
}
