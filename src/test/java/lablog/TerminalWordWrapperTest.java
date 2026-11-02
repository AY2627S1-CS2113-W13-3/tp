package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.Test;

/** Checks word preservation, styled text and Unicode boundaries in terminal wrapping. */
class TerminalWordWrapperTest {
    @Test
    void wrap_keepsWordsWholeAndRepeatsIndentation() throws IOException {
        String text = "    Issue equipment to a registered borrower using the supplied due date.";
        List<AttributedString> rows = wrap(new AttributedString(text), 32);
        String reconstructed = String.join(" ", rows.stream().map(row -> row.toString().strip()).toList());

        assertEquals(text.strip(), reconstructed);
        assertTrue(rows.size() > 1);
        assertTrue(rows.stream().allMatch(row -> row.toString().startsWith("    ")));
        assertTrue(rows.stream().allMatch(row -> row.columnLength() <= 32));
    }

    @Test
    void wrap_retainsStylesAcrossWordBoundaries() throws IOException {
        AttributedString text = AttributedString.fromAnsi(
                "\u001B[32mImportant coloured message for the borrower\u001B[0m");
        List<AttributedString> rows = wrap(text, 18);

        assertEquals(text.toString(), String.join(" ", rows.stream().map(AttributedString::toString).toList()));
        assertTrue(rows.stream().allMatch(row -> row.styleAt(0).equals(text.styleAt(0))));
    }

    @Test
    void wrap_longUnicodeTokenLosesNoCodePoints() throws IOException {
        String token = "LONG-" + "\u6f22\u5b57\ud83d\ude42".repeat(8);
        List<AttributedString> rows = wrap(new AttributedString(token), 12);

        assertEquals(token, String.join("", rows.stream().map(AttributedString::toString).toList()));
        assertTrue(rows.stream().allMatch(row -> row.columnLength() <= 12));
        assertTrue(rows.stream().noneMatch(row -> Character.isLowSurrogate(row.charAt(0))));
    }

    @Test
    void wrap_preservesBlankLinesAndNormalisesCrLf() throws IOException {
        assertEquals(List.of("one", "", "two"), wrap(new AttributedString("one\r\n\r\ntwo\r\n"), 10)
                .stream().map(AttributedString::toString).toList());
    }

    @Test
    void wrap_keepsJoinedEmojiModifiersFlagsAndCombiningMarksTogether() throws IOException {
        for (String cluster : List.of("\uD83D\uDC69\u200D\uD83D\uDCBB", "\uD83D\uDC4D\uD83C\uDFFD",
                "\uD83C\uDDF8\uD83C\uDDEC", "1\uFE0F\u20E3", "e\u0301",
                "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66")) {
            String token = "x".repeat(6) + cluster + "TAIL";
            List<AttributedString> rows = wrap(new AttributedString(token), 8);

            assertEquals(token, String.join("", rows.stream().map(AttributedString::toString).toList()));
            assertTrue(rows.stream().anyMatch(row -> row.toString().contains(cluster)),
                    "The complete visible character must stay on one row: " + cluster);
        }
    }

    @Test
    void wrap_clusterWiderThanTheTerminal_retainsTheWholeStyledCharacter() throws IOException {
        String emoji = "\uD83D\uDC69\u200D\uD83D\uDCBB";
        AttributedString text = AttributedString.fromAnsi("\u001B[32m" + emoji.repeat(2) + "\u001B[0m");
        for (int width : new int[]{0, 1, 2}) {
            List<AttributedString> rows = wrap(text, width);
            assertEquals(List.of(emoji, emoji), rows.stream().map(AttributedString::toString).toList());
            assertTrue(rows.stream().allMatch(row -> row.styleAt(0).equals(text.styleAt(0))));
        }
    }

    private List<AttributedString> wrap(AttributedString text, int width) throws IOException {
        try (Terminal terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())) {
            return TerminalWordWrapper.wrap(terminal, text, width);
        }
    }
}
