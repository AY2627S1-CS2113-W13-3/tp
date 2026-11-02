package lablog;

import java.time.Clock;
import java.time.LocalDate;

/** Retires an equipment unit after checking outstanding loans and reservations. */
public class RetireEquipmentCommands {
    private final Inventory inventory;
    private final LoanBook loans;
    private final ReservationBook reservations;
    private final Clock clock;

    public RetireEquipmentCommands(Inventory inventory, LoanBook loans, ReservationBook reservations, Clock clock) {
        this.inventory = inventory;
        this.loans = loans;
        this.reservations = reservations;
        this.clock = clock;
    }

    /** Retains the asset and its history while preventing future use. */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+", 3);
        if (parts.length != 3 || !parts[0].equals("retire") || !parts[2].startsWith("reason/")) {
            throw new IllegalArgumentException("Use: retire ASSET_ID reason/TEXT");
        }
        String reason = parts[2].substring("reason/".length()).strip();
        Equipment unit = inventory.getEquipment(parts[1]);
        if (unit.isRetired()) {
            throw new IllegalArgumentException("Equipment " + parts[1] + " is already retired.");
        }
        if (reason.isBlank() || reason.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Supply a non-empty single-line retirement reason.");
        }
        LocalDate today = LocalDate.now(clock);
        if (loans.hasActiveLoan(parts[1])) {
            throw new IllegalArgumentException("Return or resolve the outstanding loan before retiring equipment.");
        }
        if (reservations.getReservations().stream().anyMatch(item -> !item.isCancelled()
                && item.assetId().equals(parts[1]) && !item.window().end().isBefore(today))) {
            throw new IllegalArgumentException("Cancel current or future reservations before retiring equipment.");
        }
        inventory.updateEquipment(new Equipment(unit.getAssetId(), unit.getName(), unit.getCategory(),
                unit.getModel(), unit.getLocation(), today, reason));
        return "Retired equipment " + parts[1] + " on " + today + ". Reason: " + reason;
    }
}
