package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises complete round trips, invalid imports, migration and failed-save atomicity. */
class SnapshotExportTest {
    /** Includes Unicode, returned and active loans, and every supported note type. */
    private ApplicationSnapshot sample() {
        Equipment unit = new Equipment("OSC-001", "Scope Ω", "Electronics", "S1", "Lab A");
        Borrower borrower = new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu");
        Loan returned = new Loan("L-returned", "OSC-001", "A0123456X",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5));
        returned.markReturned(LocalDate.of(2026, 1, 4), "Cracked = inspect: Ω");
        Loan active = new Loan("L-active", "OSC-001", "A0123456X", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5));
        List<HistoryEntry> notes = new ArrayList<>();
        for (HistoryEntry.Type type : List.of(HistoryEntry.Type.RESERVATION,
                HistoryEntry.Type.FAULT, HistoryEntry.Type.MAINTENANCE)) {
            notes.add(new HistoryEntry("OSC-001", LocalDate.of(2026, 1, 5), type, "Inspect = : backslash \\"));
        }
        return new ApplicationSnapshot(List.of(unit), List.of(borrower), List.of(returned, active), notes);
    }

    /** Runs the real dispatcher, retaining the same application instance for subsequent calls. */
    private String run(LabLog app, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        app.run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }

    @Test
    void write_read_preservesAllFieldsAndEmptySnapshot(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("snapshot.properties");
        sample().write(file);
        assertEquals(sample().toProperties(), ApplicationSnapshot.read(file).toProperties());
        ApplicationSnapshot empty = new ApplicationSnapshot(List.of(), List.of(), List.of(), List.of());
        empty.write(file);
        assertEquals(empty.toProperties(), ApplicationSnapshot.read(file).toProperties());
    }

    @Test
    void run_export_preservesSessionAndExistingDestination(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("live.properties");
        LabLog app = LabLog.load(data);
        run(app, "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu");
        run(app, "add OSC-001 n/Scope c/Electronics m/S1 l/Lab");
        run(app, "fault OSC-001 d/2026-01-01 desc/Inspect");
        String saved = Files.readString(data);
        Path output = directory.resolve("with spaces.properties");
        assertTrue(run(app, "export \"" + output + "\"").contains("Exported application data"));
        assertEquals(1, ApplicationSnapshot.read(output).getFaults().size());
        assertTrue(ApplicationSnapshot.read(output).getNotes().isEmpty());
        assertEquals(1, ApplicationSnapshot.read(output).getBorrowers().size());
        assertEquals(saved, Files.readString(data));
        String exported = Files.readString(output);
        assertTrue(run(app, "export " + output).contains("Choose a new file"));
        assertEquals(exported, Files.readString(output));
        assertTrue(run(app, "export " + data).contains("Choose a new file"));
        assertFalse(run(app, "export").contains("Exported application data"));
    }
}
