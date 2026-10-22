package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Scanner;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies restarts, complete return details, corrupt input, and save-failure behavior. */
class LabLogStoreTest {
    @Test
    void save_reload_preservesEquipmentAndCompleteLoanHistory(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("records.properties");
        LabLogStore store = new LabLogStore(file);
        store.getInventory().addEquipment(new Equipment("OSC-001", "Scope Ω", "Electronics", "S1", "Lab A"));
        Loan returned = new Loan("OSC-001", "A0123456X", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5));
        returned.markReturned(LocalDate.of(2026, 1, 4), "Cracked screen = inspect");
        store.getLoans().addLoan(returned);
        store.getLoans().addLoan(new Loan("OSC-001", "A0123457X",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5)));
        store.save();
        LabLogStore loaded = new LabLogStore(file);
        assertEquals("Scope Ω", loaded.getInventory().getEquipmentUnits().get(0).getName());
        assertEquals(2, loaded.getLoans().getLoans().size());
        assertEquals(LocalDate.of(2026, 1, 4), loaded.getLoans().getLoans().get(0).getReturnDate());
        assertEquals("Cracked screen = inspect", loaded.getLoans().getLoans().get(0).getReturnFaults());
        assertEquals("A0123457X", loaded.getLoans().findActiveLoan("OSC-001").orElseThrow().getBorrowerId());
        String saved = Files.readString(file);
        String invalidDate = saved.replace("2026-01-05", "not-a-date");
        Files.writeString(file, invalidDate);
        assertThrows(IOException.class, () -> new LabLogStore(file));
        assertEquals(invalidDate, Files.readString(file));
    }

    @Test
    void run_batchReturn_persistsBothReturnsAfterReload(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("records.properties");
        LabLogStore store = new LabLogStore(file);
        LocalDate issued = LocalDate.of(2026, 1, 1);

        for (String assetId : List.of("OSC-001", "OSC-002")) {
            store.getInventory().addEquipment(
                    new Equipment(assetId, "Scope", "Electronics", "S1", "Lab"));
            store.getLoans().addLoan(
                    new Loan(assetId, "A0123456X", issued, issued.plusDays(7)));
        }
        store.save();

        BorrowerBook borrowers = new BorrowerBook(directory.resolve("borrowers.properties"));
        borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LabLog.load(file).run(
                new Scanner("return OSC-001 OSC-002\nexit\n"),
                new PrintStream(output));

        assertTrue(output.toString().contains("Returned OSC-001, OSC-002 on "));

        LabLogStore reloaded = new LabLogStore(file);
        List<Loan> savedLoans = reloaded.getLoans().getLoans();
        assertEquals(2, savedLoans.size());
        assertEquals(List.of("OSC-001", "OSC-002"),
                savedLoans.stream().map(Loan::getAssetId).toList());

        HistoryCommands history = new HistoryCommands(
                reloaded.getInventory(), reloaded.getLoans(), List::of);

        for (Loan savedLoan : savedLoans) {
            assertFalse(savedLoan.isActive());
            assertNotNull(savedLoan.getReturnDate());
            assertEquals("", savedLoan.getReturnFaults());
            assertEquals(issued, savedLoan.getLoanDate());
            assertEquals(issued.plusDays(7), savedLoan.getDueDate());
            assertEquals("A0123456X", savedLoan.getBorrowerId());
            assertTrue(history.execute("history " + savedLoan.getAssetId())
                    .contains("returned " + savedLoan.getReturnDate()));
        }

        assertEquals(savedLoans.get(0).getReturnDate(),
                savedLoans.get(1).getReturnDate());
    }

    @Test
    void load_corruptFile_rejectsWithoutOverwriting(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("records.properties");
        for (String invalid : List.of("", "format=2\nequipment.count=0\nloan.count=0",
                "format=1\nequipment.count=1\nloan.count=0", "format=1\nequipment.count=-1\nloan.count=0")) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, () -> LabLog.load(file));
            assertEquals(invalid, Files.readString(file));
        }
    }

    @Test
    void load_persistsBorrowersInCompleteSnapshot(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("records.properties");
        Path borrowerFile = directory.resolve("borrowers.properties");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LabLog.load(file).run(new Scanner("add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\nexit\n"),
                new PrintStream(output));

        assertFalse(Files.exists(borrowerFile));
        assertEquals("Jamie", ApplicationSnapshot.read(file).getBorrowers().get(0).getName());
        output.reset();
        LabLog.load(file).run(new Scanner("loans A0123456X\nexit\n"), new PrintStream(output));
        assertTrue(output.toString().contains("No active loans for A0123456X - Jamie."));
    }

    @Test
    void run_saveFailure_stopsWithoutSuccessOrOverwriting(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("records.properties");
        LabLog application = LabLog.load(file);
        // Turn the destination into a non-empty directory to force a portable replacement failure.
        Files.createDirectory(file);
        Files.writeString(file.resolve("keep.txt"), "keep");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        application.run(new Scanner("add OSC-001\nScope\nElectronics\nS1\nLab\nhelp\n"), new PrintStream(output));
        assertTrue(output.toString().contains("Changes could not be saved"));
        assertFalse(output.toString().contains("Equipment added to inventory."));
        assertEquals(1, output.toString().split("Common Version 1 commands:", -1).length - 1);
        assertEquals("keep", Files.readString(file.resolve("keep.txt")));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }
}
