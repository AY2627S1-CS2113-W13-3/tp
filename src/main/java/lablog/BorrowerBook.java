package lablog;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Stores unique borrowers and saves additions before making them available to commands.
 */
public class BorrowerBook {
    private final Map<String, Borrower> borrowers = new LinkedHashMap<>();
    private final Path file;
    private Saver saver;

    /** Persists proposed borrowers together with the main application's other records. */
    @FunctionalInterface
    interface Saver {
        void save(List<Borrower> proposed) throws IOException;
    }

    /**
     * Creates an in-memory book for callers that manage their own persistence.
     */
    public BorrowerBook() {
        file = null;
    }

    /**
     * Loads borrowers from a UTF-8 properties file, treating a missing file as a new book.
     *
     * @param file File used to load and save borrower records.
     * @throws IOException If existing data cannot be read or contains invalid records.
     */
    public BorrowerBook(Path file) throws IOException {
        this.file = file.toAbsolutePath();
        if (!Files.exists(this.file)) {
            return;
        }
        Properties records = new Properties();
        try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
            records.load(reader);
            for (String id : records.stringPropertyNames()) {
                String[] fields = records.getProperty(id).split("\t", -1);
                if (fields.length != 3) {
                    throw new IllegalArgumentException("Invalid borrower record: " + id);
                }
                borrowers.put(id, new Borrower(id, fields[0], fields[1], fields[2]));
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid borrower data in " + this.file + ". " + exception.getMessage(), exception);
        }
    }

    /** Selects whole-application persistence for the main console. */
    void setSaver(Saver saver) {
        this.saver = saver;
    }

    /** Installs validated records after a successful whole-application import. */
    void replaceAll(List<Borrower> replacement) {
        borrowers.clear();
        for (Borrower borrower : replacement) {
            borrowers.put(borrower.getBorrowerId(), borrower);
        }
    }

    /** Saves a validated edit before replacing the immutable borrower, keeping its ID stable. */
    public void updateBorrower(Borrower borrower) throws IOException {
        getBorrower(borrower.getBorrowerId());
        Map<String, Borrower> updated = new LinkedHashMap<>(borrowers);
        updated.put(borrower.getBorrowerId(), borrower);
        persist(updated);
        borrowers.put(borrower.getBorrowerId(), borrower);
    }

    /** Chooses standalone or whole-application persistence without changing live records. */
    private void persist(Map<String, Borrower> updated) throws IOException {
        if (saver != null) {
            saver.save(List.copyOf(updated.values()));
        } else if (file != null) {
            save(updated);
        }
    }

    /**
     * Adds a borrower after saving the proposed state, leaving memory unchanged if saving fails.
     *
     * @param borrower Validated borrower to register.
     * @throws IllegalArgumentException If the identifier is already registered.
     * @throws IOException If the proposed state cannot be saved.
     */
    public void addBorrower(Borrower borrower) throws IOException {
        String id = borrower.getBorrowerId();
        if (borrowers.containsKey(id)) {
            throw new IllegalArgumentException("Borrower ID " + id
                    + " already exists. Choose a different borrower ID.");
        }
        Map<String, Borrower> updated = new LinkedHashMap<>(borrowers);
        updated.put(id, borrower);
        persist(updated);
        borrowers.put(id, borrower);
    }

    /**
     * Returns the registered borrower or explains that the identifier is unknown.
     *
     * @param borrowerId Identifier to look up.
     * @return Matching borrower.
     * @throws IllegalArgumentException If the identifier is invalid or unknown.
     */
    public Borrower getBorrower(String borrowerId) {
        Borrower.validateId(borrowerId);
        Borrower borrower = borrowers.get(borrowerId);
        if (borrower == null) {
            throw new IllegalArgumentException("Borrower " + borrowerId + " was not found.");
        }
        return borrower;
    }

    public List<Borrower> getBorrowers() {
        return List.copyOf(borrowers.values());
    }

    /**
     * Replaces the data file atomically so a failed save cannot leave a partial borrower record.
     */
    private void save(Map<String, Borrower> updated) throws IOException {
        Properties records = new Properties();
        for (Borrower borrower : updated.values()) {
            records.setProperty(borrower.getBorrowerId(), borrower.getName() + "\t"
                    + borrower.getPhone() + "\t" + borrower.getEmail());
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "borrowers-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                records.store(writer, "LabLog borrowers");
            }
            // Fail safely if this file system cannot provide an atomic replacement.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
