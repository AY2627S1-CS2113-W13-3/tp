package lablog;

import java.io.PrintStream;

/** Presents the main console's start-up banner, product version and purpose. */
public final class ConsoleWelcome {
    /** Current released version; update alongside the next release. */
    public static final String VERSION = "2.0";

    /**
     * "LabLog" in the figlet Standard font. Plain ASCII keeps it readable in every terminal
     * encoding; Windows consoles garble non-ASCII characters such as box-drawing symbols.
     */
    private static final String[] BANNER = {
        "  _          _     _",
        " | |    __ _| |__ | |    ___   __ _",
        " | |   / _` | '_ \\| |   / _ \\ / _` |",
        " | |__| (_| | |_) | |__| (_) | (_| |",
        " |_____\\__,_|_.__/|_____\\___/ \\__, |",
        "                              |___/",
    };

    private ConsoleWelcome() {
    }

    /** Prints the banner, then the version and purpose of the main console. */
    public static void print(PrintStream output) {
        for (String line : BANNER) {
            output.println(line);
        }
        output.println("LabLog v" + VERSION + " - Lab equipment and loan manager");
        output.println("Manage your laboratory equipment and loans here.");
    }
}
