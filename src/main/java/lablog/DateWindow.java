package lablog;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** An inclusive calendar-date interval; even a one-day interval occupies that whole day. */
public record DateWindow(LocalDate start, LocalDate end) {
    /** Validates restored and newly entered intervals without imposing today's date on history. */
    public DateWindow {
        if (start == null || end == null || end.isBefore(start)) {
            throw new IllegalArgumentException("End date must be on or after the start date.");
        }
    }

    /** Parses strict ISO dates and converts invalid calendar input into a recoverable command error. */
    public static DateWindow parse(String start, String end) {
        try {
            return new DateWindow(LocalDate.parse(start), LocalDate.parse(end));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Use real dates in YYYY-MM-DD format.");
        }
    }

    /** Rejects backdated new requests while allowing today and same-day requests. */
    public void requireNotPast(LocalDate today) {
        if (start.isBefore(today)) {
            throw new IllegalArgumentException("Start date must not be before today (" + today + ").");
        }
    }

    /** Inclusive endpoints mean sharing even one boundary day is a conflict. */
    public boolean overlaps(DateWindow other) {
        return !end.isBefore(other.start) && !other.end.isBefore(start);
    }
}
