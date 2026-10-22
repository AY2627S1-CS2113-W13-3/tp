package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
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

/** Verifies cancellation history and constraint release without deleting or partly changing bookings. */
class ReservationCancellationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    private ReservationBook book() throws IOException {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC", "Scope", "Electronics", "S1", "Lab"));
        BorrowerBook borrowers = new BorrowerBook();
        borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
        return new ReservationBook(inventory, borrowers,
                Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC));
    }

    private String run(LabLog app, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        app.run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }

    @Test
    void cancel_releasesOnlySelectedBookingAndPreservesHistory() throws Exception {
        ReservationBook book = book();
        Reservation first = book.reserve("OSC", "A0123456X", new DateWindow(TODAY, TODAY));
        Reservation other = book.reserve("OSC", "A0123456X", new DateWindow(TODAY.plusDays(1), TODAY.plusDays(1)));
        new ReservationCommands(book).execute("cancel-reservation " + first.id() + " reason/Experiment postponed");
        Reservation cancelled = book.find(first.id());
        assertTrue(cancelled.isCancelled());
        assertFalse(first.isCancelled());
        assertEquals(first.window(), cancelled.window());
        assertEquals(first.borrowerId(), cancelled.borrowerId());
        assertEquals(TODAY, cancelled.cancelledOn());
        assertEquals("Experiment postponed", cancelled.cancellationReason());
        assertEquals(2, cancelled.toHistoryEntries().size());
        book.reserve("OSC", "A0123456X", first.window());
        assertEquals(other, book.find(other.id()));
        assertThrows(IllegalArgumentException.class,
                () -> book.reserve("OSC", "A0123456X", other.window()));
        assertThrows(IllegalArgumentException.class, () -> book.cancel(first.id(), "Again"));
        assertThrows(IllegalArgumentException.class, () -> book.edit(first.id(), first.borrowerId(), first.window()));
    }

    @Test
    void cancel_badInputOrFailedSave_preservesConfirmedRecord() throws Exception {
        ReservationBook book = book();
        Reservation original = book.reserve("OSC", "A0123456X", new DateWindow(TODAY, TODAY));
        ReservationCommands commands = new ReservationCommands(book);
        for (String input : List.of("cancel-reservation MISSING reason/No longer needed",
                "cancel-reservation " + original.id(), "cancel-reservation " + original.id() + " reason/",
                "cancel-reservation " + original.id() + " reason/One reason/Two",
                "cancel-reservation " + original.id() + " reason/Bad\ttext")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input));
            assertEquals(original, book.find(original.id()));
        }
        book.setSaver(proposed -> {
            throw new IOException("Disk unavailable");
        });
        assertThrows(IOException.class, () -> book.cancel(original.id(), "Postponed"));
        assertEquals(original, book.find(original.id()));
    }

    @Test
    void run_cancellationSurvivesRestartExportAndImport(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("live.properties");
        LabLog app = LabLog.load(file);
        run(app, "add OSC n/Scope c/Electronics m/S1 l/Lab");
        run(app, "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu");
        run(app, "reserve OSC b/A0123456X from/2099-01-01 to/2099-01-02");
        String id = ApplicationSnapshot.read(file).getReservations().getFirst().id();
        assertTrue(run(app, "cancel-reservation " + id + " reason/Postponed").contains("Cancelled reservation"));
        assertTrue(run(LabLog.load(file), "history OSC").contains("Cancelled " + id + ": Postponed"));
        Path export = directory.resolve("export.properties");
        run(app, "export " + export);
        run(app, "import " + export + " --replace");
        assertTrue(ApplicationSnapshot.read(file).getReservations().getFirst().isCancelled());
        assertTrue(run(app, "reserve OSC b/A0123456X from/2099-01-01 to/2099-01-02").contains("Reserved OSC"));
    }

    @Test
    void decode_partialCancellationAndLegacySchema_respectsVersion() throws Exception {
        Equipment unit = new Equipment("OSC", "Scope", "Electronics", "S1", "Lab");
        Borrower borrower = new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu");
        Reservation item = new Reservation("R-1", "OSC", borrower.getBorrowerId(), new DateWindow(TODAY, TODAY));
        Properties records = new ApplicationSnapshot(List.of(unit), List.of(borrower), List.of(), List.of(),
                List.of(), List.of(item)).toProperties();
        records.setProperty("reservation.0.reason", "Orphan reason");
        assertThrows(IOException.class, () -> ApplicationSnapshot.decode(records));
        records.setProperty("reservation.0.reason", "");
        records.setProperty("reservation.0.cancelled", "2026-10-23");
        assertThrows(IOException.class, () -> ApplicationSnapshot.decode(records));
        records.setProperty("format", "6");
        records.remove("waiting.count");
        records.remove("reservation.0.reason");
        records.remove("reservation.0.cancelled");
        assertFalse(ApplicationSnapshot.decode(records).getReservations().getFirst().isCancelled());
    }
}
