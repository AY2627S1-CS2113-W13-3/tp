package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises real command dispatch, restart and transfer boundaries for completed handovers. */
class LendingWorkflowTest {
    private static final String USER = "A0123456X";
    private static final String OTHER = "A0123457X";

    private String run(Path data, String input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LabLog.load(data).run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }

    private void setup(Path data) throws Exception {
        String output = run(data, "add OSC n/Scope c/Electronics m/S1 l/Lab\n"
                + "add MIC n/Microphone c/Audio m/M1 l/Lab\n"
                + "add-borrower " + USER + " n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                + "add-borrower " + OTHER + " n/Alex p/91234568 e/alex@u.nus.edu");
        assertFalse(output.contains("Error:"), output);
    }

    @Test
    void returnedUnit_canBeRelentTodayAndSurvivesRestart(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("data.properties");
        setup(data);
        String due = LocalDate.now().plusDays(2).toString();
        String output = run(data, "loan OSC b/" + USER + " d/" + due
                + "\nreturn OSC\nloan OSC b/" + OTHER + " d/" + due);
        assertFalse(output.contains("Error:"), output);
        ApplicationSnapshot saved = ApplicationSnapshot.read(data);
        assertEquals(2, saved.getLoans().size());
        assertFalse(saved.getLoans().getFirst().isActive());
        assertTrue(saved.getLoans().getLast().isActive());
        assertEquals(3, saved.getAuditEvents().size());
    }

    @Test
    void reservationCollection_enforcesBorrowerWindowAndBatchAtomicity(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("data.properties");
        setup(data);
        LocalDate today = LocalDate.now();
        assertFalse(run(data, "reserve OSC b/" + USER + " from/" + today + " to/" + today.plusDays(2))
                .contains("Error:"));
        byte[] before = Files.readAllBytes(data);
        for (String command : java.util.List.of("loan MIC OSC b/" + OTHER + " d/" + today,
                "loan OSC b/" + USER + " d/" + today.plusDays(3))) {
            assertTrue(run(data, command).contains("Error:"), command);
            assertTrue(Arrays.equals(before, Files.readAllBytes(data)));
        }
        assertFalse(run(data, "loan OSC b/" + USER + " d/" + today.plusDays(2)).contains("Error:"));
        assertFalse(ApplicationSnapshot.read(data).getReservations().getFirst().isCancelled());
        assertEquals(USER, ApplicationSnapshot.read(data).getLoans().getFirst().getBorrowerId());
    }

    @Test
    void recovery_retainsEvidenceThroughRestartExportImportAndRejectsRepeats(@TempDir Path directory)
            throws Exception {
        Path data = directory.resolve("data.properties");
        setup(data);
        LocalDate today = LocalDate.now();
        String output = run(data, "loan OSC b/" + USER + " d/" + today.plusDays(2)
                + "\nlost OSC\nrecover OSC");
        assertFalse(output.contains("Error:"), output);
        Loan recovered = ApplicationSnapshot.read(data).getLoans().getFirst();
        assertEquals(today, recovered.getLostDate());
        assertEquals(today, recovered.getReturnDate());
        assertFalse(recovered.isLost());
        assertFalse(recovered.isActive());
        byte[] before = Files.readAllBytes(data);
        for (String command : new String[]{"recover OSC", "recover UNKNOWN", "recover OSC MIC"}) {
            assertTrue(run(data, command).contains("Error:"));
            assertTrue(Arrays.equals(before, Files.readAllBytes(data)));
        }
        Path backup = directory.resolve("backup.properties");
        assertFalse(run(data, "export " + backup).contains("Error:"));
        Path imported = directory.resolve("imported.properties");
        output = run(imported, "import " + backup + " --replace\nloan OSC b/" + OTHER
                + " d/" + today.plusDays(1));
        assertFalse(output.contains("Error:"), output);
        ApplicationSnapshot saved = ApplicationSnapshot.read(imported);
        assertEquals(today, saved.getLoans().getFirst().getLostDate());
        assertEquals(3, saved.getAuditEvents().size());
        assertTrue(saved.getLoans().getLast().isActive());
        assertThrows(IllegalArgumentException.class, () -> recovered.recover(today));
    }
    @Test
    void recovery_rejectsEarlierDatesAndRetainsFaultBlocks() throws Exception {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC", "Scope", "Electronics", "S1", "Lab"));
        LoanBook loans = new LoanBook();
        LocalDate today = LocalDate.now();
        Loan loan = new Loan("OSC", USER, today.minusDays(1), today.plusDays(2));
        loans.addLoan(loan);
        loan.markLost(today);
        assertThrows(IllegalArgumentException.class, () -> loans.recover(loan, today.minusDays(1)));
        assertTrue(loan.isLost());
        assertEquals(1, loans.getAuditEvents().size());
        FaultBook faults = new FaultBook(inventory);
        faults.report("OSC", today, "Broken", Fault.Severity.SEVERE);
        loans.recover(loan, today);
        assertThrows(IllegalArgumentException.class, loan::reverseReturn);
        LoanCommands commands = new LoanCommands(inventory, loans, java.time.Clock.systemDefaultZone(), null, faults);
        assertThrows(IllegalArgumentException.class, () -> commands.execute("loan OSC b/" + USER + " d/" + today));
        assertEquals(1, loans.getLoans().size());
        assertEquals(2, loans.getAuditEvents().size());
    }

}
