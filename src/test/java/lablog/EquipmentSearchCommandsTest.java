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

/** Verifies partial name/model search, case handling, empty results, and read-only behavior. */
class EquipmentSearchCommandsTest {
    @Test
    void execute_partialNameOrModel_matchesIgnoringCaseAndKeepsInventoryOrder() {
        Inventory inventory = new Inventory();
        Equipment first = new Equipment("OSC-001", "Digital Oscilloscope", "Electronics", "ScopeMate", "Lab A");
        Equipment second = new Equipment("CAM-002", "Camera", "Electronics", "scope cam", "Lab B");
        Equipment third = new Equipment("CAB-003", "Cable", "Accessories", "USB-C", "Lab A");
        inventory.addEquipment(first);
        inventory.addEquipment(second);
        inventory.addEquipment(third);
        LoanBook loans = new LoanBook();
        Loan loan = new Loan("CAM-002", "A0123456X", LocalDate.now(), LocalDate.now().plusDays(1));
        loans.addLoan(loan);
        EquipmentSearchCommands commands = new EquipmentSearchCommands(inventory, loans, new FaultBook(inventory));

        String result = commands.execute("search ScOpE");

        assertEquals("Asset ID | Name | Category | Model | Location | Availability"
                + "\nOSC-001 | Digital Oscilloscope | Electronics | ScopeMate | Lab A | Available"
                + "\nCAM-002 | Camera | Electronics | scope cam | Lab B | On loan", result);
        assertEquals(List.of(first, second, third), inventory.getEquipmentUnits());
        assertSame(loan, loans.getLoans().get(0));
        assertTrue(loan.isActive());
    }

    @Test
    void execute_multiWordQuery_normalizesWhitespaceAndFindsNameOrModelPhrase() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Digital Oscilloscope", "Electronics", "ScopeMate", "Lab A"));
        EquipmentSearchCommands commands = new EquipmentSearchCommands(inventory, new LoanBook(),
                new FaultBook(inventory));

        assertTrue(commands.execute("search digital   oscilloscope").contains("OSC-001 | Digital Oscilloscope"));
        assertTrue(commands.execute("search  scopemate ").contains("OSC-001 | Digital Oscilloscope"));
    }

    @Test
    void execute_emptyAndUnmatchedSearches_reportClearResults() {
        Inventory inventory = new Inventory();
        EquipmentSearchCommands commands = new EquipmentSearchCommands(inventory, new LoanBook(),
                new FaultBook(inventory));

        assertEquals("No equipment registered.", commands.execute("search scope"));

        inventory.addEquipment(new Equipment("OSC-001", "Digital Oscilloscope", "Electronics", "S1", "Lab A"));
        assertEquals("No equipment matches 'microscope'.", commands.execute("search microscope"));
    }

    @Test
    void execute_invalidSyntax_rejectsWithoutMutation() {
        Inventory inventory = new Inventory();
        Equipment equipment = new Equipment("OSC-001", "Digital Oscilloscope", "Electronics", "S1", "Lab A");
        inventory.addEquipment(equipment);
        EquipmentSearchCommands commands = new EquipmentSearchCommands(inventory, new LoanBook(),
                new FaultBook(inventory));

        for (String input : new String[] {null, "", "search", "search   ", "searchx scope", "find OSC-001"}) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input), String.valueOf(input));
        }
        assertSame(equipment, inventory.getEquipmentUnits().get(0));
    }

    @Test
    void run_searchCommand_showsResultsAndRecoversFromEmptyQuery() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Digital Oscilloscope", "Electronics", "S1", "Lab A"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new LabLog(inventory, new LoanBook(), List::of).run(
                new Scanner("search scope\nsearch\nexit\n"), new PrintStream(output));

        assertTrue(output.toString().contains("OSC-001 | Digital Oscilloscope"));
        assertTrue(output.toString().contains("Error: Use: search TEXT"));
        assertEquals(1, inventory.getEquipmentUnits().size());
    }
}
