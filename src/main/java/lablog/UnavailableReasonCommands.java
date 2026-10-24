package lablog;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explains a registered unit's booking blockers without saving or changing shared records. */
public class UnavailableReasonCommands {
    private static final String USAGE = "Use: why-unavailable ASSET_ID [from/START to/END]";
    private final BookingAvailability availability;
    private final Clock clock;

    public UnavailableReasonCommands(BookingAvailability availability, Clock clock) {
        this.availability = availability;
        this.clock = clock;
    }

    /** Defaults to today's inclusive interval; explicit intervals use the booking query date rules. */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+", 3);
        if (parts.length < 2 || !parts[0].equals("why-unavailable")) {
            throw new IllegalArgumentException(USAGE);
        }
        LocalDate today = LocalDate.now(clock);
        DateWindow window = new DateWindow(today, today);
        if (parts.length == 3) {
            Map<String, String> fields = BookingArguments.fields(parts[2], Set.of("from", "to"));
            if (fields.size() != 2) {
                throw new IllegalArgumentException(USAGE);
            }
            window = DateWindow.parse(fields.get("from"), fields.get("to"));
            window.requireNotPast(today);
        }
        List<String> reasons = availability.reasons(parts[1], window, null, null);
        String interval = window.start() + " through " + window.end() + " (inclusive)";
        if (reasons.isEmpty()) {
            return "Equipment " + parts[1] + " is available throughout " + interval + ".";
        }
        return "Equipment " + parts[1] + " is unavailable during " + interval + ":\n- "
                + String.join("\n- ", reasons);
    }
}
