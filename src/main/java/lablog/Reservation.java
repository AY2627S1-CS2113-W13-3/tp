package lablog;

/** An immutable confirmed booking with a stable ID, registered unit and intended borrower. */
public record Reservation(String id, String assetId, String borrowerId, DateWindow window) {
    /** Validates record fields independently of command parsing and cross-record references. */
    public Reservation {
        requireToken(id, "Reservation ID");
        requireToken(assetId, "Asset ID");
        Borrower.validateId(borrowerId);
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

    /** Derives display history without serializing duplicated descriptions as booking records. */
    public HistoryEntry toHistoryEntry() {
        return new HistoryEntry(assetId, window.start(), HistoryEntry.Type.RESERVATION,
                id + " for " + borrowerId + " from " + window.start() + " through " + window.end() + " (confirmed)");
    }
}
