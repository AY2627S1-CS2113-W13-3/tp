package lablog;

import java.util.List;

/**
 * Lays out rows of text as a plain-text table with a header row and a separator line.
 * Every column except the last is padded to its widest cell so the {@code |} separators line up
 * in a monospaced console. The last column is not padded, so lines carry no trailing spaces.
 */
final class TableFormatter {
    private static final String COLUMN_SEPARATOR = " | ";
    private static final String HEADER_JOINT = "-+-";

    private TableFormatter() {
    }

    /**
     * Formats a table without a trailing newline.
     *
     * @param headers column titles; the number of columns
     * @param rows data rows, each with exactly one cell per header
     * @return the header, a separator line and one line per row, joined by newlines
     * @throws IllegalArgumentException if a row has a different number of cells than there are headers
     */
    static String format(List<String> headers, List<List<String>> rows) {
        int[] widths = new int[headers.size()];
        for (int column = 0; column < widths.length; column++) {
            widths[column] = headers.get(column).length();
        }
        for (List<String> row : rows) {
            if (row.size() != widths.length) {
                throw new IllegalArgumentException("Table row has " + row.size() + " cells; expected "
                        + widths.length);
            }
            for (int column = 0; column < widths.length; column++) {
                widths[column] = Math.max(widths[column], row.get(column).length());
            }
        }
        StringBuilder table = new StringBuilder();
        appendRow(table, headers, widths);
        table.append('\n');
        for (int column = 0; column < widths.length; column++) {
            table.append(column == 0 ? "" : HEADER_JOINT).append("-".repeat(widths[column]));
        }
        for (List<String> row : rows) {
            table.append('\n');
            appendRow(table, row, widths);
        }
        return table.toString();
    }

    /** Appends one line with every cell but the last padded to its column width. */
    private static void appendRow(StringBuilder table, List<String> cells, int[] widths) {
        int last = widths.length - 1;
        for (int column = 0; column <= last; column++) {
            String cell = cells.get(column);
            table.append(column == 0 ? "" : COLUMN_SEPARATOR).append(cell);
            if (column < last) {
                table.append(" ".repeat(widths[column] - cell.length()));
            }
        }
    }
}
