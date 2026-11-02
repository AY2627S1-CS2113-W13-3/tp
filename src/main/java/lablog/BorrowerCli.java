package lablog;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Scanner;

/**
 * Runs the standalone borrower console with shared terminal input and result presentation.
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
        try {
            BorrowerCommands commands = new BorrowerCommands(new BorrowerBook(file), new LoanBook(),
                    assetId -> null, Clock.systemDefaultZone());
            try (ConsoleInput console = TerminalInput.open()) {
                PrintStream output = ConsolePresentation.decorate(System.out, console.isInteractive());
                run(console, output, commands);
            }
        } catch (IOException exception) {
            System.err.println("Error: Cannot load borrower data. "
                    + TerminalText.escapeControls(exception.getMessage()));
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
        run(ConsoleInput.plain(scanner, output), output, commands);
    }

    /**
     * Shares borrower dispatch between native terminal sessions and caller-owned plain input.
     * Interactive results are captured for framing and paging; exit and EOF produce no result frame.
     *
     * @param console command reader owned by the caller
     * @param output destination for startup text and presented results
     * @param commands borrower handlers backed by the caller's data
     */
    static void run(ConsoleInput console, PrintStream output, BorrowerCommands commands) {
        output.println("LabLog borrower console. Use add-borrower or loans. Type exit or send EOF to exit.");
        CommandHistory history = new CommandHistory();
        PrintStream destination = output;
        while (true) {
            String input = console.readLine("TYPE COMMAND", "lablog> ");
            if (input == null) {
                break;
            }
            if (!TerminalText.hasUnsafeControls(input) && input.strip().equals("exit")) {
                break;
            }
            CommandOutput resultOutput = console.isInteractive() ? new CommandOutput(console, destination) : null;
            if (resultOutput != null) {
                output = resultOutput;
            }
            try {
                if (TerminalText.hasUnsafeControls(input)) {
                    output.println("Error: Input must not contain control characters.");
                    continue;
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
                    output.println("Error: " + TerminalText.escapeControls(exception.getMessage()));
                    output.println("No changes saved.");
                } catch (IOException exception) {
                    output.println("Error: Cannot save borrower data. "
                            + TerminalText.escapeControls(exception.getMessage()));
                    output.println("No changes saved.");
                }
            } finally {
                if (resultOutput != null) {
                    try {
                        resultOutput.showPending();
                    } finally {
                        resultOutput.close();
                        output = destination;
                    }
                }
            }
        }
    }
}
