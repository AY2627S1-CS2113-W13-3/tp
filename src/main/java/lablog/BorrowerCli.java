package lablog;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Scanner;

/**
 * Runs borrower commands independently while the team's shared command dispatcher is being developed.
 */
public class BorrowerCli {
    /**
     * Starts the borrower console with persistent borrowers and an initially empty loan book.
     *
     * @param args Optional path to the borrower data file.
     */
    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Use: java lablog.BorrowerCli [BORROWER_FILE]");
            return;
        }
        Path file = Path.of(args.length == 1 ? args[0] : "data/borrowers.properties");
        try (Scanner scanner = new Scanner(System.in)) {
            BorrowerCommands commands = new BorrowerCommands(new BorrowerBook(file), new LoanBook(),
                    assetId -> null, Clock.systemDefaultZone());
            run(scanner, System.out, commands);
        } catch (IOException exception) {
            System.err.println("Error: Cannot load borrower data. " + exception.getMessage());
        }
    }

    /**
     * Processes commands until end of input, recovering from rejected commands and save failures.
     *
     * @param scanner Source of console commands, owned by the caller.
     * @param output Destination for prompts and results.
     * @param commands Handlers backed by the caller's shared data.
     */
    public static void run(Scanner scanner, PrintStream output, BorrowerCommands commands) {
        output.println("LabLog borrower console. Use add-borrower or loans. Type exit or send EOF to exit.");
        CommandHistory history = new CommandHistory();
        while (true) {
            output.print("lablog> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine();
            if (input.strip().equals("exit")) {
                break;
            }
            try {
                String recalled = history.accept(input);
                if (recalled != null) {
                    output.println(recalled);
                    continue;
                }
                String result = commands.execute(input);
                boolean changesRecords = input.strip().startsWith("add-borrower")
                        || input.strip().startsWith("edit-borrower");
                output.println(changesRecords ? Messages.ok(result) : result);
            } catch (IllegalArgumentException exception) {
                output.println("Error: " + exception.getMessage());
                output.println("No changes saved.");
            } catch (IOException exception) {
                output.println("Error: Cannot save borrower data. " + exception.getMessage());
                output.println("No changes saved.");
            }
        }
    }
}
