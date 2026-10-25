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
    void run_batchLoan_persistsEveryLoanAndSharedBorrowerAfterReload(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("records.properties");
        LocalDate before = LocalDate.now();
        LocalDate dueDate = before.plusDays(7);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        LabLog.load(file).run(new Scanner("add OSC-001 n/Scope c/Electronics m/S1 l/Lab A\n"
                + "add MIC-002 n/Microscope c/Optics m/M2 l/Lab B\n"
                + "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                + "loan OSC-001 MIC-002 b/A0123456X d/" + dueDate + "\nexit\n"), new PrintStream(output));

        assertFalse(output.toString().contains("Error:"));
        assertTrue(output.toString().contains("Loaned OSC-001, MIC-002 to A0123456X on "));
        ApplicationSnapshot saved = ApplicationSnapshot.read(file);
        assertEquals(2, saved.getEquipment().size());
        assertEquals(1, saved.getBorrowers().size());
        assertEquals(List.of("OSC-001", "MIC-002"), saved.getLoans().stream().map(Loan::getAssetId).toList());
        LocalDate issued = saved.getLoans().getFirst().getLoanDate();
        assertFalse(issued.isBefore(before));
        assertFalse(issued.isAfter(LocalDate.now()));
        for (Loan loan : saved.getLoans()) {
            assertEquals("A0123456X", loan.getBorrowerId());
            assertEquals(issued, loan.getLoanDate());
            assertEquals(dueDate, loan.getDueDate());
            assertTrue(loan.isActive());
        }
        String savedFile = Files.readString(file);
        output.reset();
        LabLog.load(file).run(new Scanner("active-loans\nloans A0123456X\n"
                + "history OSC-001\nhistory MIC-002\nexit\n"), new PrintStream(output));
        assertFalse(output.toString().contains("Error:"));
        assertTrue(output.toString().contains("OSC-001 | A0123456X | " + dueDate));
        assertTrue(output.toString().contains("MIC-002 | A0123456X | " + dueDate));
        assertTrue(output.toString().contains("2 active loans."));
        assertTrue(output.toString().contains("History for OSC-001 - Scope:"));
        assertTrue(output.toString().contains("History for MIC-002 - Microscope:"));
        assertEquals(savedFile, Files.readString(file));
    }

    @Test
    void run_invalidBatchLoans_preservesSnapshotAndExistingLoan(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("records.properties");
        LocalDate dueDate = LocalDate.now().plusDays(7);
        LabLog.load(file).run(new Scanner("add OSC-001 n/Scope c/Electronics m/S1 l/Lab A\n"
                + "add MIC-002 n/Microscope c/Optics m/M2 l/Lab B\n"
                + "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                + "loan MIC-002 b/A0123456X d/" + dueDate + "\nexit\n"),
                new PrintStream(new ByteArrayOutputStream()));
        String originalFile = Files.readString(file);

        for (String command : List.of("loan OSC-001 MIC-002 b/A0123456X d/" + dueDate,
                "loan OSC-001 UNKNOWN b/A0123456X d/" + dueDate,
                "loan OSC-001 OSC-001 b/A0123456X d/" + dueDate,
                "loan OSC-001 MIC-002 b/A7654321Y d/" + dueDate,
                "loan OSC-001 MIC-002 b/A0123456X d/2026-02-30")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            LabLog.load(file).run(new Scanner(command + "\nactive-loans\nexit\n"), new PrintStream(output));

            assertTrue(output.toString().contains("Error:"), command);
            assertFalse(output.toString().contains("Loaned OSC-001"), command);
            assertTrue(output.toString().contains("MIC-002 | A0123456X | " + dueDate), command);
            assertEquals(originalFile, Files.readString(file), command);
            List<Loan> saved = ApplicationSnapshot.read(file).getLoans();
            assertEquals(1, saved.size(), command);
            assertEquals("MIC-002", saved.getFirst().getAssetId(), command);
            assertTrue(saved.getFirst().isActive(), command);
        }
    }

    @Test
    void run_batchLoanReservationConflict_preservesSnapshot(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("records.properties");
        LocalDate reservationStart = LocalDate.now().plusDays(2);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LabLog.load(file).run(new Scanner("add OSC-001 n/Scope c/Electronics m/S1 l/Lab A\n"
                + "add MIC-002 n/Microscope c/Optics m/M2 l/Lab B\n"
                + "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                + "reserve MIC-002 b/A0123456X from/" + reservationStart + " to/" + reservationStart
                + "\nexit\n"), new PrintStream(output));
        assertFalse(output.toString().contains("Error:"));
        String originalFile = Files.readString(file);
        output.reset();

        LabLog.load(file).run(new Scanner("loan OSC-001 MIC-002 b/A0123456X d/"
                + reservationStart + "\nexit\n"), new PrintStream(output));

        assertTrue(output.toString().contains("Conflicts with reservation"));
        assertFalse(output.toString().contains("Loaned OSC-001"));
        assertEquals(originalFile, Files.readString(file));
        assertTrue(ApplicationSnapshot.read(file).getLoans().isEmpty());
        assertEquals(1, ApplicationSnapshot.read(file).getReservations().size());
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
        assertEquals(1, output.toString().split("LabLog commands:", -1).length - 1);
        assertEquals("keep", Files.readString(file.resolve("keep.txt")));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }
}
