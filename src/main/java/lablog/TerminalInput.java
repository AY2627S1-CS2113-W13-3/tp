package lablog;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Display;
import org.jline.utils.InfoCmp.Capability;
import org.jline.utils.NonBlockingReader;

/** Owns the native terminal and delegates command editing to JLine. */
final class TerminalInput implements ConsoleInput {
    private final Terminal terminal;
    private final BoxReader reader;
    private int outputWidth;

    private TerminalInput(Terminal terminal) throws IOException {
        this.terminal = terminal;
        this.reader = new BoxReader(terminal);
    }

    /** Opens a native console, falling back without reading any input on unsupported sessions. */
    static ConsoleInput open() {
        if (System.console() == null || "dumb".equalsIgnoreCase(System.getenv("TERM"))) {
            return ConsoleInput.plain(new Scanner(System.in), System.out);
        }
        Terminal terminal = null;
        try {
            terminal = TerminalBuilder.builder().system(true).dumb(false).build();
            if (terminal.getType().startsWith("dumb")) {
                terminal.close();
                return ConsoleInput.plain(new Scanner(System.in), System.out);
            }
            return new TerminalInput(terminal);
        } catch (IOException | RuntimeException | LinkageError exception) {
            if (terminal != null) {
                try {
                    terminal.close();
                } catch (IOException ignored) {
                    // Retain the plain fallback even when native cleanup fails.
                }
            }
            return ConsoleInput.plain(new Scanner(System.in), System.out);
        }
    }

