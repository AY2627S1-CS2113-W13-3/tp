package lablog;

import java.util.stream.Collectors;

/** Test helper that reduces padded console tables to their compact "a | b | c" row form. */
final class TableText {
    private TableText() {
    }

    /**
     * Removes table separator lines and the padding in front of each column separator, so tests can
     * assert on row content and order without depending on column widths.
     */
    static String compact(String text) {
        return text.lines().filter(line -> !line.matches("-+(-\\+-+)+"))
                .map(line -> line.replaceAll(" +\\| ", " | "))
                .collect(Collectors.joining("\n"));
    }
}
