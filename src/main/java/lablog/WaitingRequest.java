package lablog;

/** Unallocated demand for a category and inclusive period, never a confirmed reservation. */
public record WaitingRequest(String id, String borrowerId, String category, DateWindow window) {
    public WaitingRequest {
        Reservation.requireToken(id, "Waiting request ID");
        Borrower.validateId(borrowerId);
        if (category == null || category.isBlank() || category.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Supply a nonblank category without control characters.");
        }
        category = category.strip();
        if (window == null) {
            throw new IllegalArgumentException("Supply a waiting request period.");
        }
    }
}
