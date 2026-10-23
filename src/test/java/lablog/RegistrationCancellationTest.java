package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Verifies cancellation at every detail prompt and recovery in the same session. */
class RegistrationCancellationTest {
    @Test
    void run_cancelAtEachPrompt_discardsPartialUnitAndContinues() {
        for (int field = 0; field < 4; field++) {
            Inventory inventory = new Inventory();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            String input = "add CANCELLED\n" + "detail\n".repeat(field) + " cancel \n"
                    + "help\nadd SAVED\nScope\nElectronics\nS1\nLab\nexit\n";
            new LabLog(inventory, new LoanBook(), List::of).run(new Scanner(input), new PrintStream(output));
            assertTrue(output.toString().contains("registration cancelled"));
            assertTrue(output.toString().contains("LabLog commands:"));
            assertEquals(1, inventory.getEquipmentUnits().size());
            assertEquals("SAVED", inventory.getEquipmentUnits().get(0).getAssetId());
        }
    }
}
