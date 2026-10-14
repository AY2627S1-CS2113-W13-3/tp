package lablog;

import java.io.PrintStream;

/** Presents a consistent product version and starting point in both consoles. */
public final class ConsoleWelcome {
    /** Current released version; update alongside the next release. */
    public static final String VERSION = "1.0";

    private ConsoleWelcome() {
    }

    /** Prints the console identity, purpose, and help entry point. */
    public static void print(PrintStream output, boolean borrowerConsole) {
        output.println("LabLog v" + VERSION + (borrowerConsole ? " - Borrower console" : " - Main console"));
        output.println("Manage your laboratory equipment and loans here.");
        output.println("Type help for commands and examples.");
        if (borrowerConsole) {
            output.println("Send EOF to exit (Ctrl+Z then Enter on Windows, Ctrl+D on macOS/Linux).");
        }
    }
}
