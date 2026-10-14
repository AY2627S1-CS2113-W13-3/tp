package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Checks that repeated prompts stay concise while startup guidance remains available. */
class ConsolePromptTest {
    @Test
    void run_multipleCommands_printsSummaryOnceAndShortPrompts() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog().run(new Scanner("help loan\nhelp return\nexit\n"), new PrintStream(output));
        String text = output.toString();
        assertEquals(1, text.split("Common Version 1 commands:", -1).length - 1);
        assertEquals(3, text.split("lablog> ", -1).length - 1);
        assertFalse(text.contains("Enter asset ID, help"));
    }
}
