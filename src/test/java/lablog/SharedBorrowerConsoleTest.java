package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies one-console registration, lending, named lookup, returns, and persistence. */
class SharedBorrowerConsoleTest {
    @Test
    void run_sharedBooks_usesCurrentLoansAndPersistsBorrower(@TempDir Path directory) throws Exception {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Bench scope", "Electronics", "S1", "Lab"));
        LoanBook loans = new LoanBook();
        Path file = directory.resolve("borrowers.properties");
        BorrowerBook borrowers = new BorrowerBook(file);
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog(inventory, loans, List::of, clock, borrowers).run(new Scanner(
                "loan OSC-001 b/A0123456X d/2026-10-20\n"
                        + "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                        + "loan OSC-001 b/A0123456X d/2026-10-20\nloans A0123456X\n"
                        + "return OSC-001\nloans A0123456X\nexit\n"), new PrintStream(output));
        String text = output.toString();
        assertTrue(text.contains("Error: Borrower A0123456X was not found."));
        assertTrue(text.contains("Bench scope"));
        assertTrue(text.contains("No active loans for A0123456X - Jamie."));
        assertFalse(text.contains("Unknown equipment"));
        assertEquals(1, loans.getLoans().size());
        assertFalse(loans.getLoans().get(0).isActive());
        assertEquals("Jamie", new BorrowerBook(file).getBorrower("A0123456X").getName());
    }
}
