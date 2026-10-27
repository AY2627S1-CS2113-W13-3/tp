package lablog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Displays one unit's loan, renewal, reservation, fault and maintenance records.
 */
public class HistoryCommands {
    private final Inventory inventory;
    private final LoanBook loans;
    private final Supplier<List<HistoryEntry>> otherHistory;

    /**
     * Creates a handler backed by shared records rather than a cached history copy.
     *
     * @param inventory shared equipment inventory
     * @param loans shared active and returned loans
     * @param otherHistory supplies current reservation, fault, and maintenance entries on each request;
     *                     must return a non-null list and must not duplicate loans from the loan book
     */
    public HistoryCommands(Inventory inventory, LoanBook loans, Supplier<List<HistoryEntry>> otherHistory) {
        this.inventory = Objects.requireNonNull(inventory);
        this.loans = Objects.requireNonNull(loans);
        this.otherHistory = Objects.requireNonNull(otherHistory);
    }

    /**
     * Executes a history command without changing the underlying records.
     *
     * @param input complete command, in the form history ASSET_ID
     * @return all matching records, oldest first, or an empty-history message
     * @throws IllegalArgumentException if the command is malformed or the equipment does not exist
     */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+");
        if (parts.length != 2 || !parts[0].equals("history")) {
            throw new IllegalArgumentException("Use: history ASSET_ID");
        }
        String assetId = parts[1];
        Equipment equipment = inventory.getEquipmentUnits().stream()
                .filter(unit -> assetId.equals(unit.getAssetId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Equipment " + assetId + " was not found."));
        List<HistoryEntry> entries = getHistory(assetId);
        String label = assetId + " - " + equipment.getName();
        if (entries.isEmpty()) {
            return "No history found for " + label + ".";
        }
        StringBuilder output = new StringBuilder("History for " + label + ":\nDate | Type | Details\n");
        for (HistoryEntry entry : entries) {
            output.append(entry.getDate()).append(" | ").append(entry.getType()).append(" | ")
                    .append(entry.getDescription()).append('\n');
        }
        return output.append(entries.size()).append(entries.size() == 1 ? " record." : " records.").toString();
    }

    /**
     * Builds fresh display entries so subsequent loan returns and source updates appear immediately.
     * Same-day entries are ordered by type, then description; renewals retain their acceptance order.
     */
    private List<HistoryEntry> getHistory(String assetId) {
        List<HistoryEntry> entries = new ArrayList<>();
        for (Loan loan : loans.getLoans()) {
            if (assetId.equals(loan.getAssetId())) {
                String details = "Borrower " + loan.getBorrowerId() + "; due " + loan.getDueDate()
                        + (loan.isReversed() ? "; REVERSED (loan cancelled)"
                                : loan.isActive() ? "; active" : "; returned " + loan.getReturnDate());
                if (loan.isLost()) {
                    details += "; LOST since " + loan.getLostDate();
                }
                if (loan.getReturnDate() != null) {
                    details += loan.getReturnFaults().isEmpty()
                            ? "; no faults reported on return" : "; faults: " + loan.getReturnFaults();
                }
                entries.add(new HistoryEntry(assetId, loan.getLoanDate(), HistoryEntry.Type.LOAN, details));
                if (loan.isLost()) {
                    entries.add(new HistoryEntry(assetId, loan.getLostDate(), HistoryEntry.Type.LOST,
                            "Borrower " + loan.getBorrowerId() + "; equipment reported lost; due "
                                    + loan.getDueDate()));
                }
                for (LoanRenewal event : loan.getRenewals()) {
                    entries.add(new HistoryEntry(assetId, event.date(), HistoryEntry.Type.RENEWAL,
                            "Borrower " + loan.getBorrowerId() + "; due changed from "
                                    + event.previousDueDate() + " to " + event.newDueDate()));
                }
            }
        }
        for (HistoryEntry entry : otherHistory.get()) {
            if (assetId.equals(entry.getAssetId())) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparing(HistoryEntry::getDate)
                .thenComparing(HistoryEntry::getType)
                .thenComparing(entry -> entry.getType() == HistoryEntry.Type.RENEWAL ? "" : entry.getDescription()));
        return entries;
    }
}
