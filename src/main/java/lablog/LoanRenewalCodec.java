package lablog;

import java.time.LocalDate;
import java.util.Properties;

/** Stores structured renewal events within their owning loan in snapshot format 9. */
final class LoanRenewalCodec {
    private LoanRenewalCodec() {
    }

    /** Replays a validated chain from the original deadline and checks it matches the saved current deadline. */
    static Loan read(Properties records, String key, String assetId, String borrowerId,
            LocalDate loanDate, LocalDate dueDate) {
        LocalDate originalDue = LocalDate.parse(required(records, key + "originalDue"));
        if (originalDue.isBefore(loanDate)) {
            throw new IllegalArgumentException("Original due date must not precede the loan date.");
        }
        Loan loan = new Loan(assetId, borrowerId, loanDate, originalDue);
        int count = Integer.parseInt(required(records, key + "renewal.count"));
        if (count < 0 || count > 100000) {
            throw new IllegalArgumentException("Renewal count must be between 0 and 100000.");
        }
        for (int index = 0; index < count; index++) {
            String eventKey = key + "renewal." + index + ".";
            LocalDate previous = LocalDate.parse(required(records, eventKey + "previousDue"));
            if (!previous.equals(loan.getDueDate())) {
                throw new IllegalArgumentException("Renewal deadlines must form a continuous chain.");
            }
            loan.renew(LocalDate.parse(required(records, eventKey + "newDue")),
                    LocalDate.parse(required(records, eventKey + "date")));
        }
        if (!loan.getDueDate().equals(dueDate)) {
            throw new IllegalArgumentException("Current deadline must match the final renewal deadline.");
        }
        return loan;
    }

    /** Writes original and current deadlines separately so no successful change erases earlier evidence. */
    static void write(Properties records, String key, Loan loan) {
        records.setProperty(key + "originalDue", loan.getOriginalDueDate().toString());
        records.setProperty(key + "renewal.count", Integer.toString(loan.getRenewals().size()));
        int index = 0;
        for (LoanRenewal event : loan.getRenewals()) {
            String eventKey = key + "renewal." + index++ + ".";
            records.setProperty(eventKey + "date", event.date().toString());
            records.setProperty(eventKey + "previousDue", event.previousDueDate().toString());
            records.setProperty(eventKey + "newDue", event.newDueDate().toString());
        }
    }

    /** Requires explicit event fields instead of inventing missing audit information. */
    private static String required(Properties records, String key) {
        String value = records.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing field " + key + ".");
        }
        return value;
    }
}
