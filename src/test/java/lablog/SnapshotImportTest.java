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
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises complete round trips, invalid imports, migration and failed-save atomicity. */
class SnapshotImportTest {
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
    void run_importAndExport_persistsReplacementAndUpdatedContacts(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("snapshot with spaces.properties");
        sample().write(source);
        Path data = directory.resolve("live.properties");
        LabLog app = LabLog.load(data);
        run(app, "add OLD n/Old c/Old m/Old l/Old");
        assertTrue(run(app, "import " + source + " --replace").contains("All current records were replaced"));
        assertFalse(run(app, "list").contains("OLD |"));
        assertTrue(run(app, "loans A0123456X").contains("1 active loan."));
        assertTrue(run(LabLog.load(data), "history OSC-001").contains("Cracked = inspect"));
        assertEquals(sample().toProperties(), ApplicationSnapshot.read(data).toProperties());
        run(app, "edit-borrower A0123456X p/92345678");
        assertEquals("92345678", ApplicationSnapshot.read(data).getBorrowers().get(0).getPhone());
        Path exported = directory.resolve("export.properties");
        assertTrue(run(app, "export " + exported).contains("Exported application data"));
        assertEquals(ApplicationSnapshot.read(data).toProperties(), ApplicationSnapshot.read(exported).toProperties());
        String saved = Files.readString(exported);
        assertTrue(run(app, "export " + exported).contains("Choose a new file"));
        assertEquals(saved, Files.readString(exported));
        assertTrue(run(app, "import " + source).contains("Use: import FILE --replace"));
    }

    @Test
    void run_failedImportSave_keepsLiveRecordsAndDestination(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("live.properties");
        LabLog app = LabLog.load(data);
        run(app, "add OLD n/Original c/Electronics m/S1 l/Lab");
        Path source = directory.resolve("import.properties");
        sample().write(source);
        Files.delete(data);
        Files.createDirectory(data);
        Files.writeString(data.resolve("keep"), "keep");
        assertTrue(run(app, "import " + source + " --replace").contains("Current records were not changed."));
        assertTrue(run(app, "list").contains("OLD | Original"));
        assertFalse(run(app, "list").contains("OSC-001 |"));
        assertEquals("keep", Files.readString(data.resolve("keep")));
        try (var files = Files.list(directory)) {
            assertEquals(2, files.count());
        }
    }

    @Test
    void load_legacyFiles_migratesOnSaveAndKeepsOldFile(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("live.properties");
        LabLogStore legacy = new LabLogStore(data);
        legacy.getInventory().addEquipment(sample().getEquipment().get(0));
        legacy.getLoans().addLoan(sample().getLoans().get(1));
        Properties legacyRecords = legacy.toProperties();
        legacyRecords.setProperty("format", "1");
        legacyRecords.remove("fault.count");
        legacy.write(legacyRecords);
        Path borrowerFile = directory.resolve("borrowers.properties");
        BorrowerBook borrowers = new BorrowerBook(borrowerFile);
        borrowers.addBorrower(sample().getBorrowers().get(0));
        String oldBorrowers = Files.readString(borrowerFile);
        LabLog app = LabLog.load(data);
        assertTrue(Files.readString(data).contains("format=1"));
        assertTrue(run(app, "edit-borrower A0123456X n/Updated").contains("Updated borrower"));
        assertEquals("Updated", ApplicationSnapshot.read(data).getBorrowers().get(0).getName());
        assertEquals(oldBorrowers, Files.readString(borrowerFile));
        Files.writeString(borrowerFile, "broken old file");
        assertTrue(run(LabLog.load(data), "loans A0123456X").contains("A0123456X - Updated"));
    }
}