    @Override
    public String readLine(String label, String prompt) {
        reader.label = label == null ? "" : label;
        if (!reader.label.isEmpty() && terminal.getSize().getColumns() >= BoxReader.MIN_BOX_WIDTH) {
            terminal.writer().println();
            terminal.flush();
        }
        while (true) {
            try {
                String line = reader.readLine(reader.colored(prompt).toAnsi(terminal));
                if (!reader.label.isEmpty() || line.isEmpty()) {
                    return line;
                }
            } catch (UserInterruptException exception) {
                // Ctrl-C discards the current command and starts a fresh prompt.
            } catch (EndOfFileException exception) {
                return null;
            }
        }
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    @Override
    public void clearScreen() {
        terminal.puts(Capability.clear_screen);
        reader.resetDisplay();
        terminal.flush();
    }

    /** Scrolls the old viewport into history and starts the newest result at the top. */
    @Override
    public void beginOutput() {
        int rows = terminal.getSize().getRows();
        int columns = terminal.getSize().getColumns();
        outputWidth = 0;
        if (rows <= 0 || columns <= 1 || terminal.getStringCapability(Capability.cursor_address) == null) {
            return;
        }
        outputWidth = columns - 1;
        // Leave breathing room after the input hint in the preserved command history.
        terminal.writer().println();
        terminal.writer().println();
        terminal.puts(Capability.cursor_address, rows - 1, 0);
        // Newlines at the bottom scroll every old row into history without erasing it.
        for (int row = 0; row < rows; row++) {
            terminal.writer().println();
        }
        terminal.puts(Capability.cursor_address, 0, 0);
        reader.resetDisplay();
        terminal.writer().println("-".repeat(outputWidth));
        terminal.writer().println();
        terminal.flush();
    }

    /** Closes the result block, leaving normal scrolling in effect for long results. */
    @Override
    public void endOutput() {
        if (outputWidth == 0) {
            return;
        }
        terminal.writer().println();
        terminal.writer().println("-".repeat(outputWidth));
        terminal.writer().println();
        terminal.flush();
        reader.resetDisplay();
        outputWidth = 0;
    }

    /** Frames short results and opens long results at their first page. */
    @Override
    public void presentOutput(String text, PrintStream output) {
        Size size = terminal.getSize();
        if (size.getRows() < 7 || size.getColumns() < 2
                || terminal.getStringCapability(Capability.cursor_address) == null) {
            output.print(text);
            output.flush();
            return;
        }
        List<AttributedString> lines = wrapOutput(text, size.getColumns() - 1);
        // Reserve the frame, spacing, and next command box below a short result.
        if (lines.size() <= Math.max(1, size.getRows() - 10)) {
            beginOutput();
            try {
                for (AttributedString line : lines) {
                    terminal.writer().println(line.toAnsi(terminal));
                }
                terminal.flush();
            } finally {
                endOutput();
            }
            return;
        }
        showPages(text);
    }

    /** Wraps at word boundaries while preserving rendered widths, indentation and colour. */
    private List<AttributedString> wrapOutput(String text, int width) {
        String adapted = ResponsiveTableLayout.reflow(text, Math.max(1, width));
        AttributedString content = new AttributedStringBuilder().tabs(4)
                .append(AttributedString.fromAnsi(ConsolePresentation.format(adapted, true))).toAttributedString();
        return TerminalWordWrapper.wrap(terminal, content, Math.max(1, width));
    }

    /** Uses JLine key bindings and display updates without entering an alternate screen. */
    private void showPages(String text) {
        beginOutput();
        terminal.puts(Capability.cursor_address, 0, 0);
        Display pages = new Display(terminal, true);
        pages.setScrollOptimization(false);
        BindingReader keys = new BindingReader(terminal.reader());
        KeyMap<PageAction> bindings = pageBindings();
        Attributes previous = terminal.enterRawMode();
        int offset = 0;
        int wrappedWidth = -1;
        int displayedWidth = -1;
        int displayedRows = -1;
        int displayedOffset = -1;
        List<AttributedString> lines = List.of();
        terminal.puts(Capability.keypad_xmit);
        try {
            while (true) {
                Size size = terminal.getSize();
                if (size.getRows() < 7 || size.getColumns() < 2) {
                    return;
                }
                int width = Math.max(1, size.getColumns() - 1);
                int capacity = Math.max(1, size.getRows() - 6);
                if (width != wrappedWidth) {
                    lines = wrapOutput(text, width);
                    wrappedWidth = width;
                }
                int lastOffset = Math.max(0, lines.size() - capacity);
                offset = Math.max(0, Math.min(offset, lastOffset));
                if (width != displayedWidth || size.getRows() != displayedRows || offset != displayedOffset) {
                    List<AttributedString> screen = new ArrayList<>();
                    AttributedString separator = new AttributedString("-".repeat(width));
                    String context = PagerContext.at(lines, offset);
                    if (context.equals("RESULT")) {
                        String firstSourceLine = text.stripLeading().lines().findFirst().orElse("");
                        context = PagerContext.at(List.of(new AttributedString(firstSourceLine)), 0);
                    }
                    screen.add(contextHeading(context, width));
                    screen.add(separator);
                    screen.add(AttributedString.EMPTY);
                    for (int row = 0; row < capacity; row++) {
                        int index = offset + row;
                        screen.add(index < lines.size() ? lines.get(index) : AttributedString.EMPTY);
                    }
                    screen.add(AttributedString.EMPTY);
                    screen.add(separator);
                    String hint = String.format(
                            "Lines %d-%d/%d | Up/Down Arrow: scroll | Enter: close",
                            offset + 1, Math.min(offset + capacity, lines.size()), lines.size());
                    screen.add(reader.colored(hint).columnSubSequence(terminal, 0, width));
                    // Explicit default-style padding prevents a heading background leaking across blank cells.
                    for (int row = 0; row < screen.size(); row++) {
                        AttributedString line = screen.get(row);
                        screen.set(row, new AttributedStringBuilder().append(line).style(AttributedStyle.DEFAULT)
                                .append(" ".repeat(Math.max(0, width - line.columnLength(terminal))))
                                .toAttributedString());
                    }
                    pages.resize(size);
                    pages.update(screen, size.cursorPos(screen.size() - 1, 0), true);
                    displayedWidth = width;
                    displayedRows = size.getRows();
                    displayedOffset = offset;
                }
                // A timed, non-consuming peek lets a resize redraw while no key is pressed.
                int next = keys.peekCharacter(200);
                if (next == NonBlockingReader.READ_EXPIRED) {
                    continue;
                }
                if (next < 0) {
                    return;
                }
                PageAction action = keys.readBinding(bindings);
                if (action == null || action == PageAction.CLOSE) {
                    return;
                }
                switch (action) {
                case NEXT -> offset++;
                case PREVIOUS -> offset--;
                default -> {
                    // Ignore unrelated keys rather than passing them to the command prompt.
                }
                }
            }
        } finally {
            terminal.puts(Capability.keypad_local);
            terminal.setAttributes(previous);
            terminal.puts(Capability.cursor_address, Math.max(0, terminal.getSize().getRows() - 1), 0);
            terminal.writer().println();
            terminal.flush();
            reader.resetDisplay();
            outputWidth = 0;
        }
    }

    /** Keeps the current result and section visible independently of the scrolled body. */
    private AttributedString contextHeading(String context, int width) {
        AttributedStringBuilder heading = new AttributedStringBuilder();
        if (System.getenv("NO_COLOR") == null) {
            heading.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE)
                    .background(AttributedStyle.BLUE).bold());
        }
        return heading.append(" ").append(context).append(" ").toAttributedString()
                .columnSubSequence(terminal, 0, width);
    }

    /** Binds navigation through JLine's terminal-specific key definitions. */
    private KeyMap<PageAction> pageBindings() {
        KeyMap<PageAction> bindings = new KeyMap<>();
        bindings.setNomatch(PageAction.IGNORE);
        bindings.setUnicode(PageAction.IGNORE);
        bindings.bind(PageAction.CLOSE, "\r", "\n", KeyMap.ctrl('C'), KeyMap.ctrl('D'));
        bindPageKey(bindings, PageAction.NEXT, Capability.key_down);
        bindPageKey(bindings, PageAction.PREVIOUS, Capability.key_up);
        return bindings;
    }

    private void bindPageKey(KeyMap<PageAction> bindings, PageAction action, Capability capability) {
        String key = KeyMap.key(terminal, capability);
        if (key != null) {
            bindings.bind(action, key);
        }
    }

    /** Navigation operations decoded by JLine, independent of native key sequences. */
    private enum PageAction {
        NEXT, PREVIOUS, CLOSE, IGNORE
    }

    @Override
    public void close() {
        try {
            terminal.close();
        } catch (IOException ignored) {
            // Terminal close has already attempted to restore native console settings.
        }
    }

    /** Renders a bordered editing region through JLine's cursor-aware display engine. */
    private static final class BoxReader extends LineReaderImpl {
        private static final int MIN_BOX_WIDTH = 40;
        private final boolean useColor = System.getenv("NO_COLOR") == null;
        private String label = "";
        private boolean finishing;

        private BoxReader(Terminal terminal) throws IOException {
            super(terminal, "LabLog");
        }

        /** Forgets stale cursor coordinates after the application moves the viewport. */
        private void resetDisplay() {
            lock.lock();
            try {
                display.reset();
            } finally {
                lock.unlock();
            }
        }

        private AttributedString colored(String text) {
            AttributedStringBuilder result = new AttributedStringBuilder();
            if (useColor) {
                result.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
            }
            return result.append(text).toAttributedString();
        }

        /** Keeps the final box intact and moves subsequent output below its last row. */
        @Override
        protected void cleanup() {
            finishing = true;
            try {
                super.cleanup();
            } finally {
                finishing = false;
            }
        }

        /** Redraws wrapped rows and places the cursor using JLine's display coordinates. */
        @Override
        protected void redisplay(boolean flush) {
            if (label.isEmpty() || size.getColumns() < MIN_BOX_WIDTH || size.getRows() < 6) {
                super.redisplay(flush);
                return;
            }
            lock.lock();
            try {
                int width = size.getColumns() - 1;
                int innerWidth = width - 4;
                AttributedString content = new AttributedStringBuilder().tabs(4)
                        .append(prompt).style(AttributedStyle.DEFAULT).append(buf.toString()).toAttributedString();
                List<AttributedString> wrapped = new ArrayList<>(
                        content.columnSplitLength(terminal, innerWidth, true, false));
                AttributedString beforeCursor = new AttributedStringBuilder().tabs(4)
                        .append(prompt).append(buf.upToCursor()).toAttributedString();
                List<AttributedString> cursorLines = beforeCursor.columnSplitLength(terminal, innerWidth, true, false);
                int cursorRow = Math.max(0, cursorLines.size() - 1);
                int cursorColumn = cursorLines.isEmpty() ? 0
                        : cursorLines.get(cursorRow).columnLength(terminal);
                if (cursorColumn == innerWidth) {
                    cursorRow++;
                    cursorColumn = 0;
                }
                while (wrapped.size() <= cursorRow) {
                    wrapped.add(AttributedString.EMPTY);
                }
                boolean mainPrompt = "TYPE COMMAND".equalsIgnoreCase(label);
                int capacity = Math.max(1, size.getRows() - (mainPrompt ? 4 : 3));
                int start = Math.max(0, cursorRow - capacity + 1);
                int end = Math.min(wrapped.size(), start + capacity);
                List<AttributedString> lines = new ArrayList<>();
                String title = " " + label + " ";
                if (title.length() > width - 2) {
                    title = title.substring(0, width - 2);
                }
                lines.add(colored("+" + title + "-".repeat(width - 2 - title.length()) + "+"));
                for (int i = start; i < end; i++) {
                    AttributedString row = wrapped.get(i);
                    lines.add(new AttributedStringBuilder().append("| ").append(row)
                            .append(" ".repeat(Math.max(0, innerWidth - row.columnLength(terminal))))
                            .append(" |").toAttributedString());
                }
                lines.add(colored("+" + "-".repeat(width - 2) + "+"));
                if (mainPrompt) {
                    lines.add(new AttributedString("Enter to run | help for commands | exit to quit")
                            .columnSubSequence(terminal, 0, width));
                }
                int cursor = finishing ? size.cursorPos(lines.size() - 1, 0)
                        : size.cursorPos(cursorRow - start + 1, cursorColumn + 2);
                display.update(lines, cursor, flush);
            } finally {
                lock.unlock();
            }
        }
    }
}
