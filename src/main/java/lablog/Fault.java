package lablog;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Locale;
import java.util.List;

/** An immutable equipment fault report with its optional repair resolution. */
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
    private final LocalDate resolutionDate;
    private final String repairDetails;

    /** Validates persisted and newly reported faults using the same rules. */
    public Fault(String id, String assetId, LocalDate date, String description) {
        this(id, assetId, date, description, Severity.UNSPECIFIED);
    }

    /** Validates an assessed report without silently replacing missing severity. */
    public Fault(String id, String assetId, LocalDate date, String description, Severity severity) {
        this(id, assetId, date, description, severity, null, "");
    }

    /** Restores a complete record, rejecting partial resolutions and dates before the report. */
    public Fault(String id, String assetId, LocalDate date, String description, Severity severity,
            LocalDate resolutionDate, String repairDetails) {
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
        if (repairDetails == null || (resolutionDate == null && !repairDetails.isEmpty())
                || (resolutionDate != null && (resolutionDate.isBefore(date) || repairDetails.isBlank()
                || repairDetails.chars().anyMatch(Character::isISOControl)))) {
            throw new IllegalArgumentException("Repair details must be non-empty and on one line,"
                    + " with a resolution date on or after the fault report.");
        }
        this.resolutionDate = resolutionDate;
        this.repairDetails = repairDetails;
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

    public boolean isResolved() {
        return resolutionDate != null;
    }

    public LocalDate getResolutionDate() {
        return resolutionDate;
    }

    public String getRepairDetails() {
        return repairDetails;
    }

    /** Returns a validated replacement while preserving the original report and old snapshots. */
    public Fault resolve(LocalDate date, String details) {
        if (isResolved()) {
            throw new IllegalArgumentException("Fault " + id + " is already resolved.");
        }
        if (date == null) {
            throw new IllegalArgumentException("Supply a resolution date.");
        }
        return new Fault(id, assetId, reportDate, description, severity, date, details);
    }

    /** Keeps the original report entry and adds a separate dated repair entry after resolution. */
    public List<HistoryEntry> toHistoryEntries() {
        if (!isResolved()) {
            return List.of(toHistoryEntry());
        }
        return List.of(toHistoryEntry(), new HistoryEntry(assetId, resolutionDate, HistoryEntry.Type.MAINTENANCE,
                "Resolved fault " + id + ": " + repairDetails));
    }

    /** Creates the display entry from the original record, retaining its identifier. */
    public HistoryEntry toHistoryEntry() {
        return new HistoryEntry(getAssetId(), getReportDate(), HistoryEntry.Type.FAULT,
                id + " [" + severity.name().toLowerCase(Locale.ROOT) + "]: " + getDescription());
    }
}
