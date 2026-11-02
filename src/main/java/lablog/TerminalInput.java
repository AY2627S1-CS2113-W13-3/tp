package lablog;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/** Provides line-based prompts and paging without native terminal controls. */
final class TerminalInput implements ConsoleInput {
    private final Scanner scanner;
    /** Raw destination: presentation is applied once, after wrapping each page. */
    private final PrintStream output;
    private final int columns;
    private final int rows;
    private boolean ended;

    /** Uses caller-owned streams, making paging independent of a physical terminal. */
    TerminalInput(Scanner scanner, PrintStream output, int columns, int rows) {
        this.scanner = scanner;
        this.output = output;
        this.columns = columns >= 20 && columns <= 1000 ? columns : 100;
        this.rows = rows >= 10 && rows <= 1000 ? rows : 30;
    }

    /** Opens interactive presentation only when a real, supported console is available. */
    static ConsoleInput open() {
        Scanner scanner = new Scanner(System.in);
        if (System.console() == null || "dumb".equalsIgnoreCase(System.getenv("TERM"))) {
            return ConsoleInput.plain(scanner, System.out);
        }
        return new TerminalInput(scanner, System.out,
                dimension(System.getenv("COLUMNS"), 20, 100), dimension(System.getenv("LINES"), 10, 30));
    }

    /** Ignores malformed, tiny and excessive dimensions rather than allocating an enormous page. */
    static int dimension(String value, int minimum, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= minimum && parsed <= 1000 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    @Override
    public String readLine(String label, String prompt) {
        if (ended) {
            return null;
        }
        if (label != null && !label.isEmpty()) {
            output.println();
            output.println("--- " + label + " ---");
        }
        while (true) {
            output.print(prompt);
            output.flush();
            String line = nextLine();
            if (line == null || (label != null && !label.isEmpty()) || line.isEmpty()) {
                return line;
            }
        }
    }

    /** Records EOF so pager input can never be interpreted as a subsequent application command. */
    private String nextLine() {
        if (ended || !scanner.hasNextLine()) {
            ended = true;
            return null;
        }
        return scanner.nextLine();
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    /** Separates startup content without clearing or moving the terminal cursor. */
    @Override
    public void clearScreen() {
        output.println();
    }

    /** Wraps plain content before styling, retaining normal terminal scrollback for every page. */
    @Override
    public void presentOutput(String text, PrintStream destination) {
        String adapted = ResponsiveTableLayout.reflow(sanitize(text), columns - 1);
        List<String> lines = TerminalWordWrapper.wrap(adapted, columns - 1);
        // Style the whole wrapped result once so later pages retain table-column context.
        List<String> styled = ConsolePresentation.format(String.join("\n", lines) + "\n", true).lines().toList();
        int capacity = Math.max(1, rows - 8);
        List<Integer> starts = pageStarts(lines, capacity);
        if (starts.size() == 1) {
            printPage(lines, styled, 0, lines.size(), false);
            return;
        }
        int page = 0;
        while (true) {
            int offset = starts.get(page);
            int end = page + 1 < starts.size() ? starts.get(page + 1) : lines.size();
            printPage(lines, styled, offset, end - offset, true);
            String action;
            while (true) {
                output.print("Enter: next page/close at end | b: back | q: close (then Enter): ");
                output.flush();
                action = nextLine();
                if (action == null || action.isEmpty() || action.equals("b") || action.equals("q")) {
                    break;
                }
                output.println("Use Enter, b or q.");
            }
            if (action == null || action.equals("q")) {
                return;
            }
            if (action.isEmpty()) {
                if (page + 1 >= starts.size()) {
                    return;
                }
                page++;
            } else {
                page = Math.max(0, page - 1);
            }
        }
    }

    /** Precomputes variable page boundaries so backwards navigation returns to the same page. */
    private static List<Integer> pageStarts(List<String> lines, int capacity) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        boolean seenSection = false;
        for (int index = 1; index < lines.size(); index++) {
            int start = starts.getLast();
            boolean sectionStart = PagerContext.isSectionStart(lines, index);
            // Keep the introduction with the first section instead of creating a title-only page.
            if (index - start >= capacity || (sectionStart && seenSection)) {
                starts.add(index);
            }
            seenSection |= sectionStart;
        }
        return starts;
    }

    /** Prints a complete page together so table and heading styling retains its context. */
    private void printPage(List<String> lines, List<String> styled, int offset, int capacity, boolean paged) {
        int end = Math.min(lines.size(), offset + capacity);
        output.println("-".repeat(columns - 1));
        if (paged) {
            output.println(PagerContext.at(lines, offset));
        }
        output.print(String.join("\n", styled.subList(offset, end)) + "\n");
        if (paged) {
            // Reserve the same body height even when a section ends before the page is full.
            for (int row = end - offset; row < Math.max(1, rows - 8); row++) {
                output.println();
            }
        }
        output.println("-".repeat(columns - 1));
        if (paged) {
            output.printf("Lines %d-%d/%d%n", offset + 1, end, lines.size());
        }
        output.flush();
    }

    /** Makes untrusted controls visible before measuring text, while preserving layout whitespace. */
    private static String sanitize(String text) {
        StringBuilder safe = new StringBuilder();
        int column = 0;
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == '\t') {
                int spaces = 4 - column % 4;
                safe.append(" ".repeat(spaces));
                column += spaces;
            } else if (character == '\n') {
                safe.append(character);
                column = 0;
            } else if (Character.isISOControl(character)) {
                safe.append(String.format("\\u%04X", (int) character));
                column += 6;
            } else {
                safe.append(character);
                column++;
            }
        }
        return safe.toString();
    }

    @Override
    public void close() {
        output.flush();
        // Standard input and output belong to the application, not this presentation boundary.
    }
}
