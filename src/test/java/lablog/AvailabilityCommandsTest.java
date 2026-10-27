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
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies full-window availability, live state changes and a read-only persistent command path. */
class AvailabilityCommandsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);
    private final Inventory inventory = new Inventory();
    private final BorrowerBook borrowers = new BorrowerBook();
    private final LoanBook loans = new LoanBook();
    private final FaultBook faults = new FaultBook(inventory);
    private final ReservationBook bookings = new ReservationBook(inventory, borrowers, loans, faults, CLOCK);
    private final AvailableEquipmentCommands commands = new AvailableEquipmentCommands(bookings.getAvailability());

    private void seed() throws IOException {
        for (String id : List.of("B", "A", "C")) {
            inventory.addEquipment(new Equipment(id, "Scope", "Lab Electronics", "S1", "Lab"));
        }
        borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
    }

    @Test
    void available_filtersEntireIntervalAndReflectsCancellation() throws Exception {
        seed();
        Reservation booking = bookings.reserve("A", "A0123456X", new DateWindow(TODAY.plusDays(1), TODAY.plusDays(2)));
        String query = "available category/lab electronics to/2026-10-24 from/2026-10-23";
        String output = commands.execute(query);
        assertFalse(output.contains("A | Scope"));
        assertTrue(output.indexOf("B | Scope") < output.indexOf("C | Scope"));
        bookings.cancel(booking.id(), "Postponed");
        assertTrue(commands.execute(query).contains("A | Scope"));
        assertEquals(List.of("B", "A", "C"), inventory.getEquipmentUnits().stream()
                .map(Equipment::getAssetId).toList());
        assertThrows(UnsupportedOperationException.class,
                () -> bookings.getAvailability().availableUnits(new DateWindow(TODAY, TODAY), null).clear());
    }

    @Test
    void available_loanBoundariesAndFaultRepair_followSharedRules() throws Exception {
        seed();
        Loan loan = new Loan("A", "A0123456X", TODAY, TODAY.plusDays(1));
        loans.addLoan(loan);
        loans.addLoan(new Loan("B", "A0123456X", TODAY.minusDays(4), TODAY.minusDays(1)));
        Fault fault = faults.report("C", TODAY, "Damage", Fault.Severity.SEVERE);
        assertTrue(commands.execute("available from/2026-10-24 to/2026-10-24").contains("No equipment available"));
        loan.markReturned(TODAY);
        assertTrue(commands.execute("available from/2026-10-24 to/2026-10-24").contains("A | Scope"));
        faults.resolve(fault.getId(), TODAY, "Repaired");
        assertTrue(commands.execute("available from/2026-10-24 to/2026-10-24").contains("C | Scope"));
        assertFalse(commands.execute("available from/2099-01-01 to/2099-01-02").contains("B | Scope"));
    }

    @Test
    void available_invalidQueries_areRejectedEvenForEmptyInventory() {
        for (String input : List.of("available", "available from/2026-10-23", "available from/2026-10-23 to/2026-10-22",
                "available from/2026-10-22 to/2026-10-24", "available from/2026-02-30 to/2026-12-01",
                "available from/2026-10-23 to/2026-10-24 from/2026-10-25",
                "available from/2026-10-23 to/2026-10-24 status/free",
                "available from/2026-10-23 to/2026-10-24 category/")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input), input);
        }
        assertTrue(commands.execute("available from/2026-10-23 to/2026-10-23").contains("No equipment available"));
    }

    @Test
    void run_availableAfterRestart_leavesSavedBytesUnchanged(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("live.properties");
        LabLog app = LabLog.load(file);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        app.run(new Scanner("add OSC n/Scope c/Electronics m/S1 l/Lab\nexit\n"), new PrintStream(output));
        String saved = Files.readString(file);
        output.reset();
        LabLog.load(file).run(new Scanner("available from/2099-01-01 to/2099-01-02\nexit\n"), new PrintStream(output));
        assertTrue(output.toString().contains("OSC | Scope"));
        assertEquals(saved, Files.readString(file));
    }
}
