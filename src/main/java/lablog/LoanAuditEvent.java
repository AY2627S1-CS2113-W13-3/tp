package lablog;

import java.time.LocalDate;
import java.util.UUID;

/** Immutable evidence of a loan/return operation, independent of later changes to its owning loan. */
public record LoanAuditEvent(String id, String loanId, LocalDate date, Type type, String assetId,
        String borrowerId, LocalDate dueDate, String faults, String relatedEventId, String reason) {
    /** Correction types retain their original event and identify it explicitly for the correction workflow. */
    public enum Type {
        LOAN, RETURN, REVERSE_LOAN, REVERSE_RETURN
    }

    /** Validates complete single-line event data; reference relationships are checked by the owning book/codec. */
    public LoanAuditEvent {
        requireToken(id);
        requireToken(loanId);
        requireToken(assetId);
        requireToken(borrowerId);
        if (date == null || type == null || dueDate == null) {
            throw new IllegalArgumentException("Audit event dates and type are required.");
        }
        requireLine(faults);
        requireLine(reason);
        if (relatedEventId == null) {
            throw new IllegalArgumentException("Audit event relation must be explicit, including when empty.");
        }
        if (type == Type.REVERSE_LOAN || type == Type.REVERSE_RETURN) {
            requireToken(relatedEventId);
            if (reason.isBlank()) {
                throw new IllegalArgumentException("A correction must explain why the original event was reversed.");
            }
        } else if (!relatedEventId.isEmpty() || !reason.isEmpty()) {
            throw new IllegalArgumentException("Only correction events may have a related event and reason.");
        }
        if (type == Type.LOAN && (dueDate.isBefore(date) || !faults.isEmpty())) {
            throw new IllegalArgumentException("A loan event requires a valid initial deadline and no return faults.");
        }
    }

    /** Captures issue-time facts, including the original deadline, rather than a mutable loan reference. */
    static LoanAuditEvent issued(Loan loan) {
        return new LoanAuditEvent("E-" + UUID.randomUUID(), loan.getId(), loan.getLoanDate(), Type.LOAN,
                loan.getAssetId(), loan.getBorrowerId(), loan.getOriginalDueDate(), "", "", "");
    }

    /** Prepares return evidence before a batch publishes any loan or audit changes. */
    static LoanAuditEvent returned(Loan loan, LocalDate date, String faults) {
        return new LoanAuditEvent("E-" + UUID.randomUUID(), loan.getId(), date, Type.RETURN,
                loan.getAssetId(), loan.getBorrowerId(), loan.getDueDate(), faults.strip(), "", "");
    }

    /** Adds a dated explanation referring to the complete original evidence rather than overwriting it. */
    static LoanAuditEvent reversed(LoanAuditEvent original, LocalDate date, String reason) {
        Type type = original.type() == Type.LOAN ? Type.REVERSE_LOAN : Type.REVERSE_RETURN;
        return new LoanAuditEvent("E-" + UUID.randomUUID(), original.loanId(), date, type, original.assetId(),
                original.borrowerId(), original.dueDate(), original.faults(), original.id(),
                reason == null ? null : reason.strip());
    }

    private static void requireToken(String value) {
        if (value == null || value.isBlank() || value.chars().anyMatch(character ->
                Character.isWhitespace(character) || Character.isISOControl(character))) {
            throw new IllegalArgumentException("Audit identifiers must be non-empty single tokens.");
        }
    }

    private static void requireLine(String value) {
        if (value == null || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Audit details must be explicit and on one line.");
        }
    }
}
