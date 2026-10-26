package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Verifies the domain's date invariants even when callers bypass command parsing. */
class LoanRenewalTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 23);

    @Test
    void renew_invalidEvents_leaveCurrentDeadlineAndHistoryUnchanged() {
        Loan loan = new Loan("OSC", "USER", DAY, DAY.plusDays(1));
        assertEquals(DAY.plusDays(1), loan.getOriginalDueDate());
        for (LocalDate date : List.of(DAY.minusDays(1), DAY.plusDays(4))) {
            assertThrows(IllegalArgumentException.class, () -> loan.renew(DAY.plusDays(3), date));
        }
        assertThrows(IllegalArgumentException.class, () -> loan.renew(DAY.plusDays(2), null));
        assertThrows(IllegalArgumentException.class, () -> loan.renew(null, DAY));
        assertThrows(IllegalArgumentException.class, () -> loan.renew(DAY.plusDays(1), DAY));
        assertThrows(IllegalArgumentException.class, () -> loan.renew(DAY, DAY));
        assertTrue(loan.getRenewals().isEmpty());
        assertEquals(DAY.plusDays(1), loan.getDueDate());
        loan.renew(DAY.plusDays(3), DAY.plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> loan.renew(DAY.plusDays(4), DAY));
        assertThrows(IllegalArgumentException.class, () -> loan.markReturned(DAY));
        assertTrue(loan.isActive());
        assertEquals(1, loan.getRenewals().size());
        loan.markReturned(DAY.plusDays(1), "Checked");
        assertThrows(IllegalArgumentException.class, () -> loan.renew(DAY.plusDays(4), DAY.plusDays(1)));
        assertEquals("Checked", loan.getReturnFaults());
        assertEquals(DAY.plusDays(3), loan.getDueDate());
    }

    @Test
    void event_invalidComponents_areRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LoanRenewal(null, DAY, DAY.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> new LoanRenewal(DAY, null, DAY.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> new LoanRenewal(DAY, DAY, null));
        assertThrows(IllegalArgumentException.class, () -> new LoanRenewal(DAY, DAY, DAY));
        assertThrows(IllegalArgumentException.class, () -> new LoanRenewal(DAY.plusDays(2), DAY, DAY.plusDays(1)));
        assertEquals(DAY, new LoanRenewal(DAY, DAY.minusDays(1), DAY).newDueDate());
    }
}
