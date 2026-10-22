package lablog;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/** Loads and atomically saves equipment, complete loan history and structured fault reports. */
public class LabLogStore {
    private final Path file;
    private final Inventory inventory = new Inventory();
    private final LoanBook loans = new LoanBook();
    private final FaultBook faults = new FaultBook(inventory);

    /** Loads a snapshot, treating a missing file as a new installation. */
    public LabLogStore(Path file) throws IOException {
        this.file = file.toAbsolutePath();
        if (!Files.exists(this.file)) {
            return;
        }
        Properties records = new Properties();
        try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
            records.load(reader);
            if (!Set.of("1", "3", "4", "5").contains(required(records, "format"))) {
                throw new IllegalArgumentException("Unsupported data format.");
            }
            Set<String> assets = readEquipment(records);
            readLoans(records, assets);
            if (Set.of("3", "4", "5").contains(records.getProperty("format", ""))) {
                readFaults(records);
            }
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new IOException("Invalid LabLog data: " + exception.getMessage(), exception);
        }
    }

    /** Decodes equipment and loans already read by the import boundary. */
    LabLogStore(Properties records) {
        file = null;
        Set<String> assets = readEquipment(records);
        readLoans(records, assets);
        if (Set.of("3", "4", "5").contains(records.getProperty("format", ""))) {
            readFaults(records);
        }
    }

    public FaultBook getFaults() {
        return faults;
    }

    /** Reads structured reports with duplicate-ID and equipment-reference validation. */
    private void readFaults(Properties records) {
        int count = count(records, "fault.count");
        for (int index = 0; index < count; index++) {
            String key = "fault." + index + ".";
            boolean hasResolution = "5".equals(records.getProperty("format"));
            String resolved = hasResolution ? required(records, key + "resolved") : "";
            String repair = hasResolution ? required(records, key + "repair") : "";
            faults.add(new Fault(required(records, key + "id"), required(records, key + "asset"),
                    LocalDate.parse(required(records, key + "date")), required(records, key + "description"),
                    Set.of("4", "5").contains(records.getProperty("format", ""))
                            ? Fault.Severity.valueOf(required(records, key + "severity"))
                            : Fault.Severity.UNSPECIFIED,
                    resolved.isEmpty() ? null : LocalDate.parse(resolved), repair));
        }
    }

    public Inventory getInventory() {
        return inventory;
    }

    public LoanBook getLoans() {
        return loans;
    }

    /** Validates unique equipment IDs before making the loaded snapshot available. */
    private Set<String> readEquipment(Properties records) {
        Set<String> assets = new HashSet<>();
        int count = count(records, "equipment.count");
        for (int index = 0; index < count; index++) {
            String key = "equipment." + index + ".";
            String id = required(records, key + "id");
            if (id.isBlank() || id.chars().anyMatch(Character::isWhitespace) || !assets.add(id)) {
                throw new IllegalArgumentException("Invalid or duplicate equipment ID.");
            }
            inventory.addEquipment(new Equipment(id, required(records, key + "name"),
                    required(records, key + "category"), required(records, key + "model"),
                    required(records, key + "location")));
        }
        return assets;
    }

    /** Restores returns and rejects broken references or multiple active loans for one unit. */
    private void readLoans(Properties records, Set<String> assets) {
        Set<String> activeAssets = new HashSet<>();
        int count = count(records, "loan.count");
        for (int index = 0; index < count; index++) {
            String key = "loan." + index + ".";
            String assetId = required(records, key + "asset");
            String borrowerId = required(records, key + "borrower");
            LocalDate loanDate = LocalDate.parse(required(records, key + "date"));
            LocalDate dueDate = LocalDate.parse(required(records, key + "due"));
            if (!assets.contains(assetId) || borrowerId.isBlank()
                    || borrowerId.chars().anyMatch(Character::isWhitespace) || dueDate.isBefore(loanDate)) {
                throw new IllegalArgumentException("Invalid loan identifiers or dates.");
            }
            Loan loan = new Loan(assetId, borrowerId, loanDate, dueDate);
            String returned = required(records, key + "returned");
            String faults = required(records, key + "faults");
            if (!returned.isEmpty()) {
                loan.markReturned(LocalDate.parse(returned), faults);
            } else if (!faults.isEmpty() || !activeAssets.add(assetId)) {
                throw new IllegalArgumentException("Invalid active loan or return details.");
            }
            loans.addLoan(loan);
        }
    }

    /** Requires explicit fields so truncated files cannot silently load as empty records. */
    private static String required(Properties records, String key) {
        String value = records.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing field " + key + ".");
        }
        return value;
    }

    /** Parses a nonnegative record count from the snapshot header. */
    private static int count(Properties records, String key) {
        int count = Integer.parseInt(required(records, key));
        if (count < 0 || count > 100000) {
            throw new IllegalArgumentException("Record count must be between 0 and 100000.");
        }
        return count;
    }

    /** Writes a complete temporary snapshot and replaces the previous file only after success. */
    public void save() throws IOException {
        write(toProperties());
    }

    /** Reuses the established equipment and loan schema inside a complete snapshot. */
    Properties toProperties() {
        Properties records = new Properties();
        records.setProperty("format", "5");
        records.setProperty("equipment.count", Integer.toString(inventory.getEquipmentUnits().size()));
        records.setProperty("loan.count", Integer.toString(loans.getLoans().size()));
        int index = 0;
        for (Equipment unit : inventory.getEquipmentUnits()) {
            String key = "equipment." + index++ + ".";
            records.setProperty(key + "id", unit.getAssetId());
            records.setProperty(key + "name", unit.getName());
            records.setProperty(key + "category", unit.getCategory());
            records.setProperty(key + "model", unit.getModel());
            records.setProperty(key + "location", unit.getLocation());
        }
        index = 0;
        for (Loan loan : loans.getLoans()) {
            String key = "loan." + index++ + ".";
            records.setProperty(key + "asset", loan.getAssetId());
            records.setProperty(key + "borrower", loan.getBorrowerId());
            records.setProperty(key + "date", loan.getLoanDate().toString());
            records.setProperty(key + "due", loan.getDueDate().toString());
            records.setProperty(key + "returned", loan.isActive() ? "" : loan.getReturnDate().toString());
            records.setProperty(key + "faults", loan.getReturnFaults());
        }
        records.setProperty("fault.count", Integer.toString(faults.getFaults().size()));
        index = 0;
        for (Fault fault : faults.getFaults()) {
            String key = "fault." + index++ + ".";
            records.setProperty(key + "id", fault.getId());
            records.setProperty(key + "asset", fault.getAssetId());
            records.setProperty(key + "date", fault.getReportDate().toString());
            records.setProperty(key + "description", fault.getDescription());
            records.setProperty(key + "severity", fault.getSeverity().name());
            records.setProperty(key + "resolved", fault.isResolved() ? fault.getResolutionDate().toString() : "");
            records.setProperty(key + "repair", fault.getRepairDetails());
        }
        return records;
    }

    /** Atomically saves proposed records before the caller changes live data. */
    void write(Properties records) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "lablog-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                records.store(writer, "LabLog equipment and loan history");
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
