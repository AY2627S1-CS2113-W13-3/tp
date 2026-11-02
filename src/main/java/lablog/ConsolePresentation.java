package lablog;

import java.io.PrintStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds terminal color at the output boundary without changing command results or saved records.
 * Only fixed ANSI sequences are emitted; text supplied by commands cannot supply terminal escapes.
 */
final class ConsolePresentation {
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[1;36m";
    /** Fixed foreground/background pairs keep filled labels readable on dark terminals. */
    private static final String GREEN_LABEL = "\u001B[30;102m";
    private static final String AMBER_LABEL = "\u001B[30;103m";
    private static final String RED_LABEL = "\u001B[97;41m";
    private static final String NEUTRAL_LABEL = "\u001B[30;106m";
    private static final String CYAN_TITLE = "\u001B[1;30;106m";
    private static final String BLUE_TITLE = "\u001B[1;97;44m";
    private static final String MAGENTA_TITLE = "\u001B[1;97;45m";
    private static final String NEUTRAL_TITLE = "\u001B[30;47m";
    private static final Pattern STATUS = Pattern.compile(
            "(?<![A-Za-z0-9_-])(ON LOAN|ON_LOAN|AVAILABLE|UNAVAILABLE|OVERDUE|LOST|RETIRED"
                    + "|CONFIRMED|CANCELLED|WAITING|RESOLVED|REVERSED|Available|On loan|Unavailable|Retired)"
                    + "(?![A-Za-z0-9_-])");

    private ConsolePresentation() {
    }

    /**
     * Returns a non-owning stream that decorates string output when color is requested.
     * A present NO_COLOR environment variable disables color, including when its value is empty.
     * Closing the wrapper flushes but never closes the caller's stream.
     *
     * @param output destination owned by the caller
     * @param color whether the destination supports ANSI color
     * @return presentation stream; plain mode preserves the original text exactly
     */
    static PrintStream decorate(PrintStream output, boolean color) {
        return new PresentationStream(output, color && System.getenv("NO_COLOR") == null);
    }

    /**
     * Formats text before a native terminal or pager measures and renders it.
     *
     * @param text text to present
     * @param color whether the destination supports ANSI color
     * @return styled text, or the original text when color is disabled or NO_COLOR is present
     */
    static String format(String text, boolean color) {
        return color && System.getenv("NO_COLOR") == null ? style(text) : text;
    }

    /**
     * Suggests the accepted date format only for explicit date-validation errors.
     * Callers choose whether to print the hint, so decorating plain output never changes its text.
     *
     * @param message diagnostic text without any presentation escapes
     * @return a focused hint, or an empty string when no date-format problem is identified
     */
    static String errorHint(String message) {
        if (message == null) {
            return "";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("date") && (lower.contains("invalid") || lower.contains("yyyy-mm-dd")
                || lower.contains("could not be parsed") || lower.contains("must be a valid"))) {
            return "Hint: Use a real calendar date in YYYY-MM-DD format, for example 2099-09-18.";
        }
        return "";
    }

    /** Styles each logical line independently so a multiline command result cannot leak color. */
    private static String style(String text) {
        String safe = escapeControls(text);
        Matcher lines = Pattern.compile("[^\r\n]+").matcher(safe);
        StringBuilder result = new StringBuilder();
        while (lines.find()) {
            lines.appendReplacement(result, Matcher.quoteReplacement(styleLine(lines.group())));
        }
        lines.appendTail(result);
        return result.toString();
    }

    /** Escapes control characters except the line breaks and tabs needed for console layout. */
    private static String escapeControls(String text) {
        StringBuilder result = new StringBuilder();
        text.codePoints().forEach(character -> {
            if (Character.isISOControl(character) && character != '\n'
                    && character != '\r' && character != '\t') {
                result.append(String.format("\\u%04X", character));
            } else {
                result.appendCodePoint(character);
            }
        });
        return result.toString();
    }

    /** Colors known feedback and heading prefixes before highlighting individual status words. */
    private static String styleLine(String line) {
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("Error:")) {
            return feedbackLabel(line, "Error:", " ERROR ", RED_LABEL);
        }
        if (trimmed.startsWith("Warning:")) {
            return feedbackLabel(line, "Warning:", " WARNING ", AMBER_LABEL);
        }
        if (trimmed.startsWith("[WARN]")) {
            return feedbackLabel(line, "[WARN]", " WARNING ", AMBER_LABEL);
        }
        if (trimmed.startsWith("Overdue loans (") || trimmed.startsWith("Unresolved severe faults (")
                || trimmed.startsWith("Overdue loans /") || trimmed.startsWith("Unresolved severe faults /")
                || trimmed.startsWith("OVERDUE LOANS (") || trimmed.startsWith("SEVERE FAULTS (")) {
            return headingLabel(line, trimmed, AMBER_LABEL);
        }
        String headingColor = headingColor(trimmed);
        if (headingColor != null) {
            return headingLabel(line, trimmed, headingColor);
        }
        String detailLabel = detailLabel(trimmed);
        if (detailLabel != null) {
            return headingLabel(line, detailLabel, NEUTRAL_TITLE);
        }
        if (trimmed.startsWith("lablog>")) {
            int start = line.length() - trimmed.length();
            return line.substring(0, start) + CYAN + trimmed + RESET;
        }
        if (trimmed.startsWith("[OK]")) {
            return feedbackLabel(line, "[OK]", " OK ", GREEN_LABEL);
        }
        Matcher statuses = STATUS.matcher(line);
        StringBuilder result = new StringBuilder();
        while (statuses.find()) {
            String status = statuses.group();
            String color = switch (status.toUpperCase(Locale.ROOT)) {
            case "AVAILABLE", "CONFIRMED", "RESOLVED" -> GREEN_LABEL;
            case "UNAVAILABLE", "LOST" -> RED_LABEL;
            case "RETIRED", "CANCELLED", "REVERSED" -> NEUTRAL_LABEL;
            default -> AMBER_LABEL;
            };
            statuses.appendReplacement(result, Matcher.quoteReplacement(color + status + RESET));
        }
        statuses.appendTail(result);
        return result.toString();
    }

