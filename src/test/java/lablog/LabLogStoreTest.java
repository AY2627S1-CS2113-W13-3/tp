package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void run_saveFailure_stopsWithoutSuccessOrOverwriting(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("records.properties");
        LabLog application = LabLog.load(file);
        // Turn the destination into a non-empty directory to force a portable replacement failure.
        Files.createDirectory(file);
        Files.writeString(file.resolve("keep.txt"), "keep");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        application.run(new Scanner("OSC-001\nScope\nElectronics\nS1\nLab\nhelp\n"), new PrintStream(output));
        assertTrue(output.toString().contains("Changes could not be saved"));
        assertFalse(output.toString().contains("Equipment added to inventory."));
        assertFalse(output.toString().contains("Common Version 1 commands:"));
        assertEquals("keep", Files.readString(file.resolve("keep.txt")));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }
}
