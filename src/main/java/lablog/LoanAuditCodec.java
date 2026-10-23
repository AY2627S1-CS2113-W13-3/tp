package lablog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Version-11 serialization and reference validation for immutable loan and return evidence. */
final class LoanAuditCodec {
    private LoanAuditCodec() {
    }

    /** Reads events in acceptance order; display sorting must never rewrite this stored order. */
    static List<LoanAuditEvent> read(Properties records, List<Loan> loans) {
        int count = Integer.parseInt(required(records, "audit.count"));
        if (count < 0 || count > 100000) {
            throw new IllegalArgumentException("Audit count must be between 0 and 100000.");
        }
        List<LoanAuditEvent> events = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String key = "audit." + index + ".";
            events.add(new LoanAuditEvent(required(records, key + "id"), required(records, key + "loan"),
                    LocalDate.parse(required(records, key + "date")),
                    LoanAuditEvent.Type.valueOf(required(records, key + "type")),
                    required(records, key + "asset"), required(records, key + "borrower"),
                    LocalDate.parse(required(records, key + "due")), required(records, key + "faults"),
                    required(records, key + "related"), required(records, key + "reason")));
        }
        validate(loans, events);
        return List.copyOf(events);
    }

    /** Writes complete typed fields, retaining original records even when a later correction refers to them. */
    static void write(Properties records, List<LoanAuditEvent> events) {
        records.setProperty("audit.count", Integer.toString(events.size()));
        int index = 0;
        for (LoanAuditEvent event : events) {
            String key = "audit." + index++ + ".";
            records.setProperty(key + "id", event.id());
            records.setProperty(key + "loan", event.loanId());
            records.setProperty(key + "date", event.date().toString());
            records.setProperty(key + "type", event.type().name());
            records.setProperty(key + "asset", event.assetId());
            records.setProperty(key + "borrower", event.borrowerId());
            records.setProperty(key + "due", event.dueDate().toString());
            records.setProperty(key + "faults", event.faults());
            records.setProperty(key + "related", event.relatedEventId());
            records.setProperty(key + "reason", event.reason());
        }
    }

    /** Rejects duplicate identities, broken ownership and invalid/forward correction references before publication. */
    static void validate(List<Loan> loans, List<LoanAuditEvent> events) {
        Map<String, Loan> byLoan = new HashMap<>();
        for (Loan loan : loans) {
            if (byLoan.putIfAbsent(loan.getId(), loan) != null) {
                throw new IllegalArgumentException("Duplicate loan ID: " + loan.getId());
            }
        }
        Map<String, LoanAuditEvent> seen = new HashMap<>();
        Set<String> issued = new HashSet<>();
        Set<String> reversed = new HashSet<>();
        Set<String> returned = new HashSet<>();
        Set<String> voided = new HashSet<>();
        for (LoanAuditEvent event : events) {
            Loan loan = byLoan.get(event.loanId());
            if (loan == null || !loan.getAssetId().equals(event.assetId())
                    || !loan.getBorrowerId().equals(event.borrowerId())
                    || event.date().isBefore(loan.getLoanDate()) || event.dueDate().isBefore(loan.getLoanDate())) {
                throw new IllegalArgumentException("Invalid audit loan reference or dates: " + event.id());
            }
            if (seen.containsKey(event.id())) {
                throw new IllegalArgumentException("Duplicate audit event ID: " + event.id());
            }
            if (event.type() == LoanAuditEvent.Type.LOAN
                    && (!issued.add(event.loanId()) || !event.date().equals(loan.getLoanDate())
                            || !event.dueDate().equals(loan.getOriginalDueDate()))) {
                throw new IllegalArgumentException("Loan event must retain the original issue date and deadline.");
            }
            if (event.type() == LoanAuditEvent.Type.RETURN
                    && (voided.contains(event.loanId()) || !returned.add(event.loanId()))) {
                throw new IllegalArgumentException(
                        "A return event must be reversed before another return is recorded.");
            }
            if (event.type() == LoanAuditEvent.Type.REVERSE_LOAN
                    || event.type() == LoanAuditEvent.Type.REVERSE_RETURN) {
                LoanAuditEvent original = seen.get(event.relatedEventId());
                LoanAuditEvent.Type expected = event.type() == LoanAuditEvent.Type.REVERSE_LOAN
                        ? LoanAuditEvent.Type.LOAN : LoanAuditEvent.Type.RETURN;
                if (original == null || original.type() != expected || !original.loanId().equals(event.loanId())
                        || event.date().isBefore(original.date()) || !event.dueDate().equals(original.dueDate())
                        || !event.faults().equals(original.faults()) || !reversed.add(original.id())) {
                    throw new IllegalArgumentException(
                            "Correction must reference one earlier matching original event.");
                }
                if (event.type() == LoanAuditEvent.Type.REVERSE_RETURN) {
                    returned.remove(event.loanId());
                } else {
                    if (returned.contains(event.loanId())) {
                        throw new IllegalArgumentException("Reverse dependent return events before reversing a loan.");
                    }
                    voided.add(event.loanId());
                }
            }
            seen.put(event.id(), event);
        }
    }

    private static String required(Properties records, String key) {
        String value = records.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing field " + key + ".");
        }
        return value;
    }
}
