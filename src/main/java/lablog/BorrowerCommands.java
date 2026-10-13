package lablog;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles borrower commands using the same borrower and loan books as the rest of LabLog.
 */
public class BorrowerCommands {
    private static final String ADD_USAGE = "Use: add-borrower BORROWER_ID n/NAME p/PHONE e/EMAIL";
    // Recognize unknown prefixes too, so they cannot silently become part of a name.
    private static final Pattern PREFIX = Pattern.compile("(?<!\\S)([^\\s/]+)/");

    private final BorrowerBook borrowers;
    private final LoanBook loans;
    private final Function<String, String> equipmentNames;
    private final Clock clock;

    /**
     * Creates handlers with shared records and an equipment-name lookup supplied by the caller.
     *
     * @param borrowers Shared borrower records.
     * @param loans Shared loan records, including returned loans.
     * @param equipmentNames Lookup from asset ID to equipment name; null means missing equipment.
     * @param clock Clock used to determine today's local date.
     */
    public BorrowerCommands(BorrowerBook borrowers, LoanBook loans,
            Function<String, String> equipmentNames, Clock clock) {
        this.borrowers = borrowers;
        this.loans = loans;
        this.equipmentNames = equipmentNames;
        this.clock = clock;
    }

    /**
     * Executes one complete borrower command and returns its display text.
     *
     * @param input Complete command line.
     * @return Success message or active-loan listing.
     * @throws IllegalArgumentException If syntax, fields, or borrower lookup are invalid.
     * @throws IOException If an addition cannot be saved.
     */
    public String execute(String input) throws IOException {
        String[] parts = input.strip().split("\\s+", 2);
        String arguments = parts.length == 2 ? parts[1].strip() : "";
        switch (parts[0]) {
        case "help":
            return new HelpCommands().execute(input);
        case "add-borrower":
            return addBorrower(arguments);
        case "loans":
            return listLoans(arguments);
        default:
            throw new IllegalArgumentException("Unknown borrower command. Use add-borrower or loans.");
        }
    }

    /**
     * Validates all fields before adding a borrower, accepting each prefix in any order.
     */
    private String addBorrower(String arguments) throws IOException {
        Matcher matcher = PREFIX.matcher(arguments);
        if (!matcher.find()) {
            throw new IllegalArgumentException(ADD_USAGE);
        }
        String id = arguments.substring(0, matcher.start()).strip();
        Borrower.validateId(id);
        Map<String, String> fields = new HashMap<>();
        String prefix = matcher.group(1);
        int valueStart = matcher.end();
        while (matcher.find()) {
            addField(fields, prefix, arguments.substring(valueStart, matcher.start()).strip());
            prefix = matcher.group(1);
            valueStart = matcher.end();
        }
        addField(fields, prefix, arguments.substring(valueStart).strip());
        if (fields.size() != 3) {
            throw new IllegalArgumentException(ADD_USAGE);
        }
        Borrower borrower = new Borrower(id, fields.get("n"), fields.get("p"), fields.get("e"));
        borrowers.addBorrower(borrower);
        return "Added borrower " + id + ": " + borrower.getName() + ".\nPhone: "
                + borrower.getPhone() + " | Email: " + borrower.getEmail();
    }

    /**
     * Rejects repeated, unknown, or empty prefixed fields.
     */
    private void addField(Map<String, String> fields, String prefix, String value) {
        if (!List.of("n", "p", "e").contains(prefix) || value.isEmpty() || fields.containsKey(prefix)) {
            throw new IllegalArgumentException("Fields must be non-empty and use n/, p/, e/ exactly once. "
                    + ADD_USAGE);
        }
        fields.put(prefix, value);
    }

    /**
     * Lists only this borrower's active loans in due-date and asset-ID order.
     */
    private String listLoans(String id) {
        if (id.isEmpty() || id.matches(".*\\s.*")) {
            throw new IllegalArgumentException("Use: loans BORROWER_ID");
        }
        Borrower borrower = borrowers.getBorrower(id);
        String label = id + " - " + borrower.getName();
        List<Loan> active = new ArrayList<>();
        for (Loan loan : loans.getLoans()) {
            if (loan.isActive() && id.equals(loan.getBorrowerId())) {
                active.add(loan);
            }
        }
        active.sort(Comparator.comparing(Loan::getDueDate).thenComparing(Loan::getAssetId));
        if (active.isEmpty()) {
            return "No active loans for " + label + ".";
        }
        LocalDate today = LocalDate.now(clock);
        StringBuilder output = new StringBuilder("Active loans for " + label + ":\n"
                + "Asset ID | Equipment | Issued | Due | Overdue\n");
        for (Loan loan : active) {
            String name = equipmentNames.apply(loan.getAssetId());
            output.append(loan.getAssetId()).append(" | ")
                    .append(name == null ? "Unknown equipment" : name).append(" | ")
                    .append(loan.getLoanDate()).append(" | ").append(loan.getDueDate()).append(" | ")
                    .append(today.isAfter(loan.getDueDate()) ? "yes" : "no").append('\n');
        }
        return output.append(active.size()).append(active.size() == 1 ? " active loan." : " active loans.").toString();
    }
}
