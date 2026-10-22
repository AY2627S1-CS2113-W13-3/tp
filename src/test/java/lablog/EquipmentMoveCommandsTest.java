package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies grouped location updates, all-or-nothing validation, stable references, and persistence. */
class EquipmentMoveCommandsTest {
    @Test
    void execute_selectedUnits_updatesOnlyTheirLocationsAndPreservesReferences() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab 1"));
        inventory.addEquipment(new Equipment("MIC-002", "Microscope", "Optics", "M2", "Lab 2"));
        inventory.addEquipment(new Equipment("CAM-003", "Camera", "Imaging", "C3", "Lab 1"));
        Loan loan = new Loan("OSC-001", "A0123456X", LocalDate.now(), LocalDate.now().plusDays(1));
        LoanBook loans = new LoanBook();
        loans.addLoan(loan);

        new EquipmentMoveCommands(inventory).execute("move-equipment OSC-001, CAM-003 l/Lab 3");

        assertEquals("Lab 3", inventory.getEquipment("OSC-001").getLocation());
        assertEquals("Lab 2", inventory.getEquipment("MIC-002").getLocation());
        assertEquals("Lab 3", inventory.getEquipment("CAM-003").getLocation());
        assertEquals("Scope", inventory.getEquipment("OSC-001").getName());
        assertEquals(List.of("OSC-001", "MIC-002", "CAM-003"), inventory.getEquipmentUnits().stream()
                .map(Equipment::getAssetId).toList());
        assertSame(loan, loans.getLoans().get(0));
    }

    @Test
    void execute_invalidSelectionOrLocation_preservesEveryUnit() {
        Inventory inventory = new Inventory();
        Equipment first = new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab 1");
        Equipment second = new Equipment("MIC-002", "Microscope", "Optics", "M2", "Lab 2");
        inventory.addEquipment(first);
        inventory.addEquipment(second);
        EquipmentMoveCommands commands = new EquipmentMoveCommands(inventory);

        for (String input : List.of("move-equipment OSC-001,UNKNOWN l/Lab 3",
                "move-equipment OSC-001,OSC-001 l/Lab 3", "move-equipment OSC-001,,MIC-002 l/Lab 3",
                "move-equipment OSC-001,MIC-002 l/", "move-equipment OSC-001")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input));
            assertSame(first, inventory.getEquipment("OSC-001"));
            assertSame(second, inventory.getEquipment("MIC-002"));
        }
    }

    @Test
    void run_moveCommand_persistsTheWholeBatch(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("lablog.properties");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LabLog.load(file).run(new Scanner("add OSC-001 n/Scope c/Electronics m/S1 l/Lab 1\n"
                        + "add MIC-002 n/Microscope c/Optics m/M2 l/Lab 2\n"
                        + "move-equipment OSC-001,MIC-002 l/Lab 3\nexit\n"), new PrintStream(output));

        Inventory saved = new LabLogStore(file).getInventory();
        assertEquals("Lab 3", saved.getEquipment("OSC-001").getLocation());
        assertEquals("Lab 3", saved.getEquipment("MIC-002").getLocation());
    }
}
