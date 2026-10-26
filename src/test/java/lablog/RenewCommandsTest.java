package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Checks renewal state changes, inclusive conflicts, local dates and the real console dispatcher. */
class RenewCommandsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T16:30:00Z"), ZoneOffset.UTC);
    private final Inventory inventory = new Inventory();
    private final LoanBook loans = new LoanBook();
    private final BorrowerBook borrowers = new BorrowerBook();
    private final FaultBook faults = new FaultBook(inventory);
    private ReservationBook bookings;
    private RenewCommands commands;
    private Loan loan;

    @BeforeEach
    void seed() throws Exception {
        inventory.addEquipment(new Equipment("OSC", "Scope", "Electronics", "S1", "Lab"));
        borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
        loan = new Loan("OSC", "A0123456X", TODAY.minusDays(2), TODAY.plusDays(1));
        loans.addLoan(loan);
        bookings = new ReservationBook(inventory, borrowers, loans, faults, CLOCK);
        commands = new RenewCommands(loans, bookings.getAvailability(), CLOCK);
    }

    @Test
    void execute_repeatedRenewals_preservesLoanIdentityAndPriorEvents() {
        assertEquals("Renewed OSC for A0123456X. Due date changed from 2026-10-24 to 2026-10-25.",
                commands.execute("  renew\tOSC  d/2026-10-25  "));
        List<LoanRenewal> firstSnapshot = loan.getRenewals();
        commands.execute("renew OSC d/2026-11-01");
        assertEquals(List.of(loan), loans.getLoans());
        assertEquals(TODAY.minusDays(2), loan.getLoanDate());
        assertEquals("A0123456X", loan.getBorrowerId());
        assertEquals(TODAY.plusDays(1), loan.getOriginalDueDate());
        assertEquals(List.of(new LoanRenewal(TODAY, TODAY.plusDays(1), TODAY.plusDays(2)),
                new LoanRenewal(TODAY, TODAY.plusDays(2), LocalDate.of(2026, 11, 1))), loan.getRenewals());
        assertEquals(1, firstSnapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> firstSnapshot.clear());
        assertTrue(loan.isActive());
        assertEquals("", loan.getReturnFaults());
    }

    @Test
    void execute_malformedInputAndInvalidDates_changesNothing() {
        assertThrows(IllegalArgumentException.class, () -> commands.execute(null));
        for (String input : List.of("", "renew", "loan OSC d/2026-10-25", "renew OSC",
                "renew OSC due/2026-10-25", "renew OSC d/", "renew OSC d/2026-02-30",
                "renew OSC d/not-a-date", "renew OSC d/2026-10-25 d/2026-10-26",
                "renew OSC OTHER d/2026-10-25", "renew OSC d/2026-10-25 extra",
                "renew OSC d/2026-10-24", "renew OSC d/2026-10-23", "renew OSC d/2026-10-22")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input), input);
            assertEquals(TODAY.plusDays(1), loan.getDueDate());
            assertTrue(loan.getRenewals().isEmpty());
        }
    }

    @Test
    void execute_unknownWrongCaseAndReturnedUnits_rejectsWithoutReopening() {
        for (String id : List.of("UNKNOWN", "osc")) {
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> commands.execute("renew " + id + " d/2026-10-25")).getMessage()
                    .contains("No active loan"));
        }
        loan.markReturned(TODAY, "cracked screen");
        assertThrows(IllegalArgumentException.class, () -> commands.execute("renew OSC d/2026-10-25"));
        assertFalse(loan.isActive());
        assertEquals(TODAY, loan.getReturnDate());
        assertEquals("cracked screen", loan.getReturnFaults());
        assertTrue(loan.getRenewals().isEmpty());
    }

    @Test
    void execute_reservationEndpointAndSevereFault_rejectUntilConflictIsResolved() throws Exception {
        Reservation booking = bookings.reserve("OSC", "A0123456X",
                new DateWindow(TODAY.plusDays(3), TODAY.plusDays(4)));
        assertThrows(IllegalArgumentException.class, () -> commands.execute("renew OSC d/2026-10-26"));
        assertEquals(TODAY.plusDays(1), loan.getDueDate());
        assertTrue(loan.getRenewals().isEmpty());
        commands.execute("renew OSC d/2026-10-25");
        bookings.cancel(booking.id(), "No longer needed");
        Fault fault = faults.report("OSC", TODAY, "Broken probe", Fault.Severity.SEVERE);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> commands.execute("renew OSC d/2026-10-26")).getMessage().contains("severe fault"));
        assertEquals(1, loan.getRenewals().size());
        faults.resolve(fault.getId(), TODAY, "Replaced probe");
        commands.execute("renew OSC d/2026-10-26");
        assertEquals(2, loan.getRenewals().size());
        assertEquals(TODAY.plusDays(3), loan.getDueDate());
    }

    @Test
    void execute_excludesOnlyTheActualLoan_otherLoanRecordsStillConflict() {
        Loan historical = new Loan("OSC", "A0123456X", TODAY.minusDays(5), TODAY.minusDays(3));
        historical.markReturned(TODAY.minusDays(3));
        loans.addLoan(historical);
        commands.execute("renew OSC d/2026-10-25");
        Loan conflicting = new Loan("OSC", "OTHER", TODAY.plusDays(3), TODAY.plusDays(4));
        loans.addLoan(conflicting);
        assertThrows(IllegalArgumentException.class, () -> commands.execute("renew OSC d/2026-10-26"));
        assertEquals(TODAY.plusDays(2), loan.getDueDate());
        assertEquals(1, loan.getRenewals().size());
        assertEquals(TODAY.minusDays(3), historical.getReturnDate());
    }

    @Test
    void execute_overdueLoans_canRenewToLocalTodayButNotYesterday() {
        Loan overdue = new Loan("OSC", "A0123456X", TODAY.minusDays(3), TODAY.minusDays(2));
        loans.replaceAll(List.of(overdue));
        Clock localClock = CLOCK.withZone(ZoneOffset.ofHours(8));
        RenewCommands localCommands = new RenewCommands(loans, bookings.getAvailability(), localClock);
        assertThrows(IllegalArgumentException.class, () -> localCommands.execute("renew OSC d/2026-10-23"));
        assertEquals(TODAY.minusDays(2), overdue.getDueDate());
        assertTrue(overdue.getRenewals().isEmpty());
        localCommands.execute("renew OSC d/2026-10-24");
        assertEquals(TODAY.plusDays(1), overdue.getRenewals().getFirst().date());
        assertEquals(TODAY.plusDays(1), overdue.getDueDate());
    }

    @Test
    void run_renewHelpAndHistory_showCurrentDeadlineAndOrderedChanges() {
        LabLog app = new LabLog(inventory, loans, List::of, CLOCK, borrowers);
        String example = new HelpCommands().execute("help renew").lines()
                .filter(line -> line.startsWith("Example: ")).findFirst().orElseThrow().substring(9)
                .replace("OSC-001", "OSC");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        app.run(new Scanner("renew OSC d/2026-10-25\n" + example
                + "\nhistory OSC\nactive-loans\nloans A0123456X\nhelp renew\nexit\n"), new PrintStream(output));
        String text = output.toString();
        assertFalse(text.contains("Error:"), text);
        assertTrue(text.contains("due 2099-11-05; active"));
        assertTrue(text.contains("2026-10-23 | RENEWAL | Borrower A0123456X; due changed from"));
        assertTrue(text.indexOf("due changed from 2026-10-24 to 2026-10-25")
                < text.indexOf("due changed from 2026-10-25 to 2099-11-05"));
        assertTrue(text.contains("Format: renew ASSET_ID d/YYYY-MM-DD"));
        assertTrue(new HelpCommands().execute("help").contains("renew ASSET_ID d/DUE_DATE"));
        assertEquals(2, loan.getRenewals().size());
    }
}
