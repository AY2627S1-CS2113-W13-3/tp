package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Verifies validation before inventory mutation and console recovery. */
class EquipmentValidationTest {
    @Test
    void addEquipment_duplicate_preservesOriginal() {
        Inventory inventory = new Inventory();
        Equipment original = new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab");
        inventory.addEquipment(original);
        assertThrows(IllegalArgumentException.class,
                () -> inventory.addEquipment(new Equipment("OSC-001", "Other", "Other", "Other", "Other")));
        assertEquals(List.of(original), inventory.getEquipmentUnits());
    }

    @Test
    void constructor_blankDetails_rejectsEveryField() {
        for (int index = 0; index < 4; index++) {
            String[] fields = {"Scope", "Electronics", "S1", "Lab"};
            fields[index] = "  ";
            assertThrows(IllegalArgumentException.class,
                    () -> new Equipment("OSC-001", fields[0], fields[1], fields[2], fields[3]));
        }
    }

    @Test
    void run_invalidThenDuplicate_recoversWithoutChangingRecords() {
        Inventory inventory = new Inventory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog(inventory, new LoanBook(), List::of).run(new Scanner(
                "BAD\n \nElectronics\nS1\nLab\nGOOD\nScope\nElectronics\nS1\nLab\nGOOD\nhelp\nexit\n"),
                new PrintStream(output));
        assertEquals(1, inventory.getEquipmentUnits().size());
        assertEquals("GOOD", inventory.getEquipmentUnits().get(0).getAssetId());
        assertTrue(output.toString().contains("already exists"));
        assertTrue(output.toString().contains("Common Version 1 commands:"));
    }
}
