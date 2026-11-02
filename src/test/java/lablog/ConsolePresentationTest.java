package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Checks output-boundary safety and table styling independently of domain commands. */
class ConsolePresentationTest {
    @Test
    void format_plainModeRetainsOriginalText() {
        String text = "Name: \u6f22\u5b57\u001B[2J\u0007\n";
        assertEquals(text, ConsolePresentation.format(text, false));
    }

    @Test
    void format_interactiveEscapesControlsEvenWithoutColour() {
        String result = ConsolePresentation.format("Name: \u6f22\u5b57\u001B[2J\u0007\n", true);
        assertFalse(result.contains("\u001B[2J"));
        assertFalse(result.contains("\u0007"));
        assertTrue(result.contains("\u6f22\u5b57"));
        if (System.getenv("NO_COLOR") != null) {
            assertFalse(result.contains("\u001B"));
        }
    }

    @Test
    void format_statusBadgesPreserveColumnsAndFreeText() {
        String table = TableFormatter.format(
                List.of("Asset ID", "Name", "Category", "Model", "Location", "Availability"),
                List.of(List.of("A", "AVAILABLE sensor", "LOST tools", "M", "Lab", "ON LOAN")));
        String result = ConsolePresentation.format(table, true);
        assertEquals(table, result.replaceAll("\u001B\\[[0-9;]*m", ""));
        assertTrue(result.contains("AVAILABLE sensor"));
        assertTrue(result.contains("LOST tools"));
    }
}
