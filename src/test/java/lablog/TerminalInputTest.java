package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/** Checks line-based page navigation without a native terminal or asynchronous keyboard input. */
class TerminalInputTest {
    @Test
    void pager_nextBackInvalidAndQuit_leaveNextCommandUnread() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        TerminalInput input = new TerminalInput(new Scanner("invalid\n\nb\nq\nexit\n"), output, 100, 10);
        input.presentOutput(result(5), output);
        String rendered = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(rendered.contains("Lines 1-2/5"));
        assertTrue(rendered.contains("Lines 3-4/5"));
        assertTrue(rendered.contains("Use Enter, b or q."));
        assertEquals("exit", input.readLine("TYPE COMMAND", "lablog> "));
    }

    @Test
    void pager_enterAtLastPageClosesWithoutConsumingCommand() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        TerminalInput input = new TerminalInput(new Scanner("\n\n\nexit\n"), output, 100, 10);
        input.presentOutput(result(5), output);
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("Lines 5-5/5"));
        assertEquals("exit", input.readLine("TYPE COMMAND", "lablog> "));
    }

    @Test
    void pager_eofEndsSubsequentCommandInput() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        TerminalInput input = new TerminalInput(new Scanner(""), output, 100, 10);
        input.presentOutput(result(5), output);
        assertNull(input.readLine("TYPE COMMAND", "lablog> "));
    }

    @Test
    void shortOutput_doesNotConsumeInputAndEscapesControls() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        TerminalInput input = new TerminalInput(new Scanner("exit\n"), output, 100, 30);
        input.presentOutput("value \u001B[2J\n", output);
        String rendered = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(rendered.contains("\\u001B[2J"));
        assertFalse(rendered.contains("\u001B[2J"));
        assertFalse(rendered.contains("next page"));
        assertEquals("exit", input.readLine("TYPE COMMAND", "lablog> "));
    }

    @Test
    void dimensions_invalidAndTinyValuesUseDefaults() {
        assertEquals(100, TerminalInput.dimension(null, 20, 100));
        assertEquals(100, TerminalInput.dimension("bad", 20, 100));
        assertEquals(100, TerminalInput.dimension("19", 20, 100));
        assertEquals(100, TerminalInput.dimension("1000000", 20, 100));
        assertEquals(80, TerminalInput.dimension("80", 20, 100));
        assertEquals(30, TerminalInput.dimension("9", 10, 30));
        assertEquals(24, TerminalInput.dimension("24", 10, 30));
    }

    @Test
    void plainInput_preservesOutputAndNeverPages() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        ConsoleInput input = ConsoleInput.plain(new Scanner("exit\n"), output);
        String text = result(50);
        input.presentOutput(text, output);
        assertEquals(text, bytes.toString(StandardCharsets.UTF_8));
        assertFalse(input.isInteractive());
        assertEquals("exit", input.readLine("TYPE COMMAND", "lablog> "));
    }

    @Test
    void pager_sectionsStartNewPagesAndBackReturnsToSameBoundary() {
        for (String text : new String[] {
            "LabLog commands:\n== Equipment ==\nlist\n== Loans ==\nloan",
            "Help: add\n    Usage\nadd syntax\n    Example\nadd example",
            "OVERVIEW / 2026-11-02\nOVERDUE LOANS (1)\nloan\nSEVERE FAULTS (1)\nfault"
        }) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
            TerminalInput input = new TerminalInput(new Scanner("\nb\nq\nexit\n"), output, 100, 30);
            input.presentOutput(text, output);
            String rendered = bytes.toString(StandardCharsets.UTF_8);
            assertTrue(rendered.contains("Lines 1-3/5"));
            assertTrue(rendered.contains("Lines 4-5/5"));
            assertEquals(2, rendered.split("Lines 1-3/5", -1).length - 1);
            assertEquals("exit", input.readLine("TYPE COMMAND", "lablog> "));
        }
    }

    @Test
    void pager_shortSectionsAndFinalPageKeepTheSameHeight() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        TerminalInput input = new TerminalInput(new Scanner("\nq\n"), output, 100, 12);
        input.presentOutput("LabLog commands:\n== Equipment ==\nlist\n== Loans ==\nloan", output);
        String prompt = "Enter: next page/close at end | b: back | q: close (then Enter): ";
        String[] pages = bytes.toString(StandardCharsets.UTF_8).split(java.util.regex.Pattern.quote(prompt), -1);
        assertEquals(3, pages.length);
        for (int page = 0; page < 2; page++) {
            // Four body rows plus the top border, context, bottom border and line counter.
            assertEquals(8, pages[page].lines().count());
        }
        assertTrue(pages[0].replace("\r\n", "\n").contains("LabLog commands:"));
        assertTrue(pages[0].contains("Lines 1-3/5"));
        assertTrue(pages[1].contains("Lines 4-5/5"));
    }

    @Test
    void help_firstPageIncludesCommandsWithoutAdvancing() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        TerminalInput input = new TerminalInput(new Scanner("q\nexit\n"), output, 100, 30);
        input.presentOutput(new HelpCommands().execute("help"), output);
        String rendered = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(rendered.contains("== Equipment =="));
        assertTrue(rendered.contains("Register equipment"));
        assertEquals("exit", input.readLine("TYPE COMMAND", "lablog> "));
    }

    /** Produces numbered plain lines so page boundaries are deterministic. */
    private String result(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(index -> "Result line " + index)
                .collect(Collectors.joining("\n"));
    }
}
