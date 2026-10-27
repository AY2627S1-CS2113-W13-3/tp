package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Verifies bounded session history and confirms recall does not replay mutating commands. */
class CommandHistoryTest {
    @Test
    void accept_boundsAndInvalidIndexes_preservesRecentLines() {
        CommandHistory history = new CommandHistory();
        assertEquals("No recent commands.", history.accept("commands"));
        for (String input : List.of("recall", "recall 0", "recall -1", "recall 1", "recall x",
                "recall 2147483648", "recall 1 extra", "commands extra")) {
            assertThrows(IllegalArgumentException.class, () -> history.accept(input));
        }
        assertNull(history.accept(" "));
        assertNull(history.accept("exit"));
        for (int index = 1; index <= 21; index++) {
            assertNull(history.accept("invalid-command-" + index));
        }
        assertTrue(history.accept("recall 1").contains("invalid-command-2\n"));
        assertTrue(history.accept("recall 20").contains("invalid-command-21\n"));
        assertFalse(history.accept("commands").contains("invalid-command-1\n"));
        assertThrows(IllegalArgumentException.class, () -> history.accept("recall 21"));
        assertEquals("No recent commands.", new CommandHistory().accept("commands"));
    }

    @Test
    void run_recallThenExplicitEdit_onlySubmissionChangesRecords() throws Exception {
        BorrowerBook borrowers = new BorrowerBook();
        borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LabLog app = new LabLog(new Inventory(), new LoanBook(), List::of, Clock.systemDefaultZone(), borrowers);
        app.run(new Scanner("edit-borrower A0123456X n/First\nrecall 1\n"
                + "edit-borrower A0123456X n/Second\ncommands\nexit\n"), new PrintStream(output));
        assertEquals("Second", borrowers.getBorrower("A0123456X").getName());
        assertEquals(2, output.toString().split("Updated borrower", -1).length - 1);
        assertTrue(output.toString().contains("Recalled (not executed):"));
    }

    @Test
    void run_wizardAnswersAndRecall_areNotStoredAsCommands() {
        Inventory inventory = new Inventory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog(inventory, new LoanBook(), List::of).run(new Scanner(
                "add OSC-001\nScope\nElectronics\nS1\nLab\nrecall 1\ncommands\nexit\n"), new PrintStream(output));
        assertEquals(1, inventory.getEquipmentUnits().size());
        assertTrue(output.toString().contains("Recent commands (oldest first):\n1: add OSC-001"));
        assertFalse(output.toString().contains("2: Scope"));
    }

    @Test
    void run_borrowerConsole_recallDoesNotRepeatRegistration() {
        BorrowerBook borrowers = new BorrowerBook();
        BorrowerCommands commands = new BorrowerCommands(borrowers, new LoanBook(), id -> null,
                Clock.systemDefaultZone());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BorrowerCli.run(new Scanner("add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                + "recall 1\nexit\n"), new PrintStream(output), commands);
        assertEquals(1, borrowers.getBorrowers().size());
        assertFalse(output.toString().contains("already exists"));
    }
}
