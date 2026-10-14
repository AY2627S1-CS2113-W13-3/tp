package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Verifies that rejected initial input cannot start an accidental equipment registration. */
class LabLogTest {
    @Test
    void run_borrowerCommands_dispatchesWithoutConsumingEquipmentDetails() {
        for (String input : List.of("loans A0123456X",
                "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu")) {
            Inventory inventory = new Inventory();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            new LabLog(inventory, new LoanBook(), List::of).run(new Scanner(input + "\nhelp\nexit\n"),
                    new PrintStream(output));
            assertFalse(output.toString().contains("is not available in this console"));
            assertTrue(output.toString().contains("Common Version 1 commands:"));
            assertFalse(output.toString().contains("Enter name:"));
            assertTrue(inventory.getEquipmentUnits().isEmpty());
        }
    }

    @Test
    void run_loanCommand_issuesLoanInsteadOfRegisteringEquipment() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab"));
        LoanBook loans = new LoanBook();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new LabLog(inventory, loans, List::of).run(
                new Scanner("add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                        + "loan OSC-001 b/A0123456X d/2099-01-01\nexit\n"),
                new PrintStream(output));

        assertTrue(output.toString().contains("Loaned OSC-001"));
        assertFalse(output.toString().contains("Enter name:"));
        assertTrue(loans.hasActiveLoan("OSC-001"));
    }

    @Test
    void run_listCommand_displaysAllRegisteredEquipmentDetails() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab 1"));
        inventory.addEquipment(new Equipment("MIC-002", "Microscope", "Optics", "M2", "Lab 2"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new LabLog(inventory, new LoanBook(), List::of).run(
                new Scanner("list\nexit\n"), new PrintStream(output));

        String result = output.toString();
        assertTrue(result.contains("Asset ID | Name | Category | Model | Location"));
        assertTrue(result.contains("OSC-001 | Scope | Electronics | S1 | Lab 1"));
        assertTrue(result.contains("MIC-002 | Microscope | Optics | M2 | Lab 2"));
    }

    @Test
    void run_listCommand_reportsWhenInventoryIsEmpty() {
        Inventory inventory = new Inventory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new LabLog(inventory, new LoanBook(), List::of).run(
                new Scanner("list\nexit\n"), new PrintStream(output));

        assertTrue(output.toString().contains("No equipment registered."));
    }

    @Test
    void run_listCommand_rejectsUnexpectedArguments() {
        Inventory inventory = new Inventory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new LabLog(inventory, new LoanBook(), List::of).run(
                new Scanner("list OSC-001\nexit\n"), new PrintStream(output));

        assertTrue(output.toString().contains("Error: Use: list"));
        assertTrue(inventory.getEquipmentUnits().isEmpty());
    }

    @Test
    void run_invalidAssetInput_recoversAndRegistersNextValidUnit() {
        for (String input : List.of("", "   ", "OSC 001", "unknown command", "exit extra")) {
            Inventory inventory = new Inventory();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            new LabLog(inventory, new LoanBook(), List::of).run(new Scanner(
                    input + "\nadd OSC-001\nScope\nElectronics\nS1\nLab\nhistory OSC-001\nexit\n"),
                    new PrintStream(output));
            assertTrue(output.toString().contains("Error: Unknown command. Use help"));
            assertTrue(output.toString().contains("No history found for OSC-001 - Scope."));
            assertEquals(1, inventory.getEquipmentUnits().size());
            assertEquals("OSC-001", inventory.getEquipmentUnits().get(0).getAssetId());
        }
    }
}
