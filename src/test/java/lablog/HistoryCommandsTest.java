package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Verifies equipment history filtering, ordering, live updates, and console integration. */
class HistoryCommandsTest {
    private final Inventory inventory = new Inventory();
    private final LoanBook loans = new LoanBook();
    private final List<HistoryEntry> otherHistory = new ArrayList<>();
    private final HistoryCommands commands = new HistoryCommands(inventory, loans, () -> otherHistory);

    private void addEquipment() {
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab"));
    }

    @Test
    void execute_mixedRecords_filtersAndSortsAllTypes() {
        addEquipment();
        Loan returned = new Loan("OSC-001", "A0123456X", LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-08"));
        returned.markReturned(LocalDate.parse("2026-10-07"));
        loans.addLoan(new Loan("OTHER", "A0123456X", LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-08")));
        loans.addLoan(returned);
        otherHistory.add(new HistoryEntry("OSC-001", LocalDate.parse("2026-10-09"),
                HistoryEntry.Type.MAINTENANCE, "Power supply replaced"));
        otherHistory.add(new HistoryEntry("OTHER", LocalDate.parse("2026-10-01"),
                HistoryEntry.Type.FAULT, "Other equipment fault"));
        otherHistory.add(new HistoryEntry("OSC-001", LocalDate.parse("2026-10-08"),
                HistoryEntry.Type.FAULT, "Power supply failed"));
        otherHistory.add(new HistoryEntry("OSC-001", LocalDate.parse("2026-10-15"),
                HistoryEntry.Type.RESERVATION, "Borrower A0123457X; confirmed"));

        assertEquals("History for OSC-001 - Scope:\nDate | Type | Details\n"
                + "2026-10-01 | LOAN | Borrower A0123456X; due 2026-10-08; returned 2026-10-07"
                + "; no faults reported on return\n"
                + "2026-10-08 | FAULT | Power supply failed\n"
                + "2026-10-09 | MAINTENANCE | Power supply replaced\n"
                + "2026-10-15 | RESERVATION | Borrower A0123457X; confirmed\n4 records.",
                commands.execute("  history\tOSC-001  "));
        assertEquals("OTHER", loans.getLoans().get(0).getAssetId());
        assertEquals(HistoryEntry.Type.MAINTENANCE, otherHistory.get(0).getType());
    }

    @Test
    void execute_changedSources_reflectsReturnAndNewRecords() {
        addEquipment();
        LocalDate date = LocalDate.parse("2026-10-01");
        Loan loan = new Loan("OSC-001", "A0123456X", date, date.plusDays(7));
        loans.addLoan(loan);
        assertTrue(commands.execute("history OSC-001").contains("; active\n1 record."));
        loan.markReturned(date.plusDays(1));
        otherHistory.add(new HistoryEntry("OSC-001", date, HistoryEntry.Type.FAULT, "Reported"));
        String result = commands.execute("history OSC-001");
        assertTrue(result.contains("; returned 2026-10-02"));
        assertTrue(result.endsWith("2 records."));
        assertFalse(result.contains("; active"));
    }

    @Test
    void execute_sameDate_usesTypeThenDescription() {
        addEquipment();
        LocalDate date = LocalDate.parse("2026-10-01");
        otherHistory.add(new HistoryEntry("OSC-001", date, HistoryEntry.Type.FAULT, "Z fault"));
        otherHistory.add(new HistoryEntry("OSC-001", date, HistoryEntry.Type.MAINTENANCE, "Repair"));
        otherHistory.add(new HistoryEntry("OSC-001", date, HistoryEntry.Type.FAULT, "A fault"));
        otherHistory.add(new HistoryEntry("OSC-001", date, HistoryEntry.Type.RESERVATION, "Booked"));
        String result = commands.execute("history OSC-001");
        assertTrue(result.indexOf("Booked") < result.indexOf("A fault"));
        assertTrue(result.indexOf("A fault") < result.indexOf("Z fault"));
        assertTrue(result.indexOf("Z fault") < result.indexOf("Repair"));
    }

    @Test
    void execute_emptyAndUnknownEquipment_distinguishesResults() {
        addEquipment();
        assertEquals("No history found for OSC-001 - Scope.", commands.execute("history OSC-001"));
        assertEquals("Equipment osc-001 was not found.",
                assertThrows(IllegalArgumentException.class, () -> commands.execute("history osc-001")).getMessage());
    }

    @Test
    void execute_invalidSyntax_rejectsInput() {
        for (String input : new String[]{null, "", "history", "history OSC-001 extra", "loans OSC-001"}) {
            assertEquals("Use: history ASSET_ID",
                    assertThrows(IllegalArgumentException.class, () -> commands.execute(input)).getMessage());
        }
    }

    @Test
    void run_registrationAndHistory_recoversFromErrorsAndExits() {
        LocalDate date = LocalDate.parse("2026-10-01");
        loans.addLoan(new Loan("OSC-001", "A0123456X", date, date.plusDays(7)));
        otherHistory.add(new HistoryEntry("OSC-001", date, HistoryEntry.Type.RESERVATION, "Booked"));
        otherHistory.add(new HistoryEntry("OSC-001", date, HistoryEntry.Type.FAULT, "Fault"));
        otherHistory.add(new HistoryEntry("OSC-001", date, HistoryEntry.Type.MAINTENANCE, "Repair"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog(inventory, loans, () -> otherHistory).run(new Scanner(
                "history missing\nhistory\nOSC-001\nScope\nElectronics\nS1\nLab\nhistory OSC-001\nexit\n"),
                new PrintStream(output));
        String result = output.toString();
        assertTrue(result.contains("Error: Equipment missing was not found."));
        assertTrue(result.contains("Error: Use: history ASSET_ID"));
        assertTrue(result.contains("Equipment added to inventory."));
        assertTrue(result.contains("4 records."));
        assertEquals(1, inventory.getEquipmentUnits().size());
    }

    @Test
    void run_incompleteRegistration_doesNotStoreEquipment() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog(inventory, loans, () -> otherHistory).run(new Scanner("OSC-001\nScope\n"),
                new PrintStream(output));
        assertTrue(inventory.getEquipmentUnits().isEmpty());
        assertTrue(output.toString().contains("Equipment not added: incomplete input."));
    }

    @Test
    void constructor_invalidEntry_rejectsIncompleteOrMultilineData() {
        LocalDate date = LocalDate.parse("2026-10-01");
        assertThrows(IllegalArgumentException.class,
                () -> new HistoryEntry("", date, HistoryEntry.Type.FAULT, "Fault"));
        assertThrows(IllegalArgumentException.class,
                () -> new HistoryEntry("OSC-001", date, HistoryEntry.Type.FAULT, "Fault\nextra"));
        assertThrows(NullPointerException.class,
                () -> new HistoryEntry("OSC-001", null, HistoryEntry.Type.FAULT, "Fault"));
    }
}
