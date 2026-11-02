package lablog;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts wide, known console tables into complete stacked records before terminal styling and wrapping. */
final class ResponsiveTableLayout {
    private static final Pattern SEPARATOR = Pattern.compile("-+(?:  -+)+");
    private static final Pattern COLUMN = Pattern.compile("-+");
    private static final Pattern LINE = Pattern.compile("([^\\r\\n]*)(\\r\\n|\\n|\\r|$)");

    private ResponsiveTableLayout() {
    }

    /**
     * Reflows recognized tables wider than the terminal or containing uncertain-width Unicode cells.
     * Cell boundaries come from the dashed separator, so spaces inside values are preserved.
     * Non-ASCII cells are conservatively stacked because UTF-16 length does not establish display width.
     * Surrounding text and fitting ASCII tables retain their original line endings and contents.
     *
     * @param text plain command output, before ANSI styling
     * @param width available terminal columns; nonpositive widths are treated as one column
     * @return output with wide or Unicode tables replaced by records containing every field
     */
    static String reflow(String text, int width) {
        List<Line> lines = linesOf(text);
        StringBuilder output = new StringBuilder();
        int index = 0;
        while (index < lines.size()) {
            Layout layout = index + 1 < lines.size()
                    ? layoutOf(lines.get(index).content(), lines.get(index + 1).content()) : null;
            if (layout == null) {
                output.append(lines.get(index++).original());
                continue;
            }
            List<List<String>> rows = new ArrayList<>();
            int end = index + 2;
            while (end < lines.size()) {
                List<String> cells = layout.cellsOf(lines.get(end).content());
                if (cells == null || lines.get(end).content().isBlank()) {
                    break;
                }
                rows.add(cells);
                end++;
            }
            if (rows.isEmpty()) {
                output.append(lines.get(index++).original());
                continue;
            }
            boolean uncertainDisplayWidth = rows.stream().flatMap(List::stream)
                    .anyMatch(cell -> cell.codePoints().anyMatch(character -> character > 0x7F || Character.isISOControl(character)));
            if (layout.width() <= Math.max(1, width) && !uncertainDisplayWidth) {
                while (index < end) {
                    output.append(lines.get(index++).original());
                }
                continue;
            }
            String newline = lines.get(index).ending();
            appendRecords(output, layout.headers(), rows, newline,
                    lines.get(end - 1).ending());
            index = end;
        }
        return output.toString();
    }

    /** Retains each original newline, including CRLF and an unterminated final line. */
    private static List<Line> linesOf(String text) {
        List<Line> lines = new ArrayList<>();
        Matcher matcher = LINE.matcher(text);
        while (matcher.find() && matcher.start() < text.length()) {
            lines.add(new Line(matcher.group(1), matcher.group(2)));
        }
        return lines;
    }

    /** Requires both a genuine separator layout and one of the application's fixed header signatures. */
    private static Layout layoutOf(String header, String separator) {
        if (!SEPARATOR.matcher(separator).matches()) {
            return null;
        }
        List<Column> columns = new ArrayList<>();
        Matcher matcher = COLUMN.matcher(separator);
        while (matcher.find()) {
            columns.add(new Column(matcher.start(), matcher.end()));
        }
        Layout candidate = new Layout(columns, List.of(), separator.length());
        List<String> headers = candidate.cellsOf(header);
        if (headers == null || !knownHeaders(String.join("|", headers))) {
            return null;
        }
        return new Layout(columns, headers, separator.length());
    }

    /** Enumerates actual table signatures to avoid treating arbitrary dashed prose as a table. */
    private static boolean knownHeaders(String headers) {
        return switch (headers) {
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

    /** Emits every original column, including the first field repeated in the numbered record title. */
    private static void appendRecords(StringBuilder output, List<String> headers, List<List<String>> rows,
            String newline, String finalEnding) {
        for (int row = 0; row < rows.size(); row++) {
            if (row > 0) {
                output.append(newline).append(newline);
            }
            List<String> cells = rows.get(row);
            output.append('[').append(row + 1).append("] ").append(cells.getFirst());
            for (int column = 0; column < headers.size(); column++) {
                output.append(newline).append("    ").append(headers.get(column))
                        .append(": ").append(cells.get(column));
            }
        }
        output.append(finalEnding);
    }

    /** One source line and its exact terminator. */
    private record Line(String content, String ending) {
        private String original() {
            return content + ending;
        }
    }

    /** Half-open character positions of a column, excluding its two-space separator. */
    private record Column(int start, int end) {
    }

    /** Separator-derived positions and fixed field labels for one recognized table. */
    private record Layout(List<Column> columns, List<String> headers, int width) {
        /** Rejects blank/short suffixes and malformed separators rather than consuming surrounding prose. */
        private List<String> cellsOf(String line) {
            int lastStart = columns.getLast().start();
            if (line.length() < lastStart || line.length() > width) {
                return null;
            }
            List<String> cells = new ArrayList<>();
            for (int index = 0; index < columns.size(); index++) {
                Column column = columns.get(index);
                if (index + 1 < columns.size()) {
                    int nextStart = columns.get(index + 1).start();
                    if (line.length() < nextStart || !line.substring(column.end(), nextStart).equals("  ")) {
                        return null;
                    }
                }
                cells.add(line.substring(column.start(), Math.min(column.end(), line.length())).stripTrailing());
            }
            return cells;
        }
    }
}
