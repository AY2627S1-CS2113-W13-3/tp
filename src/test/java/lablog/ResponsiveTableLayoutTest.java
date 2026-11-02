package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Verifies that responsive tables preserve complete records and surrounding output. */
class ResponsiveTableLayoutTest {
    @Test
    void reflow_fittingAsciiTableIsByteForByteUnchanged() {
        String table = TableFormatter.format(List.of("Asset ID", "Name", "Category", "Model", "Location"),
                List.of(List.of("A1", "Scope", "Electronics", "Model 1", "Lab A"))).replace("\n", "\r\n");
        assertEquals(table, ResponsiveTableLayout.reflow(table, 200));
    }

    @Test
    void reflow_narrowTablePreservesLongIdsDoubleSpacesAndEmptyCells() {
        String id = "R-" + "abcdef0123456789".repeat(5);
        List<String> headers = List.of("Reservation ID", "Asset ID", "Borrower ID", "From", "Through", "Status");
        List<String> cells = List.of(id, "OSC-001", "A  B", "2099-01-01", "2099-01-02", "");
        String table = TableFormatter.format(headers, List.of(cells));
        String adapted = ResponsiveTableLayout.reflow(table, 40);
        for (int column = 0; column < headers.size(); column++) {
            assertTrue(adapted.contains("    " + headers.get(column) + ": " + cells.get(column)));
        }
        assertTrue(adapted.startsWith("[1] " + id));
    }

    @Test
    void reflow_preservesCrLfTitlesAndFootersAroundMultipleRecords() {
        String table = TableFormatter.format(List.of("Date", "Type", "Details"),
                List.of(List.of("2026-11-02", "FAULT", "Long details for the first record"),
                        List.of("2026-11-03", "MAINTENANCE", "Other details"))).replace("\n", "\r\n");
        String result = ResponsiveTableLayout.reflow("History for X:\r\n" + table + "\r\n\r\nFooter.", 20);

        assertTrue(result.startsWith("History for X:\r\n[1] 2026-11-02"));
        assertTrue(result.contains("\r\n\r\n[2] 2026-11-03"));
        assertTrue(result.endsWith("\r\n\r\nFooter."));
        assertTrue(result.contains("Details: Long details for the first record"));
        assertTrue(result.contains("Details: Other details"));
    }

    @Test
    void reflow_stacksUnicodeCellsWithoutLosingSurrogatePairs() {
        String name = "\u6f22\u5b57\ud83d\ude42";
        String table = TableFormatter.format(List.of("Asset ID", "Name", "Category", "Model", "Location"),
                List.of(List.of("A1", name, "Electronics", "Model 1", "Lab A")));
        assertTrue(ResponsiveTableLayout.reflow(table, 200).contains("Name: " + name));
    }

    @Test
    void reflow_leavesUnknownDashedProseAlone() {
        String prose = "Not a known table\n----  -----\nAAAA  BBBBB\n";
        assertEquals(prose, ResponsiveTableLayout.reflow(prose, 5));
    }

    @Test
    void reflow_recognisesAllCommandTableSignatures() {
        List<List<String>> layouts = List.of(
                List.of("Asset ID", "Name", "Category", "Model", "Location", "Availability"),
                List.of("Asset ID", "Name", "Category", "Model", "Location"),
                List.of("Asset ID", "Borrower ID", "Due date", "Status"),
                List.of("Asset ID", "Equipment", "Issued", "Due", "Overdue", "Status"),
                List.of("Date", "Type", "Details"),
                List.of("Reservation ID", "Asset ID", "Borrower ID", "From", "Through", "Status"),
                List.of("Reservation ID", "Asset ID", "Borrower ID", "From", "Through"),
                List.of("Request ID", "Borrower ID", "Category", "From", "Through", "Status"),
                List.of("Loan ID", "Asset ID", "Borrower ID", "Due date", "Status"),
                List.of("Fault ID", "Asset ID", "Report date", "Description"));
        for (List<String> headers : layouts) {
            List<String> cells = java.util.stream.IntStream.range(0, headers.size()).mapToObj(i -> "value-" + i).toList();
            String result = ResponsiveTableLayout.reflow(TableFormatter.format(headers, List.of(cells)), 10);
            for (int column = 0; column < headers.size(); column++) {
                assertTrue(result.contains(headers.get(column) + ": " + cells.get(column)));
            }
        }
    }
}
