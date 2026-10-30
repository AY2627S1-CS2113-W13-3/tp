package lablog;

import java.util.Comparator;
import java.util.List;

/** Displays stored borrowing/return evidence without reconstructing events from current loan state. */
public class LoanAuditCommands {
    private final Inventory inventory;
    private final LoanBook loans;

    public LoanAuditCommands(Inventory inventory, LoanBook loans) {
        this.inventory = inventory;
        this.loans = loans;
    }

    /** Lists all events or those for one exact registered asset; same-day ties retain acceptance order. */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+");
        if (parts.length < 1 || parts.length > 2 || !parts[0].equals("loan-audit")) {
            throw new IllegalArgumentException("Use: loan-audit [ASSET_ID]");
        }
        String asset = parts.length == 2 ? parts[1] : null;
        if (asset != null && !inventory.containsAssetId(asset)) {
            throw new IllegalArgumentException("Equipment " + asset + " was not found.");
        }
        List<LoanAuditEvent> events = loans.getAuditEvents().stream()
                .filter(event -> asset == null || event.assetId().equals(asset))
                .sorted(Comparator.comparing(LoanAuditEvent::date)).toList();
        String label = asset == null ? "" : " for " + asset;
        if (events.isEmpty()) {
            return "No loan audit events" + label + ". Legacy loans may have no recorded events; use history ASSET_ID.";
        }
        StringBuilder output = new StringBuilder("Loan audit" + label + ":");
        int index = 1;
        for (LoanAuditEvent event : events) {
            output.append("\n\n[").append(index++).append("] ").append(event.type()).append(" — ")
                    .append(event.date())
                    .append("\n    Asset: ").append(event.assetId())
                    .append("\n    Borrower: ").append(event.borrowerId())
                    .append("\n    Due: ").append(event.dueDate());
            appendOptionalDetail(output, "Faults", event.faults());
            output.append("\n    Event ID: ").append(event.id())
                    .append("\n    Loan ID: ").append(event.loanId());
            appendOptionalDetail(output, "Related event", event.relatedEventId());
            appendOptionalDetail(output, "Reason", event.reason());
        }
        return output.toString();
    }

    /** Omits empty detail lines while retaining complete identifiers on their own lines for copying. */
    private void appendOptionalDetail(StringBuilder output, String label, String value) {
        if (!value.isBlank()) {
            output.append("\n    ").append(label).append(": ").append(value);
        }
    }
}
