package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Verifies exact equipment lookup, unknown IDs, availability, and read-only behavior. */
class EquipmentFindCommandsTest {
    @Test
    void execute_exactId_returnsDetailsAndAvailabilityWithoutMutation() {
        Inventory inventory = new Inventory();
        Equipment equipment = new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab 1");
        inventory.addEquipment(equipment);
        LoanBook loans = new LoanBook();
        Loan loan = new Loan("OSC-001", "A0123456X", LocalDate.now(), LocalDate.now().plusDays(1));
        loans.addLoan(loan);

        String result = new EquipmentFindCommands(inventory, loans).execute("find OSC-001");

        assertEquals("Asset ID: OSC-001\nName: Scope\nCategory: Electronics\nModel: S1\n"
                + "Location: Lab 1\nAvailability: On loan", result);
        assertSame(equipment, inventory.getEquipmentUnits().get(0));
        assertSame(loan, loans.getLoans().get(0));
    }

    @Test
    void execute_unknownIdAndMalformedSyntax_rejectWithoutMutation() {
        Inventory inventory = new Inventory();
        Equipment equipment = new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab 1");
        inventory.addEquipment(equipment);
        EquipmentFindCommands commands = new EquipmentFindCommands(inventory, new LoanBook());

        assertThrows(IllegalArgumentException.class, () -> commands.execute("find UNKNOWN"));
        for (String input : List.of("find", "find OSC-001 extra", "list OSC-001")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input));
        }
        assertSame(equipment, inventory.getEquipmentUnits().get(0));
    }

    @Test
    void run_findCommand_displaysDetailsAndUnknownIdError() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab 1"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new LabLog(inventory, new LoanBook(), List::of).run(
                new Scanner("find OSC-001\nfind UNKNOWN\nexit\n"), new PrintStream(output));

        assertTrue(output.toString().contains("Asset ID: OSC-001\nName: Scope"));
        assertTrue(output.toString().contains("Error: Equipment UNKNOWN not found."));
        assertEquals(1, inventory.getEquipmentUnits().size());
    }
}
