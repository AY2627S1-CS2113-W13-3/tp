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

/** Covers fault resolution, immutable report history, availability and durable repair records. */
class RepairCommandsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC);

    private Inventory inventory() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab"));
        return inventory;
    }

    private Fault report(FaultBook faults, LocalDate date) {
        return faults.report("OSC-001", date, "Cracked screen", Fault.Severity.SEVERE);
    }

    private String run(LabLog application, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        application.run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }

    @Test
    void execute_validRepair_retainsOriginalAndAddsDatedHistory() {
        Inventory inventory = inventory();
        FaultBook faults = new FaultBook(inventory);
        Fault original = report(faults, TODAY.minusDays(1));
        List<Fault> before = faults.getFaults();
        String result = new RepairCommands(faults, CLOCK).execute("repair " + original.getId()
                + " d/2026-10-22 desc/Replaced screen Ω");
        Fault resolved = faults.getFaults().getFirst();
        assertTrue(result.contains(original.getId()));
        assertFalse(before.getFirst().isResolved());
        assertTrue(resolved.isResolved());
        assertEquals(original.getId(), resolved.getId());
        assertEquals(original.getAssetId(), resolved.getAssetId());
        assertEquals(original.getReportDate(), resolved.getReportDate());
        assertEquals(original.getDescription(), resolved.getDescription());
        assertEquals(original.getSeverity(), resolved.getSeverity());
        assertEquals(TODAY, resolved.getResolutionDate());
        assertEquals("Replaced screen Ω", resolved.getRepairDetails());
        List<HistoryEntry> entries = new HistoryRecordCommands(inventory, faults).getEntries();
        assertEquals(2, entries.size());
        assertEquals(original.toHistoryEntry().getDescription(), entries.getFirst().getDescription());
        assertEquals(TODAY, entries.getLast().getDate());
        assertEquals(HistoryEntry.Type.MAINTENANCE, entries.getLast().getType());
        assertTrue(entries.getLast().getDescription().contains(original.getId()));
        assertTrue(faults.getUnresolvedSevereFaults().isEmpty());
    }

    @Test
    void execute_invalidInputsAndRepeat_preservesRecords() {
        FaultBook faults = new FaultBook(inventory());
        Fault original = report(faults, TODAY.minusDays(1));
        RepairCommands commands = new RepairCommands(faults, CLOCK);
        for (String suffix : List.of("", " d/2026-10-22", " d/2026-02-30 desc/Repair",
                " d/2026-10-20 desc/Repair", " d/2026-10-23 desc/Repair", " d/2026-10-22 desc/",
                " desc/Repair d/2026-10-22", " d/2026-10-22 desc/Bad\ntext",
                " d/2026-10-22 desc/Bad\ttext", " d/2026-10-22 desc/Repair d/2026-10-22",
                " d/2026-10-22 desc/Repair desc/Again")) {
            assertThrows(IllegalArgumentException.class,
                    () -> commands.execute("repair " + original.getId() + suffix));
            assertEquals(List.of(original), faults.getFaults());
        }
        assertThrows(IllegalArgumentException.class, () -> commands.execute(null));
        assertThrows(IllegalArgumentException.class,
                () -> commands.execute("repair UNKNOWN d/2026-10-22 desc/Repair"));
        commands.execute("repair " + original.getId() + " d/2026-10-21 desc/Same-day repair");
        Fault resolved = faults.getFaults().getFirst();
        assertThrows(IllegalArgumentException.class,
                () -> commands.execute("repair " + original.getId() + " d/2026-10-22 desc/Again"));
        assertEquals(List.of(resolved), faults.getFaults());
    }

    @Test
    void execute_localDateBoundaryAndFutureReport_enforcesCompletedRepair() {
        FaultBook faults = new FaultBook(inventory());
        Fault original = report(faults, TODAY);
        Clock singapore = Clock.fixed(Instant.parse("2026-10-21T16:00:00Z"), ZoneOffset.ofHours(8));
        new RepairCommands(faults, singapore).execute("repair " + original.getId()
                + " d/2026-10-22 desc/Repaired today");
        Fault future = report(faults, TODAY.plusDays(1));
        RepairCommands commands = new RepairCommands(faults, CLOCK);
        assertThrows(IllegalArgumentException.class,
                () -> commands.execute("repair " + future.getId() + " d/2026-10-22 desc/Too early"));
        assertThrows(IllegalArgumentException.class,
                () -> commands.execute("repair " + future.getId() + " d/2026-10-23 desc/Future repair"));
        assertFalse(faults.getFaults().getLast().isResolved());
    }

    @Test
    void resolve_multipleSevereFaults_blocksUntilLastRepairAndRespectsActiveLoan() {
        Inventory inventory = inventory();
        FaultBook faults = new FaultBook(inventory);
        LoanBook loans = new LoanBook();
        Fault first = report(faults, TODAY);
        Fault second = report(faults, TODAY);
        LoanCommands lending = new LoanCommands(inventory, loans, CLOCK, null, faults);
        faults.resolve(first.getId(), TODAY, "First repair");
        assertEquals(List.of(second), faults.getUnresolvedSevereFaults());
        assertTrue(new EquipmentFindCommands(inventory, loans, faults).execute("find OSC-001")
                .contains("Unavailable: severe fault"));
        assertThrows(IllegalArgumentException.class,
                () -> lending.execute("loan OSC-001 b/A0123456X d/2026-10-22"));
        faults.resolve(second.getId(), TODAY, "Second repair");
        assertTrue(new EquipmentListCommands(inventory, loans, faults).execute("list").contains("Available"));
        lending.execute("loan OSC-001 b/A0123456X d/2026-10-22");
        Fault third = report(faults, TODAY);
        faults.resolve(third.getId(), TODAY, "Repair while on loan");
        assertTrue(new EquipmentFindCommands(inventory, loans, faults).execute("find OSC-001").contains("On loan"));
        assertThrows(IllegalArgumentException.class,
                () -> lending.execute("loan OSC-001 b/A0123456X d/2026-10-22"));
        assertEquals(1, loans.getLoans().size());
    }

    @Test
    void run_restartAndExport_preserveReportAndResolution(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("live.properties");
        LabLogStore store = new LabLogStore(file);
        store.getInventory().addEquipment(inventory().getEquipmentUnits().getFirst());
        Fault original = report(store.getFaults(), LocalDate.of(2024, 2, 29));
        store.save();
        String output = run(LabLog.load(file), "repair " + original.getId()
                + " d/2024-02-29 desc/Replaced screen Ω");
        assertTrue(output.contains("Resolved fault " + original.getId()));
        Fault restored = new LabLogStore(file).getFaults().getFaults().getFirst();
        assertTrue(restored.isResolved());
        assertEquals("Replaced screen Ω", restored.getRepairDetails());
        LabLog restarted = LabLog.load(file);
        String history = run(restarted, "history OSC-001");
        assertTrue(history.contains("2 records."));
        assertTrue(history.contains("Cracked screen"));
        assertTrue(history.contains("Resolved fault " + original.getId()));
        assertTrue(run(restarted, "find OSC-001").contains("Availability: Available"));
        Path exported = directory.resolve("export.properties");
        run(restarted, "export " + exported);
        ApplicationSnapshot snapshot = ApplicationSnapshot.read(exported);
        assertTrue(snapshot.getNotes().isEmpty());
        Fault copy = snapshot.getFaults().getFirst();
        assertEquals(restored.getResolutionDate(), copy.getResolutionDate());
        assertEquals(restored.getRepairDetails(), copy.getRepairDetails());
        assertEquals(original.getId(), copy.getId());
        assertEquals(original.getDescription(), copy.getDescription());
    }

    @Test
    void load_legacyAndCorruptResolution_validatesSchema(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("live.properties");
        LabLogStore store = new LabLogStore(file);
        store.getInventory().addEquipment(inventory().getEquipmentUnits().getFirst());
        Fault original = report(store.getFaults(), TODAY);
        Properties records = store.toProperties();
        records.setProperty("format", "4");
        records.remove("fault.0.resolved");
        records.remove("fault.0.repair");
        store.write(records);
        Fault legacy = new LabLogStore(file).getFaults().getFaults().getFirst();
        assertFalse(legacy.isResolved());
        assertEquals(original.getSeverity(), legacy.getSeverity());
        records.setProperty("borrower.count", "0");
        records.setProperty("note.count", "0");
        records.setProperty("audit.count", "0");
        assertFalse(ApplicationSnapshot.decode(records).getFaults().getFirst().isResolved());
        records.setProperty("format", "5");
        assertThrows(IOException.class, () -> ApplicationSnapshot.decode(records));
        for (String[] invalid : List.of(new String[]{"", "Orphan repair"},
                new String[]{"2026-10-22", ""}, new String[]{"2026-10-21", "Too early"},
                new String[]{"2026-02-30", "Invalid date"}, new String[]{"2026-10-22", "Bad\ntext"})) {
            records.setProperty("fault.0.resolved", invalid[0]);
            records.setProperty("fault.0.repair", invalid[1]);
            store.write(records);
            assertThrows(IOException.class, () -> new LabLogStore(file));
            assertThrows(IOException.class, () -> ApplicationSnapshot.decode(records));
        }
    }

    @Test
    void run_failedRepairSave_keepsPreviousSavedReport(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("live.properties");
        LabLogStore store = new LabLogStore(file);
        store.getInventory().addEquipment(inventory().getEquipmentUnits().getFirst());
        Fault original = report(store.getFaults(), LocalDate.of(2024, 1, 1));
        store.save();
        LabLog application = LabLog.load(file);
        Files.move(file, directory.resolve("saved.properties"));
        Files.createDirectory(file);
        Files.writeString(file.resolve("keep.txt"), "keep");
        String output = run(application, "repair " + original.getId() + " d/2024-01-01 desc/Repair");
        assertTrue(output.contains("Changes could not be saved"));
        assertFalse(output.contains("Resolved fault " + original.getId()));
        assertFalse(new LabLogStore(directory.resolve("saved.properties")).getFaults()
                .getFaults().getFirst().isResolved());
        assertEquals("keep", Files.readString(file.resolve("keep.txt")));
    }
}
