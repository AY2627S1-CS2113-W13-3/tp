package lablog;

/** Lists registered units with availability derived from current loans and severe faults. */
public class EquipmentListCommands {
    private final Inventory inventory;
    private final LoanBook loans;
    private final FaultBook faults;

    public EquipmentListCommands(Inventory inventory, LoanBook loans) {
        this(inventory, loans, new FaultBook(inventory));
    }

    /** Shares the fault reports used by lending validation. */
    public EquipmentListCommands(Inventory inventory, LoanBook loans, FaultBook faults) {
        this.inventory = inventory;
        this.loans = loans;
        this.faults = faults;
    }

    /** Lists units in registration order without changing inventory or loans. */
    public String execute(String input) {
        if (input == null || !input.strip().equals("list")) {
            throw new IllegalArgumentException("Use: list");
        }
        if (inventory.getEquipmentUnits().isEmpty()) {
            return "No equipment registered.";
        }
        StringBuilder result = new StringBuilder("Asset ID | Name | Category | Model | Location | Availability");
        for (Equipment unit : inventory.getEquipmentUnits()) {
            result.append("\n").append(unit.getAssetId()).append(" | ").append(unit.getName())
                    .append(" | ").append(unit.getCategory()).append(" | ").append(unit.getModel())
                    .append(" | ").append(unit.getLocation()).append(" | ")
                    .append(loans.hasActiveLoan(unit.getAssetId()) ? "On loan"
                            : faults.blocksLending(unit.getAssetId()) ? "Unavailable: severe fault" : "Available");
        }
        return result.toString();
    }
}
