package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises identifiable fault reports through command, persistence and export boundaries. */
class FaultReportingTest {
    private Inventory inventory() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab"));
        return inventory;
    }

    private String run(LabLog application, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        application.run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }

    @Test
    void report_validDatesAndRepeatedReports_createDistinctRecords() {
        Inventory inventory = inventory();
        FaultBook faults = new FaultBook(inventory);
        HistoryRecordCommands commands = new HistoryRecordCommands(inventory, faults);
        for (String date : List.of("2024-02-29", "2026-10-22", "2099-12-31")) {
            commands.execute("fault OSC-001 d/" + date + " desc/Cracked screen Ω");
        }
        commands.execute("fault OSC-001 d/2024-02-29 desc/Cracked screen Ω");
        assertEquals(4, faults.getFaults().size());
        assertNotEquals(faults.getFaults().get(0).getId(), faults.getFaults().get(3).getId());
        assertEquals(LocalDate.of(2024, 2, 29), faults.getFaults().get(0).getReportDate());
        assertEquals(4, commands.getEntries().size());
        assertTrue(commands.getNotes().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> faults.getFaults().clear());
    }

    @Test
    void report_invalidInput_preservesRecords() {
        Inventory inventory = inventory();
        FaultBook faults = new FaultBook(inventory);
        HistoryRecordCommands commands = new HistoryRecordCommands(inventory, faults);
        for (String input : List.of("fault MISSING d/2026-01-01 desc/Damage",
                "fault osc-001 d/2026-01-01 desc/Damage", "fault OSC-001 d/2025-02-29 desc/Damage",
                "fault OSC-001 d/2026-01-01 desc/", "fault OSC-001 d/2026-01-01 desc/Bad\ttext",
                "fault OSC-001", "fault OSC-001 desc/Damage d/2026-01-01")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input));
        }
        assertTrue(faults.getFaults().isEmpty());
        Fault fault = faults.report("OSC-001", LocalDate.of(2026, 1, 1), "Damage");
        assertThrows(IllegalArgumentException.class, () -> faults.add(fault));
        assertEquals(1, faults.getFaults().size());
    }

    @Test
    void run_restartAndExport_preserveIdsWithoutDuplicateHistory(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("live.properties");
        LabLog application = LabLog.load(file);
        run(application, "add OSC-001 n/Scope c/Electronics m/S1 l/Lab");
        String result = run(application, "fault OSC-001 d/2026-01-01 desc/Damage Ω");
        Fault original = new LabLogStore(file).getFaults().getFaults().get(0);
        assertTrue(result.contains(original.getId()));
        LabLog restarted = LabLog.load(file);
        String history = run(restarted, "history OSC-001");
        assertEquals(1, history.split(original.getId(), -1).length - 1);
        Path exported = directory.resolve("export.properties");
        run(restarted, "export " + exported);
        ApplicationSnapshot snapshot = ApplicationSnapshot.read(exported);
        assertTrue(snapshot.getNotes().isEmpty());
        assertEquals(original.getId(), snapshot.getFaults().get(0).getId());
        assertEquals("Damage Ω", snapshot.getFaults().get(0).getDescription());
        run(restarted, "fault OSC-001 d/2026-01-02 desc/Another fault");
        List<Fault> loaded = new LabLogStore(file).getFaults().getFaults();
        assertEquals(2, loaded.size());
        assertNotEquals(loaded.get(0).getId(), loaded.get(1).getId());
    }

    @Test
    void load_legacyAndCorruptRecords_handlesSchemaBoundaries(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("live.properties");
        Files.writeString(file, "format=1\nequipment.count=0\nloan.count=0\n");
        LabLogStore legacy = new LabLogStore(file);
        assertTrue(legacy.getFaults().getFaults().isEmpty());
        legacy.getInventory().addEquipment(inventory().getEquipmentUnits().get(0));
        legacy.getFaults().report("OSC-001", LocalDate.of(2026, 1, 1), "Damage");
        legacy.save();
        Properties valid = ApplicationSnapshot.readProperties(file);
        for (String field : List.of("fault.0.id", "fault.0.asset", "fault.0.date", "fault.0.description",
                "fault.count")) {
            Properties broken = new Properties();
            broken.putAll(valid);
            broken.remove(field);
            legacy.write(broken);
            assertThrows(IOException.class, () -> new LabLogStore(file));
        }
        valid.setProperty("fault.0.asset", "MISSING");
        legacy.write(valid);
        assertThrows(IOException.class, () -> new LabLogStore(file));
        valid.setProperty("fault.0.asset", "OSC-001");
        valid.setProperty("fault.count", "2");
        for (String suffix : List.of("id", "asset", "date", "description", "severity", "resolved", "repair")) {
            valid.setProperty("fault.1." + suffix, valid.getProperty("fault.0." + suffix));
        }
        legacy.write(valid);
        assertThrows(IOException.class, () -> new LabLogStore(file));
    }

    @Test
    void decode_legacyExport_retainsFaultNotes() throws Exception {
        HistoryEntry note = new HistoryEntry("OSC-001", LocalDate.of(2026, 1, 1),
                HistoryEntry.Type.FAULT, "Legacy damage");
        Properties records = new ApplicationSnapshot(inventory().getEquipmentUnits(), List.of(),
                List.of(), List.of(note)).toProperties();
        records.setProperty("format", "2");
        records.remove("reservation.count");
        records.remove("fault.count");
        ApplicationSnapshot snapshot = ApplicationSnapshot.decode(records);
        assertTrue(snapshot.getFaults().isEmpty());
        assertEquals("Legacy damage", snapshot.getNotes().get(0).getDescription());
        assertEquals("Legacy damage", ApplicationSnapshot.decode(snapshot.toProperties())
                .getNotes().get(0).getDescription());
    }

    @Test
    void run_failedFaultSave_stopsBeforeSuccess(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("live.properties");
        LabLog application = LabLog.load(file);
        run(application, "add OSC-001 n/Scope c/Electronics m/S1 l/Lab");
        Files.delete(file);
        Files.createDirectory(file);
        Files.writeString(file.resolve("keep.txt"), "keep");
        String output = run(application, "fault OSC-001 d/2026-01-01 desc/Damage\nhistory OSC-001");
        assertTrue(output.contains("Changes could not be saved"));
        assertFalse(output.contains("Recorded fault for"));
        assertEquals("keep", Files.readString(file.resolve("keep.txt")));
    }
}
