package lablog;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** Creates and lists structured reservations; legacy reservation notes remain separate history. */
public class ReservationCommands {
    private static final String USAGE = "Use: reserve ASSET_ID b/BORROWER_ID from/START to/END";
    private final ReservationBook reservations;

    public ReservationCommands(ReservationBook reservations) {
        this.reservations = reservations;
    }

    /** Dispatches exact command syntax and returns success only after the booking is saved. */
    public String execute(String input) throws IOException {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+", 3);
        if (parts.length == 1 && parts[0].equals("reservations")) {
            if (reservations.getReservations().isEmpty()) {
                return "No reservations recorded.";
            }
            StringBuilder output = new StringBuilder("Reservations (inclusive dates):");
            for (Reservation item : reservations.getReservations()) {
                output.append('\n').append(item.toHistoryEntry().getDescription())
                        .append(" | ").append(item.assetId());
            }
            return output.toString();
        }
        if (parts.length != 3 || !parts[0].equals("reserve")) {
            throw new IllegalArgumentException(USAGE);
        }
        Map<String, String> fields = BookingArguments.fields(parts[2], Set.of("b", "from", "to"));
        if (fields.size() != 3) {
            throw new IllegalArgumentException(USAGE);
        }
        Reservation item = reservations.reserve(parts[1], fields.get("b"),
                DateWindow.parse(fields.get("from"), fields.get("to")));
        return "Reserved " + item.assetId() + ". Reservation ID: " + item.id() + ".";
    }
}
