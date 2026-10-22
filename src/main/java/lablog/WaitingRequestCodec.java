package lablog;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Encodes unallocated demand separately from confirmed bookings in snapshot format 8. */
final class WaitingRequestCodec {
    private WaitingRequestCodec() {
    }

    static List<WaitingRequest> read(Properties records) {
        int count = Integer.parseInt(required(records, "waiting.count"));
        if (count < 0 || count > 100000) {
            throw new IllegalArgumentException("Waiting request count must be between 0 and 100000.");
        }
        List<WaitingRequest> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String key = "waiting." + index + ".";
            result.add(new WaitingRequest(required(records, key + "id"), required(records, key + "borrower"),
                    required(records, key + "category"),
                    DateWindow.parse(required(records, key + "from"), required(records, key + "to"))));
        }
        return result;
    }

    static void write(Properties records, List<WaitingRequest> requests) {
        records.setProperty("waiting.count", Integer.toString(requests.size()));
        for (int index = 0; index < requests.size(); index++) {
            String key = "waiting." + index + ".";
            WaitingRequest request = requests.get(index);
            records.setProperty(key + "id", request.id());
            records.setProperty(key + "borrower", request.borrowerId());
            records.setProperty(key + "category", request.category());
            records.setProperty(key + "from", request.window().start().toString());
            records.setProperty(key + "to", request.window().end().toString());
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
