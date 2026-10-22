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

/** Verifies severity assessment, lending restrictions, overview queries and persistence compatibility. */
class FaultSeverityTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC);

    private Inventory inventory() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab"));
        inventory.addEquipment(new Equipment("OSC-002", "Scope", "Electronics", "S2", "Lab"));
        return inventory;
    }

    private String run(LabLog application, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        application.run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }

    @Test
    void execute_severityLevels_preservesDescriptionAndHistory() {
        Inventory inventory = inventory();
        FaultBook faults = new FaultBook(inventory);
        HistoryRecordCommands commands = new HistoryRecordCommands(inventory, faults);
        for (String level : List.of("minor", "MODERATE", "SeVeRe")) {
            String result = commands.execute("fault OSC-001 d/2024-02-29 s/" + level + " desc/Cracked screen Ω");
            Fault fault = faults.getFaults().getLast();
            assertEquals(Fault.Severity.parse(level), fault.getSeverity());
            assertEquals("Cracked screen Ω", fault.getDescription());
            assertTrue(result.contains(fault.getSeverity().name()));
            assertTrue(fault.toHistoryEntry().getDescription().contains(fault.getId()));
        }
        commands.execute("fault OSC-001 d/2026-10-22 desc/Legacy syntax");
        assertEquals(Fault.Severity.UNSPECIFIED, faults.getFaults().getLast().getSeverity());
    }

    @Test
    void execute_invalidSeverityAndDescription_doesNotCreateReport() {
        Inventory inventory = inventory();
        FaultBook faults = new FaultBook(inventory);
        HistoryRecordCommands commands = new HistoryRecordCommands(inventory, faults);
        for (String details : List.of("s/critical desc/Damage", "s/ desc/Damage", "s/unspecified desc/Damage",
                "s/severe s/minor desc/Damage", "s/severe", "s/severe desc/", "s/severe desc/Bad\ttext",
                "s/severe desc/Bad\ntext", "desc/Damage s/severe", "s/severe desc/Damage desc/Other")) {
            assertThrows(IllegalArgumentException.class,
                    () -> commands.execute("fault OSC-001 d/2026-10-22 " + details));
        }
        assertTrue(faults.getFaults().isEmpty());
        assertThrows(NullPointerException.class, () -> faults.report("OSC-001", TODAY, "Damage", null));
        assertTrue(faults.getFaults().isEmpty());
    }

    @Test
    void severeFault_blocksOnlyAffectedUnitAndExposesImmutableOverview() {
        Inventory inventory = inventory();
        FaultBook faults = new FaultBook(inventory);
        LoanBook loans = new LoanBook();
        LoanCommands commands = new LoanCommands(inventory, loans, CLOCK, null, faults);
        Fault severe = faults.report("OSC-001", TODAY.plusDays(1), "Damage", Fault.Severity.SEVERE);
        faults.report("OSC-002", TODAY, "Scratch", Fault.Severity.MINOR);
        List<Fault> overview = faults.getUnresolvedSevereFaults();
        assertEquals(List.of(severe), overview);
        assertThrows(UnsupportedOperationException.class, overview::clear);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> commands.execute("loan OSC-001 b/A0123456X d/2026-10-22"));
        assertTrue(error.getMessage().contains("unresolved severe fault"));
        assertTrue(loans.getLoans().isEmpty());
        commands.execute("loan OSC-002 b/A0123456X d/2026-10-22");
        assertEquals(1, loans.getLoans().size());
        assertTrue(new EquipmentListCommands(inventory, loans, faults).execute("list")
                .contains("Unavailable: severe fault"));
        assertTrue(new EquipmentFindCommands(inventory, loans, faults).execute("find OSC-001")
                .contains("Unavailable: severe fault"));
    }

    @Test
    void nonSevereFaults_doNotBlockLending() {
        for (Fault.Severity severity : List.of(Fault.Severity.MINOR, Fault.Severity.MODERATE,
                Fault.Severity.UNSPECIFIED)) {
            Inventory inventory = inventory();
            FaultBook faults = new FaultBook(inventory);
            LoanBook loans = new LoanBook();
            faults.report("OSC-001", TODAY, "Damage", severity);
            new LoanCommands(inventory, loans, CLOCK, null, faults)
                    .execute("loan OSC-001 b/A0123456X d/2026-10-22");
            assertEquals(1, loans.getLoans().size());
            assertTrue(faults.getUnresolvedSevereFaults().isEmpty());
        }
    }

    @Test
    void reportDuringLoan_returnDoesNotClearSevereFault() {
        Inventory inventory = inventory();
        FaultBook faults = new FaultBook(inventory);
        LoanBook loans = new LoanBook();
        LoanCommands commands = new LoanCommands(inventory, loans, CLOCK, null, faults);
        commands.execute("loan OSC-001 b/A0123456X d/2026-10-22");
        faults.report("OSC-001", TODAY.minusDays(1), "Damage", Fault.Severity.SEVERE);
        assertTrue(loans.hasActiveLoan("OSC-001"));
        new ReturnCommands(inventory, loans, CLOCK).execute("return OSC-001");
        assertFalse(loans.hasActiveLoan("OSC-001"));
        assertThrows(IllegalArgumentException.class,
                () -> commands.execute("loan OSC-001 b/A0123456X d/2026-10-22"));
        assertEquals(1, faults.getUnresolvedSevereFaults().size());
    }

    @Test
    void restartAndExport_preserveSeverityAndBlockLending(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("live.properties");
        LabLog application = LabLog.load(file);
        run(application, "add OSC-001 n/Scope c/Electronics m/S1 l/Lab");
        run(application, "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu");
        run(application, "fault OSC-001 d/2026-10-22 s/severe desc/Cracked screen Ω");
        String saved = Files.readString(file);
        LabLog restarted = LabLog.load(file);
        assertTrue(run(restarted, "loan OSC-001 b/A0123456X d/2099-12-31")
                .contains("unresolved severe fault"));
        assertEquals(saved, Files.readString(file));
        assertTrue(run(restarted, "history OSC-001").contains("[severe]: Cracked screen Ω"));
        Path exported = directory.resolve("export.properties");
        run(restarted, "export " + exported);
        Fault original = new LabLogStore(file).getFaults().getFaults().getFirst();
        Fault copy = ApplicationSnapshot.read(exported).getFaults().getFirst();
        assertEquals(original.getId(), copy.getId());
        assertEquals(Fault.Severity.SEVERE, copy.getSeverity());
        assertEquals(original.getDescription(), copy.getDescription());
    }

    @Test
    void load_oldSchemaDefaultsAndInvalidSeverity_rejectsCorruption(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("live.properties");
        LabLogStore store = new LabLogStore(file);
        store.getInventory().addEquipment(inventory().getEquipmentUnits().getFirst());
        Fault original = store.getFaults().report("OSC-001", TODAY, "Damage", Fault.Severity.SEVERE);
        Properties records = store.toProperties();
        records.setProperty("format", "3");
        records.remove("fault.0.severity");
        records.remove("fault.0.resolved");
        records.remove("fault.0.repair");
        store.write(records);
        Fault migrated = new LabLogStore(file).getFaults().getFaults().getFirst();
        assertEquals(original.getId(), migrated.getId());
        assertEquals(Fault.Severity.UNSPECIFIED, migrated.getSeverity());
        records.setProperty("borrower.count", "0");
        records.setProperty("note.count", "0");
        records.setProperty("audit.count", "0");
        assertEquals(Fault.Severity.UNSPECIFIED,
                ApplicationSnapshot.decode(records).getFaults().getFirst().getSeverity());
        records.setProperty("format", "4");
        store.write(records);
        assertThrows(IOException.class, () -> new LabLogStore(file));
        assertThrows(IOException.class, () -> ApplicationSnapshot.decode(records));
        for (String invalid : List.of("", "CRITICAL", "severe")) {
            records.setProperty("fault.0.severity", invalid);
            store.write(records);
            assertThrows(IOException.class, () -> new LabLogStore(file));
            assertThrows(IOException.class, () -> ApplicationSnapshot.decode(records));
        }
    }
}
