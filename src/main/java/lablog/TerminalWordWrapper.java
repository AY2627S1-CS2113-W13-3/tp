package lablog;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Wraps plain terminal output at word boundaries while retaining its indentation. */
final class TerminalWordWrapper {
    /** Keeps joined emoji, modifiers and combining marks within one visible character. */
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private TerminalWordWrapper() {
    }

    /**
     * Splits logical lines and wraps their content using conservative display-column estimates.
     * Tabs must already be expanded by the caller. One final newline does not create an extra row.
     * Widths below one are treated as one. A grapheme cluster wider than the available columns is
     * retained whole on its own row, since splitting or dropping it would corrupt the content.
     *
     * @param content plain output before ANSI styling
     * @param width available display columns
     * @return plain display rows, including intentional empty logical lines
     */
    static List<String> wrap(String content, int width) {
        List<String> rows = new ArrayList<>();
        int start = 0;
        for (int end = 0; end <= content.length(); end++) {
            if (end == content.length() || content.charAt(end) == '\n') {
                int lineEnd = end > start && content.charAt(end - 1) == '\r' ? end - 1 : end;
                wrapLine(content.substring(start, lineEnd), Math.max(1, width), rows);
                start = end + 1;
            }
        }
        if (content.length() > 0 && content.charAt(content.length() - 1) == '\n'
                && !rows.isEmpty() && rows.getLast().length() == 0) {
            rows.removeLast();
        }
        return rows;
    }

    /** Repeats leading spaces, leaving at least half the row available for actual content. */
    private static void wrapLine(String line, int width,
            List<String> rows) {
        if (displayWidth(line) <= width) {
            rows.add(line);
            return;
        }
        int leading = 0;
        while (leading < line.length() && line.charAt(leading) == ' ') {
            leading++;
        }
        int indent = Math.min(leading, width / 2);
        String prefix = line.substring(0, indent);
        int available = width - indent;
        int start = leading;
        if (start == line.length()) {
            rows.add(prefix);
            return;
        }
        while (start < line.length()) {
            int fit = fittingEnd(line, start, available);
            int end = fit;
            int next = fit;
            if (fit < line.length()) {
                // Whitespace just beyond the fitted text is also a valid word boundary.
                int boundary = fit;
                while (boundary > start && !Character.isWhitespace(Character.codePointAt(line, boundary))) {
                    boundary = Character.offsetByCodePoints(line, boundary, -1);
                }
                if (boundary > start) {
                    end = boundary;
                    next = boundary;
                }
                while (next < line.length() && Character.isWhitespace(Character.codePointAt(line, next))) {
                    next += Character.charCount(Character.codePointAt(line, next));
                }
            }
            rows.add(prefix + line.substring(start, end));
            start = next;
        }
    }

    /** Estimates each non-ASCII grapheme as two columns, retaining complete clusters. */
    static int displayWidth(String text) {
        int columns = 0;
        Matcher clusters = GRAPHEME.matcher(text);
        while (clusters.find()) {
            String cluster = clusters.group();
            columns += cluster.codePoints().allMatch(character -> character < 128) ? cluster.length() : 2;
        }
        return columns;
    }

    /**
     * Finds a slice ending at a grapheme boundary, never separating joined emoji or their modifiers.
     * Non-ASCII graphemes use a conservative estimate rather than terminal-specific width rules.
     */
    private static int fittingEnd(String line, int start, int width) {
        int end = start;
        int columns = 0;
        Matcher graphemes = GRAPHEME.matcher(line).region(start, line.length());
        while (graphemes.find()) {
            int next = graphemes.end();
            int characterWidth = displayWidth(line.substring(graphemes.start(), next));
            if (columns + characterWidth > width && end > start) {
                break;
            }
            columns += characterWidth;
            end = next;
        }
        return end;
    }
}
