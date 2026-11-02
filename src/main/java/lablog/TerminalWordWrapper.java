package lablog;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;

/** Wraps styled terminal output at word boundaries while retaining its indentation and attributes. */
final class TerminalWordWrapper {
    /** Keeps joined emoji, modifiers and combining marks within one visible character. */
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private TerminalWordWrapper() {
    }

    /**
     * Splits logical lines and wraps their content using JLine display-column measurements.
     * Tabs must already be expanded by the caller. One final newline does not create an extra row.
     * Widths below one are treated as one. A grapheme cluster wider than the available columns is
     * retained whole on its own row, since splitting or dropping it would corrupt the content.
     *
     * @param terminal terminal whose Unicode width rules apply
     * @param content attributed output with any ANSI escapes already decoded
     * @param width available display columns
     * @return styled display rows, including intentional empty logical lines
     */
    static List<AttributedString> wrap(Terminal terminal, AttributedString content, int width) {
        List<AttributedString> rows = new ArrayList<>();
        int start = 0;
        for (int end = 0; end <= content.length(); end++) {
            if (end == content.length() || content.charAt(end) == '\n') {
                int lineEnd = end > start && content.charAt(end - 1) == '\r' ? end - 1 : end;
                wrapLine(terminal, content.subSequence(start, lineEnd), Math.max(1, width), rows);
                start = end + 1;
            }
        }
        if (content.length() > 0 && content.charAt(content.length() - 1) == '\n'
                && !rows.isEmpty() && rows.getLast().length() == 0) {
            rows.removeLast();
        }
        return rows;
    }

    /** Repeats styled leading spaces, leaving at least half the row available for actual content. */
    private static void wrapLine(Terminal terminal, AttributedString line, int width,
            List<AttributedString> rows) {
        if (line.columnLength(terminal) <= width) {
            rows.add(line);
            return;
        }
        int leading = 0;
        while (leading < line.length() && line.charAt(leading) == ' ') {
            leading++;
        }
        int indent = Math.min(leading, width / 2);
        AttributedString prefix = line.subSequence(0, indent);
        int available = width - indent;
        int start = leading;
        if (start == line.length()) {
            rows.add(prefix);
            return;
        }
        while (start < line.length()) {
            int fit = fittingEnd(terminal, line, start, available);
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
            rows.add(new AttributedStringBuilder().append(prefix).append(line.subSequence(start, end))
                    .toAttributedString());
            start = next;
        }
    }

    /**
     * Finds a slice ending at a grapheme boundary, never separating joined emoji or their modifiers.
     * Measuring attributed slices delegates Unicode and hidden-character widths to JLine.
     */
    private static int fittingEnd(Terminal terminal, AttributedString line, int start, int width) {
        int end = start;
        int columns = 0;
        Matcher graphemes = GRAPHEME.matcher(line).region(start, line.length());
        while (graphemes.find()) {
            int next = graphemes.end();
            int characterWidth = line.subSequence(graphemes.start(), next).columnLength(terminal);
            if (columns + characterWidth > width && end > start) {
                break;
            }
            columns += characterWidth;
            end = next;
        }
        return end;
    }
}
