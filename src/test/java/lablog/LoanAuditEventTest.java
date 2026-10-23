package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Checks immutable event fields before they can enter a ledger or a snapshot. */
class LoanAuditEventTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 23);

    @Test
    void constructor_missingOrMultilineFields_rejectsIncompleteEvidence() {
        for (int position : new int[]{0, 1, 4, 5}) {
            for (String invalid : new String[]{null, "", " ", "two words", "bad\nvalue", "bad\u0000value"}) {
                String[] fields = {"E-one", "L-one", "", "", "OSC", "USER"};
                fields[position] = invalid;
                assertThrows(IllegalArgumentException.class, () -> new LoanAuditEvent(fields[0], fields[1], DAY,
                        LoanAuditEvent.Type.LOAN, fields[4], fields[5], DAY, "", "", ""));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> event(null, LoanAuditEvent.Type.LOAN, DAY, "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> event(DAY, null, DAY, "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> event(DAY, LoanAuditEvent.Type.LOAN, null, "", "", ""));
        for (String invalid : new String[]{null, "line\nbreak"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> event(DAY, LoanAuditEvent.Type.RETURN, DAY, invalid, "", ""));
            assertThrows(IllegalArgumentException.class,
                    () -> event(DAY, LoanAuditEvent.Type.RETURN, DAY, "", "", invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> event(DAY, LoanAuditEvent.Type.LOAN, DAY, "", null, ""));
    }

    @Test
    void constructor_eventSpecificFields_requireOriginalFactsAndExplicitCorrectionReasons() {
        assertThrows(IllegalArgumentException.class,
                () -> event(DAY, LoanAuditEvent.Type.LOAN, DAY.minusDays(1), "", "", ""));
        assertThrows(IllegalArgumentException.class,
                () -> event(DAY, LoanAuditEvent.Type.LOAN, DAY, "fault", "", ""));
        assertThrows(IllegalArgumentException.class,
                () -> event(DAY, LoanAuditEvent.Type.RETURN, DAY, "", "E-other", ""));
        assertThrows(IllegalArgumentException.class,
                () -> event(DAY, LoanAuditEvent.Type.RETURN, DAY, "", "", "unexpected"));
        assertThrows(IllegalArgumentException.class,
                () -> event(DAY, LoanAuditEvent.Type.REVERSE_LOAN, DAY, "", "", "Wrong unit"));
        assertThrows(IllegalArgumentException.class,
                () -> event(DAY, LoanAuditEvent.Type.REVERSE_RETURN, DAY, "", "E-original", " "));
        assertEquals("Wrong unit", event(DAY, LoanAuditEvent.Type.REVERSE_LOAN, DAY,
                "", "E-original", "Wrong unit").reason());
        assertEquals("Damage", event(DAY, LoanAuditEvent.Type.RETURN, DAY.minusDays(1),
                "Damage", "", "").faults());
    }

    @Test
    void loanIdentity_requiresValidTokenAndSurvivesInjectedRestore() {
        for (String id : new String[]{null, "", "L-", "wrong", "L-two words", "L-bad\u0000value"}) {
            assertThrows(IllegalArgumentException.class, () -> new Loan(id, "OSC", "USER", DAY, DAY));
        }
        assertEquals("L-stable", new Loan("L-stable", "OSC", "USER", DAY, DAY).getId());
    }

    private LoanAuditEvent event(LocalDate date, LoanAuditEvent.Type type, LocalDate due,
            String faults, String related, String reason) {
        return new LoanAuditEvent("E-one", "L-one", date, type, "OSC", "USER", due, faults, related, reason);
    }
}
