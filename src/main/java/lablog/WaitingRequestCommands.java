package lablog;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** Parses waiting demand and explicitly distinguishes it from allocated reservations. */
public class WaitingRequestCommands {
    private static final String USAGE = "Use: wait-request b/USER category/CATEGORY from/START to/END";
    private final WaitingRequestBook requests;

    public WaitingRequestCommands(WaitingRequestBook requests) {
        this.requests = requests;
    }

    /** Rejects incomplete, duplicate or unknown fields before changing any records. */
    public String execute(String input) throws IOException {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+", 2);
        if (parts.length == 1 && parts[0].equals("waiting-requests")) {
            if (requests.getRequests().isEmpty()) {
                return "No waiting requests.";
            }
            StringBuilder output = new StringBuilder("WAITING requests (not confirmed reservations):");
            for (WaitingRequest request : requests.getRequests()) {
                output.append('\n').append(describe(request));
            }
            return output.toString();
        }
        if (parts.length != 2 || !parts[0].equals("wait-request")) {
            throw new IllegalArgumentException(USAGE);
        }
        Set<String> required = Set.of("b", "category", "from", "to");
        Map<String, String> fields = BookingArguments.fields(parts[1], required);
        if (!fields.keySet().equals(required)) {
            throw new IllegalArgumentException(USAGE);
        }
        WaitingRequest request = requests.request(fields.get("b"), fields.get("category"),
                DateWindow.parse(fields.get("from"), fields.get("to")));
        return "Waiting request recorded: " + describe(request) + ". No equipment allocated.";
    }

    private String describe(WaitingRequest request) {
        return request.id() + " | " + request.borrowerId() + " | " + request.category() + " | "
                + request.window().start() + " through " + request.window().end() + " | WAITING";
    }
}
