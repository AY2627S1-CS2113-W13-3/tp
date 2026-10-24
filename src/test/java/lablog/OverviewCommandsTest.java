package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies daily boundaries, live state changes and read-only overview integration. */
class OverviewCommandsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);
    private static final String USER = "A0123456X";
    private final Inventory inventory = new Inventory();
    private final BorrowerBook borrowers = new BorrowerBook();
    private final LoanBook loans = new LoanBook();
    private final FaultBook faults = new FaultBook(inventory);
    private final ReservationBook reservations = new ReservationBook(inventory, borrowers, loans, faults, CLOCK);
    private final OverviewCommands commands = new OverviewCommands(reservations, loans, faults, CLOCK);

    @BeforeEach
    void seed() throws Exception {
        for (String id : List.of("A", "B", "C", "D", "E", "F")) {
            inventory.addEquipment(new Equipment(id, "Scope", "Electronics", "S1", "Lab"));
        }
        borrowers.addBorrower(new Borrower(USER, "Jamie", "91234567", "jamie@u.nus.edu"));
    }

    @Test
    void execute_emptyAndInvalidInput_showsAllSectionsWithoutInventingMaintenance() {
        String output = commands.execute(" overview ");
        assertTrue(output.contains("Operational overview for 2026-10-23"));
        assertTrue(output.contains("No reservations for today."));
        assertTrue(output.contains("No overdue loans."));
        assertTrue(output.contains("No unresolved severe faults."));
        assertTrue(output.contains("Maintenance scheduling is not supported"));
        assertThrows(IllegalArgumentException.class, () -> commands.execute(null));
        for (String input : List.of("", "Overview", "overview today", "overview d/2026-10-23")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input));
        }
        assertTrue(loans.getLoans().isEmpty());
    }

    @Test
    void execute_reservations_includesBothBoundariesAndExcludesCancelledPastFuture() {
        Reservation ending = new Reservation("R-end", "B", USER, new DateWindow(TODAY.minusDays(2), TODAY));
        Reservation starting = new Reservation("R-start", "A", USER, new DateWindow(TODAY, TODAY.plusDays(2)));
        Reservation past = new Reservation("R-past", "C", USER,
                new DateWindow(TODAY.minusDays(2), TODAY.minusDays(1)));
        Reservation future = new Reservation("R-future", "D", USER,
                new DateWindow(TODAY.plusDays(1), TODAY.plusDays(2)));
        Reservation cancelled = new Reservation("R-cancelled", "E", USER, new DateWindow(TODAY, TODAY))
                .cancel(TODAY, "Cancelled");
        Reservation sameDay = new Reservation("R-same", "F", USER, new DateWindow(TODAY, TODAY));
        List<Reservation> before = List.of(ending, starting, past, future, cancelled, sameDay);
        reservations.replaceAll(before);
        String output = commands.execute("overview");
        assertTrue(output.contains("Today's reservations (3)"));
        assertTrue(output.indexOf("R-start") < output.indexOf("R-end"));
        assertTrue(output.contains("R-same"));
        for (String id : List.of("R-past", "R-future", "R-cancelled")) {
            assertFalse(output.contains(id));
        }
        assertEquals(before, reservations.getReservations());
    }

    @Test
    void execute_overdue_excludesDueTodayReturnedAndReversedButIncludesLost() {
        Loan recent = new Loan("A", USER, TODAY.minusDays(3), TODAY.minusDays(1));
        Loan lost = new Loan("B", USER, TODAY.minusDays(5), TODAY.minusDays(2));
        lost.markLost(TODAY.minusDays(1));
        Loan dueToday = new Loan("C", USER, TODAY.minusDays(2), TODAY);
        Loan returned = new Loan("D", USER, TODAY.minusDays(3), TODAY.minusDays(1));
        Loan reversed = new Loan("E", USER, TODAY.minusDays(3), TODAY.minusDays(1));
        for (Loan loan : List.of(recent, lost, dueToday, returned, reversed)) {
            loans.addLoan(loan);
        }
        LoanAuditEvent issue = loans.getAuditEvents().getLast();
        loans.returnLoans(List.of(returned), TODAY, "");
        loans.reverse(issue.id(), LoanAuditEvent.Type.LOAN, TODAY, "Mistake", reservations.getAvailability());
        List<Loan> before = loans.getLoans();
        String output = commands.execute("overview");
        assertTrue(output.contains("Overdue loans (2)"));
        assertTrue(output.contains("LOST (overdue)"));
        assertTrue(output.indexOf(lost.getId()) < output.indexOf(recent.getId()));
        for (Loan excluded : List.of(dueToday, returned, reversed)) {
            assertFalse(output.contains(excluded.getId()));
        }
        assertEquals(before, loans.getLoans());
    }

    @Test
    void execute_faults_usesSharedUnresolvedSevereRuleAndKeepsMultipleReports() {
        Fault later = faults.report("A", TODAY.plusDays(1), "Future dated blocker", Fault.Severity.SEVERE);
        Fault earlier = faults.report("A", TODAY.minusDays(1), "Broken screen", Fault.Severity.SEVERE);
        Fault repaired = faults.report("B", TODAY.minusDays(1), "Repaired", Fault.Severity.SEVERE);
        faults.resolve(repaired.getId(), TODAY, "Done");
        faults.report("C", TODAY, "Minor scratch", Fault.Severity.MINOR);
        faults.report("D", TODAY, "Moderate defect", Fault.Severity.MODERATE);
        faults.report("E", TODAY, "Unassessed");
        String output = commands.execute("overview");
        assertTrue(output.contains("Unresolved severe faults (2)"));
        assertTrue(output.indexOf(earlier.getId()) < output.indexOf(later.getId()));
        for (String excluded : List.of(repaired.getId(), "Minor scratch", "Moderate defect", "Unassessed")) {
            assertFalse(output.contains(excluded));
        }
    }

    @Test
    void execute_liveUpdates_reflectsCancellationRenewalReturnAndRepair() throws Exception {
        Reservation booking = reservations.reserve("A", USER, new DateWindow(TODAY, TODAY));
        Loan loan = new Loan("B", USER, TODAY.minusDays(3), TODAY.minusDays(1));
        loans.addLoan(loan);
        Fault fault = faults.report("C", TODAY, "Damage", Fault.Severity.SEVERE);
        String before = commands.execute("overview");
        assertTrue(before.contains(booking.id()));
        assertTrue(before.contains(loan.getId()));
        assertTrue(before.contains(fault.getId()));
        reservations.cancel(booking.id(), "Postponed");
        loan.renew(TODAY.plusDays(1), TODAY);
        faults.resolve(fault.getId(), TODAY, "Repaired");
        String after = commands.execute("overview");
        assertTrue(after.contains("Today's reservations (0)"));
        assertTrue(after.contains("Overdue loans (0)"));
        assertTrue(after.contains("Unresolved severe faults (0)"));
        Loan another = new Loan("D", USER, TODAY.minusDays(4), TODAY.minusDays(1));
        loans.addLoan(another);
        loans.returnLoans(List.of(another), TODAY, "");
        assertFalse(commands.execute("overview").contains(another.getId()));
    }

    @Test
    void execute_midnight_usesLocalDateConsistentlyAcrossSections() {
        reservations.replaceAll(List.of(new Reservation("R-next", "A", USER,
                new DateWindow(TODAY.plusDays(1), TODAY.plusDays(1)))));
        Loan loan = new Loan("B", USER, TODAY, TODAY);
        loans.addLoan(loan);
        Clock boundary = Clock.fixed(Instant.parse("2026-10-23T16:00:00Z"), ZoneOffset.ofHours(8));
        String local = new OverviewCommands(reservations, loans, faults, boundary).execute("overview");
        assertTrue(local.contains("Operational overview for 2026-10-24"));
        assertTrue(local.contains("R-next"));
        assertTrue(local.contains(loan.getId()));
        String utc = commands.execute("overview");
        assertFalse(utc.contains("R-next"));
        assertFalse(utc.contains(loan.getId()));
    }

    @Test
    void run_restartImportAndQuery_preservesSavedBytesAndReadsReplacement(@TempDir Path directory) throws Exception {
        LocalDate today = LocalDate.now();
        Loan loan = new Loan("B", USER, today.minusDays(3), today.minusDays(1));
        Fault fault = new Fault("F-blocker", "C", today, "Damaged screen", Fault.Severity.SEVERE);
        Reservation booking = new Reservation("R-today", "A", USER, new DateWindow(today, today));
        Path file = directory.resolve("live.properties");
        new ApplicationSnapshot(inventory.getEquipmentUnits(), borrowers.getBorrowers(), List.of(loan),
                List.of(), List.of(fault), List.of(booking)).write(file);
        LabLog app = LabLog.load(file);
        String saved = Files.readString(file);
        String output = run(app, "overview");
        assertTrue(output.contains(loan.getId()));
        assertTrue(output.contains(fault.getId()));
        assertTrue(output.contains(booking.id()));
        assertEquals(saved, Files.readString(file));
        assertEquals(output, run(LabLog.load(file), "overview"));
        Path replacement = directory.resolve("empty.properties");
        new ApplicationSnapshot(List.of(), List.of(), List.of(), List.of()).write(replacement);
        assertFalse(run(app, "import " + replacement + " --replace").contains("Error:"));
        String imported = Files.readString(file);
        String empty = run(app, "overview");
        assertTrue(empty.contains("No reservations for today."));
        assertTrue(empty.contains("No overdue loans."));
        assertTrue(empty.contains("No unresolved severe faults."));
        assertEquals(imported, Files.readString(file));
        assertTrue(run(app, "overview extra\nhelp overview").contains("Error: Use: overview"));
        assertTrue(run(app, "help overview").contains("Format: overview"));
    }

    private String run(LabLog application, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        application.run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }
}
