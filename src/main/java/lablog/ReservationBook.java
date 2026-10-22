package lablog;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Owns shared bookings and publishes validated changes only after successful persistence. */
public class ReservationBook {
    /** Persists proposed bookings together with the rest of the application snapshot. */
    @FunctionalInterface
    interface Saver {
        void save(List<Reservation> proposed) throws IOException;
    }

    private final Inventory inventory;
    private final BorrowerBook borrowers;
    private final Clock clock;
    private final List<Reservation> reservations = new ArrayList<>();
    private Saver saver = proposed -> { };

    public ReservationBook(Inventory inventory, BorrowerBook borrowers, Clock clock) {
        this.inventory = inventory;
        this.borrowers = borrowers;
        this.clock = clock;
    }

    void setSaver(Saver saver) {
        this.saver = saver;
    }

    public List<Reservation> getReservations() {
        return List.copyOf(reservations);
    }

    /** Checks identity, references and period before saving a new booking. */
    public Reservation reserve(String assetId, String borrowerId, DateWindow window) throws IOException {
        window.requireNotPast(LocalDate.now(clock));
        Reservation reservation = new Reservation("R-" + UUID.randomUUID(), assetId, borrowerId, window);
        List<Reservation> proposed = new ArrayList<>(reservations);
        proposed.add(reservation);
        save(proposed);
        return reservation;
    }

    /** Returns one stable record or an actionable error, never a silently missing result. */
    public Reservation find(String id) {
        return reservations.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Reservation " + id + " was not found."));
    }

    /** Validates a detached collection before either persistence or import publication. */
    private void validate(List<Reservation> proposed) {
        Set<String> ids = new HashSet<>();
        for (Reservation item : proposed) {
            if (!ids.add(item.id())) {
                throw new IllegalArgumentException("Duplicate reservation ID: " + item.id());
            }
            if (!inventory.containsAssetId(item.assetId())) {
                throw new IllegalArgumentException("Equipment " + item.assetId() + " was not found.");
            }
            borrowers.getBorrower(item.borrowerId());
        }
    }

    /** Keeps memory unchanged on a validation or atomic-save failure. */
    private void save(List<Reservation> proposed) throws IOException {
        validate(proposed);
        saver.save(List.copyOf(proposed));
        replaceAll(proposed);
    }

    /** Installs already persisted records without changing this book's identity or saver. */
    void replaceAll(List<Reservation> replacement) {
        validate(replacement);
        reservations.clear();
        reservations.addAll(replacement);
    }
}
