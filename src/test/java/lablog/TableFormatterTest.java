package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Verifies the exact column padding, separator line and edge cases of {@link TableFormatter}. */
class TableFormatterTest {
    @Test
    void format_padsAllColumnsButLastAndAddsSeparatorLine() {
        String table = TableFormatter.format(List.of("Asset ID", "Name", "Status"),
                List.of(List.of("A", "Oscilloscope", "On loan"), List.of("LONG-ID-1", "Fan", "Available")));

        // Column widths are 9, 12 and 9: the widest cell in each column.
        assertEquals("Asset ID  | Name         | Status\n"
                + "-".repeat(10) + "+" + "-".repeat(14) + "+" + "-".repeat(10) + "\n"
                + "A         | Oscilloscope | On loan\n"
                + "LONG-ID-1 | Fan          | Available", table);
    }

    @Test
    void format_noRows_showsHeaderAndSeparatorOnly() {
        assertEquals("Name | Status\n-----+-------", TableFormatter.format(List.of("Name", "Status"), List.of()));
    }

    @Test
    void format_rowWithWrongCellCount_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TableFormatter.format(List.of("A", "B"), List.of(List.of("only one"))));
    }

    @Test
    void format_neverLeavesTrailingSpaces() {
        String table = TableFormatter.format(List.of("Name", "Notes"), List.of(List.of("x", "short")));

        assertEquals(table.lines().map(String::stripTrailing).toList(), table.lines().toList());
    }
}
