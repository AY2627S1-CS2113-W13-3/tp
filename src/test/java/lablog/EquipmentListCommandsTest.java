package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Verifies equipment listing filters, live availability, and main-console routing. */
class EquipmentListCommandsTest {
    @Test
    void execute_changedLoans_updatesAvailability() {
        Inventory inventory = new Inventory();
        LoanBook loans = new LoanBook();
        EquipmentListCommands commands = new EquipmentListCommands(inventory, loans);
        assertEquals("No equipment registered.", commands.execute("list"));
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab"));
        assertTrue(commands.execute("list").endsWith("Lab | Available"));
        Loan loan = new Loan("OSC-001", "A0123456X", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));
        loans.addLoan(loan);
        assertTrue(commands.execute("list").endsWith("Lab | On loan"));
        loan.markReturned(LocalDate.of(2026, 1, 2));
        assertTrue(commands.execute("list").endsWith("Lab | Available"));
        assertThrows(IllegalArgumentException.class, () -> commands.execute("list extra"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog(inventory, loans, List::of).run(new Scanner("list\nexit\n"), new PrintStream(output));
        assertTrue(output.toString().contains("Lab | Available"));
        assertEquals(1, inventory.getEquipmentUnits().size());
    }

    @Test
    void execute_filtersByCategoryLocationAndStatus() {
        Inventory inventory = new Inventory();
        LoanBook loans = new LoanBook();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab A"));
        inventory.addEquipment(new Equipment("CAM-001", "Camera", "Electronics", "C1", "Lab B"));
        inventory.addEquipment(new Equipment("FAN-001", "Fan", "Cooling", "F1", "Lab A"));
        loans.addLoan(new Loan("CAM-001", "A0123456X", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)));
        EquipmentListCommands commands = new EquipmentListCommands(inventory, loans);

        assertEquals("Asset ID | Name | Category | Model | Location | Availability\n"
                + "OSC-001 | Scope | Electronics | S1 | Lab A | Available",
                commands.execute("list category/electronics location/Lab A status/available"));
        assertTrue(commands.execute("list status/on loan").contains("CAM-001 | Camera"));
        assertEquals(3, inventory.getEquipmentUnits().size());
    }

    @Test
    void execute_noMatches_returnsMessage() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab A"));

        assertEquals("No equipment matches the supplied filters.",
                new EquipmentListCommands(inventory, new LoanBook()).execute("list category/Cooling"));
    }

    @Test
    void execute_invalidFilters_areRejected() {
        EquipmentListCommands commands = new EquipmentListCommands(new Inventory(), new LoanBook());
        for (String input : List.of("list category", "list category/", "list type/Electronics",
                "list category/Electronics category/Cooling", "list status/reserved")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input), input);
        }
    }

    @Test
    void execute_reservationAndFaultNotes_doNotChangeLoanStatus() {
        Inventory inventory = new Inventory();
        LoanBook loans = new LoanBook();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab A"));
        List<HistoryEntry> notes = List.of(
                new HistoryEntry("OSC-001", LocalDate.of(2026, 10, 20), HistoryEntry.Type.FAULT, "Cracked screen"),
                new HistoryEntry("OSC-001", LocalDate.of(2026, 10, 22), HistoryEntry.Type.RESERVATION, "Teaching"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new LabLog(inventory, loans, () -> notes).run(
                new Scanner("list status/available\nexit\n"), new PrintStream(output));

        assertTrue(output.toString().contains("OSC-001 | Scope | Electronics | S1 | Lab A | Available"));
    }
}
