package lablog;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/** Records a completed repair against one identified unresolved fault. */
public class RepairCommands {
    private static final String USAGE = "Use: repair FAULT_ID d/YYYY-MM-DD desc/DETAILS";
    private final FaultBook faults;
    private final Clock clock;

    public RepairCommands(FaultBook faults, Clock clock) {
        this.faults = Objects.requireNonNull(faults);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Validates syntax and date boundaries before replacing a shared fault record. */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+", 4);
        if (parts.length != 4 || !parts[0].equals("repair") || !parts[2].startsWith("d/")
                || !parts[3].startsWith("desc/")) {
            throw new IllegalArgumentException(USAGE);
        }
        LocalDate date;
        try {
            date = LocalDate.parse(parts[2].substring(2));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Repair date must be a real date in YYYY-MM-DD format.");
        }
        if (date.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("Repair date cannot be after today.");
        }
        String details = parts[3].substring(5).strip();
        if (details.matches("(?s).*(?<!\\S)(?:d|desc)/.*")) {
            throw new IllegalArgumentException("Supply repair fields once each. " + USAGE);
        }
        Fault resolved = faults.resolve(parts[1], date, details);
        return "Resolved fault " + resolved.getId() + " for " + resolved.getAssetId() + " on " + date + ".";
    }
}