    /**
     * Replaces a feedback prefix with a padded filled label and resets before the message.
     * Status tokens below use no padding, preserving the visible width of aligned table cells.
     */
    private static String feedbackLabel(String line, String prefix, String label, String color) {
        int start = line.indexOf(prefix);
        String message = line.substring(start + prefix.length());
        String gap = message.isEmpty() || Character.isWhitespace(message.charAt(0)) ? "" : " ";
        return line.substring(0, start) + color + label + RESET + gap + message;
    }

    /**
     * Adds one space of padding around only the known heading, preserving its indentation
     * and any following explanation. The reset prevents the background filling the line.
     */
    private static String headingLabel(String line, String heading, String color) {
        int start = line.length() - line.stripLeading().length();
        return line.substring(0, start) + color + " " + heading + " " + RESET
                + line.substring(start + heading.length());
    }

    /** Selects a consistent palette for fixed application titles and help categories. */
    private static String headingColor(String line) {
        String categoryColor = switch (line) {
        case "== Equipment ==", "== Records ==", "== Records and overview ==" -> CYAN_TITLE;
        case "== Loans ==" -> BLUE_TITLE;
        case "== Borrowers ==", "== Reservations ==" -> MAGENTA_TITLE;
        case "== Faults ==", "== Faults and maintenance ==" -> AMBER_LABEL;
        case "== General ==" -> NEUTRAL_TITLE;
        default -> null;
        };
        if (categoryColor != null) {
            return categoryColor;
        }
        if (line.equals("Start here") || line.equals("LabLog commands:")
                || line.startsWith("LabLog v") || line.startsWith("Help:")) {
            return BLUE_TITLE;
        }
        if (line.startsWith("EQUIPMENT /")
                || line.startsWith("OVERVIEW /") || line.startsWith("ADD EQUIPMENT /")
                || line.startsWith("Operational overview for ") || line.startsWith("TODAY /")
                || line.startsWith("Today's reservations (") || line.startsWith("Today's reservations /")
                || line.startsWith("TODAY'S RESERVATIONS (")) {
            return CYAN_TITLE;
        }
        return line.startsWith("LOAN /") ? BLUE_TITLE : null;
    }

    /** Recognizes nested help labels, leaving command examples and their values unfilled. */
    private static String detailLabel(String line) {
        if (line.equals("Usage") || line.equals("Example") || line.equals("Required")
                || line.equals("Before issuing")) {
            return line;
        }
        for (String prefix : new String[] {"Format:", "Usage:", "Batch usage:", "Fields:", "Hint:", "Example:"}) {
            if (line.equals(prefix) || line.startsWith(prefix + " ")) {
                return prefix;
            }
        }
        return null;
    }

    /** Delegates output and error state while retaining ownership of the underlying stream. */
    private static final class PresentationStream extends PrintStream {
        private final PrintStream destination;
        private final boolean color;

        private PresentationStream(PrintStream destination, boolean color) {
            super(destination, true, destination.charset());
            this.destination = destination;
            this.color = color;
        }

        @Override
        public synchronized void print(String text) {
            destination.print(color ? style(String.valueOf(text)) : text);
        }

        @Override
        public synchronized void println(String text) {
            destination.println(color ? style(String.valueOf(text)) : text);
        }

        @Override
        public void flush() {
            destination.flush();
        }

        @Override
        public boolean checkError() {
            return destination.checkError();
        }

        @Override
        public void close() {
            flush();
        }
    }
}
