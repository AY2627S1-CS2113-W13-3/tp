package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Verifies explicit registration and rejection of accidental or partial input. */
class EquipmentAddCommandsTest {
    @Test
    void execute_fieldsInAnyOrder_validatesBeforeAdding() {
        Inventory inventory = new Inventory();
        EquipmentAddCommands commands = new EquipmentAddCommands(inventory);
        for (String input : List.of("add A n/Scope", "add A n/Scope c/Electronics m/S1 l/",
                "add A n/Scope n/Other c/Electronics m/S1 l/Lab",
                "add A n/Scope c/Electronics m/S1 l/Lab x/Extra")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input));
        }
        assertTrue(inventory.getEquipmentUnits().isEmpty());
        commands.execute("add A l/Lab 1 m/S1 c/Electronics n/Bench scope");
        assertEquals("Bench scope", inventory.getEquipmentUnits().get(0).getName());
        assertEquals("Lab 1", inventory.getEquipmentUnits().get(0).getLocation());
        assertThrows(IllegalArgumentException.class,
                () -> commands.execute("add A n/Other c/Electronics m/S1 l/Lab"));
    }

    @Test
    void run_typoThenExplicitCommand_createsOnlyRequestedUnit() {
        Inventory inventory = new Inventory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog(inventory, new LoanBook(), List::of).run(new Scanner(
                "histroy\nx\nx\nx\nx\nadd A n/Scope c/Electronics m/S1 l/Lab\nexit\n"),
                new PrintStream(output));
        assertEquals(1, inventory.getEquipmentUnits().size());
        assertEquals("A", inventory.getEquipmentUnits().get(0).getAssetId());
        assertTrue(output.toString().contains("Unknown command. Use help"));
    }
}
