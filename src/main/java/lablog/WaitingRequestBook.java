package lablog;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Records demand only when operationally suitable units are fully booked for the requested window. */
public class WaitingRequestBook {
    /** Persists the proposed collection before it becomes visible in memory. */
    @FunctionalInterface
    interface Saver {
        void save(List<WaitingRequest> proposed) throws IOException;
    }

    private final Inventory inventory;
    private final BorrowerBook borrowers;
    private final BookingAvailability availability;
    private final Clock clock;
    private final List<WaitingRequest> requests = new ArrayList<>();
    private Saver saver = proposed -> { };

    /**
     * Uses the shared availability policy for loss and fault restrictions.
     * The fault-book parameter is retained for compatibility with existing callers.
     */
    public WaitingRequestBook(Inventory inventory, BorrowerBook borrowers, FaultBook faults,
            BookingAvailability availability, Clock clock) {
        this.inventory = inventory;
        this.borrowers = borrowers;
        this.availability = availability;
        this.clock = clock;
    }

    public List<WaitingRequest> getRequests() {
        return List.copyOf(requests);
    }

    void setSaver(Saver saver) {
        this.saver = saver;
    }

    /** Validates demand against current capacity, then saves without allocating any equipment. */
    public WaitingRequest request(String borrowerId, String category, DateWindow window) throws IOException {
        WaitingRequest request = new WaitingRequest("W-" + UUID.randomUUID(), borrowerId, category, window);
        window.requireNotPast(LocalDate.now(clock));
        borrowers.getBorrower(borrowerId);
        boolean suitable = inventory.getEquipmentUnits().stream()
                .anyMatch(unit -> unit.getCategory().equalsIgnoreCase(request.category())
                        && availability.isOperational(unit.getAssetId()));
        if (!suitable) {
            throw new IllegalArgumentException("No operationally suitable equipment exists in that category.");
        }
        if (!availability.availableUnits(window, request.category()).isEmpty()) {
            throw new IllegalArgumentException("Equipment is available for that period; create a reservation instead.");
        }
        List<WaitingRequest> proposed = new ArrayList<>(requests);
        proposed.add(request);
        validate(proposed, borrowers);
        saver.save(List.copyOf(proposed));
        replaceAll(proposed);
        return request;
    }

    /** Restores historical demand even if capacity has since become available or changed category. */
    static void validate(List<WaitingRequest> proposed, BorrowerBook borrowers) {
        Set<String> ids = new HashSet<>();
        Set<String> demand = new HashSet<>();
        for (WaitingRequest request : proposed) {
            borrowers.getBorrower(request.borrowerId());
            if (!ids.add(request.id()) || !demand.add(request.borrowerId() + "\n"
                    + request.category().toLowerCase(Locale.ROOT) + "\n" + request.window())) {
                throw new IllegalArgumentException("Duplicate waiting request.");
            }
        }
    }

    /** Publishes validated records while retaining the shared book and persistence callback. */
    void replaceAll(List<WaitingRequest> replacement) {
        validate(replacement, borrowers);
        requests.clear();
        requests.addAll(replacement);
    }
}
