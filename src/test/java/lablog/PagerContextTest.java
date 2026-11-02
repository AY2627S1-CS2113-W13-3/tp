package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Checks that sticky pager context follows real sections and ignores record values. */
class PagerContextTest {
    @Test
    void at_helpContextChangesOnlyWhenSectionReachesTop() {
        List<String> lines = lines("LabLog commands:", "", " == Equipment == ",
                "    add ASSET_ID", "        Register equipment", " == Loans == ", "    loan ASSET_ID");
        assertEquals("HELP", PagerContext.at(lines, 0));
        assertEquals("HELP / EQUIPMENT", PagerContext.at(lines, 4));
        assertEquals("HELP / LOANS", PagerContext.at(lines, 6));
    }

    @Test
    void at_detailedHelpRetainsNestedContext() {
        List<String> lines = List.of(
                " LOAN / Issue equipment to a borrower ",
                "    Usage", "        loan ASSET_ID",
                "     Required ",
                "        b/BORROWER_ID");
        assertEquals("HELP LOAN / USAGE", PagerContext.at(lines, 2));
        assertEquals("HELP LOAN / REQUIRED", PagerContext.at(lines, 4));
    }

    @Test
    void at_overviewFollowsOperationalSection() {
        List<String> lines = lines("OVERVIEW / 2026-11-02", "OVERDUE LOANS (1)",
                "row", "TODAY'S RESERVATIONS (2)", "row", "SEVERE FAULTS (3)", "row");
        assertEquals("OVERVIEW / OVERDUE LOANS", PagerContext.at(lines, 2));
        assertEquals("OVERVIEW / TODAY'S RESERVATIONS", PagerContext.at(lines, 4));
        assertEquals("OVERVIEW / SEVERE FAULTS", PagerContext.at(lines, 6));
    }

    @Test
    void at_recordAndRecalledValuesCannotReplaceContext() {
        assertEquals("RECALLED COMMAND", PagerContext.at(lines("Recalled (not executed):",
                "LabLog commands:", "== Loans =="), 2));
        assertEquals("HISTORY", PagerContext.at(lines("History for A1:", "    == Loans ==",
                "    OVERVIEW / 2026-11-02"), 2));
    }

    @Test
    void at_recognisesBareTablesAndHandlesOutOfRangeOffsets() {
        assertEquals("EQUIPMENT",
                PagerContext.at(lines("Asset ID  Name  Category  Model  Location  Availability"), 99));
        assertEquals("EQUIPMENT", PagerContext.at(lines("Asset ID: A1", "Name: Scope"), 1));
        assertEquals("RESULT", PagerContext.at(List.of(), 0));
        assertEquals("RESULT", PagerContext.at(lines("Unknown output"), -1));
    }

    private List<String> lines(String... lines) {
        return List.of(lines);
    }
}
