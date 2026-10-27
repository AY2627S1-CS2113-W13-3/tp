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

/** Checks complete explanations against shared booking rules and replacement snapshots. */
class UnavailableReasonCommandsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);
    private static final String USER = "A0123456X";
    private final Inventory inventory = new Inventory();
    private final BorrowerBook borrowers = new BorrowerBook();
    private final LoanBook loans = new LoanBook();
    private final FaultBook faults = new FaultBook(inventory);
    private final ReservationBook reservations = new ReservationBook(inventory, borrowers, loans, faults, CLOCK);
    private final BookingAvailability availability = reservations.getAvailability();
    private final UnavailableReasonCommands commands = new UnavailableReasonCommands(availability, CLOCK);

    @BeforeEach
    void seed() throws Exception {
        inventory.addEquipment(new Equipment("OSC", "Scope", "Electronics", "S1", "Lab"));
        inventory.addEquipment(new Equipment("OTHER", "Meter", "Electronics", "M1", "Lab"));
        borrowers.addBorrower(new Borrower(USER, "Jamie", "91234567", "jamie@u.nus.edu"));
    }

    @Test
    void execute_allApplicableReasons_usesSharedPolicyWithoutHidingBlockers() throws Exception {
        Reservation booking = reservations.reserve("OSC", USER, new DateWindow(TODAY.plusDays(2), TODAY.plusDays(3)));
        Loan loan = new Loan("OSC", USER, TODAY, TODAY.plusDays(1));
        loans.addLoan(loan);
        Fault first = faults.report("OSC", TODAY, "Broken screen", Fault.Severity.SEVERE);
        Fault second = faults.report("OSC", TODAY, "Damaged cable", Fault.Severity.SEVERE);
        faults.report("OTHER", TODAY, "Other damage", Fault.Severity.SEVERE);
        DateWindow window = new DateWindow(TODAY, TODAY.plusDays(3));
        List<String> reasons = availability.reasons("OSC", window, null, null);
        assertEquals(4, reasons.size());
        assertEquals(reasons.getFirst(), availability.conflict("OSC", window, null, null).orElseThrow());
        assertThrows(UnsupportedOperationException.class, reasons::clear);
        String output = commands.execute("why-unavailable OSC from/2026-10-23 to/2026-10-26");
        for (String detail : List.of(first.getId(), second.getId(), booking.id(), loan.getId(), USER,
                "Broken screen", "Damaged cable", "due 2026-10-24")) {
            assertTrue(output.contains(detail), detail);
        }
        assertFalse(output.contains("Other damage"));
        assertEquals(2, availability.reasons("OSC", window, booking.id(), loan).size());
    }

    @Test
    void execute_inclusiveDatesAndStateChanges_recomputesReasons() throws Exception {
        Reservation booking = reservations.reserve("OSC", USER, new DateWindow(TODAY, TODAY));
        assertTrue(commands.execute("why-unavailable OSC").contains(booking.id()));
        assertTrue(commands.execute("why-unavailable OSC from/2026-10-24 to/2026-10-24")
                .contains("is available"));
        reservations.cancel(booking.id(), "Cancelled");
        Fault severe = faults.report("OSC", TODAY, "Damage", Fault.Severity.SEVERE);
        faults.report("OSC", TODAY, "Scratch", Fault.Severity.MINOR);
        assertTrue(commands.execute("why-unavailable OSC").contains(severe.getId()));
        faults.resolve(severe.getId(), TODAY, "Repaired");
        assertTrue(commands.execute("why-unavailable OSC").contains("is available"));
        Loan loan = new Loan("OSC", USER, TODAY.minusDays(2), TODAY.plusDays(1));
        loans.addLoan(loan);
        loan.markReturned(TODAY);
        assertTrue(commands.execute("why-unavailable OSC").contains("returned 2026-10-23"));
        assertTrue(commands.execute("why-unavailable OSC to/2026-10-24 from/2026-10-24")
                .contains("is available"));
    }

    @Test
    void execute_overdueAndLost_blockFutureIntervals() {
        Loan loan = new Loan("OSC", USER, TODAY.minusDays(4), TODAY.minusDays(1));
        loans.addLoan(loan);
        assertTrue(commands.execute("why-unavailable OSC from/2099-01-01 to/2099-01-02")
                .contains("overdue and not returned"));
        loan.markLost(TODAY);
        assertTrue(commands.execute("why-unavailable OSC from/2099-01-01 to/2099-01-02")
                .contains("marked lost"));
        assertFalse(commands.execute("why-unavailable OTHER").contains("unavailable during"));
    }

    @Test
    void execute_invalidQueries_rejectsWithoutChangingRecords() {
        assertThrows(IllegalArgumentException.class, () -> commands.execute(null));
        for (String input : List.of("", "why-unavailable", "why-unavailable UNKNOWN", "why-unavailable osc",
                "why-unavailable OSC OTHER", "why-unavailable OSC from/2026-10-23",
                "why-unavailable OSC from/2026-10-24 to/2026-10-23",
                "why-unavailable OSC from/2026-10-22 to/2026-10-24",
                "why-unavailable OSC from/2026-02-30 to/2026-10-24",
                "why-unavailable OSC from/2026-10-23 to/2026-10-24 from/2026-10-25",
                "why-unavailable OSC from/2026-10-23 to/2026-10-24 category/Electronics")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input), input);
        }
        assertTrue(loans.getLoans().isEmpty());
        assertTrue(faults.getFaults().isEmpty());
        assertTrue(reservations.getReservations().isEmpty());
    }

    @Test
    void execute_defaultDate_usesLocalClockZone() throws Exception {
        Reservation booking = reservations.reserve("OSC", USER,
                new DateWindow(TODAY.plusDays(1), TODAY.plusDays(1)));
        Clock boundary = Clock.fixed(Instant.parse("2026-10-23T16:00:00Z"), ZoneOffset.ofHours(8));
        assertTrue(new UnavailableReasonCommands(availability, boundary).execute("why-unavailable OSC")
                .contains(booking.id()));
        assertTrue(commands.execute("why-unavailable OSC").contains("is available"));
    }

    @Test
    void run_restartAndImport_readsCurrentBooksWithoutSavingQueries(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("live.properties");
        LabLog app = LabLog.load(file);
        run(app, "add OSC n/Scope c/Electronics m/S1 l/Lab\n"
                + "add-borrower " + USER + " n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                + "reserve OSC b/" + USER + " from/2099-01-01 to/2099-01-02");
        String query = "why-unavailable OSC from/2099-01-01 to/2099-01-02";
        String saved = Files.readString(file);
        LabLog restarted = LabLog.load(file);
        assertTrue(run(restarted, query).contains("Conflicts with reservation"));
        assertEquals(saved, Files.readString(file));
        Path replacement = directory.resolve("replacement.properties");
        new ApplicationSnapshot(inventory.getEquipmentUnits(), borrowers.getBorrowers(), List.of(), List.of())
                .write(replacement);
        assertFalse(run(restarted, "import " + replacement + " --replace").contains("Error:"));
        String imported = Files.readString(file);
        assertTrue(run(restarted, query).contains("is available"));
        assertEquals(imported, Files.readString(file));
        assertTrue(run(restarted, "help why-unavailable").contains("Format: why-unavailable"));
    }

    private String run(LabLog application, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        application.run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }
}
