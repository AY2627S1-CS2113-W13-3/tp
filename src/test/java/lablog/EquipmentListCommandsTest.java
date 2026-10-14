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

/** Verifies empty inventory, live availability, and main-console routing. */
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
}
