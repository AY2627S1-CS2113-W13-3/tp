package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

/** Verifies loss transitions, unchanged history and shared availability across all command consumers. */
class LostCommandsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T16:30:00Z"), ZoneOffset.UTC);
    private static final String USER = "A0123456X";
    private final Inventory inventory = new Inventory();
    private final LoanBook loans = new LoanBook();
    private final BorrowerBook borrowers = new BorrowerBook();
    private final FaultBook faults = new FaultBook(inventory);
    private ReservationBook bookings;
    private LostCommands commands;
    private Loan loan;

    @BeforeEach
    void seed() throws Exception {
        for (String id : List.of("OSC", "MIC", "FREE")) {
            inventory.addEquipment(new Equipment(id, id + " unit", "Electronics", "M1", "Lab"));
        }
        borrowers.addBorrower(new Borrower(USER, "Jamie", "91234567", "jamie@u.nus.edu"));
        loan = new Loan("OSC", USER, TODAY.minusDays(2), TODAY.plusDays(1));
        loans.addLoan(loan);
        bookings = new ReservationBook(inventory, borrowers, loans, faults, CLOCK);
        commands = new LostCommands(inventory, loans, CLOCK);
    }

    @Test
    void execute_success_preservesBorrowerDeadlineAndRenewalEvents() {
        loan.renew(TODAY.plusDays(2), TODAY.minusDays(1));
        List<LoanRenewal> renewals = loan.getRenewals();
        assertEquals("Marked OSC as lost on 2026-10-23. Borrower: A0123456X. "
                + "Original loan and renewal history retained.", commands.execute("  lost\tOSC  "));
        assertEquals(TODAY, loan.getLostDate());
        assertTrue(loan.isLost());
        assertTrue(loan.isActive());
        assertNull(loan.getReturnDate());
        assertEquals("", loan.getReturnFaults());
        assertEquals(USER, loan.getBorrowerId());
        assertEquals(TODAY.minusDays(2), loan.getLoanDate());
        assertEquals(TODAY.plusDays(2), loan.getDueDate());
        assertEquals(TODAY.plusDays(1), loan.getOriginalDueDate());
        assertEquals(renewals, loan.getRenewals());
        assertEquals(List.of(loan), loans.getLoans());
        assertEquals("Jamie", borrowers.getBorrower(USER).getName());
    }

    @Test
    void execute_malformedUnknownFreeAndReturnedInputs_changeNothing() {
        assertThrows(IllegalArgumentException.class, () -> commands.execute(null));
        for (String input : List.of("", "lost", "lost OSC MIC", "lost OSC d/2026-10-23", "loan OSC",
                "LOST OSC", "lost UNKNOWN", "lost osc", "lost FREE")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input), input);
            assertFalse(loan.isLost());
            assertNull(loan.getLostDate());
        }
        loan.markReturned(TODAY, "Checked");
        assertThrows(IllegalArgumentException.class, () -> commands.execute("lost OSC"));
        assertFalse(loan.isLost());
        assertEquals(TODAY, loan.getReturnDate());
        assertEquals("Checked", loan.getReturnFaults());
        assertFalse(loans.hasLostLoan("OSC"));
    }

    @Test
    void execute_duplicateActiveLoans_rejectsWithoutChoosingAnArbitraryRecord() {
        Loan duplicate = new Loan("OSC", "OTHER", TODAY, TODAY.plusDays(2));
        loans.addLoan(duplicate);
        assertThrows(IllegalArgumentException.class, () -> commands.execute("lost OSC"));
        assertFalse(loan.isLost());
        assertFalse(duplicate.isLost());
    }

    @Test
    void markLost_invalidDatesAndTransitions_preservePreviousState() {
        assertThrows(IllegalArgumentException.class, () -> loan.markLost(null));
        assertThrows(IllegalArgumentException.class, () -> loan.markLost(TODAY.minusDays(3)));
        loan.renew(TODAY.plusDays(2), TODAY);
        assertThrows(IllegalArgumentException.class, () -> loan.markLost(TODAY.minusDays(1)));
        assertNull(loan.getLostDate());
        loan.markLost(TODAY);
        assertThrows(IllegalArgumentException.class, () -> loan.markLost(TODAY.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> loan.markReturned(TODAY));
        assertThrows(IllegalArgumentException.class, () -> loan.renew(TODAY.plusDays(3), TODAY));
        assertEquals(TODAY, loan.getLostDate());
        assertNull(loan.getReturnDate());
        assertEquals(TODAY.plusDays(2), loan.getDueDate());
        Loan returned = new Loan("MIC", USER, TODAY, TODAY);
        returned.markReturned(TODAY);
        assertThrows(IllegalArgumentException.class, () -> returned.markLost(TODAY));
        assertFalse(returned.isLost());
    }

    @Test
    void execute_futureLoanRejectsAndOverdueLoanUsesLocalReportDate() {
        Loan future = new Loan("MIC", USER, TODAY.plusDays(2), TODAY.plusDays(3));
        loans.addLoan(future);
        assertThrows(IllegalArgumentException.class, () -> commands.execute("lost MIC"));
        assertFalse(future.isLost());
        Loan overdue = new Loan("FREE", USER, TODAY.minusDays(3), TODAY.minusDays(1));
        loans.addLoan(overdue);
        new LostCommands(inventory, loans, CLOCK.withZone(ZoneOffset.ofHours(8))).execute("lost FREE");
        assertEquals(TODAY.plusDays(1), overdue.getLostDate());
        assertEquals(TODAY.minusDays(1), overdue.getDueDate());
    }

    @Test
    void sharedPolicy_lossBlocksEveryFuturePeriodWithoutCancellingExistingBookings() throws Exception {
        Reservation existing = bookings.reserve("OSC", USER,
                new DateWindow(TODAY.plusDays(3), TODAY.plusDays(4)));
        commands.execute("lost OSC");
        BookingAvailability policy = bookings.getAvailability();
        DateWindow future = new DateWindow(TODAY.plusYears(1), TODAY.plusYears(1).plusDays(1));
        assertTrue(policy.conflict("OSC", future, null, loan).orElseThrow().contains("marked lost"));
        assertFalse(policy.isOperational("OSC"));
        assertTrue(policy.isOperational("FREE"));
        faults.report("MIC", TODAY, "Severe damage", Fault.Severity.SEVERE);
        assertFalse(policy.isOperational("MIC"));
        assertEquals(List.of("FREE"), policy.availableUnits(future, null).stream().map(Equipment::getAssetId).toList());
        assertThrows(IllegalArgumentException.class, () -> bookings.reserve("OSC", USER, future));
        assertThrows(IllegalArgumentException.class, () -> bookings.edit(existing.id(), null, future));
        assertEquals(List.of(existing), bookings.getReservations());
        assertFalse(existing.isCancelled());
        assertThrows(IllegalArgumentException.class, () -> new LoanCommands(inventory, loans, CLOCK, borrowers, faults)
                .execute("loan OSC b/" + USER + " d/2026-10-26"));
        assertThrows(IllegalArgumentException.class, () -> new RenewCommands(loans, policy, CLOCK)
                .execute("renew OSC d/2026-10-26"));
        assertEquals(TODAY.plusDays(1), loan.getDueDate());
        assertTrue(loan.getRenewals().isEmpty());
    }

    @Test
    void returnBatch_laterLostUnit_leavesEverySelectedLoanUnchanged() {
        Loan other = new Loan("MIC", USER, TODAY.minusDays(1), TODAY.plusDays(1));
        loans.addLoan(other);
        commands.execute("lost OSC");
        assertThrows(IllegalArgumentException.class,
                () -> new ReturnCommands(inventory, loans, CLOCK).execute("return MIC OSC"));
        assertTrue(other.isActive());
        assertNull(other.getReturnDate());
        assertTrue(loan.isLost());
        assertEquals(TODAY, loan.getLostDate());
    }

    @Test
    void waitingRequest_categoryWithOnlyLostUnits_isNotOperationalCapacity() {
        inventory.replaceAll(List.of(inventory.getEquipmentUnits().getFirst()));
        commands.execute("lost OSC");
        WaitingRequestBook waiting = new WaitingRequestBook(inventory, borrowers, faults,
                bookings.getAvailability(), CLOCK);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> waiting.request(USER, "Electronics",
                new DateWindow(TODAY.plusDays(5), TODAY.plusDays(6)))).getMessage().contains("operationally suitable"));
        assertTrue(waiting.getRequests().isEmpty());
    }

    @Test
    void run_allListingsAndHelp_showLossWithoutDroppingBorrowerOrEarlierHistory() {
        Loan overdue = new Loan("MIC", USER, TODAY.minusDays(5), TODAY.minusDays(1));
        loans.addLoan(overdue);
        loan.renew(TODAY.plusDays(2), TODAY);
        String example = new HelpCommands().execute("help lost").lines()
                .filter(line -> line.startsWith("Example: ")).findFirst().orElseThrow().substring(9)
                .replace("OSC-001", "OSC");
        LabLog app = new LabLog(inventory, loans, List::of, CLOCK, borrowers);
        String output = run(app, example + "\nlost MIC\nfind OSC\nhistory OSC\nactive-loans\nloans " + USER);
        assertFalse(output.contains("Error:"), output);
        assertTrue(output.contains("Availability: Lost"));
        assertTrue(output.contains("; LOST since 2026-10-23"));
        assertTrue(output.contains("2026-10-23 | LOST | Borrower " + USER));
        assertTrue(output.contains(" | RENEWAL | "));
        assertTrue(output.contains("Due | Overdue | Status"));
        assertTrue(output.contains("2026-10-25 | no | LOST"));
        String overdueListing = new ActiveLoanCommands(loans, CLOCK).execute("active-loans overdue");
        assertTrue(overdueListing.contains("MIC | " + USER + " | 2026-10-22 | LOST"));
        assertFalse(overdueListing.contains("OSC"));
        EquipmentListCommands listing = new EquipmentListCommands(inventory, loans, faults);
        assertTrue(listing.execute("list status/LoSt sort/availability").contains("OSC | OSC unit"));
        assertFalse(listing.execute("list status/lost").contains("FREE |"));
        assertEquals("No equipment matches the supplied filters.", listing.execute("list status/on-loan"));
        assertFalse(listing.execute("list status/available").contains("OSC |"));
        assertTrue(listing.execute("list sort/availability").indexOf("FREE |")
                < listing.execute("list sort/availability").indexOf("OSC |"));
        assertTrue(new HelpCommands().execute("help").contains("lost ASSET_ID"));
        assertTrue(new HelpCommands().execute("help lost").contains("No recovery command"));
    }

    private String run(LabLog app, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        app.run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }
}
