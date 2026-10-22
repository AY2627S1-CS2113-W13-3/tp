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
        if (parts.length != 4 || !parts[2].startsWith("d/")) {
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
        String details = parts[3];
        Fault.Severity severity = Fault.Severity.UNSPECIFIED;
        if (type == HistoryEntry.Type.FAULT && details.startsWith("s/")) {
            String[] fields = details.split("\\s+", 2);
            severity = Fault.Severity.parse(fields[0].substring(2));
            details = fields.length == 2 ? fields[1] : "";
        }
        if (!details.startsWith("desc/") || (type == HistoryEntry.Type.FAULT
                && details.substring(5).matches("(?s).*(?<!\\S)(?:s|d|desc)/.*"))) {
            throw new IllegalArgumentException("Use fault ASSET_ID d/YYYY-MM-DD [s/minor|moderate|severe]"
                    + " desc/DESCRIPTION; put severity before the description and supply fields once.");
        }
        HistoryEntry entry = new HistoryEntry(parts[1], date, type, details.substring(5).strip());
        if (type == HistoryEntry.Type.FAULT) {
            Fault fault = faults.report(entry.getAssetId(), date, entry.getDescription(), severity);
            return "Recorded fault for " + parts[1] + ". Fault ID: " + fault.getId()
                    + ". Severity: " + severity + ".";
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
        faults.getFaults().stream().flatMap(fault -> fault.toHistoryEntries().stream()).forEach(combined::add);
        return List.copyOf(combined);
    }
}
