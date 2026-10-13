package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies persistent borrower records and failure behavior without using real application data.
 */
class BorrowerBookTest {
    @TempDir
    Path directory;

    @Test
    void addBorrower_savedRecords_reloadExactly() throws IOException {
        Path file = directory.resolve("nested/borrowers.properties");
        BorrowerBook book = new BorrowerBook(file);
        book.addBorrower(new Borrower("A0123456X", "Jamie = Tan 李", "01234567", "Jamie+lab@u.nus.edu"));
        book.addBorrower(new Borrower("A0123457X", "Other", "91234567", "other@u.nus.edu"));
        BorrowerBook loaded = new BorrowerBook(file);
        Borrower borrower = loaded.getBorrower("A0123456X");
        assertEquals("Jamie = Tan 李", borrower.getName());
        assertEquals("01234567", borrower.getPhone());
        assertEquals("Jamie+lab@u.nus.edu", borrower.getEmail());
        assertEquals(2, loaded.getBorrowers().size());
        String before = Files.readString(file);
        assertThrows(IllegalArgumentException.class, () -> loaded.addBorrower(borrower));
        assertEquals(before, Files.readString(file));
    }

    @Test
    void addBorrower_saveFails_doesNotRegisterBorrower() throws IOException {
        Path parent = directory.resolve("blocked");
        BorrowerBook book = new BorrowerBook(parent.resolve("borrowers.properties"));
        Files.writeString(parent, "This is a file, not a directory.");
        assertThrows(IOException.class, () -> book.addBorrower(
                new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu")));
        assertTrue(book.getBorrowers().isEmpty());
        assertEquals("This is a file, not a directory.", Files.readString(parent));
    }

    @Test
    void constructor_corruptRecord_rejectsWithoutOverwriting() throws IOException {
        Path file = directory.resolve("borrowers.properties");
        String corrupt = "A0123456X=Jamie\tinvalid phone\tjamie@u.nus.edu\n";
        Files.writeString(file, corrupt);
        assertThrows(IOException.class, () -> new BorrowerBook(file));
        assertEquals(corrupt, Files.readString(file));
    }

    @Test
    void getBorrowers_snapshot_cannotModifyBook() throws IOException {
        BorrowerBook book = new BorrowerBook();
        List<Borrower> snapshot = book.getBorrowers();
        book.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
        assertTrue(snapshot.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> book.getBorrowers().clear());
    }
}
