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
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/** Complete transfer snapshot, validated independently of the running application's records. */
public class ApplicationSnapshot {
    private static final long MAX_BYTES = 10 * 1024 * 1024;
    private final List<Equipment> equipment;
    private final List<Borrower> borrowers;
    private final List<Loan> loans;
    private final List<HistoryEntry> notes;
    private final List<Fault> faults;
    private final List<Reservation> reservations;
    private final List<WaitingRequest> waiting;
    private final List<LoanAuditEvent> audit;

    /** Captures supported records; serialization validates the complete graph again. */
    public ApplicationSnapshot(List<Equipment> equipment, List<Borrower> borrowers,
            List<Loan> loans, List<HistoryEntry> notes) {
        this(equipment, borrowers, loans, notes, List.of());
    }

    /** Captures structured fault records separately from display-only history notes. */
    public ApplicationSnapshot(List<Equipment> equipment, List<Borrower> borrowers,
            List<Loan> loans, List<HistoryEntry> notes, List<Fault> faults) {
        this(equipment, borrowers, loans, notes, faults, List.of());
    }

    /** Captures confirmed reservations alongside every previously supported record type. */
    public ApplicationSnapshot(List<Equipment> equipment, List<Borrower> borrowers,
            List<Loan> loans, List<HistoryEntry> notes, List<Fault> faults, List<Reservation> reservations) {
        this(equipment, borrowers, loans, notes, faults, reservations, List.of());
    }

    /** Captures waiting demand separately from bookings, preserving both in one atomic snapshot. */
    public ApplicationSnapshot(List<Equipment> equipment, List<Borrower> borrowers,
            List<Loan> loans, List<HistoryEntry> notes, List<Fault> faults, List<Reservation> reservations,
            List<WaitingRequest> waiting) {
        this(equipment, borrowers, loans, notes, faults, reservations, waiting, List.of());
    }

    /** Captures immutable audit evidence alongside the current domain records. */
    public ApplicationSnapshot(List<Equipment> equipment, List<Borrower> borrowers,
            List<Loan> loans, List<HistoryEntry> notes, List<Fault> faults, List<Reservation> reservations,
            List<WaitingRequest> waiting, List<LoanAuditEvent> audit) {
        this.audit = List.copyOf(audit);
        this.waiting = List.copyOf(waiting);
        this.reservations = List.copyOf(reservations);
        this.faults = List.copyOf(faults);
        this.equipment = List.copyOf(equipment);
        this.borrowers = List.copyOf(borrowers);
        this.loans = List.copyOf(loans);
        this.notes = List.copyOf(notes);
    }

    public List<LoanAuditEvent> getAuditEvents() {
        return audit;
    }

    public List<WaitingRequest> getWaitingRequests() {
        return waiting;
    }

