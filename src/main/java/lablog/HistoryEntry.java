package lablog;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents one equipment record in a combined history display.
 * This is a display snapshot, not a replacement for the owning feature's records.
 */
public class HistoryEntry {
    /** Identifies the feature that owns a history record. */
    public enum Type {
        LOAN, RESERVATION, FAULT, MAINTENANCE, RENEWAL, LOST
    }

    private final String assetId;
    private final LocalDate date;
    private final Type type;
    private final String description;

    /**
     * Creates an entry using the record's start, report or event date and its details.
     *
     * @param assetId equipment identifier, matched exactly and case-sensitively
     * @param date loan date, reservation start, fault report, maintenance, renewal or loss report date
     * @param type feature that owns the record
     * @param description single-line details, including status and end dates where applicable
     * @throws IllegalArgumentException if the identifier or description is blank or contains control characters
     * @throws NullPointerException if the date or type is null
     */
    public HistoryEntry(String assetId, LocalDate date, Type type, String description) {
        if (assetId == null || assetId.isBlank() || assetId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("History asset ID must not be blank or contain control characters.");
        }
        if (description == null || description.isBlank()
                || description.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("History description must be non-empty and on one line.");
        }
        this.assetId = assetId;
        this.date = Objects.requireNonNull(date);
        this.type = Objects.requireNonNull(type);
        this.description = description;
    }

    public String getAssetId() {
        return assetId;
    }

    public LocalDate getDate() {
        return date;
    }

    public Type getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }
}
