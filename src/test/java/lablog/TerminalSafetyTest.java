package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks that terminal control characters cannot reach the console through IDs or errors. */
class TerminalSafetyTest {
    private static final String ESCAPE = "\u001b[2J";

    @Test
    void equipment_rejectsControlCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> new Equipment("ID" + ESCAPE, "Scope", "Tools", "M1", "Lab"));
    }

    @Test
    void mainConsole_rejectsControlCharactersAndContinues() {
        Inventory inventory = new Inventory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String input = "add ID" + ESCAPE + " n/Scope c/Tools m/M1 l/Lab\n"
                + "find ID" + ESCAPE + "\nadd\tGOOD n/Scope c/Tools m/M1 l/Lab\nlist\nexit\n";
        new LabLog(inventory, new LoanBook(), List::of).run(new Scanner(input), new PrintStream(output));
        assertFalse(output.toString().contains(ESCAPE));
        assertTrue(output.toString().contains("Input must not contain control characters"));
        assertEquals("GOOD", inventory.getEquipmentUnits().getFirst().getAssetId());
    }

    @Test
    void borrowerConsole_rejectsControlCharactersAndContinues() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BorrowerCommands commands = new BorrowerCommands(
                new BorrowerBook(), new LoanBook(), asset -> null, Clock.systemDefaultZone());
        BorrowerCli.run(new Scanner("loans BAD" + ESCAPE + "\nhelp\nexit\n"), new PrintStream(output), commands);
        assertFalse(output.toString().contains(ESCAPE));
        assertTrue(output.toString().contains("Input must not contain control characters"));
        assertTrue(output.toString().contains("LabLog commands:"));
    }

    @Test
    void invalidImport_escapesControlsInDiagnostics(@TempDir Path directory) throws IOException {
        Path bad = directory.resolve("bad.properties");
        Files.writeString(bad, "bad\\u001b=value\nbad\\u001b=value\n");
        LabLog app = LabLog.load(directory.resolve("data.properties"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        app.run(new Scanner("import " + bad + " --replace\nexit\n"), new PrintStream(output));
        assertFalse(output.toString().contains("\u001b"));
        assertTrue(output.toString().contains("\\u001B"));
        assertTrue(output.toString().contains("Current records were not changed."));
    }

    @Test
    void existingSnapshot_invalidId_rejectedWithoutOverwriting(@TempDir Path directory) throws IOException {
        Path data = directory.resolve("data.properties");
        LabLog app = LabLog.load(data);
        app.run(new Scanner("add GOOD n/Scope c/Tools m/M1 l/Lab\nexit\n"),
                new PrintStream(new ByteArrayOutputStream()));
        String broken = Files.readString(data).replace("equipment.0.id=GOOD", "equipment.0.id=GOOD\\u001b");
        Files.writeString(data, broken);
        assertThrows(IOException.class, () -> LabLog.load(data));
        assertEquals(broken, Files.readString(data));
    }
}
