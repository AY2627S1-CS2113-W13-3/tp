package lablog;

import java.io.PrintStream;

/** Presents the main console's start-up banner, product version and purpose. */
public final class ConsoleWelcome {
    /** Current released version; update alongside the next release. */
    public static final String VERSION = "2.1";

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

    /**
     * Prints the compact welcome after the startup banner has been acknowledged.
     *
     * @param output destination for the ready screen
     */
    public static void printReady(PrintStream output) {
        output.println("LabLog v" + VERSION);
        output.println("Lab equipment and loan manager");
        output.println();
        output.println("Ready. Changes are saved automatically.");
        output.println();
        output.println("Start here");
        output.println("    overview");
        output.println("        View overdue loans, today's reservations and severe faults");
        output.println("    list");
        output.println("        Browse equipment and availability");
        output.println("    help");
        output.println("        Explore commands and examples");
    }
}
