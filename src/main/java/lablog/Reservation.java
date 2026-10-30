package lablog;

import java.time.LocalDate;
import java.util.List;

/** An immutable confirmed booking with a stable ID, registered unit and intended borrower. */
public record Reservation(String id, String assetId, String borrowerId, DateWindow window,
        LocalDate cancelledOn, String cancellationReason) {
    /** Creates a confirmed record; cancellation details are absent until a transition occurs. */
    public Reservation(String id, String assetId, String borrowerId, DateWindow window) {
        this(id, assetId, borrowerId, window, null, "");
    }

    /** Validates record fields independently of command parsing and cross-record references. */
    public Reservation {
        requireToken(id, "Reservation ID");
        requireToken(assetId, "Asset ID");
        Borrower.validateId(borrowerId);
        if (cancellationReason == null || (cancelledOn == null && !cancellationReason.isEmpty())
                || (cancelledOn != null && (cancellationReason.isBlank()
                || cancellationReason.chars().anyMatch(Character::isISOControl)))) {
            throw new IllegalArgumentException("A cancellation requires a date and non-empty single-line reason.");
        }
        if (window == null) {
            throw new IllegalArgumentException("Supply a reservation period.");
        }
    }

    /** Identifiers must remain unambiguous in commands, history and serialized records. */
    static void requireToken(String value, String label) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isWhitespace)
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + " must be a single non-empty word.");
        }
    }

    public boolean isCancelled() {
        return cancelledOn != null;
    }

    /** Retains the original booking and records one irreversible cancellation transition. */
    public Reservation cancel(LocalDate date, String reason) {
        if (isCancelled()) {
            throw new IllegalArgumentException("Reservation " + id + " is already cancelled.");
        }
        if (date == null) {
            throw new IllegalArgumentException("Supply a cancellation date.");
        }
        return new Reservation(id, assetId, borrowerId, window, date, reason);
    }

    /** Adds the cancellation event separately so its date and reason remain visible in history. */
    public List<HistoryEntry> toHistoryEntries() {
        if (!isCancelled()) {
            return List.of(toHistoryEntry());
        }
        return List.of(toHistoryEntry(), new HistoryEntry(assetId, cancelledOn, HistoryEntry.Type.RESERVATION,
                "Cancelled " + id + ": " + cancellationReason));
    }

    /** Derives display history without serializing duplicated descriptions as booking records. */
    public HistoryEntry toHistoryEntry() {
        return new HistoryEntry(assetId, window.start(), HistoryEntry.Type.RESERVATION,
                id + " for " + borrowerId + " from " + window.start() + " through " + window.end()
                        + (isCancelled() ? " (cancelled)" : " (confirmed)"));
    }
}
