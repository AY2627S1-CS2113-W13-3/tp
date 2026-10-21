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
        new LabLog(inventory, loans, List::of).run(new Scanner("list sort/name\nexit\n"), new PrintStream(output));
        assertTrue(output.toString().contains("Lab | Available"));
        assertEquals(1, inventory.getEquipmentUnits().size());
    }

    @Test
    void execute_supportedSortFields_useStableTiesWithoutChangingInventory() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("B-001", "Scope", "Optics", "S1", "Room C"));
        inventory.addEquipment(new Equipment("A-002", "probe", "Electronics", "S2", "Room A"));
        inventory.addEquipment(new Equipment("A-001", "Probe", "Electronics", "S1", "Room B"));
        LoanBook loans = new LoanBook();
        loans.addLoan(new Loan("B-001", "A0123456X", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)));
        EquipmentListCommands commands = new EquipmentListCommands(inventory, loans);

        String[][] sortCases = {
            {"asset-id", "A-001,A-002,B-001"},
            {"name", "A-001,A-002,B-001"},
            {"category", "A-001,A-002,B-001"},
            {"model", "A-001,B-001,A-002"},
            {"location", "A-002,A-001,B-001"},
            {"availability", "A-001,A-002,B-001"}
        };
        for (String[] sortCase : sortCases) {
            List<String> assetIds = commands.execute("list sort/" + sortCase[0]).lines().skip(1)
                    .map(row -> row.substring(0, row.indexOf(" | "))).toList();
            assertEquals(List.of(sortCase[1].split(",")), assetIds, sortCase[0]);
        }
        assertEquals(List.of("B-001", "A-002", "A-001"), inventory.getEquipmentUnits().stream()
                .map(Equipment::getAssetId).toList());
    }

    @Test
    void execute_invalidSortFieldOrSyntax_explainsSupportedFields() {
        EquipmentListCommands commands = new EquipmentListCommands(new Inventory(), new LoanBook());

        IllegalArgumentException unsupported = assertThrows(IllegalArgumentException.class,
                () -> commands.execute("list sort/owner"));
        assertTrue(unsupported.getMessage().contains("asset-id, name, category, model, location, availability"));
        for (String input : new String[]{"list sort/", "list sort/name extra", "list category/Electronics"}) {
            assertEquals("Use: list [sort/FIELD]",
                    assertThrows(IllegalArgumentException.class, () -> commands.execute(input)).getMessage());
        }
    }
}
