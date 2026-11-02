package lablog;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Buffers one interactive result so every console shares paging and result framing. */
final class CommandOutput extends PrintStream {
    private final ByteArrayOutputStream pending;
    private final ConsoleInput console;
    private final PrintStream destination;

    /** Captures output without taking ownership of the destination stream. */
    CommandOutput(ConsoleInput console, PrintStream destination) {
        this(new ByteArrayOutputStream(), console, destination);
    }

    private CommandOutput(ByteArrayOutputStream pending, ConsoleInput console, PrintStream destination) {
        super(pending, true, StandardCharsets.UTF_8);
        this.pending = pending;
        this.console = console;
        this.destination = destination;
    }

    /** Presents pending text before a guided prompt or after a complete command. */
    void showPending() {
        flush();
        String text = pending.toString(StandardCharsets.UTF_8);
        pending.reset();
        if (!text.isEmpty()) {
            console.presentOutput(text, destination);
        }
    }
}
