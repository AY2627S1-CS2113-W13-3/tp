package lablog;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads fixed-width tables into compact rows while preserving spaces inside cell values. */
final class TableText {
    private TableText() {
    }

    /** Returns only table rows when a table is present, leaving empty-result messages intact. */
    static String table(String text) {
        String compact = compact(text);
        List<String> rows = compact.lines().filter(line -> line.contains(" | ")).toList();
        return rows.isEmpty() ? compact : String.join("\n", rows);
    }

    /** Uses the generated separator's column boundaries rather than splitting cell contents on spaces. */
    static String compact(String text) {
        List<String> lines = text.lines().toList();
        List<String> result = new ArrayList<>();
        List<Integer> starts = List.of();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (index + 1 < lines.size() && lines.get(index + 1).matches("-+(?:  -+)+")
                    && line.startsWith("lablog> ")) {
                line = line.substring("lablog> ".length());
            }
            if (index + 1 < lines.size() && lines.get(index + 1).matches("-+(?:  -+)+")) {
                starts = new ArrayList<>();
                Matcher columns = Pattern.compile("-+").matcher(lines.get(index + 1));
                while (columns.find()) {
                    starts.add(columns.start());
                }
            } else if (line.matches("-+(?:  -+)+")) {
                continue;
            } else if (line.isBlank() || starts.isEmpty() || line.length() < starts.getLast()) {
                starts = List.of();
                result.add(line);
                continue;
            }
            List<String> cells = new ArrayList<>();
            for (int column = 0; column < starts.size(); column++) {
                int end = column + 1 < starts.size() ? starts.get(column + 1) - 2 : line.length();
                cells.add(line.substring(starts.get(column), Math.min(end, line.length())).stripTrailing());
            }
            result.add(String.join(" | ", cells));
        }
        return String.join("\n", result);
    }
}
