package lablog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
            List<List<String>> rows = new ArrayList<>();
            for (Reservation item : reservations.getReservations()) {
                rows.add(List.of(item.id(), item.assetId(), item.borrowerId(), item.window().start().toString(),
                        item.window().end().toString(), item.isCancelled() ? "Cancelled" : "Confirmed"));
            }
            return "Reservations (inclusive dates):\n" + TableFormatter.format(
                    List.of("Reservation ID", "Asset ID", "Borrower ID", "From", "Through", "Status"), rows);
        }
        if (parts.length == 3 && parts[0].equals("cancel-reservation")) {
            Map<String, String> fields = BookingArguments.fields(parts[2], Set.of("reason"));
            Reservation cancelled = reservations.cancel(parts[1], fields.get("reason"));
            return "Cancelled reservation " + cancelled.id() + ".";
        }
        if (parts.length == 3 && parts[0].equals("edit-reservation")) {
            Map<String, String> fields = BookingArguments.fields(parts[2], Set.of("b", "from", "to"));
            Reservation original = reservations.find(parts[1]);
            DateWindow window = DateWindow.parse(fields.getOrDefault("from", original.window().start().toString()),
                    fields.getOrDefault("to", original.window().end().toString()));
            Reservation updated = reservations.edit(original.id(),
                    fields.getOrDefault("b", original.borrowerId()), window);
            return "Updated reservation " + updated.id() + ".";
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
