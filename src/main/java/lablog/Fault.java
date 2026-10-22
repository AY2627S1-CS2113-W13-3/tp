package lablog;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Locale;

/** An identifiable equipment fault report with severity; repairs belong to a later feature. */
public class Fault {
    /** UNSPECIFIED preserves reports whose severity has not been assessed. */
    public enum Severity {
        UNSPECIFIED, MINOR, MODERATE, SEVERE;

        /** Accepts the three documented user-facing levels, ignoring case. */
        public static Severity parse(String value) {
            if (value != null) {
                for (Severity severity : values()) {
                    if (severity != UNSPECIFIED && severity.name().equalsIgnoreCase(value)) {
                        return severity;
                    }
                }
            }
            throw new IllegalArgumentException("Severity must be minor, moderate, or severe.");
        }
    }

    private final String id;
    private final String assetId;
    private final LocalDate reportDate;
    private final String description;
    private final Severity severity;

    /** Validates persisted and newly reported faults using the same rules. */
    public Fault(String id, String assetId, LocalDate date, String description) {
        this(id, assetId, date, description, Severity.UNSPECIFIED);
    }

    /** Validates an assessed report without silently replacing missing severity. */
    public Fault(String id, String assetId, LocalDate date, String description, Severity severity) {
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
        this.severity = Objects.requireNonNull(severity);
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

    public Severity getSeverity() {
        return severity;
    }

    /** Creates the display entry from the original record, retaining its identifier. */
    public HistoryEntry toHistoryEntry() {
        return new HistoryEntry(getAssetId(), getReportDate(), HistoryEntry.Type.FAULT,
                id + " [" + severity.name().toLowerCase(Locale.ROOT) + "]: " + getDescription());
    }
}
