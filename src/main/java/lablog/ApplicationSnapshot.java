package lablog;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Complete transfer snapshot, validated independently of the running application's records. */
public class ApplicationSnapshot {
    private static final long MAX_BYTES = 10 * 1024 * 1024;
    private final List<Equipment> equipment;
    private final List<Borrower> borrowers;
    private final List<Loan> loans;
    private final List<HistoryEntry> notes;
    private final List<Fault> faults;

    /** Captures supported records; serialization validates the complete graph again. */
    public ApplicationSnapshot(List<Equipment> equipment, List<Borrower> borrowers,
            List<Loan> loans, List<HistoryEntry> notes) {
        this(equipment, borrowers, loans, notes, List.of());
    }

    /** Captures structured fault records separately from display-only history notes. */
    public ApplicationSnapshot(List<Equipment> equipment, List<Borrower> borrowers,
            List<Loan> loans, List<HistoryEntry> notes, List<Fault> faults) {
        this.faults = List.copyOf(faults);
        this.equipment = List.copyOf(equipment);
        this.borrowers = List.copyOf(borrowers);
        this.loans = List.copyOf(loans);
        this.notes = List.copyOf(notes);
    }

    public List<Fault> getFaults() {
        return faults;
    }

    public List<Equipment> getEquipment() {
        return equipment;
    }

    public List<Borrower> getBorrowers() {
        return borrowers;
    }

    public List<Loan> getLoans() {
        return loans;
    }

    public List<HistoryEntry> getNotes() {
        return notes;
    }

    /** Reads the UTF-8 properties envelope before domain validation. */
    static Properties readProperties(Path file) throws IOException {
        Properties records = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            records.load(reader);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid snapshot: " + exception.getMessage(), exception);
        }
        return records;
    }

    /** Reads transfer formats 2 through 4; legacy two-file data is handled only by the application loader. */
    public static ApplicationSnapshot read(Path file) throws IOException {
        return decode(readProperties(file));
    }

    /** Validates supported records and their references independently of live data. */
    static ApplicationSnapshot decode(Properties records) throws IOException {
        try {
            if (!Set.of("2", "3", "4").contains(required(records, "format"))) {
                throw new IllegalArgumentException("Unsupported snapshot format; expected 2, 3 or 4.");
            }
            LabLogStore base = new LabLogStore(records);
            List<Borrower> borrowers = new ArrayList<>();
            Set<String> borrowerIds = new HashSet<>();
            int borrowerCount = count(records, "borrower.count");
            for (int index = 0; index < borrowerCount; index++) {
                String key = "borrower." + index + ".";
                Borrower borrower = new Borrower(required(records, key + "id"), required(records, key + "name"),
                        required(records, key + "phone"), required(records, key + "email"));
                if (!borrowerIds.add(borrower.getBorrowerId())) {
                    throw new IllegalArgumentException("Duplicate borrower ID: " + borrower.getBorrowerId());
                }
                borrowers.add(borrower);
            }
            for (Loan loan : base.getLoans().getLoans()) {
                if (!borrowerIds.contains(loan.getBorrowerId())) {
                    throw new IllegalArgumentException("Unknown borrower reference: " + loan.getBorrowerId());
                }
            }
            List<HistoryEntry> notes = new ArrayList<>();
            int noteCount = count(records, "note.count");
            for (int index = 0; index < noteCount; index++) {
                String key = "note." + index + ".";
                HistoryEntry entry = new HistoryEntry(required(records, key + "asset"),
                        LocalDate.parse(required(records, key + "date")),
                        HistoryEntry.Type.valueOf(required(records, key + "type")),
                        required(records, key + "description"));
                if (entry.getType() == HistoryEntry.Type.LOAN
                        || !base.getInventory().containsAssetId(entry.getAssetId())) {
                    throw new IllegalArgumentException("Invalid note type or equipment reference.");
                }
                notes.add(entry);
            }
            if (count(records, "audit.count") != 0) {
                throw new IllegalArgumentException("Audit records are not supported by this schema version.");
            }
            ApplicationSnapshot result = new ApplicationSnapshot(base.getInventory().getEquipmentUnits(), borrowers,
                    base.getLoans().getLoans(), notes, base.getFaults().getFaults());
            return result;
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new IOException("Invalid snapshot: " + exception.getMessage(), exception);
        }
    }

    /** Serializes actual domain fields and the current reservation, fault and maintenance notes. */
    private Properties encode() {
        Properties empty = new Properties();
        empty.setProperty("equipment.count", "0");
        empty.setProperty("loan.count", "0");
        LabLogStore base = new LabLogStore(empty);
        base.getInventory().replaceAll(equipment);
        base.getLoans().replaceAll(loans);
        faults.forEach(base.getFaults()::add);
        Properties records = base.toProperties();
        records.setProperty("format", "4");
        records.setProperty("borrower.count", Integer.toString(borrowers.size()));
        records.setProperty("note.count", Integer.toString(notes.size()));
        records.setProperty("audit.count", "0");
        for (int index = 0; index < borrowers.size(); index++) {
            String key = "borrower." + index + ".";
            Borrower borrower = borrowers.get(index);
            records.setProperty(key + "id", borrower.getBorrowerId());
            records.setProperty(key + "name", borrower.getName());
            records.setProperty(key + "phone", borrower.getPhone());
            records.setProperty(key + "email", borrower.getEmail());
        }
        for (int index = 0; index < notes.size(); index++) {
            String key = "note." + index + ".";
            HistoryEntry note = notes.get(index);
            records.setProperty(key + "asset", note.getAssetId());
            records.setProperty(key + "date", note.getDate().toString());
            records.setProperty(key + "type", note.getType().name());
            records.setProperty(key + "description", note.getDescription());
        }
        return records;
    }

    /** Rejects invalid injected records before writing an unusable backup or saved state. */
    Properties toProperties() throws IOException {
        Properties records = encode();
        decode(records);
        return records;
    }

    /** Writes the entire snapshot then atomically replaces the destination; failures retain old data. */
    public void write(Path destination) throws IOException {
        Properties records = toProperties();
        Path file = destination.toAbsolutePath();
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "snapshot-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                records.store(writer, "LabLog complete snapshot, format 4");
            }
            if (Files.size(temporary) > MAX_BYTES) {
                throw new IOException("Snapshot exceeds the supported 10 MiB size limit.");
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Requires explicit fields, including empty return details. */
    private static String required(Properties records, String key) {
        String value = records.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing field " + key + ".");
        }
        return value;
    }

    /** Rejects malformed counts and bounds work performed on untrusted input. */
    private static int count(Properties records, String key) {
        int count = Integer.parseInt(required(records, key));
        if (count < 0 || count > 100000) {
            throw new IllegalArgumentException("Record count must be between 0 and 100000.");
        }
        return count;
    }

}
