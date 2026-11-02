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
    /** Only complete values in known status columns receive badges. */
    private static final Pattern STATUS = Pattern.compile(
            "ON LOAN|ON_LOAN|AVAILABLE|UNAVAILABLE|OVERDUE|LOST|RETIRED|CONFIRMED|CANCELLED|WAITING"
                    + "|RESOLVED|REVERSED|Available|On loan|Unavailable(?:: severe fault)?|Retired|Lost|On time"
                    + "|Confirmed|Cancelled|UNAVAILABLE: SEVERE FAULT"
                    + "|LOST \\(overdue\\)");
    private ConsolePresentation() {
    }

    /**
     * Returns a non-owning stream that sanitizes and decorates interactive string output.
     * A present NO_COLOR environment variable disables color, including when its value is empty.
     * Closing the wrapper flushes but never closes the caller's stream.
     *
     * @param output destination owned by the caller
     * @param color whether interactive terminal presentation is enabled
     * @return presentation stream; plain mode preserves the original text exactly
     */
    static PrintStream decorate(PrintStream output, boolean color) {
        return new PresentationStream(output, color);
    }

    /**
     * Formats text before a native terminal or pager measures and renders it.
     * Interactive controls are escaped even when NO_COLOR suppresses styling.
     *
     * @param text text to present
     * @param color whether interactive terminal presentation is enabled
     * @return sanitized interactive text (colored unless NO_COLOR is present), or exact plain text
     */
    static String format(String text, boolean color) {
        if (!color) {
            return text;
        }
        String safe = escapeControls(text);
        return System.getenv("NO_COLOR") == null ? style(safe) : safe;
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

    /** Styles logical lines with local table-column context and resets every colored span. */
    private static String style(String text) {
        String safe = escapeControls(text);
        Matcher lines = Pattern.compile("[^\r\n]+").matcher(safe);
        StringBuilder result = new StringBuilder();
        String tableHeader = null;
        int previousEnd = 0;
        while (lines.find()) {
            String line = lines.group();
            if (safe.substring(previousEnd, lines.start()).replace("\r", "").contains("\n\n")) {
                tableHeader = null;
            }
            String presented;
            if (isTableHeader(line)) {
                tableHeader = line;
                presented = CYAN + line + RESET;
            } else if (tableHeader != null && !line.matches("[- ]+")) {
                presented = styleTableRow(line, tableHeader);
            } else {
                presented = styleLine(line);
            }
            lines.appendReplacement(result, Matcher.quoteReplacement(presented));
            previousEnd = lines.end();
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
        String record = styleRecordLine(line, trimmed);
        if (record != null) {
            return record;
        }
        if (trimmed.startsWith("lablog>")) {
            int start = line.length() - trimmed.length();
            return line.substring(0, start) + CYAN + trimmed + RESET;
        }
        if (trimmed.startsWith("[OK]")) {
            return feedbackLabel(line, "[OK]", " OK ", GREEN_LABEL);
        }
        return line;
    }

    /** Styles fixed record labels and generated availability summaries, leaving their values intact. */
    private static String styleRecordLine(String line, String trimmed) {
        for (String label : new String[] {"Asset ID:", "Name:", "Category:", "Model:", "Location:",
            "Availability:", "Retired:", "Reason:", "Asset:", "Borrower:", "Due:", "Faults:",
            "Event ID:", "Loan ID:", "Related event:", "Phone:", "Email:", "Equipment:", "Issued:",
            "Due date:", "Overdue:", "Status:", "Date:", "Type:", "Details:", "Reservation ID:",
            "Borrower ID:", "From:", "Through:", "Request ID:", "Description:", "Fault ID:", "Report date:"}) {
            if (!trimmed.startsWith(label + " ")) {
                continue;
            }
            int start = line.length() - trimmed.length();
            String value = trimmed.substring(label.length());
            if ((label.equals("Availability:") || label.equals("Status:"))
                    && STATUS.matcher(value.strip()).matches()) {
                value = " " + statusColor(value.strip()) + value.strip() + RESET;
            } else if (label.equals("Overdue:") && (value.strip().equals("yes") || value.strip().equals("no"))) {
                value = " " + (value.strip().equals("yes") ? AMBER_LABEL : GREEN_LABEL) + value.strip() + RESET;
            } else if (label.equals("Phone:")) {
                value = value.replace(" | Email: ", " | " + CYAN + "Email:" + RESET + " ");
            }
            return line.substring(0, start) + CYAN + label + RESET + value;
        }
        Matcher event = Pattern.compile("^(\\[\\d+\\] )(LOAN|RETURN|REVERSE_LOAN|REVERSE_RETURN)"
                + "( — \\d{4}-\\d{2}-\\d{2})$")
                .matcher(trimmed);
        if (event.matches()) {
            String color = switch (event.group(2)) {
            case "RETURN" -> GREEN_LABEL;
            case "REVERSE_LOAN", "REVERSE_RETURN" -> NEUTRAL_LABEL;
            default -> BLUE_TITLE;
            };
            return line.substring(0, line.length() - trimmed.length()) + event.group(1)
                    + color + event.group(2) + RESET + event.group(3);
        }
        Matcher availability = Pattern.compile("^(Equipment \\S+ is )(available throughout|unavailable during)"
                + "( \\d{4}-\\d{2}-\\d{2} through \\d{4}-\\d{2}-\\d{2} \\(inclusive\\)[.:])$")
                .matcher(trimmed);
        if (availability.matches()) {
            return line.substring(0, line.length() - trimmed.length()) + availability.group(1)
                    + (availability.group(2).startsWith("available") ? GREEN_LABEL : RED_LABEL)
                    + availability.group(2) + RESET + availability.group(3);
        }
        return null;
    }

    /** Matches complete fixed header layouts, never arbitrary lines merely containing a column word. */
    private static boolean isTableHeader(String line) {
        String columns = line.strip().replaceAll(" {2,}", "|");
        return switch (columns) {
        case "Asset ID|Name|Category|Model|Location|Availability",
                "Asset ID|Name|Category|Model|Location",
                "Asset ID|Borrower ID|Due date|Status",
                "Asset ID|Equipment|Issued|Due|Overdue|Status",
                "Date|Type|Details",
                "Reservation ID|Asset ID|Borrower ID|From|Through|Status",
                "Reservation ID|Asset ID|Borrower ID|From|Through",
                "Request ID|Borrower ID|Category|From|Through|Status",
                "Loan ID|Asset ID|Borrower ID|Due date|Status",
                "Fault ID|Asset ID|Report date|Description" -> true;
        default -> false;
        };
    }

    /** Colors known type/status columns; free-text names, models and descriptions remain untouched. */
    private static String styleTableRow(String line, String header) {
        int typeStart = header.indexOf("Type");
        int detailsStart = header.indexOf("Details");
        if (typeStart >= 0 && detailsStart > typeStart && line.length() >= detailsStart
                && line.matches("\\d{4}-\\d{2}-\\d{2}  .*")) {
            String type = line.substring(typeStart, detailsStart).stripTrailing();
            String color = switch (type) {
            case "LOAN", "RENEWAL" -> BLUE_TITLE;
            case "RESERVATION" -> MAGENTA_TITLE;
            case "FAULT", "MAINTENANCE" -> AMBER_LABEL;
            case "LOST" -> RED_LABEL;
            default -> null;
            };
            if (color != null) {
                return line.substring(0, typeStart) + color + type + RESET
                        + line.substring(typeStart + type.length());
            }
        }
        int statusStart = header.indexOf("Availability");
        if (statusStart < 0) {
            statusStart = header.indexOf("Status");
        }
        if (statusStart < 0 || line.length() <= statusStart) {
            return styleLine(line);
        }
        String status = line.substring(statusStart);
        if (!STATUS.matcher(status).matches()) {
            return styleLine(line);
        }
        String prefix = line.substring(0, statusStart);
        int overdueStart = header.indexOf("Overdue");
        if (overdueStart >= 0) {
            String overdue = prefix.substring(overdueStart).stripTrailing();
            if (overdue.equals("yes") || overdue.equals("no")) {
                prefix = prefix.substring(0, overdueStart)
                        + (overdue.equals("yes") ? AMBER_LABEL : GREEN_LABEL) + overdue + RESET
                        + prefix.substring(overdueStart + overdue.length());
            }
        }
        return prefix + statusColor(status) + status + RESET;
    }

    /** Uses the same palette for inventory availability and loan/reservation states. */
    private static String statusColor(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
        case "AVAILABLE", "CONFIRMED", "RESOLVED", "ON TIME" -> GREEN_LABEL;
        case "UNAVAILABLE", "UNAVAILABLE: SEVERE FAULT", "LOST", "LOST (OVERDUE)" -> RED_LABEL;
        case "RETIRED", "CANCELLED", "REVERSED" -> NEUTRAL_LABEL;
        default -> AMBER_LABEL;
        };
    }

    /**
     * Replaces a feedback prefix with a padded filled label and resets before the message.
     * Table status tokens use no padding, preserving the visible width of aligned cells.
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
        if (line.startsWith("LOAN /") || line.equals("Loan audit:")
                || line.startsWith("Loan audit for ") && line.endsWith(":")) {
            return BLUE_TITLE;
        }
        if (line.startsWith("Active loans for ") && line.endsWith(":")) {
            return BLUE_TITLE;
        }
        if (line.startsWith("History for ") && line.endsWith(":")) {
            return CYAN_TITLE;
        }
        if (line.equals("Reservations (inclusive dates):")) {
            return MAGENTA_TITLE;
        }
        if (line.equals("WAITING requests (not confirmed reservations):")) {
            return AMBER_LABEL;
        }
        if (line.matches("Available throughout \\d{4}-\\d{2}-\\d{2} through \\d{4}-\\d{2}-\\d{2} \\(inclusive\\):")) {
            return CYAN_TITLE;
        }
        if (line.equals("Recent commands (oldest first):") || line.equals("Recalled (not executed):")) {
            return NEUTRAL_TITLE;
        }
        return null;
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
            destination.print(ConsolePresentation.format(String.valueOf(text), color));
        }

        @Override
        public synchronized void println(String text) {
            destination.println(ConsolePresentation.format(String.valueOf(text), color));
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
