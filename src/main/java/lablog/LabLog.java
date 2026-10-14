package lablog;

import java.io.PrintStream;
import java.time.Clock;
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
        this.inventory = inventory;
        this.history = new HistoryCommands(inventory, loans, otherHistory);
        this.returns = new ReturnCommands(inventory, loans, clock);
        this.loanCommands = new LoanCommands(inventory, loans, clock);
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
     * Accepts an asset ID to register, a help, loan, history or return command, or exit at the
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
            output.print("lablog> ");
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
            if (command.equals("history") || command.equals("return") || command.equals("loan")) {
                try {
                    output.println(executeRecordCommand(command, input));
                } catch (IllegalArgumentException exception) {
                    output.println("Error: " + exception.getMessage());
                }
                continue;
            }
            if (List.of("loans", "add-borrower").contains(command)) {
                LOGGER.fine("Rejected command: available only in the borrower console");
                output.println("Error: " + command
                        + " is not available in this console. Use help for command details.");
                continue;
            }
            if (input.isEmpty() || input.chars().anyMatch(Character::isWhitespace)) {
                LOGGER.fine("Rejected equipment registration: invalid asset ID format");
                output.println("Error: Enter a single asset ID or use help for commands.");
                continue;
            }
            Equipment equipment = readEquipment(input, scanner, output);
            if (equipment == null) {
                LOGGER.fine("Discarded incomplete equipment registration at end of input");
                output.println("Equipment not added: incomplete input.");
                return;
            }
            inventory.addEquipment(equipment);
            output.println("Equipment added to inventory.");
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
            output.println("Enter " + prompts[i] + ":");
            if (!scanner.hasNextLine()) {
                return null;
            }
            details[i] = scanner.nextLine();
        }
        return new Equipment(assetId, details[0], details[1], details[2], details[3]);
    }
}
