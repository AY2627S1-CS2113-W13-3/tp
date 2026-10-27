package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies stable identities, self-exclusion and preservation of the last valid booking on failed edits. */
class ReservationEditTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    private ReservationBook book() throws IOException {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC", "Scope", "Electronics", "S1", "Lab"));
        BorrowerBook borrowers = new BorrowerBook();
        borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
        borrowers.addBorrower(new Borrower("A0123457X", "Alex", "92345678", "alex@u.nus.edu"));
        return new ReservationBook(inventory, borrowers,
                Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC));
    }

    private String run(LabLog app, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        app.run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }

    @Test
    void edit_partialAndReorderedFields_keepsIdentityAndOldSnapshot() throws Exception {
        ReservationBook book = book();
        Reservation original = book.reserve("OSC", "A0123456X", new DateWindow(TODAY, TODAY.plusDays(1)));
        ReservationCommands commands = new ReservationCommands(book);
        commands.execute("edit-reservation " + original.id() + " to/2026-10-25 b/A0123457X");
        Reservation updated = book.find(original.id());
        assertEquals("A0123457X", updated.borrowerId());
        assertEquals(original.assetId(), updated.assetId());
        assertEquals(original.window().start(), updated.window().start());
        assertEquals(TODAY.plusDays(2), updated.window().end());
        assertEquals("A0123456X", original.borrowerId());
        assertEquals(TODAY.plusDays(1), original.window().end());
        commands.execute("edit-reservation " + original.id() + " b/A0123457X");
        assertEquals(1, book.getReservations().size());
    }

    @Test
    void edit_invalidOrConflictingFields_preservesOriginal() throws Exception {
        ReservationBook book = book();
        Reservation original = book.reserve("OSC", "A0123456X", new DateWindow(TODAY, TODAY));
        book.reserve("OSC", "A0123456X", new DateWindow(TODAY.plusDays(2), TODAY.plusDays(3)));
        ReservationCommands commands = new ReservationCommands(book);
        for (String fields : List.of("to/2026-10-25", "b/A0123458X", "from/2026-10-22", "to/2026-10-22",
                "b/A0123457X b/A0123456X", "from/2026-02-30", "unknown/ignored", "b/")) {
            assertThrows(IllegalArgumentException.class,
                    () -> commands.execute("edit-reservation " + original.id() + " " + fields));
            assertEquals(original, book.find(original.id()));
        }
        assertThrows(IllegalArgumentException.class, () -> commands.execute("edit-reservation " + original.id()));
        assertThrows(IllegalArgumentException.class, () -> commands.execute("edit-reservation MISSING b/A0123457X"));
        book.setSaver(proposed -> {
            throw new IOException("No disk space");
        });
        assertThrows(IOException.class, () -> commands.execute("edit-reservation " + original.id() + " b/A0123457X"));
        assertEquals(original, book.find(original.id()));
    }

    @Test
    void edit_ongoingPeriod_allowsUnchangedPastStartButRejectsNewPastStart() throws Exception {
        ReservationBook book = book();
        Reservation ongoing = new Reservation("R-ongoing", "OSC", "A0123456X",
                new DateWindow(TODAY.minusDays(1), TODAY.plusDays(1)));
        book.replaceAll(List.of(ongoing));
        book.edit(ongoing.id(), "A0123457X", ongoing.window());
        assertThrows(IllegalArgumentException.class, () -> book.edit(ongoing.id(), "A0123457X",
                new DateWindow(TODAY.minusDays(2), TODAY.plusDays(1))));
    }

    @Test
    void run_editPersistsAcrossRestart(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("live.properties");
        LabLog app = LabLog.load(file);
        run(app, "add OSC n/Scope c/Electronics m/S1 l/Lab");
        run(app, "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu");
        run(app, "reserve OSC b/A0123456X from/2099-01-01 to/2099-01-02");
        String id = ApplicationSnapshot.read(file).getReservations().getFirst().id();
        assertTrue(run(app, "edit-reservation " + id + " to/2099-01-03").contains("Updated reservation " + id));
        assertEquals(LocalDate.of(2099, 1, 3),
                ApplicationSnapshot.read(file).getReservations().getFirst().window().end());
        assertTrue(run(LabLog.load(file), "history OSC").contains("through 2099-01-03"));
    }
}
