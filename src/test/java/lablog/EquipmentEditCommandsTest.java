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

/** Verifies partial equipment edits, stable references, validation, and persistence. */
class EquipmentEditCommandsTest {
    @Test
    void execute_partialAndReorderedEdits_preserveIdentityAndLoanReferences() {
        Inventory inventory = new Inventory();
        Equipment original = new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab 1");
        inventory.addEquipment(original);
        inventory.addEquipment(new Equipment("MIC-002", "Microscope", "Optics", "M2", "Lab 2"));
        Loan loan = new Loan("OSC-001", "A0123456X", LocalDate.now(), LocalDate.now().plusDays(1));
        LoanBook loans = new LoanBook();
        loans.addLoan(loan);
        EquipmentEditCommands commands = new EquipmentEditCommands(inventory);

        commands.execute("edit-equipment OSC-001 l/Lab 3 n/Digital scope");

        Equipment updated = inventory.getEquipment("OSC-001");
        assertEquals("OSC-001", updated.getAssetId());
        assertEquals("Digital scope", updated.getName());
        assertEquals("Electronics", updated.getCategory());
        assertEquals("S1", updated.getModel());
        assertEquals("Lab 3", updated.getLocation());
        assertEquals(List.of("OSC-001", "MIC-002"), inventory.getEquipmentUnits().stream()
                .map(Equipment::getAssetId).toList());
        assertSame(loan, loans.getLoans().get(0));
        assertEquals("OSC-001", loans.getLoans().get(0).getAssetId());
    }

    @Test
    void execute_invalidEdits_preserveOriginalUnit() {
        Inventory inventory = new Inventory();
        Equipment original = new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab 1");
        inventory.addEquipment(original);
        EquipmentEditCommands commands = new EquipmentEditCommands(inventory);

        for (String input : List.of("edit-equipment OSC-001", "edit-equipment UNKNOWN n/Scope",
                "edit-equipment OSC-001 n/", "edit-equipment OSC-001 n/New n/Again",
                "edit-equipment OSC-001 x/Other", "edit-equipment OSC-001 id/NEW n/Name")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input));
            assertSame(original, inventory.getEquipment("OSC-001"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> commands.execute("edit-equipment OSC-001 n/\u0001"));
        assertSame(original, inventory.getEquipment("OSC-001"));
    }

    @Test
    void run_editCommand_savesChangedDetails(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("lablog.properties");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        LabLog.load(file).run(new Scanner("add OSC-001 n/Scope c/Electronics m/S1 l/Lab 1\n"
                        + "edit-equipment OSC-001 l/Lab 2\nexit\n"), new PrintStream(output));

        Equipment saved = new LabLogStore(file).getInventory().getEquipment("OSC-001");
        assertEquals("Scope", saved.getName());
        assertEquals("Lab 2", saved.getLocation());
    }
}
