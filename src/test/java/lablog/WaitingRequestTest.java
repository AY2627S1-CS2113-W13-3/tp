package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers fully booked demand, invalid input, failure atomicity and persistent independent waiting state. */
class WaitingRequestTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);
    private final Inventory inventory = new Inventory();
    private final BorrowerBook borrowers = new BorrowerBook();
    private final LoanBook loans = new LoanBook();
    private final FaultBook faults = new FaultBook(inventory);
    private final ReservationBook bookings = new ReservationBook(inventory, borrowers, loans, faults, CLOCK);
    private final WaitingRequestBook waiting = new WaitingRequestBook(inventory, borrowers, faults,
            bookings.getAvailability(), CLOCK);
    private final WaitingRequestCommands commands = new WaitingRequestCommands(waiting);

    private void seed() throws IOException {
        inventory.addEquipment(new Equipment("A", "Scope", "Lab Electronics", "S1", "Lab"));
        borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
    }

    @Test
    void request_boundaryDemand_isSeparateAndNotAutomaticallyAllocated() throws Exception {
        seed();
        Reservation reservation = bookings.reserve("A", "A0123456X", new DateWindow(TODAY, TODAY.plusDays(1)));
        String result = commands.execute("wait-request to/2026-10-24 category/lab electronics"
                + " b/A0123456X from/2026-10-24");
        assertTrue(result.contains("No equipment allocated"));
        assertEquals(1, bookings.getReservations().size());
        assertTrue(loans.getLoans().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> waiting.request("A0123456X", "LAB ELECTRONICS", new DateWindow(TODAY.plusDays(1),
                        TODAY.plusDays(1))));
        bookings.cancel(reservation.id(), "Released");
        assertEquals(1, waiting.getRequests().size());
        assertTrue(commands.execute("waiting-requests").contains("not confirmed reservations"));
        assertEquals(1, bookings.getAvailability().availableUnits(new DateWindow(TODAY, TODAY), null).size());
        assertThrows(UnsupportedOperationException.class, () -> waiting.getRequests().clear());
    }

    @Test
    void request_availableUnknownFaultedAndInvalidDemand_changesNothing() throws Exception {
        seed();
        DateWindow window = new DateWindow(TODAY, TODAY);
        assertThrows(IllegalArgumentException.class, () -> waiting.request("A0123456X", "Lab Electronics", window));
        assertThrows(IllegalArgumentException.class, () -> waiting.request("A0123456X", "Unknown", window));
        faults.report("A", TODAY, "Broken", Fault.Severity.SEVERE);
        assertThrows(IllegalArgumentException.class, () -> waiting.request("A0123456X", "Lab Electronics", window));
        for (String input : List.of("wait-request", "waiting-requests extra",
                "wait-request b/A0123456X category/Lab Electronics from/2026-10-23",
                "wait-request b/A0123456X b/A0123456X category/Lab Electronics from/2026-10-23 to/2026-10-23",
                "wait-request b/A0123456X category/Lab Electronics from/2026-10-23 to/2026-10-22",
                "wait-request b/A0123456X category/Lab Electronics from/2026-02-30 to/2026-10-23",
                "wait-request b/A0123456X category/Lab Electronics from/2026-10-22 to/2026-10-23",
                "wait-request b/invalid category/Lab Electronics from/2026-10-23 to/2026-10-23")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input), input);
        }
        assertTrue(waiting.getRequests().isEmpty());
    }

    @Test
    void request_loanCapacityAndSaveFailure_preservePreviousState() throws Exception {
        seed();
        loans.addLoan(new Loan("A", "A0123456X", TODAY, TODAY.plusDays(2)));
        waiting.request("A0123456X", "Lab Electronics", new DateWindow(TODAY, TODAY));
        waiting.setSaver(proposed -> {
            throw new IOException("Disk unavailable");
        });
        assertThrows(IOException.class, () -> waiting.request("A0123456X", "Lab Electronics",
                new DateWindow(TODAY.plusDays(1), TODAY.plusDays(2))));
        assertEquals(1, waiting.getRequests().size());
        assertThrows(IllegalArgumentException.class, () -> waiting.request("A0123456X", "Lab Electronics",
                new DateWindow(TODAY.plusDays(3), TODAY.plusDays(3))));
    }

    @Test
    void snapshots_restartImportAndCapacityRelease_preserveWaiting(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("live.properties");
        Path backup = directory.resolve("backup.properties");
        String setup = "add A n/Scope c/Lab Electronics m/S1 l/Lab\n"
                + "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                + "reserve A b/A0123456X from/2099-01-01 to/2099-01-02\n"
                + "wait-request b/A0123456X category/Lab Electronics from/2099-01-02 to/2099-01-02\nexit\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LabLog.load(file).run(new Scanner(setup), new PrintStream(output));
        ApplicationSnapshot snapshot = ApplicationSnapshot.read(file);
        assertEquals(1, snapshot.getWaitingRequests().size(), output.toString());
        String id = snapshot.getWaitingRequests().getFirst().id();
        String reservationId = snapshot.getReservations().getFirst().id();
        output.reset();
        LabLog.load(file).run(new Scanner("cancel-reservation " + reservationId + " reason/Released\nexport "
                + backup + "\nexit\n"), new PrintStream(output));
        assertTrue(ApplicationSnapshot.read(backup).getReservations().getFirst().isCancelled());
        Path imported = directory.resolve("imported.properties");
        LabLog.load(imported).run(new Scanner("import " + backup + " --replace\nwaiting-requests\nexit\n"),
                new PrintStream(output));
        assertEquals(id, ApplicationSnapshot.read(imported).getWaitingRequests().getFirst().id());
        String saved = Files.readString(imported);
        LabLog.load(imported).run(new Scanner("waiting-requests\nexit\n"), new PrintStream(output));
        assertEquals(saved, Files.readString(imported));
        assertTrue(output.toString().contains("WAITING"));
    }

    @Test
    void snapshots_rejectCorruptionAndReadLegacySeven() throws Exception {
        seed();
        bookings.reserve("A", "A0123456X", new DateWindow(TODAY, TODAY));
        waiting.request("A0123456X", "Lab Electronics", new DateWindow(TODAY, TODAY));
        ApplicationSnapshot snapshot = new ApplicationSnapshot(inventory.getEquipmentUnits(), borrowers.getBorrowers(),
                loans.getLoans(), List.of(), faults.getFaults(), bookings.getReservations(), waiting.getRequests());
        for (String[] invalid : List.of(new String[]{"waiting.count", "-1"},
                new String[]{"waiting.0.borrower", "A0999999Z"}, new String[]{"waiting.0.to", "2026-10-22"},
                new String[]{"waiting.0.status", "CONFIRMED"})) {
            Properties records = snapshot.toProperties();
            records.setProperty(invalid[0], invalid[1]);
            assertThrows(IOException.class, () -> ApplicationSnapshot.decode(records));
        }
        Properties legacy = snapshot.toProperties();
        legacy.setProperty("format", "7");
        legacy.keySet().removeIf(key -> key.toString().startsWith("waiting."));
        assertTrue(ApplicationSnapshot.decode(legacy).getWaitingRequests().isEmpty());
        assertFalse(ApplicationSnapshot.decode(legacy).getReservations().isEmpty());
    }
}
