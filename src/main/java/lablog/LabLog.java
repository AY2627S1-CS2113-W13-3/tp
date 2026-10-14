package lablog;

import java.io.PrintStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Main entry point for the LabLog command-line application.
 */
public class LabLog {
    /** Records prompt validation and incomplete registration without storing user input. */
    private static final Logger LOGGER = Logger.getLogger(LabLog.class.getName());
    private final Inventory inventory;
    private final HistoryCommands history;
    private final ReturnCommands returns;
    private final LoanCommands loanCommands;
    private final HelpCommands help = new HelpCommands();
    /** Present only for persistent CLI sessions; injected test sessions remain in memory. */
    private LabLogStore store;
    private final BorrowerCommands borrowerCommands;
    private final EquipmentAddCommands equipmentAdd;
    private final HistoryRecordCommands historyRecords;
    private final ActiveLoanCommands activeLoans;
    private final EquipmentListCommands equipmentList;



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
        this(inventory, loans, otherHistory, Clock.systemDefaultZone());
    }

    /**
     * Connects shared records with an explicit clock for repeatable return-date tests.
     *
     * @param inventory shared equipment inventory
     * @param loans shared loan book
     * @param otherHistory current reservation, fault, and maintenance entries
     * @param clock clock used to date returns
     */
    public LabLog(Inventory inventory, LoanBook loans, Supplier<List<HistoryEntry>> otherHistory, Clock clock) {
        this(inventory, loans, otherHistory, clock, new BorrowerBook());
    }

    /** Connects both command groups to the same equipment, loans, and borrower records. */
    public LabLog(Inventory inventory, LoanBook loans, Supplier<List<HistoryEntry>> otherHistory,
            Clock clock, BorrowerBook borrowers) {
        this.inventory = inventory;
        this.equipmentAdd = new EquipmentAddCommands(inventory);
        this.history = new HistoryCommands(inventory, loans, otherHistory);
        this.historyRecords = new HistoryRecordCommands(inventory);
        this.activeLoans = new ActiveLoanCommands(loans, clock);
        this.history = new HistoryCommands(inventory, loans, () -> {
            List<HistoryEntry> entries = new ArrayList<>(otherHistory.get());
            entries.addAll(historyRecords.getEntries());
            return entries;
        });
        this.equipmentList = new EquipmentListCommands(inventory, loans);
        this.returns = new ReturnCommands(inventory, loans, clock);
        this.loanCommands = new LoanCommands(inventory, loans, clock, borrowers);
        this.borrowerCommands = new BorrowerCommands(borrowers, loans,
                assetId -> inventory.getEquipmentUnits().stream()
                        .filter(unit -> unit.getAssetId().equals(assetId)).map(Equipment::getName)
                        .findFirst().orElse(null), clock);
    }

    /**
     * Starts equipment registration and equipment history lookup.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Use: java -jar lablog.jar [DATA_FILE]");
            return;
        }
        try {
            load(Path.of(args.length == 1 ? args[0] : "data/lablog.properties")).run();
        } catch (IOException exception) {
            System.err.println("Error: Cannot load LabLog data. " + exception.getMessage());
        }
    }

    /** Creates a persistent session only after all saved records load successfully. */
    public static LabLog load(Path file) throws IOException {
        LabLogStore store = new LabLogStore(file);
        LabLog application = new LabLog(store.getInventory(), store.getLoans(), List::of);
        application.store = store;
        return application;
    }

    /** Saves before displaying success; failure ends the session to avoid further unsaved work. */
    private boolean saveChanges(PrintStream output) {
        if (store == null) {
            return true;
        }
        try {
            store.save();
            return true;
        } catch (IOException exception) {
            LOGGER.warning("Cannot save LabLog records; stopping the session");
            output.println("Error: Changes could not be saved. " + exception.getMessage());
            output.println("Session stopped. Previous saved data is unchanged; retry after fixing file access.");
            return false;
        }
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
     * Accepts an explicit add, help, loan, history or return command, or exit at the
     * initial prompt.
     * Incomplete equipment input is discarded at EOF rather than saving a partial unit.
     *
     * @param scanner input owned by the caller
     * @param output destination for prompts and results
     */
    public void run(Scanner scanner, PrintStream output) {
        output.println("Welcome to LabLog!");
        output.println("Manage your laboratory equipment and loans here.");
        output.println(help.execute("help"));
        while (true) {
            output.println("Enter add ASSET_ID, help [COMMAND], loan ASSET_ID b/BORROWER_ID d/DUE_DATE, "
                    + "history ASSET_ID, return ASSET_ID [faults/DESCRIPTION], or exit:");
            if (!scanner.hasNextLine()) {
                return;
            }
            String input = scanner.nextLine().strip();
            if (input.equals("exit")) {
                return;
            }
            String command = input.split("\\s+", 2)[0];
            if (command.equals("help")) {
                try {
                    output.println(help.execute(input));
                } catch (IllegalArgumentException exception) {
                    output.println("Error: " + exception.getMessage());
                }
                continue;
            }
            if (List.of("reservation", "fault", "maintenance").contains(command)) {
                try {
                    output.println(historyRecords.execute(input));
                } catch (IllegalArgumentException exception) {
                    output.println("Error: " + exception.getMessage());
                }
                continue;
            }
            if (command.equals("active-loans")) {
                try {
                    output.println(activeLoans.execute(input));
                } catch (IllegalArgumentException exception) {
                    output.println("Error: " + exception.getMessage());
                }
                continue;
            }
            if (command.equals("list")) {
                try {
                    output.println(equipmentList.execute(input));
                } catch (IllegalArgumentException exception) {
                    output.println("Error: " + exception.getMessage());
                }
                continue;
            }
            if (command.equals("history") || command.equals("return") || command.equals("loan")) {
                try {
                    String result = executeRecordCommand(command, input);
                    if (!command.equals("history") && !saveChanges(output)) {
                        return;
                    }
                    output.println(result);
                } catch (IllegalArgumentException exception) {
                    output.println("Error: " + exception.getMessage());
                }
                continue;
            }
            if (List.of("loans", "add-borrower").contains(command)) {
                try {
                    output.println(borrowerCommands.execute(input));
                } catch (IllegalArgumentException exception) {
                    output.println("Error: " + exception.getMessage());
                } catch (IOException exception) {
                    output.println("Error: Cannot save borrower data. " + exception.getMessage());
                    output.println("No changes saved.");
                }
                continue;
            }
            if (!command.equals("add")) {
                LOGGER.fine("Rejected unknown command");
                output.println("Error: Unknown command. Use help for commands.");
                continue;
            }
            if (store != null && inventory.getEquipmentUnits().stream()
                    .anyMatch(unit -> unit.getAssetId().equals(input))) {
                output.println("Error: Equipment " + input + " already exists.");
                continue;
            }
            Equipment equipment = readEquipment(input, scanner, output);
            if (equipment == null) {
                LOGGER.fine("Discarded incomplete equipment registration at end of input");
                output.println("Equipment not added: incomplete input.");
                return;
            }
            inventory.addEquipment(equipment);
            if (!saveChanges(output)) {
                return;
            }
            output.println("Equipment added to inventory.");
        }
    }

    /** Displays every equipment unit currently registered in the inventory. */
    private void listEquipment(PrintStream output) {
        List<Equipment> equipmentUnits = inventory.getEquipmentUnits();
        if (equipmentUnits.isEmpty()) {
            output.println("No equipment registered.");
            return;
        }
        output.println("Asset ID | Name | Category | Model | Location");
        for (Equipment equipment : equipmentUnits) {
            output.println(equipment.getAssetId() + " | " + equipment.getName() + " | "
                    + equipment.getCategory() + " | " + equipment.getModel() + " | "
                    + equipment.getLocation());
        }
    }

    /**
     * Runs one command that reads or changes the shared equipment and loan records.
     *
     * @param command first word of the input
     * @param input complete command line
     * @return text to display
     */
    private String executeRecordCommand(String command, String input) {
        // Only the record-command branch in run should call this helper.
        assert List.of("history", "return", "loan").contains(command) : "Unexpected record command";
        switch (command) {
        case "history":
            return history.execute(input);
        case "return":
            return returns.execute(input);
        case "loan":
            return loanCommands.execute(input);
        default:
            throw new IllegalArgumentException("Unknown command: " + command);
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
        // The initial prompt must validate the identifier before requesting equipment details.
        assert assetId != null && !assetId.isEmpty()
                && assetId.chars().noneMatch(Character::isWhitespace) : "Expected a validated asset ID";
        String[] prompts = {"name", "category", "model", "location"};
        String[] details = new String[prompts.length];
        for (int i = 0; i < prompts.length; i++) {
            output.println("Enter " + prompts[i] + ": (type cancel to abort)");
            if (!scanner.hasNextLine()) {
                return null;
            }
            details[i] = scanner.nextLine();
            if (details[i].strip().equals("cancel")) {
                throw new RegistrationCancelledException();
            }
        }
        return new Equipment(assetId, details[0], details[1], details[2], details[3]);
    }

    /** Separates an explicit cancellation from EOF, which closes the console. */
    private static class RegistrationCancelledException extends RuntimeException {
    }
}
