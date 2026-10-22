package lablog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Stores complete booking fields in snapshot formats 6 and later, keeping display notes separate. */
final class ReservationCodec {
    private ReservationCodec() {
    }

    /** Decodes fields; the shared book subsequently validates identity and cross-record references. */
    static List<Reservation> read(Properties records) {
        int count = Integer.parseInt(required(records, "reservation.count"));
        if (count < 0 || count > 100000) {
            throw new IllegalArgumentException("Reservation count must be between 0 and 100000.");
        }
        List<Reservation> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String key = "reservation." + index + ".";
            String cancelled = !"6".equals(records.getProperty("format"))
                    ? required(records, key + "cancelled") : "";
            String reason = !"6".equals(records.getProperty("format")) ? required(records, key + "reason") : "";
            result.add(new Reservation(required(records, key + "id"), required(records, key + "asset"),
                    required(records, key + "borrower"),
                    DateWindow.parse(required(records, key + "from"), required(records, key + "to")),
                    cancelled.isEmpty() ? null : LocalDate.parse(cancelled), reason));
        }
        return result;
    }

    /** Writes domain fields rather than derived history text. */
    static void write(Properties records, List<Reservation> reservations) {
        records.setProperty("reservation.count", Integer.toString(reservations.size()));
        for (int index = 0; index < reservations.size(); index++) {
            String key = "reservation." + index + ".";
            Reservation item = reservations.get(index);
            records.setProperty(key + "id", item.id());
            records.setProperty(key + "asset", item.assetId());
            records.setProperty(key + "borrower", item.borrowerId());
            records.setProperty(key + "from", item.window().start().toString());
            records.setProperty(key + "to", item.window().end().toString());
            records.setProperty(key + "cancelled", item.isCancelled() ? item.cancelledOn().toString() : "");
            records.setProperty(key + "reason", item.cancellationReason());
        }
    }

    private static String required(Properties records, String key) {
        String value = records.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing field " + key + ".");
        }
        return value;
    }
}
