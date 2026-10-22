package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Verifies return validation, local dates, fault descriptions, and shared-record integration. */
class ReturnCommandsTest {
    // Already October 13 in Singapore, while the UTC date is still October 12.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T16:30:00Z"),
            ZoneId.of("Asia/Singapore"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
    private final Inventory inventory = new Inventory();
    private final LoanBook loans = new LoanBook();
    private final Loan loan = new Loan("OSC-001", "A0123456X", TODAY.minusDays(2), TODAY.plusDays(5));
    private final ReturnCommands commands = new ReturnCommands(inventory, loans, CLOCK);

    /** Registers the equipment and an active loan for command tests. */
    private void setUpLoan() {
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab"));
        loans.addLoan(loan);
    }

    @Test
    void execute_noFaults_usesLocalDateAndKeepsRecord() {
        setUpLoan();
        assertEquals("Returned OSC-001 on 2026-10-13. No faults reported on return.",
                commands.execute("return OSC-001"));
        assertEquals(TODAY, loan.getReturnDate());
        assertEquals("", loan.getReturnFaults());
        assertFalse(loan.isActive());
        assertEquals(List.of(loan), loans.getLoans());
    }

    @Test
    void execute_multipleUnits_returnsAllAndKeepsRecords() {
        setUpLoan();
        inventory.addEquipment(
                new Equipment("OSC-002", "Scope 2", "Electronics", "S2", "Lab"));
        Loan second = new Loan("OSC-002", "A0123456X", TODAY, TODAY.plusDays(1));
        loans.addLoan(second);

        assertEquals(
                "Returned OSC-001, OSC-002 on 2026-10-13. No faults reported on return.",
                commands.execute("return OSC-001 OSC-002"));

        assertFalse(loan.isActive());
        assertFalse(second.isActive());
        assertEquals(TODAY, loan.getReturnDate());
        assertEquals(TODAY, second.getReturnDate());
        assertEquals("", loan.getReturnFaults());
        assertEquals("", second.getReturnFaults());
        assertEquals(List.of(loan, second), loans.getLoans());
    }

    @Test
    void execute_laterSelectionHasFutureLoan_changesNeitherLoan() {
        setUpLoan();
        inventory.addEquipment(
                new Equipment("OSC-002", "Scope 2", "Electronics", "S2", "Lab"));
        Loan future = new Loan(
                "OSC-002", "A0123456X", TODAY.plusDays(1), TODAY.plusDays(2));
        loans.addLoan(future);

        assertThrows(IllegalArgumentException.class,
                () -> commands.execute("return OSC-001 OSC-002"));

        assertTrue(loan.isActive());
        assertTrue(future.isActive());
        assertNull(loan.getReturnDate());
        assertNull(future.getReturnDate());
        assertEquals("", loan.getReturnFaults());
        assertEquals("", future.getReturnFaults());
    }

    @Test
    void execute_duplicateAssetIds_leavesLoanUnchanged() {
        setUpLoan();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commands.execute("return OSC-001 OSC-001"));

        assertEquals("Duplicate asset ID: OSC-001.", exception.getMessage());
        assertTrue(loan.isActive());
        assertNull(loan.getReturnDate());
        assertEquals("", loan.getReturnFaults());
    }

    @Test
    void execute_batchWithFaults_changesNeitherLoan() {
        setUpLoan();
        inventory.addEquipment(
                new Equipment("OSC-002", "Scope 2", "Electronics", "S2", "Lab"));
        Loan second = new Loan("OSC-002", "A0123456X", TODAY, TODAY.plusDays(1));
        loans.addLoan(second);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commands.execute(
                        "return OSC-001 OSC-002 faults/cracked screen"));

