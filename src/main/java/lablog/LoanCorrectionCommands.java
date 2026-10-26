package lablog;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/** Applies explicit loan/return corrections while retaining their original immutable evidence. */
public class LoanCorrectionCommands {
    private static final String USAGE = "Use: reverse-loan EVENT_ID reason/TEXT or reverse-return EVENT_ID reason/TEXT";
    private final LoanBook loans;
    private final BookingAvailability availability;
    private final Clock clock;

    public LoanCorrectionCommands(LoanBook loans, BookingAvailability availability, Clock clock) {
        this.loans = loans;
        this.availability = availability;
        this.clock = clock;
    }

    /** Parses one exact event ID and a non-empty reason; rejected corrections leave all records untouched. */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+", 3);
        if (parts.length != 3 || !Set.of("reverse-loan", "reverse-return").contains(parts[0])) {
            throw new IllegalArgumentException(USAGE);
        }
        Map<String, String> fields;
        try {
            fields = BookingArguments.fields(parts[2], Set.of("reason"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(USAGE, exception);
        }
        LoanAuditEvent.Type original = parts[0].equals("reverse-loan")
                ? LoanAuditEvent.Type.LOAN : LoanAuditEvent.Type.RETURN;
        LoanAuditEvent correction = loans.reverse(parts[1], original, LocalDate.now(clock),
                fields.get("reason"), availability);
        return "Reversed " + original + " event " + parts[1] + " for " + correction.assetId() + ".\n"
                + "Correction event: " + correction.id() + "\nReason: " + correction.reason()
                + "\nOriginal event retained in loan-audit.";
    }
}
