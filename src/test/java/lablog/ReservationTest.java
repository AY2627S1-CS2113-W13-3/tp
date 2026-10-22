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

/** Tests booking records, date boundaries, atomic publication and complete-snapshot integration. */
class ReservationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);

    private ReservationBook book() throws IOException {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC", "Scope", "Electronics", "S1", "Lab"));
        BorrowerBook borrowers = new BorrowerBook();
        borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
        return new ReservationBook(inventory, borrowers, CLOCK);
    }

    private String run(LabLog application, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        application.run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }

    @Test
    void reserve_reorderedFieldsAndSameDay_preservesImmutableRecords() throws Exception {
        ReservationBook book = book();
        ReservationCommands commands = new ReservationCommands(book);
        assertTrue(commands.execute("reserve OSC to/2026-10-23 b/A0123456X from/2026-10-23")
                .contains("Reservation ID: R-"));
        Reservation item = book.getReservations().getFirst();
        assertEquals(item, book.find(item.id()));
        assertEquals(LocalDate.of(2026, 10, 23), item.window().end());
        assertTrue(commands.execute("reservations").contains(item.id()));
        assertThrows(UnsupportedOperationException.class, () -> book.getReservations().clear());
    }

    @Test
    void reserve_invalidInputsAndFailedSave_leaveBookUnchanged() throws Exception {
        ReservationBook book = book();
        ReservationCommands commands = new ReservationCommands(book);
        for (String input : List.of("reserve", "reservations extra", "reserve OSC b/A0123456X from/2026-10-23",
                "reserve OSC b/A0123456X from/2026-10-22 to/2026-10-24",
                "reserve OSC b/A0123456X from/2026-10-24 to/2026-10-23",
                "reserve OSC b/A0123456X from/2026-02-30 to/2026-12-01",
                "reserve UNKNOWN b/A0123456X from/2026-10-23 to/2026-10-24",
                "reserve OSC b/A0123457X from/2026-10-23 to/2026-10-24",
                "reserve OSC b/A0123456X from/2026-10-23 to/2026-10-24 b/A0123456X",
                "reserve OSC b/A0123456X from/2026-10-23 to/2026-10-24 x/ignored")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input), input);
            assertTrue(book.getReservations().isEmpty());
        }
        book.setSaver(proposed -> {
            throw new IOException("Disk unavailable");
        });
        assertThrows(IOException.class,
                () -> commands.execute("reserve OSC b/A0123456X from/2026-10-23 to/2026-10-24"));
        assertTrue(book.getReservations().isEmpty());
    }

    @Test
    void run_restartExportAndImport_preserveBookingsAndLegacyNotes(@TempDir Path directory) throws Exception {
        Path live = directory.resolve("live.properties");
        LabLog app = LabLog.load(live);
        run(app, "add OSC n/Scope c/Electronics m/S1 l/Lab");
        run(app, "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu");
        run(app, "reservation OSC d/2026-01-01 desc/Legacy note");
        assertTrue(run(app, "reserve OSC b/A0123456X from/2099-01-01 to/2099-01-02").contains("Reserved OSC"));
        ApplicationSnapshot snapshot = ApplicationSnapshot.read(live);
        String id = snapshot.getReservations().getFirst().id();
        assertEquals(1, snapshot.getNotes().size());
        LabLog restarted = LabLog.load(live);
        String history = run(restarted, "history OSC");
        assertEquals(1, history.split(id, -1).length - 1);
        assertTrue(history.contains("Legacy note"));
        Path export = directory.resolve("export.properties");
        run(restarted, "export " + export);
        assertEquals(snapshot.toProperties(), ApplicationSnapshot.read(export).toProperties());
        Path empty = directory.resolve("empty.properties");
        new ApplicationSnapshot(List.of(), List.of(), List.of(), List.of()).write(empty);
        run(app, "import " + empty + " --replace");
        assertTrue(run(app, "reservations").contains("No reservations recorded"));
        run(app, "import " + export + " --replace");
        assertTrue(run(app, "reservations").contains(id));
        assertTrue(run(app, "history OSC").contains(id));
    }

    @Test
    void decode_corruptAndLegacySnapshots_validatesWithoutDiscardingBookings(@TempDir Path directory) throws Exception {
        Path live = directory.resolve("live.properties");
        LabLog app = LabLog.load(live);
        run(app, "add OSC n/Scope c/Electronics m/S1 l/Lab");
        run(app, "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu");
        run(app, "reserve OSC b/A0123456X from/2099-01-01 to/2099-01-02");
        Properties valid = ApplicationSnapshot.read(live).toProperties();
        for (String[] change : List.of(new String[]{"reservation.0.asset", "MISSING"},
                new String[]{"reservation.0.borrower", "A0123457X"},
                new String[]{"reservation.0.to", "2098-01-01"}, new String[]{"reservation.count", "-1"},
                new String[]{"reservation.0.unknown", "discard"}, new String[]{"format", "5"})) {
            Properties broken = new Properties();
            broken.putAll(valid);
            broken.setProperty(change[0], change[1]);
            assertThrows(IOException.class, () -> ApplicationSnapshot.decode(broken));
        }
        Properties legacy = new ApplicationSnapshot(List.of(), List.of(), List.of(), List.of()).toProperties();
        legacy.setProperty("format", "5");
        legacy.remove("reservation.count");
        legacy.remove("waiting.count");
        assertTrue(ApplicationSnapshot.decode(legacy).getReservations().isEmpty());
        String saved = Files.readString(live);
        Files.delete(live);
        Files.createDirectory(live);
        Files.writeString(live.resolve("keep"), "keep");
        String output = run(app, "reserve OSC b/A0123456X from/2099-02-01 to/2099-02-02");
        assertTrue(output.contains("Reservations were not changed"));
        assertFalse(output.contains("Reserved OSC"));
        assertEquals(1, run(app, "reservations").lines().filter(line -> line.endsWith(" | OSC")).count());
        assertTrue(saved.contains("reservation.count=1"));
        assertEquals("keep", Files.readString(live.resolve("keep")));
    }
}
