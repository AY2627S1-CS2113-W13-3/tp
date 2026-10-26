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
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises interactions between snapshot replacement, faults, repairs and equipment queries. */
class IntegratedFeaturesTest {
    /** Runs commands against the same handlers so replacement must refresh their shared records. */
    private String run(LabLog application, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        application.run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }

    /** Produces a complete snapshot with one assessed fault and a registered borrower. */
    private ApplicationSnapshot sample() {
        Equipment unit = new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab A");
        Borrower borrower = new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu");
        Fault fault = new Fault("F-1", unit.getAssetId(), LocalDate.of(2024, 1, 1),
                "Damage", Fault.Severity.SEVERE);
        return new ApplicationSnapshot(List.of(unit), List.of(borrower), List.of(), List.of(), List.of(fault));
    }

    @Test
    void import_repairAndReplace_refreshesAllHandlersAndPersists(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("source.properties");
        Path live = directory.resolve("live.properties");
        sample().write(source);
        LabLog app = LabLog.load(live);
        run(app, "add OLD n/Old c/Old m/Old l/Old");
        assertTrue(run(app, "import " + source + " --replace").contains("All current records were replaced"));
        assertTrue(run(app, "find OSC-001").contains("Unavailable: severe fault"));
        assertTrue(run(app, "list category/Electronics status/available sort/name")
                .contains("No equipment matches"));
        assertTrue(run(app, "loan OSC-001 b/A0123456X d/" + LocalDate.now().plusDays(1))
                .contains("unresolved severe fault"));
        assertTrue(run(app, "repair F-1 d/2024-01-02 desc/Replaced screen").contains("Resolved fault F-1"));
        assertTrue(run(app, "list status/available sort/name").contains("OSC-001 | Scope"));
        assertTrue(ApplicationSnapshot.read(live).getFaults().getFirst().isResolved());
        assertTrue(run(LabLog.load(live), "history OSC-001").contains("Replaced screen"));
        Path exported = directory.resolve("export.properties");
        assertTrue(run(app, "export " + exported).contains("Exported application data"));
        assertEquals(ApplicationSnapshot.read(live).toProperties(), ApplicationSnapshot.read(exported).toProperties());
        new ApplicationSnapshot(sample().getEquipment(), sample().getBorrowers(), List.of(), List.of()).write(source);
        run(app, "import " + source + " --replace");
        assertFalse(run(app, "history OSC-001").contains("F-1"));
        assertTrue(run(app, "loan OSC-001 b/A0123456X d/" + LocalDate.now().plusDays(1)).contains("Loaned OSC-001"));
    }

    @Test
    void list_filtersAndSortsTogether_withoutChangingInventory() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("B", "Scope", "Electronics", "S1", "Lab A"));
        inventory.addEquipment(new Equipment("C", "Fan", "Cooling", "F1", "Lab A"));
        inventory.addEquipment(new Equipment("A", "Probe", "Electronics", "P1", "Lab A"));
        FaultBook faults = new FaultBook(inventory);
        EquipmentListCommands commands = new EquipmentListCommands(inventory, new LoanBook(), faults);
        String expected = commands.execute("list category/Electronics sort/name");
        assertEquals(expected, commands.execute("list sort/name category/Electronics"));
        assertTrue(expected.indexOf("A | Probe") < expected.indexOf("B | Scope"));
        assertFalse(expected.contains("C | Fan"));
        Fault fault = faults.report("A", LocalDate.of(2024, 1, 1), "Damage", Fault.Severity.SEVERE);
        assertFalse(commands.execute("list status/available sort/name").contains("A | Probe"));
        faults.resolve(fault.getId(), LocalDate.of(2024, 1, 2), "Repaired");
        assertTrue(commands.execute("list status/available sort/name").contains("A | Probe"));
        assertEquals(List.of("B", "C", "A"), inventory.getEquipmentUnits().stream()
                .map(Equipment::getAssetId).toList());
        assertThrows(IllegalArgumentException.class, () -> commands.execute("list sort/name sort/location"));
    }

    @Test
    void decode_olderFormats_rejectsFieldsThatWouldBeDiscarded() throws Exception {
        Properties records = sample().toProperties();
        records.setProperty("format", "3");
        records.remove("reservation.count");
        records.remove("waiting.count");
        records.remove("fault.0.resolved");
        records.remove("fault.0.repair");
        assertThrows(IOException.class, () -> ApplicationSnapshot.decode(records));
        records.remove("fault.0.severity");
        assertEquals(Fault.Severity.UNSPECIFIED,
                ApplicationSnapshot.decode(records).getFaults().getFirst().getSeverity());
        records.setProperty("format", "4");
        records.setProperty("fault.0.severity", "SEVERE");
        records.setProperty("fault.0.repair", "Must not disappear");
        assertThrows(IOException.class, () -> ApplicationSnapshot.decode(records));
        records.remove("fault.0.repair");
        assertEquals(Fault.Severity.SEVERE, ApplicationSnapshot.decode(records).getFaults().getFirst().getSeverity());
        records.setProperty("format", "2");
        assertThrows(IOException.class, () -> ApplicationSnapshot.decode(records));
    }

    @Test
    void invalidFaultImport_preservesLiveFaultAndSavedBytes(@TempDir Path directory) throws Exception {
        Path live = directory.resolve("live.properties");
        Path invalid = directory.resolve("invalid.properties");
        sample().write(live);
        LabLog app = LabLog.load(live);
        String saved = Files.readString(live);
        Files.writeString(invalid, saved.replace("fault.0.asset=OSC-001", "fault.0.asset=MISSING"));
        assertTrue(run(app, "import " + invalid + " --replace").contains("Current records were not changed"));
        assertEquals(saved, Files.readString(live));
        assertTrue(run(app, "find OSC-001").contains("Unavailable: severe fault"));
    }
}
