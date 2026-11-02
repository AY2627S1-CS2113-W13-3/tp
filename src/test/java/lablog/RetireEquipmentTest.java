package lablog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises retirement through the persistent main console. */
class RetireEquipmentTest {
    private String run(LabLog app, String commands) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        app.run(new Scanner(commands + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }

    @Test
    void retire_preservesRecordsAndBlocksBookingsAcrossRestart(@TempDir Path dir) throws Exception {
        Path data = dir.resolve("data.properties");
        LabLog app = LabLog.load(data);
        run(app, "add A n/Scope c/Tools m/M1 l/Lab\n"
                + "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                + "fault A d/2026-01-01 desc/Old fault");
        assertTrue(run(app, "retire A reason/End of life").contains("Retired equipment A"));
        LabLog reloaded = LabLog.load(data);
        assertTrue(run(reloaded, "find A").contains("Reason: End of life"));
        assertTrue(run(reloaded, "history A").contains("Old fault"));
        assertFalse(run(reloaded, "list").contains("Scope"));
        assertTrue(run(reloaded, "list status/retired").contains("Scope"));
        assertTrue(run(reloaded, "loan A b/A0123456X d/2099-01-01").contains("retired"));
        assertTrue(run(reloaded, "reserve A b/A0123456X from/2099-01-01 to/2099-01-02").contains("retired"));
        assertFalse(run(reloaded, "available from/2099-01-01 to/2099-01-02").contains("Scope"));
        Path exported = dir.resolve("export.properties");
        run(reloaded, "export " + exported);
        assertTrue(ApplicationSnapshot.read(exported).getEquipment().getFirst().isRetired());
    }

    @Test
    void retire_activeLoanOrReservation_rejectedWithoutChanges(@TempDir Path dir) throws Exception {
        LabLog app = LabLog.load(dir.resolve("data.properties"));
        run(app, "add A n/Scope c/Tools m/M1 l/Lab\nadd B n/Meter c/Tools m/M2 l/Lab\n"
                + "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                + "loan A b/A0123456X d/2099-01-01\n"
                + "reserve B b/A0123456X from/2099-01-01 to/2099-01-02");
        assertTrue(run(app, "retire A reason/Old").contains("outstanding loan"));
        assertTrue(run(app, "retire B reason/Old").contains("reservations"));
        assertFalse(ApplicationSnapshot.read(dir.resolve("data.properties")).getEquipment().getFirst().isRetired());
        assertEquals(2, ApplicationSnapshot.read(dir.resolve("data.properties")).getEquipment().size());
    }
    @Test
    void format11_migratesActiveEquipmentAndPreservesLoanIds(@TempDir Path dir) throws Exception {
        Path data = dir.resolve("data.properties");
        LabLog app = LabLog.load(data);
        run(app, "add A n/Scope c/Tools m/M1 l/Lab\n"
                + "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                + "loan A b/A0123456X d/2099-01-01");
        Properties legacy = ApplicationSnapshot.readProperties(data);
        String loanId = legacy.getProperty("loan.0.id");
        legacy.setProperty("format", "11");
        legacy.remove("equipment.0.retired");
        legacy.remove("equipment.0.retirementReason");
        ApplicationSnapshot decoded = ApplicationSnapshot.decode(legacy);
        assertFalse(decoded.getEquipment().getFirst().isRetired());
        assertEquals(loanId, decoded.getLoans().getFirst().getId());
        assertEquals("12", decoded.toProperties().getProperty("format"));
    }

    @Test
    void retire_rejectsRepeatedAndIncompleteCommands(@TempDir Path dir) throws Exception {
        LabLog app = LabLog.load(dir.resolve("data.properties"));
        run(app, "add A n/Scope c/Tools m/M1 l/Lab");
        assertTrue(run(app, "retire A reason/").contains("reason"));
        assertTrue(run(app, "retire A reason/Obsolete").contains("Retired equipment A"));
        assertTrue(run(app, "retire A reason/Again").contains("already retired"));
        assertTrue(run(app, "why-unavailable A").contains("retired"));
        assertTrue(run(app, "edit-equipment A n/New").contains("cannot be edited"));
        assertTrue(run(app, "move-equipment A l/Lab 2").contains("cannot be moved"));
    }

    @Test
    void corruptedSnapshot_retiredWithOutstandingLoan_isRejected(@TempDir Path dir) throws Exception {
        Path data = dir.resolve("data.properties");
        LabLog app = LabLog.load(data);
        run(app, "add A n/Scope c/Tools m/M1 l/Lab\n"
                + "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                + "loan A b/A0123456X d/2099-01-01");
        Properties invalid = ApplicationSnapshot.readProperties(data);
        invalid.setProperty("equipment.0.retired", "2026-11-01");
        invalid.setProperty("equipment.0.retirementReason", "Obsolete");
        assertThrows(IOException.class, () -> ApplicationSnapshot.decode(invalid));
    }

}