        assertEquals("Return units with reported faults individually.",
                exception.getMessage());
        assertTrue(loan.isActive());
        assertTrue(second.isActive());
        assertNull(loan.getReturnDate());
        assertNull(second.getReturnDate());
        assertEquals("", loan.getReturnFaults());
        assertEquals("", second.getReturnFaults());
    }

    @Test
    void execute_laterSelectionAlreadyReturned_preservesBothRecords() {
        setUpLoan();
        inventory.addEquipment(
                new Equipment("OSC-002", "Scope 2", "Electronics", "S2", "Lab"));

        Loan returned = new Loan(
                "OSC-002", "A0123456X", TODAY.minusDays(3), TODAY.plusDays(1));
        LocalDate originalReturnDate = TODAY.minusDays(1);
        returned.markReturned(originalReturnDate, "Damaged cable");
        loans.addLoan(returned);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commands.execute("return OSC-001 OSC-002"));

        assertEquals("Equipment OSC-002 is not currently on loan.",
                exception.getMessage());

        assertTrue(loan.isActive());
        assertNull(loan.getReturnDate());
        assertEquals("", loan.getReturnFaults());

        assertFalse(returned.isActive());
        assertEquals(originalReturnDate, returned.getReturnDate());
        assertEquals("Damaged cable", returned.getReturnFaults());
        assertEquals(List.of(loan, returned), loans.getLoans());
    }

    @Test
    void execute_faults_updatesHistoryAndBorrowerListing() throws Exception {
        setUpLoan();
        BorrowerBook borrowers = new BorrowerBook();
        borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
        BorrowerCommands borrowerCommands = new BorrowerCommands(borrowers, loans, id -> "Scope", CLOCK);
        assertTrue(borrowerCommands.execute("loans A0123456X").contains("1 active loan."));
        assertEquals("Returned OSC-001 on 2026-10-13. Faults: cracked screen; loose cable",
                commands.execute("  return\tOSC-001 faults/ cracked screen; loose cable  "));
        assertEquals("cracked screen; loose cable", loan.getReturnFaults());
        assertTrue(new HistoryCommands(inventory, loans, List::of).execute("history OSC-001")
                .contains("returned 2026-10-13; faults: cracked screen; loose cable"));
        assertTrue(borrowerCommands.execute("loans A0123456X").startsWith("No active loans"));
    }

    @Test
    void execute_invalidCommands_leavesLoanUnchanged() {
        setUpLoan();
        for (String input : new String[]{null, "", "return", "returns OSC-001", "return OSC-001 extra",
            "return OSC-001 faults/", "return OSC-001 faults/   ", "return OSC-001 faults/a faults/b",
            "return OSC-001 faults/cracked\nscreen", "return missing", "return osc-001"}) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input), input);
            assertTrue(loan.isActive());
            assertNull(loan.getReturnDate());
            assertEquals("", loan.getReturnFaults());
        }
    }

    @Test
    void execute_repeatedReturn_preservesOriginalDetails() {
        setUpLoan();
        commands.execute("return OSC-001 faults/cracked screen");
        assertThrows(IllegalArgumentException.class, () -> commands.execute("return OSC-001"));
        assertEquals(TODAY, loan.getReturnDate());
        assertEquals("cracked screen", loan.getReturnFaults());
    }

    @Test
    void execute_multipleActiveLoans_rejectsWithoutChangingEither() {
        setUpLoan();
        Loan second = new Loan("OSC-001", "A0123457X", TODAY, TODAY.plusDays(1));
        loans.addLoan(second);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> commands.execute("return OSC-001"))
                .getMessage().contains("Multiple active loans"));
        assertTrue(loan.isActive());
        assertTrue(second.isActive());
    }

    @Test
    void execute_otherAndHistoricalLoans_returnsOnlyMatchingActiveLoan() {
        setUpLoan();
        Loan old = new Loan("OSC-001", "A0123456X", TODAY.minusDays(10), TODAY.minusDays(5));
        old.markReturned(TODAY.minusDays(6), "old fault");
        Loan other = new Loan("OSC-002", "A0123456X", TODAY, TODAY);
        loans.addLoan(old);
        loans.addLoan(other);
        commands.execute("return OSC-001");
        assertFalse(loan.isActive());
        assertTrue(other.isActive());
        assertEquals("old fault", old.getReturnFaults());
        assertEquals(TODAY.minusDays(6), old.getReturnDate());
    }

    @Test
    void execute_futureLoan_rejectsReturnBeforeLoanDate() {
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab"));
        Loan future = new Loan("OSC-001", "A0123456X", TODAY.plusDays(1), TODAY.plusDays(2));
        loans.addLoan(future);
        assertThrows(IllegalArgumentException.class, () -> commands.execute("return OSC-001 faults/cracked"));
        assertTrue(future.isActive());
        assertEquals("", future.getReturnFaults());
    }

    @Test
    void markReturned_invalidDetailsAndRepeat_doNotMutateLoan() {
        assertThrows(IllegalArgumentException.class, () -> loan.markReturned(null, "cracked"));
        assertThrows(IllegalArgumentException.class, () -> loan.markReturned(TODAY, null));
        assertThrows(IllegalArgumentException.class, () -> loan.markReturned(TODAY, "bad\ninput"));
        assertTrue(loan.isActive());
        assertEquals("", loan.getReturnFaults());
        loan.markReturned(loan.getLoanDate(), "cracked");
        assertThrows(IllegalArgumentException.class, () -> loan.markReturned(TODAY));
        assertEquals(loan.getLoanDate(), loan.getReturnDate());
        assertEquals("cracked", loan.getReturnFaults());
    }

    @Test
    void run_invalidThenValidReturn_updatesHistoryWithoutAddingEquipment() {
        setUpLoan();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog(inventory, loans, List::of, CLOCK).run(new Scanner(
                "return OSC-001 faults/\nreturn OSC-001 faults/cracked screen\nhistory OSC-001\nexit\n"),
                new PrintStream(output));
        assertTrue(output.toString().contains("Error:"));
        assertTrue(output.toString().contains("Returned OSC-001 on 2026-10-13. Faults: cracked screen"));
        assertTrue(output.toString().contains("returned 2026-10-13; faults: cracked screen"));
        assertEquals(1, inventory.getEquipmentUnits().size());
    }
}
