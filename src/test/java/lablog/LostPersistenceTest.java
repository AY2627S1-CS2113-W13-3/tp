package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies loss reports and renewal history survive storage boundaries without partial or ambiguous restores. */
class LostPersistenceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 23);
    private static final String USER = "A0123456X";

    private ApplicationSnapshot sample() {
        Loan lost = new Loan("OSC", USER, DAY.minusDays(2), DAY.plusDays(1));
        lost.renew(DAY.plusDays(2), DAY.minusDays(1));
        lost.markLost(DAY);
        Loan returned = new Loan("MIC", USER, DAY.minusDays(3), DAY.minusDays(1));
        returned.markReturned(DAY.minusDays(1), "Checked");
        return new ApplicationSnapshot(List.of(new Equipment("OSC", "Scope", "Electronics", "S1", "Lab"),
                new Equipment("MIC", "Microphone", "Audio", "M1", "Lab")),
                List.of(new Borrower(USER, "Jamie", "91234567", "jamie@u.nus.edu")),
                List.of(lost, returned), List.of());
    }

    private String run(LabLog app, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        app.run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }

    @Test
    void snapshot_restartExportAndImport_preserveLossBorrowerAndRenewals(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("records.properties");
        sample().write(data);
        assertEquals("10", ApplicationSnapshot.readProperties(data).getProperty("format"));
        Loan loaded = ApplicationSnapshot.read(data).getLoans().getFirst();
        assertTrue(loaded.isLost());
        assertTrue(loaded.isActive());
        assertEquals(DAY, loaded.getLostDate());
        assertEquals(USER, loaded.getBorrowerId());
        assertEquals(DAY.minusDays(2), loaded.getLoanDate());
        assertEquals(DAY.plusDays(1), loaded.getOriginalDueDate());
        assertEquals(sample().getLoans().getFirst().getRenewals(), loaded.getRenewals());
        Path backup = directory.resolve("backup.properties");
        assertFalse(run(LabLog.load(data), "export " + backup).contains("Error:"));
        Path imported = directory.resolve("imported.properties");
        String output = run(LabLog.load(imported), "import " + backup + " --replace\nhistory OSC\nloans " + USER);
        assertFalse(output.contains("Error:"), output);
        assertTrue(output.contains(" | LOST | Borrower " + USER));
        assertTrue(output.contains(" | RENEWAL | "));
        ApplicationSnapshot restored = ApplicationSnapshot.read(imported);
        assertEquals(DAY, restored.getLoans().getFirst().getLostDate());
        assertEquals(sample().getLoans().getFirst().getRenewals(), restored.getLoans().getFirst().getRenewals());
        assertEquals("Checked", restored.getLoans().getLast().getReturnFaults());
        byte[] before = Files.readAllBytes(imported);
        assertTrue(run(LabLog.load(imported), "find OSC\nlist status/lost\nactive-loans").contains("LOST"));
        assertTrue(Arrays.equals(before, Files.readAllBytes(imported)));
    }

    @Test
    void run_lossPersistsAndEveryRejectedTransition_preservesSavedBytes(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("records.properties");
        LocalDate today = LocalDate.now();
        String setup = "add OSC n/Scope c/Electronics m/S1 l/Lab\n"
                + "add MIC n/Microphone c/Audio m/M1 l/Lab\n"
                + "add-borrower " + USER + " n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                + "loan OSC b/" + USER + " d/" + today.plusDays(1) + "\n"
                + "loan MIC b/" + USER + " d/" + today.plusDays(1) + "\n"
                + "renew OSC d/" + today.plusDays(2) + "\nlost OSC";
        String success = run(LabLog.load(data), setup);
        assertFalse(success.contains("Error:"), success);
        assertTrue(success.contains("Marked OSC as lost on "));
        assertEquals(today, ApplicationSnapshot.read(data).getLoans().getFirst().getLostDate());
        byte[] before = Files.readAllBytes(data);
        for (String input : List.of("lost OSC", "lost UNKNOWN", "lost OSC MIC", "return OSC", "return MIC OSC",
                "renew OSC d/" + today.plusDays(3), "loan OSC b/" + USER + " d/" + today.plusDays(3),
                "reserve OSC b/" + USER + " from/" + today.plusYears(1) + " to/" + today.plusYears(1))) {
            String output = run(LabLog.load(data), input);
            assertTrue(output.contains("Error:"), input + "\n" + output);
            assertTrue(Arrays.equals(before, Files.readAllBytes(data)), input);
        }
        ApplicationSnapshot saved = ApplicationSnapshot.read(data);
        assertEquals(1, saved.getLoans().getFirst().getRenewals().size());
        assertTrue(saved.getLoans().getLast().isActive());
        assertFalse(saved.getLoans().getLast().isLost());
        String available = run(LabLog.load(data), "available from/" + today.plusYears(1) + " to/" + today.plusYears(1));
        assertFalse(available.contains("OSC |"));
        assertTrue(available.contains("MIC |"));
    }

    @Test
    void snapshot_invalidLossData_rejectsBeforeImportOrOverwrite(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("live.properties");
        sample().write(data);
        String saved = Files.readString(data);
        LabLog app = LabLog.load(data);
        Path source = directory.resolve("invalid.properties");
        for (String[] change : List.of(new String[]{"loan.0.lost", "not-a-date"},
                new String[]{"loan.0.lost", "2026-02-30"}, new String[]{"loan.0.lost", "2026-10-20"},
                new String[]{"loan.0.lost", "2026-10-21"}, new String[]{"loan.0.returned", "2026-10-23"},
                new String[]{"loan.1.lost", "2026-10-23"}, new String[]{"loan.0.faults", "Not a return"},
                new String[]{"loan.0.lost.reason", "unsupported"})) {
            Properties records = sample().toProperties();
            records.setProperty(change[0], change[1]);
            try (Writer writer = Files.newBufferedWriter(source)) {
                records.store(writer, "invalid loss");
            }
            String original = Files.readString(source);
            assertThrows(IOException.class, () -> ApplicationSnapshot.read(source), change[0]);
            assertTrue(run(app, "import " + source + " --replace").contains("Current records were not changed."));
            assertEquals(saved, Files.readString(data));
            assertEquals(original, Files.readString(source));
        }
        Properties missing = sample().toProperties();
        missing.remove("loan.0.lost");
        assertThrows(IOException.class, () -> ApplicationSnapshot.decode(missing));
        Properties fakeNote = sample().toProperties();
        fakeNote.setProperty("note.count", "1");
        fakeNote.setProperty("note.0.asset", "OSC");
        fakeNote.setProperty("note.0.date", DAY.toString());
        fakeNote.setProperty("note.0.type", "LOST");
        fakeNote.setProperty("note.0.description", "A fabricated loss report");
        assertThrows(IOException.class, () -> ApplicationSnapshot.decode(fakeNote));
    }

    @Test
    void snapshot_format9RetainsRenewalsWithoutInventingLoss_reportsHiddenLossFields() throws Exception {
        Properties legacy = sample().toProperties();
        legacy.setProperty("format", "9");
        legacy.keySet().removeIf(key -> key.toString().endsWith(".lost"));
        ApplicationSnapshot restored = ApplicationSnapshot.decode(legacy);
        Loan loan = restored.getLoans().getFirst();
        assertFalse(loan.isLost());
        assertTrue(loan.isActive());
        assertEquals(sample().getLoans().getFirst().getRenewals(), loan.getRenewals());
        assertEquals("10", restored.toProperties().getProperty("format"));
        legacy.setProperty("loan.0.lost", DAY.toString());
        assertThrows(IOException.class, () -> ApplicationSnapshot.decode(legacy));
    }

    @Test
    void legacyStore_saveAndReload_preservesBothLossAndRenewals(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("legacy.properties");
        LabLogStore store = new LabLogStore(data);
        store.getInventory().replaceAll(sample().getEquipment());
        store.getLoans().replaceAll(sample().getLoans());
        store.save();
        Loan loaded = new LabLogStore(data).getLoans().getLoans().getFirst();
        assertEquals(DAY, loaded.getLostDate());
        assertEquals(sample().getLoans().getFirst().getRenewals(), loaded.getRenewals());
        assertTrue(loaded.isActive());
        assertEquals("10", ApplicationSnapshot.readProperties(data).getProperty("format"));
    }

    @Test
    void run_failedSave_doesNotReportSuccessOrContinue(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("records.properties");
        Loan loan = new Loan("OSC", USER, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        new ApplicationSnapshot(sample().getEquipment(), sample().getBorrowers(), List.of(loan), List.of()).write(data);
        LabLog app = LabLog.load(data);
        Files.delete(data);
        Files.createDirectory(data);
        Files.writeString(data.resolve("keep.txt"), "keep");
        String output = run(app, "lost OSC\nhistory OSC");
        assertTrue(output.contains("Changes could not be saved"));
        assertFalse(output.contains("Marked OSC as lost"));
        assertFalse(output.contains("History for OSC"));
        assertEquals("keep", Files.readString(data.resolve("keep.txt")));
        try (var paths = Files.list(directory)) {
            assertEquals(1, paths.count());
        }
    }
}
