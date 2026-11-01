package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ensures stored equipment IDs cannot be confused with command field or batch separators. */
class EquipmentIdValidationTest {
    /** Runs the production dispatch against a persistent file. */
    private String run(Path data, String input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LabLog.load(data).run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }

    @Test
    void constructor_reservedSeparators_rejectsAmbiguousIds() {
        for (String id : List.of("A/B", "A,B", "b/A0123456X", "/", ",")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new Equipment(id, "Scope", "Electronics", "S1", "Lab"));
            assertTrue(error.getMessage().contains("reserved"));
        }
    }

    @Test
    void run_registrationAndCopyRejectReservedIds_validUnitsStillMoveLendAndReload(@TempDir Path directory)
            throws Exception {
        Path data = directory.resolve("lablog.properties");
        run(data, "add OSC-001 n/Scope c/Electronics m/S1 l/Lab\n"
                + "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu");
        String saved = Files.readString(data);
        for (String input : List.of("add A/B n/Scope c/Electronics m/S1 l/Lab",
                "add A,B\nScope\nElectronics\nS1\nLab", "copy OSC-001 A/B", "copy OSC-001 A,B")) {
            assertTrue(run(data, input).contains("Error: Asset ID"), input);
            assertEquals(saved, Files.readString(data));
        }
        String result = run(data, "copy OSC-001 OSC-002\nmove-equipment OSC-001,OSC-002 l/New Lab\n"
                + "loan OSC-001 OSC-002 b/A0123456X d/" + LocalDate.now().plusDays(7));
        assertFalse(result.contains("Error:"), result);
        ApplicationSnapshot loaded = ApplicationSnapshot.read(data);
        assertEquals(List.of("OSC-001", "OSC-002"), loaded.getEquipment().stream()
                .map(Equipment::getAssetId).toList());
        assertTrue(loaded.getEquipment().stream().allMatch(unit -> unit.getLocation().equals("New Lab")));
        assertEquals(2, loaded.getLoans().size());
        assertTrue(run(data, "active-loans").contains("OSC-002"));
    }

    @Test
    void loadAndImport_reservedIds_preservesSourceAndLiveRecords(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("lablog.properties");
        Path source = directory.resolve("invalid.properties");
        run(data, "add OSC-001 n/Scope c/Electronics m/S1 l/Lab");
        String saved = Files.readString(data);
        for (String id : List.of("A/B", "A,B")) {
            Properties invalid = ApplicationSnapshot.readProperties(data);
            invalid.setProperty("equipment.0.id", id);
            try (Writer writer = Files.newBufferedWriter(source)) {
                invalid.store(writer, "ambiguous equipment ID");
            }
            String original = Files.readString(source);
            assertThrows(IOException.class, () -> LabLog.load(source));
            assertEquals(original, Files.readString(source));
            assertTrue(run(data, "import " + source + " --replace").contains("reserved command separators"));
            assertEquals(original, Files.readString(source));
            assertEquals(saved, Files.readString(data));
            assertTrue(run(data, "find OSC-001").contains("Name: Scope"));
        }
    }
}
