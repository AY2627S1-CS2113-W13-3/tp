package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies damaged complete snapshots never fall back to lossy legacy migration. */
class StartupValidationTest {
    /** Includes records the legacy equipment-only loader would silently discard. */
    private Properties records() throws IOException {
        Equipment unit = new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab");
        Borrower borrower = new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu");
        HistoryEntry note = new HistoryEntry("OSC-001", LocalDate.of(2026, 1, 1),
                HistoryEntry.Type.MAINTENANCE, "Calibrated");
        return new ApplicationSnapshot(List.of(unit), List.of(borrower), List.of(), List.of(note)).toProperties();
    }

    @Test
    void load_missingBorrowerCountInCompleteFormats_rejectsWithoutChangingEitherFile(@TempDir Path directory)
            throws Exception {
        Path data = directory.resolve("lablog.properties");
        Path sibling = directory.resolve("borrowers.properties");
        BorrowerBook oldBorrowers = new BorrowerBook(sibling);
        oldBorrowers.addBorrower(new Borrower("A7654321X", "Other", "81234567", "other@u.nus.edu"));
        String savedSibling = Files.readString(sibling);
        for (String format : List.of("2", "3", "4", "5", "6", "7", "8", "9", "10", "11")) {
            Properties incomplete = records();
            incomplete.setProperty("format", format);
            incomplete.remove("borrower.count");
            try (Writer writer = Files.newBufferedWriter(data)) {
                incomplete.store(writer, "damaged complete snapshot");
            }
            String saved = Files.readString(data);
            IOException error = assertThrows(IOException.class, () -> LabLog.load(data), "format " + format);
            assertTrue(error.getMessage().contains("borrower.count"));
            assertEquals(saved, Files.readString(data));
            assertEquals(savedSibling, Files.readString(sibling));
        }
    }

    @Test
    void load_missingRequiredFieldsOrUnsupportedVersion_neverMigrates(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("lablog.properties");
        for (String field : List.of("format", "equipment.count", "loan.count", "borrower.count", "note.count",
                "fault.count", "reservation.count", "waiting.count", "audit.count")) {
            Properties incomplete = records();
            incomplete.remove(field);
            try (Writer writer = Files.newBufferedWriter(data)) {
                incomplete.store(writer, "missing field");
            }
            String saved = Files.readString(data);
            assertThrows(IOException.class, () -> LabLog.load(data), field);
            assertEquals(saved, Files.readString(data));
        }
        for (String format : List.of("0", "12", "future")) {
            Properties unsupported = records();
            unsupported.setProperty("format", format);
            unsupported.remove("borrower.count");
            try (Writer writer = Files.newBufferedWriter(data)) {
                unsupported.store(writer, "unsupported version");
            }
            String saved = Files.readString(data);
            assertThrows(IOException.class, () -> LabLog.load(data));
            assertEquals(saved, Files.readString(data));
        }
    }
}
