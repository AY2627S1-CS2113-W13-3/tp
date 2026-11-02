package lablog;

import java.io.PrintStream;
import java.util.Scanner;

/** Reads commands without coupling the application to a terminal library. */
interface ConsoleInput extends AutoCloseable {
    /** Reads one line, returning null at end of input. */
    String readLine(String label, String prompt);

    boolean isInteractive();

    /** Clears the visible viewport, retaining terminal scrollback. */
    void clearScreen();

    /** Prepares the viewport for one command result; plain streams need no framing. */
    default void beginOutput() {
        // Plain output remains suitable for pipes and scripts.
    }

    /** Finishes command result framing when the console supports it. */
    default void endOutput() {
        // Plain output remains suitable for pipes and scripts.
    }

    /** Presents a captured command result without changing plain stream output. */
    default void presentOutput(String text, PrintStream output) {
        output.print(text);
    }

    @Override
    void close();

    /** Creates a plain reader which leaves the caller-owned scanner open. */
    static ConsoleInput plain(Scanner scanner, PrintStream output) {
        return new ConsoleInput() {
            @Override
            public String readLine(String label, String prompt) {
                while (true) {
                    output.print(prompt);
                    output.flush();
                    String line = scanner.hasNextLine() ? scanner.nextLine() : null;
                    if (line == null || !label.isEmpty() || line.isEmpty()) {
                        return line;
                    }
                }
            }

            @Override
            public boolean isInteractive() {
                return false;
            }

            @Override
            public void clearScreen() {
                // Redirected output has no viewport to clear.
            }

            @Override
            public void close() {
                // The scanner belongs to the caller.
            }
        };
    }
}
