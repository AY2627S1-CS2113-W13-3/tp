package lablog;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/** Renews active loans using the live shared booking policy and a testable local clock. */
public class RenewCommands {
    private static final String USAGE = "Use: renew ASSET_ID d/YYYY-MM-DD";
    private final LoanBook loans;
    private final BookingAvailability availability;
    private final Clock clock;

    /** Connects to the same records and interval rules used by lending and reservations. */
    public RenewCommands(LoanBook loans, BookingAvailability availability, Clock clock) {
        this.loans = Objects.requireNonNull(loans);
        this.availability = Objects.requireNonNull(availability);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Validates the entire proposed period before changing the deadline or adding its renewal event. */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+");
        if (parts.length != 3 || !parts[0].equals("renew") || !parts[2].startsWith("d/")) {
            throw new IllegalArgumentException(USAGE);
        }
        LocalDate dueDate;
        try {
            dueDate = LocalDate.parse(parts[2].substring(2));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Due date must be a real date in YYYY-MM-DD format. " + USAGE);
        }
        Loan loan = loans.findActiveLoan(parts[1]).orElseThrow(() ->
                new IllegalArgumentException("No active loan found for " + parts[1] + "."));
        LocalDate today = LocalDate.now(clock);
        if (!dueDate.isAfter(loan.getDueDate()) || dueDate.isBefore(today)) {
            throw new IllegalArgumentException("New due date must be later than the current due date ("
                    + loan.getDueDate() + ") and no earlier than today (" + today + ").");
        }
        availability.requireAvailable(loan.getAssetId(), new DateWindow(loan.getLoanDate(), dueDate), null, loan);
        LocalDate previousDue = loan.getDueDate();
        loan.renew(dueDate, today);
        return "Renewed " + loan.getAssetId() + " for " + loan.getBorrowerId()
                + ". Due date changed from " + previousDue + " to " + dueDate + ".";
    }
}
