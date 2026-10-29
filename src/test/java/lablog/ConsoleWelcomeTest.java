package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Checks that the start-up banner stays portable and appears once before the command list. */
class ConsoleWelcomeTest {
    private static String welcomeText() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ConsoleWelcome.print(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void print_banner_usesNarrowPlainAscii() {
        for (String line : welcomeText().split("\\R")) {
            assertTrue(line.chars().allMatch(c -> c >= 0x20 && c < 0x7f), "Non-ASCII banner line: " + line);
            assertTrue(line.length() <= 60, "Banner line wider than 60 columns: " + line);
        }
    }

    @Test
    void print_banner_endsWithVersionAndPurpose() {
        String text = welcomeText();
        assertTrue(text.contains("LabLog v" + ConsoleWelcome.VERSION + " - Lab equipment and loan manager"));
        assertTrue(text.endsWith("Manage your laboratory equipment and loans here." + System.lineSeparator()));
    }

    @Test
    void run_mainConsole_printsBannerOnceBeforeCommandList() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Scanner input = new Scanner(new ByteArrayInputStream("exit\n".getBytes(StandardCharsets.UTF_8)));
        new LabLog().run(input, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        String output = bytes.toString(StandardCharsets.UTF_8);

        String versionLine = "LabLog v" + ConsoleWelcome.VERSION;
        assertEquals(output.indexOf(versionLine), output.lastIndexOf(versionLine));
        assertTrue(output.startsWith(welcomeText()));
        assertTrue(output.indexOf(versionLine) < output.indexOf("LabLog commands:"));
    }
}
