package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Checks word preservation and Unicode boundaries in terminal wrapping. */
class TerminalWordWrapperTest {
    @Test
    void wrap_keepsWordsWholeAndRepeatsIndentation() {
        String text = "    Issue equipment to a registered borrower using the supplied due date.";
        List<String> rows = wrap(text, 32);
        String reconstructed = String.join(" ", rows.stream().map(row -> row.strip()).toList());

        assertEquals(text.strip(), reconstructed);
        assertTrue(rows.size() > 1);
        assertTrue(rows.stream().allMatch(row -> row.startsWith("    ")));
        assertTrue(rows.stream().allMatch(row -> TerminalWordWrapper.displayWidth(row) <= 32));
    }

    @Test
    void wrap_longUnicodeTokenLosesNoCodePoints() {
        String token = "LONG-" + "\u6f22\u5b57\ud83d\ude42".repeat(8);
        List<String> rows = wrap(token, 12);

        assertEquals(token, String.join("", rows));
        assertTrue(rows.stream().allMatch(row -> TerminalWordWrapper.displayWidth(row) <= 12));
        assertTrue(rows.stream().noneMatch(row -> Character.isLowSurrogate(row.charAt(0))));
    }

    @Test
    void wrap_preservesBlankLinesAndNormalisesCrLf() {
        assertEquals(List.of("one", "", "two"), wrap("one\r\n\r\ntwo\r\n", 10));
    }

    @Test
    void wrap_keepsJoinedEmojiModifiersFlagsAndCombiningMarksTogether() {
        for (String cluster : List.of("\uD83D\uDC69\u200D\uD83D\uDCBB", "\uD83D\uDC4D\uD83C\uDFFD",
                "\uD83C\uDDF8\uD83C\uDDEC", "1\uFE0F\u20E3", "e\u0301",
                "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66")) {
            String token = "x".repeat(6) + cluster + "TAIL";
            List<String> rows = wrap(token, 8);

            assertEquals(token, String.join("", rows));
            assertTrue(rows.stream().anyMatch(row -> row.contains(cluster)),
                    "The complete visible character must stay on one row: " + cluster);
        }
    }

    @Test
    void wrap_clusterWiderThanTheTerminal_retainsTheWholeCharacter() {
        String emoji = "\uD83D\uDC69\u200D\uD83D\uDCBB";
        String text = emoji.repeat(2);
        for (int width : new int[]{0, 1, 2}) {
            List<String> rows = wrap(text, width);
            assertEquals(List.of(emoji, emoji), rows);
        }
    }

    private List<String> wrap(String text, int width) {
        return TerminalWordWrapper.wrap(text, width);
    }
}
