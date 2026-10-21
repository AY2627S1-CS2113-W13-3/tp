package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies partial edits, unchanged loan identities, validation boundaries and transactional saves. */
class BorrowerEditTest {
    @Test
    void execute_partialAndReorderedEdits_preservesIdentityAndReloads(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("borrowers.properties");
        BorrowerBook book = new BorrowerBook(file);
        book.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
        LoanBook loans = new LoanBook();
        Loan loan = new Loan("OSC-001", "A0123456X", LocalDate.now(), LocalDate.now().plusDays(1));
        loans.addLoan(loan);
        BorrowerCommands commands = new BorrowerCommands(book, loans, id -> "Scope", Clock.systemDefaultZone());
        commands.execute("edit-borrower A0123456X p/92345678");
        assertEquals("Jamie", book.getBorrower("A0123456X").getName());
        assertEquals("jamie@u.nus.edu", book.getBorrower("A0123456X").getEmail());
        commands.execute("edit-borrower A0123456X e/new@u.nus.edu n/New Name p/93456789");
        assertEquals("New Name", new BorrowerBook(file).getBorrower("A0123456X").getName());
        assertEquals("93456789", new BorrowerBook(file).getBorrower("A0123456X").getPhone());
        assertEquals("new@u.nus.edu", new BorrowerBook(file).getBorrower("A0123456X").getEmail());
        assertSame(loan, loans.getLoans().get(0));
        assertTrue(commands.execute("loans A0123456X").contains("1 active loan."));
        for (String name : List.of("X", "X".repeat(100))) {
            commands.execute("edit-borrower A0123456X n/" + name);
            assertEquals(name, book.getBorrower("A0123456X").getName());
        }
    }

    @Test
    void execute_invalidOrFailedEdits_preservesMemoryAndFile(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("borrowers.properties");
        BorrowerBook book = new BorrowerBook(file);
        Borrower original = new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu");
        book.addBorrower(original);
        String saved = Files.readString(file);
        BorrowerCommands commands = new BorrowerCommands(book, new LoanBook(), id -> null, Clock.systemDefaultZone());
        for (String arguments : List.of("", "p/", "p/123", "e/wrong@example.com", "n/" + "X".repeat(101),
                "p/92345678 p/93456789", "x/new", "n/New p/invalid", "id/A0123457X")) {
            assertThrows(IllegalArgumentException.class,
                    () -> commands.execute("edit-borrower A0123456X " + arguments));
            assertSame(original, book.getBorrower("A0123456X"));
            assertEquals(saved, Files.readString(file));
        }
        assertThrows(IllegalArgumentException.class,
                () -> commands.execute("edit-borrower A0123457X p/92345678"));
        book.setSaver(proposed -> {
            throw new IOException("Injected save failure");
        });
        assertThrows(IOException.class, () -> commands.execute("edit-borrower A0123456X n/New"));
        assertSame(original, book.getBorrower("A0123456X"));
        assertEquals(saved, Files.readString(file));
    }

    @Test
    void updateBorrower_standaloneWriteFails_preservesOriginal(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("borrowers.properties");
        BorrowerBook book = new BorrowerBook(file);
        Borrower original = new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu");
        book.addBorrower(original);
        Files.delete(file);
        Files.createDirectory(file);
        Files.writeString(file.resolve("keep"), "keep");
        assertThrows(IOException.class, () -> book.updateBorrower(
                new Borrower("A0123456X", "New", "92345678", "new@u.nus.edu")));
        assertSame(original, book.getBorrower("A0123456X"));
        assertEquals("keep", Files.readString(file.resolve("keep")));
    }
}
