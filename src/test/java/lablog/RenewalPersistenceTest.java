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
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks renewal events across snapshot migration, reload, export, import and rejected writes. */
class RenewalPersistenceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 23);

    /** Creates one renewed active loan and one unrenewed returned loan for the same unit. */
    private ApplicationSnapshot sample() {
        Equipment unit = new Equipment("OSC", "Scope", "Electronics", "S1", "Lab");
        Borrower borrower = new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu");
        Loan past = new Loan("OSC", borrower.getBorrowerId(), DAY.minusDays(5), DAY.minusDays(3));
        past.markReturned(DAY.minusDays(3), "Checked");
        Loan loan = new Loan("OSC", borrower.getBorrowerId(), DAY, DAY.plusDays(1));
        loan.renew(DAY.plusDays(2), DAY.plusDays(1));
        loan.renew(DAY.plusDays(4), DAY.plusDays(2));
        return new ApplicationSnapshot(List.of(unit), List.of(borrower), List.of(past, loan), List.of());
    }

    private String run(LabLog app, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        app.run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }

    @Test
    void snapshot_exportImportAndReturn_keepEveryRenewalWithItsOriginalLoan(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("live.properties");
        sample().write(data);
        Loan expected = sample().getLoans().getLast();
        Loan reloaded = ApplicationSnapshot.read(data).getLoans().getLast();
        assertEquals(expected.getRenewals(), reloaded.getRenewals());
        assertEquals(expected.getOriginalDueDate(), reloaded.getOriginalDueDate());
        assertEquals(expected.getDueDate(), reloaded.getDueDate());
        assertEquals("11", ApplicationSnapshot.readProperties(data).getProperty("format"));
        Path exported = directory.resolve("export.properties");
        assertFalse(run(LabLog.load(data), "export " + exported).contains("Error:"));
        Path imported = directory.resolve("imported.properties");
        assertFalse(run(LabLog.load(imported), "import " + exported + " --replace").contains("Error:"));
        ApplicationSnapshot restored = ApplicationSnapshot.read(imported);
        assertEquals(2, restored.getLoans().size());
        assertEquals(expected.getRenewals(), restored.getLoans().getLast().getRenewals());
        assertEquals("Checked", restored.getLoans().getFirst().getReturnFaults());
        assertTrue(restored.getLoans().getFirst().getRenewals().isEmpty());
        String history = run(LabLog.load(imported), "history OSC");
        assertTrue(history.contains("due changed from 2026-10-24 to 2026-10-25"));
        assertTrue(history.contains("due changed from 2026-10-25 to 2026-10-27"));
        reloaded.markReturned(DAY.plusDays(3), "Inspected");
        new ApplicationSnapshot(restored.getEquipment(), restored.getBorrowers(), List.of(reloaded), List.of())
                .write(data);
        Loan returned = ApplicationSnapshot.read(data).getLoans().getFirst();
        assertFalse(returned.isActive());
        assertEquals(DAY.plusDays(3), returned.getReturnDate());
        assertEquals(expected.getRenewals(), returned.getRenewals());
        assertEquals("Inspected", returned.getReturnFaults());
    }

    @Test
    void run_successAndReservationRejection_preserveSnapshotAcrossRestart(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("live.properties");
        LocalDate today = LocalDate.now();
        String seed = "add OSC n/Scope c/Electronics m/S1 l/Lab\n"
                + "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                + "loan OSC b/A0123456X d/" + today.plusDays(1) + "\n"
                + "reserve OSC b/A0123456X from/" + today.plusDays(3) + " to/" + today.plusDays(4);
        assertFalse(run(LabLog.load(data), seed).contains("Error:"));
        byte[] before = Files.readAllBytes(data);
        assertTrue(run(LabLog.load(data), "renew OSC d/" + today.plusDays(3))
                .contains("Conflicts with reservation"));
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(data)));
        assertTrue(ApplicationSnapshot.read(data).getLoans().getFirst().getRenewals().isEmpty());
        String success = run(LabLog.load(data), "renew OSC d/" + today.plusDays(2));
        assertTrue(success.contains("Renewed OSC for A0123456X."), success);
        Loan saved = ApplicationSnapshot.read(data).getLoans().getFirst();
        assertEquals(today.plusDays(2), saved.getDueDate());
        assertEquals(today.plusDays(1), saved.getOriginalDueDate());
        assertEquals(1, saved.getRenewals().size());
        assertEquals(today.plusDays(1), saved.getRenewals().getFirst().previousDueDate());
        assertEquals(1, ApplicationSnapshot.read(data).getReservations().size());
        byte[] after = Files.readAllBytes(data);
        String listing = run(LabLog.load(data), "history OSC\nactive-loans\nloans A0123456X");
        assertTrue(listing.contains(" | RENEWAL | "));
        assertTrue(listing.contains(today.plusDays(2).toString()));
        assertTrue(java.util.Arrays.equals(after, Files.readAllBytes(data)));
    }

    @Test
    void read_corruptRenewalChains_rejectBeforeImportOrOverwrite(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("live.properties");
        sample().write(data);
        String saved = Files.readString(data);
        LabLog app = LabLog.load(data);
        Path source = directory.resolve("invalid.properties");
        for (String[] change : List.of(
                new String[]{"loan.1.originalDue", "2026-10-22"},
                new String[]{"loan.1.originalDue", "not-a-date"},
                new String[]{"loan.1.due", "2026-10-28"},
                new String[]{"loan.1.renewal.count", "-1"},
                new String[]{"loan.1.renewal.count", "100001"},
                new String[]{"loan.1.renewal.count", "overflow"},
                new String[]{"loan.1.renewal.0.previousDue", "2026-10-23"},
                new String[]{"loan.1.renewal.0.date", "2026-10-22"},
                new String[]{"loan.1.renewal.0.date", "2026-10-26"},
                new String[]{"loan.1.renewal.1.date", "2026-10-23"},
                new String[]{"loan.1.renewal.1.newDue", "2026-10-25"},
                new String[]{"loan.1.returned", "2026-10-24"},
                new String[]{"loan.1.renewal.2.date", "2026-10-26"},
                new String[]{"loan.1.renewal.0.reason", "unsupported"})) {
            Properties records = sample().toProperties();
            records.setProperty(change[0], change[1]);
            try (Writer writer = Files.newBufferedWriter(source)) {
                records.store(writer, "invalid renewal");
            }
            assertThrows(IOException.class, () -> ApplicationSnapshot.read(source), change[0]);
            String original = Files.readString(source);
            assertTrue(run(app, "import " + source + " --replace").contains("Current records were not changed."));
            assertEquals(saved, Files.readString(data));
            assertEquals(original, Files.readString(source));
        }
        for (String key : List.of("loan.1.originalDue", "loan.1.renewal.count", "loan.1.renewal.0.date",
                "loan.1.renewal.0.previousDue", "loan.1.renewal.1.newDue")) {
            Properties records = sample().toProperties();
            records.remove(key);
            assertThrows(IOException.class, () -> ApplicationSnapshot.decode(records), key);
        }
    }

    @Test
    void read_format8MigratesWithoutInventingRenewals_rejectsHiddenNewFields() throws Exception {
        Properties records = sample().toProperties();
        records.setProperty("format", "8");
        records.keySet().removeIf(key -> key.toString().endsWith(".originalDue")
                || key.toString().contains(".renewal.") || key.toString().endsWith(".lost")
                || (key.toString().startsWith("loan.") && key.toString().endsWith(".id")));
        ApplicationSnapshot legacy = ApplicationSnapshot.decode(records);
        for (Loan loan : legacy.getLoans()) {
            assertTrue(loan.getRenewals().isEmpty());
            assertEquals(loan.getDueDate(), loan.getOriginalDueDate());
        }
        assertEquals("11", legacy.toProperties().getProperty("format"));
        records.setProperty("loan.1.renewal.count", "0");
        assertThrows(IOException.class, () -> ApplicationSnapshot.decode(records));
    }

    @Test
    void legacyStore_saveAndReload_doesNotDiscardRenewals(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("legacy.properties");
        LabLogStore store = new LabLogStore(file);
        store.getInventory().replaceAll(sample().getEquipment());
        store.getLoans().replaceAll(sample().getLoans());
        store.save();
        Loan reloaded = new LabLogStore(file).getLoans().getLoans().getLast();
        assertEquals(sample().getLoans().getLast().getRenewals(), reloaded.getRenewals());
        assertEquals(DAY.plusDays(1), reloaded.getOriginalDueDate());
        assertEquals(DAY.plusDays(4), reloaded.getDueDate());
    }

    @Test
    void snapshot_sameDayReturnAndReborrow_keepEventsOnTheirOwningLoans(@TempDir Path directory) throws Exception {
        Loan first = new Loan("OSC", "A0123456X", DAY, DAY.plusDays(1));
        first.renew(DAY.plusDays(2), DAY);
        first.markReturned(DAY);
        Loan second = new Loan("OSC", "A0123456X", DAY, DAY.plusDays(3));
        second.renew(DAY.plusDays(4), DAY);
        Path data = directory.resolve("same-day.properties");
        new ApplicationSnapshot(sample().getEquipment(), sample().getBorrowers(),
                List.of(first, second), List.of()).write(data);
        List<Loan> restored = ApplicationSnapshot.read(data).getLoans();
        assertFalse(restored.getFirst().isActive());
        assertTrue(restored.getLast().isActive());
        assertEquals(first.getRenewals(), restored.getFirst().getRenewals());
        assertEquals(second.getRenewals(), restored.getLast().getRenewals());
        assertEquals(DAY.plusDays(1), restored.getFirst().getOriginalDueDate());
        assertEquals(DAY.plusDays(3), restored.getLast().getOriginalDueDate());
    }

    @Test
    void snapshot_renewalDisplayNote_cannotReplaceStructuredEvents() throws Exception {
        Properties records = sample().toProperties();
        records.setProperty("note.count", "1");
        records.setProperty("note.0.asset", "OSC");
        records.setProperty("note.0.date", DAY.toString());
        records.setProperty("note.0.type", "RENEWAL");
        records.setProperty("note.0.description", "A fabricated renewal note");
        assertThrows(IOException.class, () -> ApplicationSnapshot.decode(records));
    }

    @Test
    void run_saveFailure_suppressesSuccessAndStopsSession(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("live.properties");
        LocalDate today = LocalDate.now();
        new ApplicationSnapshot(sample().getEquipment(), sample().getBorrowers(), List.of(
                new Loan("OSC", "A0123456X", today.minusDays(1), today.plusDays(1))), List.of()).write(file);
        LabLog app = LabLog.load(file);
        Files.delete(file);
        Files.createDirectory(file);
        Files.writeString(file.resolve("keep.txt"), "keep");
        String output = run(app, "renew OSC d/" + today.plusDays(2) + "\nhistory OSC");
        assertTrue(output.contains("Changes could not be saved"));
        assertFalse(output.contains("Renewed OSC"));
        assertFalse(output.contains("History for OSC"));
        assertEquals("keep", Files.readString(file.resolve("keep.txt")));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }
}
