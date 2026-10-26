package lablog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks that exit stops processing without treating later input as commands. */
class BorrowerExitTest {
    @Test
    void run_exit_stopsBeforeNextCommand(@TempDir Path directory) throws Exception {
        BorrowerCommands commands = new BorrowerCommands(new BorrowerBook(directory.resolve("borrowers")),
                new LoanBook(), id -> null, Clock.systemDefaultZone());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BorrowerCli.run(new Scanner("exit extra\n  exit  \nhelp\n"), new PrintStream(output), commands);
        assertTrue(output.toString().contains("Error:"));
        assertFalse(output.toString().contains("LabLog commands:"));
    }
}
