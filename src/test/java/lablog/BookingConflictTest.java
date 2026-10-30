package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Verifies inclusive boundaries and the same conflict policy for booking, lending and renewal callers. */
class BookingConflictTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);
    private final Inventory inventory = new Inventory();
    private final BorrowerBook borrowers = new BorrowerBook();
    private final LoanBook loans = new LoanBook();
    private final FaultBook faults = new FaultBook(inventory);
    private final ReservationBook bookings = new ReservationBook(inventory, borrowers, loans, faults, CLOCK);

    private void seed() throws IOException {
        inventory.addEquipment(new Equipment("OSC", "Scope", "Electronics", "S1", "Lab"));
        inventory.addEquipment(new Equipment("OTHER", "Other", "Electronics", "S1", "Lab"));
        borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
    }

    @Test
    void reserve_inclusiveOverlapAndAdjacentDay_reportsConflictWithoutMutation() throws Exception {
        seed();
        Reservation original = bookings.reserve("OSC", "A0123456X", new DateWindow(TODAY, TODAY.plusDays(2)));
        for (DateWindow window : List.of(new DateWindow(TODAY.plusDays(2), TODAY.plusDays(3)),
                new DateWindow(TODAY, TODAY), new DateWindow(TODAY.plusDays(1), TODAY.plusDays(1)))) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> bookings.reserve("OSC", "A0123456X", window));
            assertTrue(error.getMessage().contains(original.id()));
            assertEquals(1, bookings.getReservations().size());
        }
        bookings.reserve("OSC", "A0123456X", new DateWindow(TODAY.plusDays(3), TODAY.plusDays(3)));
        bookings.reserve("OTHER", "A0123456X", new DateWindow(TODAY, TODAY));
        assertEquals(3, bookings.getReservations().size());
    }

    @Test
    void policy_activeReturnedAndOverdueLoans_usesActualOccupancy() throws Exception {
        seed();
        Loan loan = new Loan("OSC", "A0123456X", TODAY.minusDays(1), TODAY.plusDays(2));
        loans.addLoan(loan);
        assertThrows(IllegalArgumentException.class,
                () -> bookings.reserve("OSC", "A0123456X", new DateWindow(TODAY.plusDays(2), TODAY.plusDays(3))));
        assertTrue(bookings.getAvailability().conflict("OSC",
                new DateWindow(TODAY.plusDays(3), TODAY.plusDays(4)), null, null).isEmpty());
        loan.markReturned(TODAY);
        assertTrue(bookings.getAvailability().conflict("OSC",
                new DateWindow(TODAY.plusDays(1), TODAY.plusDays(1)), null, null).isEmpty());
        assertTrue(bookings.getAvailability().conflict("OSC", new DateWindow(TODAY, TODAY), null, null).isPresent());
        Loan overdue = new Loan("OTHER", "A0123456X", TODAY.minusDays(5), TODAY.minusDays(1));
        loans.addLoan(overdue);
        assertTrue(bookings.getAvailability().conflict("OTHER",
                new DateWindow(TODAY.plusYears(1), TODAY.plusYears(1)), null, null).orElseThrow().contains("overdue"));
    }

    @Test
    void policy_renewalExclusion_skipsOnlyOriginalLoanAndStillChecksReservations() throws Exception {
        seed();
        Loan original = new Loan("OSC", "A0123456X", TODAY, TODAY.plusDays(1));
        loans.addLoan(original);
        bookings.reserve("OSC", "A0123456X", new DateWindow(TODAY.plusDays(3), TODAY.plusDays(4)));
        BookingAvailability policy = bookings.getAvailability();
        policy.requireAvailable("OSC", new DateWindow(TODAY, TODAY.plusDays(2)), null, original);
        assertThrows(IllegalArgumentException.class,
                () -> policy.requireAvailable("OSC", new DateWindow(TODAY, TODAY.plusDays(3)), null, original));
        assertThrows(IllegalArgumentException.class,
                () -> policy.requireAvailable("OSC", new DateWindow(TODAY, TODAY.plusDays(2)), null,
                        new Loan("OSC", "A0123456X", TODAY, TODAY.plusDays(1))));
        faults.report("OSC", TODAY, "Damage", Fault.Severity.SEVERE);
        assertTrue(policy.conflict("OSC", new DateWindow(TODAY.plusDays(6), TODAY.plusDays(7)), null, original)
                .orElseThrow().contains("severe fault"));
    }

    @Test
    void mainConsole_loanRejectsReservationThroughSharedPolicy() throws Exception {
        seed();
        LabLog app = new LabLog(inventory, loans, List::of, CLOCK, borrowers);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        app.run(new Scanner("reserve OSC b/A0123456X from/2026-10-24 to/2026-10-25\n"
                + "loan OSC b/A0123456X d/2026-10-24\nexit\n"), new PrintStream(output));
        assertTrue(output.toString().contains("Conflicts with reservation"));
        assertTrue(loans.getLoans().isEmpty());
    }
}
