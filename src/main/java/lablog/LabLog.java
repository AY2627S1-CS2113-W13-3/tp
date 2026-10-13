package lablog;

import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;
import java.util.function.Supplier;

/**
 * Main entry point for the LabLog command-line application.
 */
public class LabLog {
    private final Inventory inventory;
    private final HistoryCommands history;

    /** Creates an application with empty in-memory equipment and loan records. */
    public LabLog() {
        this(new Inventory(), new LoanBook(), List::of);
    }

    /**
     * Connects equipment registration and history to the application's shared records.
     *
     * @param inventory shared equipment inventory
     * @param loans shared loan book
     * @param otherHistory current reservation, fault, and maintenance display entries
     */
    public LabLog(Inventory inventory, LoanBook loans, Supplier<List<HistoryEntry>> otherHistory) {
        this.inventory = inventory;
        this.history = new HistoryCommands(inventory, loans, otherHistory);
    }

    /**
     * Starts equipment registration and equipment history lookup.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        new LabLog().run();
    }

    /**
     * Displays the welcome message and adds an equipment unit to the inventory.
     */
    public void run() {
        try (Scanner scanner = new Scanner(System.in)) {
            run(scanner, System.out);
        }
    }

    /**
     * Accepts an asset ID to register, a history command, or exit at the initial prompt.
     * Incomplete equipment input is discarded at EOF rather than saving a partial unit.
     *
     * @param scanner input owned by the caller
     * @param output destination for prompts and results
     */
    public void run(Scanner scanner, PrintStream output) {
        output.println("Welcome to LabLog!");
        output.println("Manage your laboratory equipment and loans here.");
        while (true) {
            output.println("Enter asset ID, history ASSET_ID, or exit:");
            if (!scanner.hasNextLine()) {
                return;
            }
            String input = scanner.nextLine().strip();
            if (input.equals("exit")) {
                return;
            }
            if (input.split("\\s+", 2)[0].equals("history")) {
                try {
                    output.println(history.execute(input));
                } catch (IllegalArgumentException exception) {
                    output.println("Error: " + exception.getMessage());
                }
                continue;
            }
            Equipment equipment = readEquipment(input, scanner, output);
            if (equipment == null) {
                output.println("Equipment not added: incomplete input.");
                return;
            }
            inventory.addEquipment(equipment);
            output.println("Equipment added to inventory.");
        }
    }

    /**
     * Prompts for the remaining details after the initial asset ID input.
     *
     * @param assetId identifier supplied at the initial prompt
     * @param scanner reads the user's input
     * @param output destination for prompts
     * @return equipment unit created from the input, or null if input ends prematurely
     */
    private Equipment readEquipment(String assetId, Scanner scanner, PrintStream output) {
        String[] prompts = {"name", "category", "model", "location"};
        String[] details = new String[prompts.length];
        for (int i = 0; i < prompts.length; i++) {
            output.println("Enter " + prompts[i] + ":");
            if (!scanner.hasNextLine()) {
                return null;
            }
            details[i] = scanner.nextLine();
        }
        return new Equipment(assetId, details[0], details[1], details[2], details[3]);
    }
}
