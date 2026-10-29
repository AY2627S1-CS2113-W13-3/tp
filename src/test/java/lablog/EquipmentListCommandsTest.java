package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static lablog.TableText.compact;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Verifies combined filtering and sorting, stable inventory order, and live availability. */
class EquipmentListCommandsTest {
    @Test
    void execute_changedLoans_updatesAvailability() {
        Inventory inventory = new Inventory();
        LoanBook loans = new LoanBook();
        EquipmentListCommands commands = new EquipmentListCommands(inventory, loans);
        assertEquals("No equipment registered.", compact(commands.execute("list")));
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab"));
        assertTrue(compact(commands.execute("list")).endsWith("Lab | Available"));
        Loan loan = new Loan("OSC-001", "A0123456X", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));
        loans.addLoan(loan);
        assertTrue(compact(commands.execute("list")).endsWith("Lab | On loan"));
        loan.markReturned(LocalDate.of(2026, 1, 2));
        assertTrue(compact(commands.execute("list")).endsWith("Lab | Available"));
        assertThrows(IllegalArgumentException.class, () -> compact(commands.execute("list extra")));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog(inventory, loans, List::of).run(new Scanner("list sort/name\nexit\n"), new PrintStream(output));
        assertTrue(compact(output.toString()).contains("Lab | Available"));
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
            List<String> assetIds = compact(commands.execute("list sort/" + sortCase[0])).lines().skip(1)
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
                () -> compact(commands.execute("list sort/owner")));
        assertTrue(unsupported.getMessage().contains("asset-id, name, category, model, location, availability"));
        String[] invalidInputs = {
            null, "", "find", "listx", "list sort/", "list sort/name extra", "list sort/name sort/model",
            "list category/Electronics sort/", "list sort/owner location/Lab A",
            "list sort/name category/Electronics sort/model"
        };
        for (String input : invalidInputs) {
            assertThrows(IllegalArgumentException.class, () -> compact(commands.execute(input)), input);
        }
    }

    @Test
    void execute_combinedOptionsInAnyOrder_filtersAndSortsWithoutMutation() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("Z-003", "Probe", "Electronics", "P3", "Lab A"));
        inventory.addEquipment(new Equipment("B-002", "Scope", "Electronics", "S2", "Lab A"));
        inventory.addEquipment(new Equipment("A-001", "Probe", "Electronics", "P1", "Lab A"));
        inventory.addEquipment(new Equipment("C-004", "Fan", "Cooling", "F4", "Lab A"));
        inventory.addEquipment(new Equipment("E-005", "Cable", "Electronics", "C5", "Lab B"));
        LoanBook loans = new LoanBook();
        Loan loan = new Loan("Z-003", "A0123456X", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));
        loans.addLoan(loan);
        EquipmentListCommands commands = new EquipmentListCommands(inventory, loans);
        List<Equipment> original = inventory.getEquipmentUnits();
        String originalList = compact(commands.execute("list"));
        String header = "Asset ID | Name | Category | Model | Location | Availability";

        for (String input : List.of(
                "list category/electronics location/Lab A status/available sort/name",
                "list sort/name status/available location/Lab A category/Electronics",
                "list location/Lab A sort/name CATEGORY/ELECTRONICS status/available")) {
            assertEquals(header + "\nA-001 | Probe | Electronics | P1 | Lab A | Available"
                    + "\nB-002 | Scope | Electronics | S2 | Lab A | Available",
                    compact(commands.execute(input)), input);
        }
        assertEquals(header + "\nB-002 | Scope | Electronics | S2 | Lab A | Available"
                + "\nA-001 | Probe | Electronics | P1 | Lab A | Available",
                compact(commands.execute("list category/Electronics location/Lab A status/available")));
        assertEquals(original, inventory.getEquipmentUnits());
        assertEquals(originalList, compact(commands.execute("list")));
        assertEquals(List.of(loan), loans.getLoans());
        assertTrue(loan.isActive());
    }

    @Test
    void execute_sortedStatusFilter_reflectsReturnsAndEmptyResults() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("B-002", "Scope", "Electronics", "S2", "Lab A"));
        inventory.addEquipment(new Equipment("A-001", "Probe", "Electronics", "P1", "Lab A"));
        LoanBook loans = new LoanBook();
        Loan loan = new Loan("A-001", "A0123456X", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));
        loans.addLoan(loan);
        EquipmentListCommands commands = new EquipmentListCommands(inventory, loans);
        String header = "Asset ID | Name | Category | Model | Location | Availability";

        assertEquals(header + "\nA-001 | Probe | Electronics | P1 | Lab A | On loan",
                compact(commands.execute("list sort/asset-id status/on loan")));
        loan.markReturned(LocalDate.of(2026, 1, 2));
        assertEquals("No equipment matches the supplied filters.",
                compact(commands.execute("list sort/name status/on-loan")));
        assertEquals(header + "\nA-001 | Probe | Electronics | P1 | Lab A | Available"
                + "\nB-002 | Scope | Electronics | S2 | Lab A | Available",
                compact(commands.execute("list status/available sort/asset-id")));
        assertEquals("No equipment registered.", new EquipmentListCommands(new Inventory(), new LoanBook())
                .execute("list category/Electronics sort/name"));
    }

    @Test
    void execute_equalNamesAndIdCase_usesExactIdAsFinalTieBreaker() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("a-001", "probe", "Electronics", "P1", "Lab A"));
        inventory.addEquipment(new Equipment("A-001", "Probe", "Electronics", "P1", "Lab A"));
        EquipmentListCommands commands = new EquipmentListCommands(inventory, new LoanBook());

        assertEquals(List.of("A-001", "a-001"), compact(commands.execute("list category/Electronics sort/name"))
                .lines().skip(1).map(row -> row.substring(0, row.indexOf(" | "))).toList());
        assertEquals(List.of("a-001", "A-001"), inventory.getEquipmentUnits().stream()
                .map(Equipment::getAssetId).toList());
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
                compact(commands.execute("list category/electronics location/Lab A status/available")));
        assertTrue(compact(commands.execute("list status/on loan")).contains("CAM-001 | Camera"));
        assertEquals(3, inventory.getEquipmentUnits().size());
    }

    @Test
    void execute_noMatches_returnsMessage() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab A"));

        assertEquals("No equipment matches the supplied filters.",
                compact(new EquipmentListCommands(inventory, new LoanBook()).execute("list category/Cooling")));
    }

    @Test
    void execute_invalidFilters_areRejected() {
        EquipmentListCommands commands = new EquipmentListCommands(new Inventory(), new LoanBook());
        for (String input : List.of("list category", "list category/", "list type/Electronics",
                "list category/Electronics category/Cooling", "list status/reserved")) {
            assertThrows(IllegalArgumentException.class, () -> compact(commands.execute(input)), input);
        }
    }

    @Test
    void execute_unavailableStatus_selectsSeverelyFaultedEquipment() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab A"));
        FaultBook faults = new FaultBook(inventory);
        faults.report("OSC-001", LocalDate.of(2026, 10, 20), "Cracked screen", Fault.Severity.SEVERE);
        EquipmentListCommands commands = new EquipmentListCommands(inventory, new LoanBook(), faults);

        assertTrue(compact(commands.execute("list status/unavailable"))
                .contains("OSC-001 | Scope | Electronics | S1 | Lab A | Unavailable: severe fault"));
        assertTrue(compact(commands.execute("list status/unavailable location/lab a"))
                .contains("OSC-001 | Scope"));
        assertEquals("No equipment matches the supplied filters.", compact(commands.execute("list status/available")));
    }

    @Test
    void execute_activeLoanTakesPrecedenceOverSevereFault() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab A"));
        LoanBook loans = new LoanBook();
        loans.addLoan(new Loan("OSC-001", "A0123456X", LocalDate.of(2026, 10, 24),
                LocalDate.of(2026, 10, 28)));
        FaultBook faults = new FaultBook(inventory);
        faults.report("OSC-001", LocalDate.of(2026, 10, 24), "Cracked screen", Fault.Severity.SEVERE);
        EquipmentListCommands commands = new EquipmentListCommands(inventory, loans, faults);

        assertTrue(compact(commands.execute("list status/on-loan")).contains("OSC-001 | Scope"));
        assertEquals("No equipment matches the supplied filters.", commands.execute("list status/unavailable"));
    }

    @Test
    void execute_realReservationDoesNotChangeUndatedOperationalStatus() throws Exception {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab A"));
        LoanBook loans = new LoanBook();
        FaultBook faults = new FaultBook(inventory);
        BorrowerBook borrowers = new BorrowerBook();
        borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
        Clock clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);
        ReservationBook reservations = new ReservationBook(inventory, borrowers, loans, faults, clock);
        reservations.reserve("OSC-001", "A0123456X", new DateWindow(
                LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 26)));
        EquipmentListCommands commands = new EquipmentListCommands(inventory, loans, faults);

        assertTrue(compact(commands.execute("list status/available")).contains("OSC-001 | Scope"));
    }

    @Test
    void execute_legacyReservationAndFaultNotes_doNotChangeOperationalStatus() {
        Inventory inventory = new Inventory();
        LoanBook loans = new LoanBook();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab A"));
        List<HistoryEntry> notes = List.of(
                new HistoryEntry("OSC-001", LocalDate.of(2026, 10, 20), HistoryEntry.Type.FAULT, "Cracked screen"),
                new HistoryEntry("OSC-001", LocalDate.of(2026, 10, 22), HistoryEntry.Type.RESERVATION, "Teaching"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new LabLog(inventory, loans, () -> notes).run(
                new Scanner("list status/available\nexit\n"), new PrintStream(output));

        assertTrue(compact(output.toString()).contains("OSC-001 | Scope | Electronics | S1 | Lab A | Available"));
    }
}
