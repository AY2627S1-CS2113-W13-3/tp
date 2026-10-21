package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises complete round trips, invalid imports, migration and failed-save atomicity. */
class SnapshotValidationTest {
    /** Includes Unicode, returned and active loans, and every supported note type. */
    private ApplicationSnapshot sample() {
        Equipment unit = new Equipment("OSC-001", "Scope Ω", "Electronics", "S1", "Lab A");
        Borrower borrower = new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu");
        Loan returned = new Loan("OSC-001", "A0123456X", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5));
        returned.markReturned(LocalDate.of(2026, 1, 4), "Cracked = inspect: Ω");
        Loan active = new Loan("OSC-001", "A0123456X", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5));
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
    void read_invalidSnapshots_preservesLiveAndSavedRecords(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("live.properties");
        sample().write(data);
        LabLog app = LabLog.load(data);
        String saved = Files.readString(data);
        Path source = directory.resolve("invalid.properties");
        List<Properties> invalid = new ArrayList<>();
        for (String[] change : List.of(
                new String[]{"format", "99"}, new String[]{"borrower.0.phone", "123"},
                new String[]{"loan.0.borrower", "A0123457X"}, new String[]{"loan.0.asset", "UNKNOWN"},
                new String[]{"loan.0.date", "2026-02-30"}, new String[]{"loan.0.due", "2025-01-01"},
                new String[]{"loan.0.returned", "2025-01-01"}, new String[]{"loan.1.faults", "bad"},
                new String[]{"note.0.asset", "UNKNOWN"}, new String[]{"note.0.type", "LOAN"},
                new String[]{"audit.count", "1"}, new String[]{"equipment.count", "-1"},
                new String[]{"borrower.count", "100001"}, new String[]{"loan.count", "999999999999"},
                new String[]{"note.count", "0"}, new String[]{"unknown.field", "data"})) {
            Properties records = sample().toProperties();
            records.setProperty(change[0], change[1]);
            invalid.add(records);
        }
        Properties missing = sample().toProperties();
        missing.remove("borrower.0.email");
        invalid.add(missing);
        for (String domain : List.of("borrower", "equipment")) {
            Properties duplicate = sample().toProperties();
            duplicate.setProperty(domain + ".count", "2");
            for (String key : List.copyOf(duplicate.stringPropertyNames())) {
                if (key.startsWith(domain + ".0.")) {
                    duplicate.setProperty(key.replace(".0.", ".1."), duplicate.getProperty(key));
                }
            }
            invalid.add(duplicate);
        }
        Properties active = sample().toProperties();
        active.setProperty("loan.0.returned", "");
        active.setProperty("loan.0.faults", "");
        invalid.add(active);
        for (Properties records : invalid) {
            try (Writer writer = Files.newBufferedWriter(source)) {
                records.store(writer, "invalid");
            }
            assertThrows(IOException.class, () -> ApplicationSnapshot.read(source));
            assertTrue(run(app, "import " + source + " --replace").contains("Current records were not changed."));
            assertEquals(saved, Files.readString(data));
            assertTrue(run(app, "loans A0123456X").contains("1 active loan."));
        }
        for (String text : List.of("", "format=2\nformat=2\n", "format=\\uZZZZ\n")) {
            Files.writeString(source, text);
            assertThrows(IOException.class, () -> ApplicationSnapshot.read(source));
            assertTrue(run(app, "import " + source + " --replace").contains("Current records were not changed."));
            assertEquals(saved, Files.readString(data));
        }
        assertThrows(IOException.class, () -> ApplicationSnapshot.read(directory.resolve("missing")));
    }

    @Test
    void read_sizeAndEncodingLimits_preservesSource(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("oversized.properties");
        Files.write(file, new byte[10 * 1024 * 1024 + 1]);
        assertThrows(IOException.class, () -> ApplicationSnapshot.read(file));
        assertEquals(10 * 1024 * 1024 + 1, Files.size(file));
        Files.write(file, new byte[]{(byte) 0xc3, (byte) 0x28});
        assertThrows(IOException.class, () -> ApplicationSnapshot.read(file));
        assertEquals(2, Files.size(file));
        assertThrows(IOException.class, () -> ApplicationSnapshot.read(directory));
    }
}
