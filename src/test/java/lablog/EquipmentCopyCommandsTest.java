package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies copied equipment details, record isolation, validation, and persistence. */
class EquipmentCopyCommandsTest {
    @Test
    void execute_copiesDetailsUnderNewIdAndPreservesSource() {
        Inventory inventory = new Inventory();
        Equipment source = new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab A");
        inventory.addEquipment(source);
        EquipmentCopyCommands commands = new EquipmentCopyCommands(inventory);

        assertEquals("Copied OSC-001 to OSC-002.", commands.execute("copy OSC-001 OSC-002"));
        List<Equipment> units = inventory.getEquipmentUnits();
        assertEquals(2, units.size());
        assertEquals(source, units.get(0));
        Equipment copy = units.get(1);
        assertEquals("OSC-002", copy.getAssetId());
        assertEquals("Scope", copy.getName());
        assertEquals("Electronics", copy.getCategory());
        assertEquals("S1", copy.getModel());
        assertEquals("Lab A", copy.getLocation());
    }

    @Test
    void execute_unknownSourceDuplicateIdOrMalformedInput_changesNothing() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab A"));
        EquipmentCopyCommands commands = new EquipmentCopyCommands(inventory);

        assertEquals("Unknown equipment: MISSING.", assertThrows(IllegalArgumentException.class,
                () -> commands.execute("copy MISSING OSC-002")).getMessage());
        assertEquals("Equipment OSC-001 already exists.", assertThrows(IllegalArgumentException.class,
                () -> commands.execute("copy OSC-001 OSC-001")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> commands.execute("copy OSC-001"));
        assertEquals(1, inventory.getEquipmentUnits().size());
    }

    @Test
    void run_copyLeavesLoanAndHistoryOnOriginalId() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab A"));
        LoanBook loans = new LoanBook();
        Loan sourceLoan = new Loan("OSC-001", "A0123456X", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));
        loans.addLoan(sourceLoan);
        List<HistoryEntry> notes = List.of(new HistoryEntry("OSC-001", LocalDate.of(2026, 1, 1),
                HistoryEntry.Type.FAULT, "Cracked screen"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new LabLog(inventory, loans, () -> notes).run(new Scanner(
                "copy OSC-001 OSC-002\nhistory OSC-002\nexit\n"), new PrintStream(output));

        assertTrue(output.toString().contains("No history found for OSC-002 - Scope."));
        assertEquals(List.of(sourceLoan), loans.getLoans());
        assertTrue(sourceLoan.isActive());
        assertEquals(List.of("OSC-001", "OSC-002"), inventory.getEquipmentUnits().stream()
                .map(Equipment::getAssetId).toList());
    }

    @Test
    void run_copyPersistsEquipment(@TempDir Path directory) throws IOException {
        Path dataFile = directory.resolve("lablog.properties");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        LabLog.load(dataFile).run(new Scanner("add OSC-001 n/Scope c/Electronics m/S1 l/Lab A\n"
                + "copy OSC-001 OSC-002\nexit\n"), new PrintStream(output));

        Inventory reloaded = new LabLogStore(dataFile).getInventory();
        assertEquals(List.of("OSC-001", "OSC-002"), reloaded.getEquipmentUnits().stream()
                .map(Equipment::getAssetId).toList());
        assertEquals("Lab A", reloaded.getEquipmentUnits().get(1).getLocation());
        assertTrue(output.toString().contains("Copied OSC-001 to OSC-002."));
    }
}
