package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Verifies production history creation and rejection without partial writes. */
class HistoryRecordCommandsTest {
    @Test
    void execute_invalidNotes_preservesHistory() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab"));
        HistoryRecordCommands commands = new HistoryRecordCommands(inventory);
        for (String input : List.of("fault MISSING d/2026-10-14 desc/Damage",
                "fault OSC-001 d/2026-02-30 desc/Damage", "fault OSC-001 d/2026-10-14 desc/",
                "fault OSC-001", "fault OSC-001 d/2026-10-14 desc/Damage\nsecond line")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input));
        }
        assertTrue(commands.getEntries().isEmpty());
        commands.execute("fault OSC-001 d/2026-10-14 desc/Cracked screen");
        assertEquals("Cracked screen", commands.getEntries().get(0).getDescription());
    }

    @Test
    void run_allNoteTypes_appearInEquipmentHistory() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog().run(new Scanner("add OSC-001\nScope\nElectronics\nS1\nLab\n"
                + "reservation OSC-001 d/2026-10-15 desc/Teaching session\n"
                + "fault OSC-001 d/2026-10-14 desc/Cracked screen\n"
                + "maintenance OSC-001 d/2026-10-16 desc/Replace screen\nhistory OSC-001\nexit\n"),
                new PrintStream(output));
        String text = output.toString();
        assertTrue(text.contains("Teaching session"));
        assertTrue(text.contains("Cracked screen"));
        assertTrue(text.contains("Replace screen"));
        assertTrue(text.indexOf("Cracked screen") < text.indexOf("Teaching session"));
    }
}
