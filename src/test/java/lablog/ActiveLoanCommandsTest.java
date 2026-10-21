package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Checks filtering, ordering, overdue boundaries, and console dispatch. */
class ActiveLoanCommandsTest {
    @Test
    void execute_mixedLoans_listsOnlyActiveInDueOrder() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);
        LoanBook loans = new LoanBook();
        ActiveLoanCommands commands = new ActiveLoanCommands(loans, clock);
        assertEquals("No active loans.", commands.execute("active-loans"));
        Loan today = new Loan("B", "A0123456X", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14));
        Loan overdue = new Loan("A", "A0123457X", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 13));
        Loan returned = new Loan("C", "A0123456X", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 12));
        returned.markReturned(LocalDate.of(2026, 10, 12));
        loans.addLoan(today);
        loans.addLoan(returned);
        loans.addLoan(overdue);
        String expected = "Asset ID | Borrower ID | Due date | Status\n"
                + "A | A0123457X | 2026-10-13 | OVERDUE\nB | A0123456X | 2026-10-14 | On time";
        assertEquals(expected, commands.execute("active-loans"));
        assertThrows(IllegalArgumentException.class, () -> commands.execute("active-loans extra"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog(new Inventory(), loans, List::of, clock)
                .run(new Scanner("active-loans\nexit\n"), new PrintStream(output));
        assertTrue(output.toString().contains(expected));
        assertEquals(3, loans.getLoans().size());
    }
}