    public List<Reservation> getReservations() {
        return reservations;
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

    /** Reads UTF-8 properties, rejecting repeated keys and files exceeding the supported size. */
    static Properties readProperties(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) {
            throw new IOException("Expected a regular snapshot file no larger than 10 MiB.");
        }
        Properties records = new UniqueProperties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            records.load(reader);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid snapshot: " + exception.getMessage(), exception);
        }
        Properties parsed = new Properties();
        parsed.putAll(records);
        return parsed;
    }

    /** Reads transfer formats 2 through 11; legacy two-file data is handled only by the application loader. */
    public static ApplicationSnapshot read(Path file) throws IOException {
        return decode(readProperties(file));
    }

    /**
     * Decodes the closed schema for the declared snapshot version before publishing a snapshot.
     * Unknown top-level, record and out-of-range indexed fields are rejected so
     * re-serialization cannot silently discard data owned by an unsupported feature.
     *
     * @param records complete properties read from the source file
     * @return snapshot containing only validated, supported records
     * @throws IOException if any required field, value, reference or property name is invalid
     */
    static ApplicationSnapshot decode(Properties records) throws IOException {
        try {
            if (!Set.of("2", "3", "4", "5", "6", "7", "8", "9", "10", "11").contains(required(records, "format"))) {
                throw new IllegalArgumentException(
                        "Unsupported snapshot format; expected 2, 3, 4, 5, 6, 7, 8, 9, 10 or 11.");
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
                if (entry.getType() == HistoryEntry.Type.LOAN || entry.getType() == HistoryEntry.Type.RENEWAL
                        || entry.getType() == HistoryEntry.Type.LOST
                        || !base.getInventory().containsAssetId(entry.getAssetId())) {
                    throw new IllegalArgumentException("Invalid note type or equipment reference.");
                }
                notes.add(entry);
            }
            if (!"11".equals(records.getProperty("format")) && count(records, "audit.count") != 0) {
                throw new IllegalArgumentException("Audit records are not supported by this schema version.");
            }
            BorrowerBook borrowerBook = new BorrowerBook();
            borrowerBook.replaceAll(borrowers);
            ReservationBook bookingBook = new ReservationBook(base.getInventory(), borrowerBook,
                    Clock.systemDefaultZone());
            if (Set.of("6", "7", "8", "9", "10", "11").contains(records.getProperty("format"))) {
                bookingBook.replaceAll(ReservationCodec.read(records));
            }
            List<WaitingRequest> waiting = Set.of("8", "9", "10", "11").contains(records.getProperty("format"))
                    ? WaitingRequestCodec.read(records) : List.of();
            WaitingRequestBook.validate(waiting, borrowerBook);
            ApplicationSnapshot result = new ApplicationSnapshot(base.getInventory().getEquipmentUnits(), borrowers,
                    base.getLoans().getLoans(), notes, base.getFaults().getFaults(),
                    bookingBook.getReservations(), waiting, base.getLoans().getAuditEvents());
            // Derive accepted keys from the export schema to keep read and write support in agreement.
            Set<String> unknownFields = new TreeSet<>(records.stringPropertyNames());
            unknownFields.removeAll(result.acceptedFields(records.getProperty("format")));
            if (!unknownFields.isEmpty()) {
                throw new IllegalArgumentException("Unknown snapshot field: " + unknownFields.iterator().next() + ".");
            }
            return result;
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new IOException("Invalid snapshot: " + exception.getMessage(), exception);
        }
    }

    /** Excludes fields introduced after the input version so they cannot be silently discarded. */
    private Set<String> acceptedFields(String format) {
        Set<String> accepted = new HashSet<>(encode().stringPropertyNames());
        if (format.equals("2")) {
            accepted.removeIf(key -> key.startsWith("fault."));
        } else {
            if (format.equals("3")) {
                accepted.removeIf(key -> key.startsWith("fault.") && key.endsWith(".severity"));
            }
            if (!Set.of("5", "6", "7", "8", "9", "10", "11").contains(format)) {
                accepted.removeIf(key -> key.startsWith("fault.")
                        && (key.endsWith(".resolved") || key.endsWith(".repair")));
            }
        }
        if (!Set.of("6", "7", "8", "9", "10", "11").contains(format)) {
            accepted.removeIf(key -> key.startsWith("reservation."));
        }
        if (format.equals("6")) {
            accepted.removeIf(key -> key.startsWith("reservation.")
                    && (key.endsWith(".cancelled") || key.endsWith(".reason")));
        }
        if (!Set.of("8", "9", "10", "11").contains(format)) {
            accepted.removeIf(key -> key.startsWith("waiting."));
        }
        if (!Set.of("9", "10", "11").contains(format)) {
            accepted.removeIf(key -> key.startsWith("loan.")
                    && (key.endsWith(".originalDue") || key.contains(".renewal.")));
        }
        if (!Set.of("10", "11").contains(format)) {
            accepted.removeIf(key -> key.startsWith("loan.") && key.endsWith(".lost"));
        }
        if (!format.equals("11")) {
            accepted.removeIf(key -> (key.startsWith("audit.") && !key.equals("audit.count"))
                    || (key.startsWith("loan.") && key.endsWith(".id")));
        }
        return accepted;
    }

    /** Serializes actual domain fields and the current reservation, fault and maintenance notes. */
    private Properties encode() {
        Properties empty = new Properties();
        empty.setProperty("equipment.count", "0");
        empty.setProperty("loan.count", "0");
        LabLogStore base = new LabLogStore(empty);
        base.getInventory().replaceAll(equipment);
        base.getLoans().replaceAll(loans, audit);
        faults.forEach(base.getFaults()::add);
        Properties records = base.toProperties();
        WaitingRequestCodec.write(records, waiting);
        ReservationCodec.write(records, reservations);
        records.setProperty("borrower.count", Integer.toString(borrowers.size()));
        records.setProperty("note.count", Integer.toString(notes.size()));
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
        try {
            Properties records = encode();
            decode(records);
            return records;
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new IOException("Invalid snapshot: " + exception.getMessage(), exception);
        }
    }

    /** Writes the entire snapshot then atomically replaces the destination; failures retain old data. */
    public void write(Path destination) throws IOException {
        Properties records = toProperties();
        Path file = destination.toAbsolutePath();
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "snapshot-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                records.store(writer, "LabLog complete snapshot, format 11");
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

    /** Properties normally overwrites repeated keys; imports reject that ambiguity. */
    private static class UniqueProperties extends Properties {
        @Override
        public synchronized Object put(Object key, Object value) {
            if (containsKey(key)) {
                throw new IllegalArgumentException("Duplicate field: " + key);
            }
            return super.put(key, value);
        }
    }
}
