package lablog;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Issues loans against the same inventory and loan book used by returns and history. */
public class LoanCommands {
    private static final String USAGE = "Use: loan ASSET_ID [ASSET_ID ...] b/BORROWER_ID d/DUE_DATE";
    // Recognize unknown prefixes too, so they cannot silently become part of another field.
    private static final Pattern PREFIX = Pattern.compile("(?<!\\S)([^\\s/]+)/");

    private final Inventory inventory;
    private final LoanBook loans;
    private final Clock clock;
    private final BorrowerBook borrowers;
    private final FaultBook faults;
    private final BookingAvailability availability;

    /**
     * Connects loan issuing to shared records and a clock that tests can fix.
     *
     * @param inventory registered equipment
     * @param loans shared loan records
     * @param clock clock and time zone used for the loan date
     */
    public LoanCommands(Inventory inventory, LoanBook loans, Clock clock) {
        this(inventory, loans, clock, null);
    }

    /** Enables registered-borrower validation when a shared borrower book is supplied. */
    public LoanCommands(Inventory inventory, LoanBook loans, Clock clock, BorrowerBook borrowers) {
        this(inventory, loans, clock, borrowers, new FaultBook(inventory));
    }

    /** Checks the shared fault book before lending equipment with a severe report. */
    public LoanCommands(Inventory inventory, LoanBook loans, Clock clock, BorrowerBook borrowers, FaultBook faults) {
        this(inventory, loans, clock, borrowers, faults, null);
    }

    /** Applies the same reservation interval policy used by booking and renewal consumers. */
    public LoanCommands(Inventory inventory, LoanBook loans, Clock clock, BorrowerBook borrowers, FaultBook faults,
            BookingAvailability availability) {
        this.inventory = Objects.requireNonNull(inventory);
        this.loans = Objects.requireNonNull(loans);
        this.clock = Objects.requireNonNull(clock);
        this.borrowers = borrowers;
        this.faults = Objects.requireNonNull(faults);
        this.availability = availability;
    }

    /**
     * Issues one or more loans dated today, after every selected unit passes validation.
     *
     * <p>The main console supplies a shared borrower book and requires registered borrowers.
     * The three-argument constructor remains available for standalone callers managing their own validation.
     *
     * @param input complete loan command
     * @return confirmation naming the selected units, the borrower, and the due date
     * @throws IllegalArgumentException if the syntax, the equipment, the due date, or the
     *     unit's availability is invalid
     */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+", 2);
        if (parts.length < 2 || !parts[0].equals("loan")) {
            throw new IllegalArgumentException(USAGE);
        }
        String arguments = parts[1].strip();
        Matcher matcher = PREFIX.matcher(arguments);
        if (!matcher.find()) {
            throw new IllegalArgumentException(USAGE);
        }
        List<String> assetIds = readAssetIds(arguments.substring(0, matcher.start()));
        LocalDate today = LocalDate.now(clock);
        Map<String, String> fields = readFields(arguments, matcher);
        List<Equipment> units = assetIds.stream().map(this::findEquipment).toList();
        String borrowerId = readBorrowerId(fields.get("b"));
        if (borrowers != null) {
            borrowers.getBorrower(borrowerId);
        }
        LocalDate dueDate = readDueDate(fields.get("d"), today);
        // Check every unit before adding any loan to the shared book.
        for (String assetId : assetIds) {
            rejectUnavailable(assetId);
            if (availability != null) {
                availability.requireAvailable(assetId, new DateWindow(today, dueDate), null, null);
            }
        }

        List<Loan> issued = assetIds.stream().map(assetId -> new Loan(assetId, borrowerId, today, dueDate)).toList();
        issued.forEach(loans::addLoan);
        String selection = assetIds.size() == 1
                ? assetIds.getFirst() + " (" + units.getFirst().getName() + ")" : String.join(", ", assetIds);
        return "Loaned " + selection + " to " + borrowerId
                + " on " + today + ". Due " + dueDate + ".";
    }

    /** Parses distinct, whitespace-separated asset IDs while preserving selection order. */
    private List<String> readAssetIds(String selection) {
        if (selection.isBlank()) {
            throw new IllegalArgumentException("Supply at least one asset ID. " + USAGE);
        }
        List<String> assetIds = List.of(selection.strip().split("\\s+"));
        Set<String> seen = new HashSet<>();
        for (String assetId : assetIds) {
            if (!seen.add(assetId)) {
                throw new IllegalArgumentException("Duplicate asset ID: " + assetId + ".");
            }
        }
        return assetIds;
    }

    /**
     * Collects the prefixed fields, accepting b/ and d/ once each in either order.
     */
    private Map<String, String> readFields(String arguments, Matcher matcher) {
        Map<String, String> fields = new HashMap<>();
        String prefix = matcher.group(1);
        int valueStart = matcher.end();
        while (matcher.find()) {
            addField(fields, prefix, arguments.substring(valueStart, matcher.start()).strip());
            prefix = matcher.group(1);
            valueStart = matcher.end();
        }
        addField(fields, prefix, arguments.substring(valueStart).strip());
        if (fields.size() != 2) {
            throw new IllegalArgumentException(USAGE);
        }
        return fields;
    }

    /**
     * Rejects repeated, unknown, or empty prefixed fields.
     */
    private void addField(Map<String, String> fields, String prefix, String value) {
        if (!List.of("b", "d").contains(prefix) || value.isEmpty() || fields.containsKey(prefix)) {
            throw new IllegalArgumentException("Fields must be non-empty and use b/ and d/ exactly once. "
                    + USAGE);
        }
        fields.put(prefix, value);
    }

    /**
     * Finds the registered unit, so a loan can never name equipment LabLog does not know.
     */
    private Equipment findEquipment(String assetId) {
        return inventory.getEquipmentUnits().stream()
                .filter(unit -> assetId.equals(unit.getAssetId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Equipment " + assetId + " was not found."));
    }

    /**
     * Rejects a borrower identifier that is not a single word.
     */
    private String readBorrowerId(String value) {
        if (value.matches(".*\\s.*")) {
            throw new IllegalArgumentException("Borrower ID must be a single word. " + USAGE);
        }
        return value;
    }

    /**
     * Reads an ISO due date that is not already in the past.
     */
    private LocalDate readDueDate(String value, LocalDate today) {
        LocalDate dueDate;
        try {
            dueDate = LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Due date must be a real date in YYYY-MM-DD format. " + USAGE);
        }
        if (dueDate.isBefore(today)) {
            throw new IllegalArgumentException("Due date " + dueDate + " is before today (" + today + ").");
        }
        return dueDate;
    }

    /**
     * Warns instead of lending a unit that someone else still has, naming who holds it.
     */
    private void rejectUnavailable(String assetId) {
        if (loans.hasLostLoan(assetId)) {
            throw new IllegalArgumentException("Equipment " + assetId + " is marked lost and cannot be loaned.");
        }
        if (faults.blocksLending(assetId)) {
            throw new IllegalArgumentException("Equipment " + assetId
                    + " has an unresolved severe fault and cannot be loaned. Use history " + assetId + ".");
        }
        Optional<Loan> active = loans.findActiveLoan(assetId);
        if (active.isPresent()) {
            Loan loan = active.get();
            throw new IllegalArgumentException("Equipment " + assetId + " is already on loan to "
                    + loan.getBorrowerId() + " until " + loan.getDueDate() + ".");
        }
    }
}
