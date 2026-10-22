package lablog;

import java.time.LocalDate;
import java.util.Objects;

/** An identifiable equipment fault report; severity and repairs belong to later features. */
public class Fault {
    private final String id;
    private final String assetId;
    private final LocalDate reportDate;
    private final String description;

    /** Validates persisted and newly reported faults using the same rules. */
    public Fault(String id, String assetId, LocalDate date, String description) {
        if (id == null || id.isBlank() || id.chars().anyMatch(Character::isWhitespace)
                || id.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Fault ID must be a single non-empty word.");
        }
        this.id = id;
        if (assetId == null || assetId.isBlank() || assetId.chars().anyMatch(Character::isWhitespace)
                || assetId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Equipment ID must be a single non-empty word.");
        }
        if (description == null || description.isBlank()
                || description.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Fault description must be non-empty and on one line.");
        }
        this.assetId = assetId;
        this.reportDate = Objects.requireNonNull(date);
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getAssetId() {
        return assetId;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public String getDescription() {
        return description;
    }

    /** Creates the display entry from the original record, retaining its identifier. */
    public HistoryEntry toHistoryEntry() {
        return new HistoryEntry(getAssetId(), getReportDate(), HistoryEntry.Type.FAULT,
                id + ": " + getDescription());
    }
}
