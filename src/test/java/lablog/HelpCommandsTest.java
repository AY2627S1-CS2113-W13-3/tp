package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies help syntax, command guidance, console recovery, and unchanged records. */
class HelpCommandsTest {
    private final HelpCommands help = new HelpCommands();

    @Test
    void execute_summaryAndDetails_coverCommonCommands() {
        String summary = help.execute("help");
        assertEquals(summary, help.execute("  help\t "));
        assertFalse(summary.contains("Example:"));
        for (String command : List.of("help", "loan", "return", "list", "edit-equipment", "move-equipment",
                "find", "history", "copy", "add-borrower", "loans", "exit")) {
            assertTrue(summary.contains(command));
            String detail = help.execute("help " + command);
            assertTrue(detail.contains("Format: " + command));
            assertTrue(detail.contains("Example: " + command));
            assertTrue(detail.contains("Fields:"));
        }
    }

    @Test
    void execute_activeLoanHelp_documentsOverdueModeAndBothExamples() {
        String detail = help.execute("help active-loans");
        assertTrue(help.execute("help").contains("active-loans [overdue]"));
        assertTrue(detail.contains("Format: active-loans [overdue]"));
        assertTrue(detail.contains("Loans due today are not overdue."));
        assertTrue(detail.contains("ordered by due date then asset ID"));
        assertTrue(detail.contains("Fields: overdue"));
        ActiveLoanCommands commands = new ActiveLoanCommands(new LoanBook(), Clock.systemDefaultZone());
        int count = 0;
        for (String line : detail.split("\n")) {
            if (line.startsWith("Example: ")) {
                String example = line.substring(9);
                assertEquals(example.endsWith(" overdue") ? "No overdue loans." : "No active loans.",
                        commands.execute(example));
                count++;
            }
        }
        assertEquals(2, count);
    }

    @Test
    void execute_listHelp_documentsFiltersAndAvailabilityRules() {
        String detail = help.execute("help list");
        assertTrue(detail.contains("Format: list [category/VALUE] [location/VALUE]"
                + " [status/available|on-loan] [sort/FIELD]"));
        assertTrue(detail.contains("Example: list category/Electronics location/Lab A status/available"));
        assertTrue(detail.contains("Match category and location exactly, ignoring case; combine any filters."));
        assertTrue(detail.contains("unresolved severe faults exclude units from available results"));
    }

    @Test
    void execute_copyHelpExplainsUniqueIdAndRecordBehavior() {
        String detail = help.execute("help copy");
        assertTrue(detail.contains("Format: copy SOURCE_ASSET_ID NEW_ASSET_ID"));
        assertTrue(detail.contains("The new ID must be unique."));
        assertTrue(detail.contains("Loans and history are not copied"));
    }

    @Test
    void run_copyHelpExampleWorksAfterRegistration() {
        String example = help.execute("help copy").lines()
                .filter(line -> line.startsWith("Example: ")).findFirst().orElseThrow().substring(9);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new LabLog().run(new Scanner("add OSC-001 n/Scope c/Electronics m/S1 l/Lab A\n"
                + example + "\nlist\nexit\n"), new PrintStream(output));

        assertTrue(output.toString().contains("Copied OSC-001 to OSC-002."));
        assertTrue(output.toString().contains("OSC-002 | Scope | Electronics | S1 | Lab A | Available"));
    }

    @Test
    void execute_loanHelp_includesRequestedExampleAndFieldGuidance() {
        String detail = help.execute("  help\tloan  ");
        assertTrue(detail.startsWith("\n" + ".".repeat(60) + "\n"));
        assertTrue(detail.endsWith("\n" + ".".repeat(60) + "\n"));
        assertTrue(detail.contains("Issue loans for one or more available equipment units.\n"
                + "Format: loan ASSET_ID [ASSET_ID ...] b/BORROWER_ID d/DUE_DATE\n"
                + "Example: loan OSC-001 b/A0123456X d/2026-09-18\n"));
        assertTrue(detail.contains("YYYY-MM-DD"));
        assertTrue(detail.contains("Register the borrower with add-borrower"));
    }

    @Test
    void execute_loanExamples_supportSingleAndBatchWithRegisteredBorrower() throws Exception {
        String detail = help.execute("help loan");
        assertTrue(detail.contains("an invalid batch records no loans"));
        assertTrue(detail.contains("distinct, case-sensitive asset IDs"));
        Clock clock = Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);
        int count = 0;
        for (String line : detail.split("\n")) {
            if (!line.startsWith("Example: ")) {
                continue;
            }
            String example = line.substring(9);
            Inventory inventory = new Inventory();
            inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab A"));
            inventory.addEquipment(new Equipment("MIC-002", "Microscope", "Optics", "M2", "Lab B"));
            LoanBook loans = new LoanBook();
            BorrowerBook borrowers = new BorrowerBook();
            borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));

            new LoanCommands(inventory, loans, clock, borrowers).execute(example);

            assertEquals(example.contains("MIC-002") ? List.of("OSC-001", "MIC-002") : List.of("OSC-001"),
                    loans.getLoans().stream().map(Loan::getAssetId).toList());
            for (Loan loan : loans.getLoans()) {
                assertEquals("A0123456X", loan.getBorrowerId());
                assertEquals(LocalDate.of(2026, 9, 10), loan.getLoanDate());
                assertEquals(LocalDate.of(2026, 9, 18), loan.getDueDate());
                assertTrue(loan.isActive());
            }
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    void execute_listHelp_documentsSortFieldsAndTieHandling() {
        String detail = help.execute("help list");

        assertTrue(detail.contains("[status/available|on-loan] [sort/FIELD]"));
        assertTrue(detail.contains("Example: list sort/name"));
        assertTrue(detail.contains("asset-id, name, category, model, location, availability"));
        assertTrue(detail.contains("case-insensitive"));
        assertTrue(detail.contains("does not change inventory order"));
    }

    @Test
    void execute_returnExamples_areAcceptedForSeparateActiveLoans() {
        String detail = help.execute("help return");
        int count = 0;
        for (String line : detail.split("\n")) {
            if (!line.startsWith("Example: ")) {
                continue;
            }
            Inventory inventory = new Inventory();
            inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab"));
            LoanBook loans = new LoanBook();
            Loan loan = new Loan("OSC-001", "A0123456X", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 18));
            loans.addLoan(loan);
            inventory.addEquipment(
                    new Equipment("OSC-002", "Scope 2", "Electronics", "S2", "Lab"));
            Loan second = new Loan("OSC-002", "A0123456X",
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 18));
            loans.addLoan(second);
            Clock clock = Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);
            new ReturnCommands(inventory, loans, clock).execute(line.substring(9));
            assertFalse(loan.isActive());
            assertEquals(line.contains("faults/") ? "cracked screen" : "", loan.getReturnFaults());
            boolean batchExample = line.equals("Example: return OSC-001 OSC-002");
            assertEquals(!batchExample, second.isActive());
            assertEquals("", second.getReturnFaults());
            count++;
        }
        assertEquals(3, count);
    }

    @Test
    void execute_borrowerExamples_registerAndLookUpBothBorrowers() throws Exception {
        BorrowerBook borrowers = new BorrowerBook();
        BorrowerCommands commands = new BorrowerCommands(borrowers, new LoanBook(), id -> null,
                Clock.systemDefaultZone());
        for (String line : help.execute("help add-borrower").split("\n")) {
            if (line.startsWith("Example: ")) {
                commands.execute(line.substring(9));
            }
        }
        assertEquals(2, borrowers.getBorrowers().size());
        assertEquals("Jamie Tan", borrowers.getBorrower("A0123456X").getName());
        assertEquals("Alex Lim", borrowers.getBorrower("A0123457X").getName());
        int lookups = 0;
        for (String line : help.execute("help loans").split("\n")) {
            if (line.startsWith("Example: ")) {
                assertTrue(commands.execute(line.substring(9)).startsWith("No active loans for "));
                lookups++;
            }
        }
        assertEquals(2, lookups);
    }

    @Test
    void execute_helpExamples_resolveToKnownGuidance() {
        int count = 0;
        for (String line : help.execute("help help").split("\n")) {
            if (line.startsWith("Example: ")) {
                assertFalse(help.execute(line.substring(9)).isBlank());
                count++;
            }
        }
        assertEquals(3, count);
    }

    @Test
    void run_historyExample_worksAfterEquipmentRegistration() {
        String example = help.execute("help history").lines()
                .filter(line -> line.startsWith("Example: ")).findFirst().orElseThrow().substring(9);
        Inventory inventory = new Inventory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog(inventory, new LoanBook(), List::of).run(new Scanner(
                "help history\nadd OSC-001\nScope\nElectronics\nS1\nLab\n" + example + "\nexit\n"),
                new PrintStream(output));
        assertTrue(output.toString().contains("No history found for OSC-001 - Scope."));
        assertEquals(1, inventory.getEquipmentUnits().size());
    }

    @Test
    void execute_invalidInput_explainsHowToRecover() {
        for (String input : new String[]{null, "", " ", "HELP", "loan", "help loan extra"}) {
            assertEquals("Use: help [COMMAND]",
                    assertThrows(IllegalArgumentException.class, () -> help.execute(input)).getMessage());
        }
        for (String command : List.of("unknown", "LOAN")) {
            assertEquals("Unknown command: " + command + ". Use help for the command list.",
                    assertThrows(IllegalArgumentException.class, () -> help.execute("help " + command)).getMessage());
        }
    }

    @Test
    void run_mainConsole_helpPreservesRecordsAndRecoversFromErrors() {
        Inventory inventory = new Inventory();
        Equipment equipment = new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab");
        inventory.addEquipment(equipment);
        LoanBook loans = new LoanBook();
        Loan loan = new Loan("OSC-001", "A0123456X", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 18));
        loans.addLoan(loan);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog(inventory, loans, List::of).run(new Scanner(
                "help\nhelp return\nhelp unknown\nhelp loan extra\nhelp loan\nhelp history\nhelp help\nexit\n"),
                new PrintStream(output));
        String result = output.toString();
        assertTrue(result.contains(help.execute("help")));
        assertTrue(result.contains(help.execute("help loan")));
        assertTrue(result.contains(help.execute("help return")));
        assertTrue(result.contains(help.execute("help history")));
        assertTrue(result.contains(help.execute("help help")));
        assertTrue(result.contains("Error: Unknown command: unknown."));
        assertTrue(result.contains("Error: Use: help [COMMAND]"));
        assertFalse(result.contains("Enter name:"));
        assertEquals(List.of(equipment), inventory.getEquipmentUnits());
        assertEquals(List.of(loan), loans.getLoans());
        assertTrue(loan.isActive());
        assertEquals("", loan.getReturnFaults());
    }

    @Test
    void run_borrowerConsole_helpDoesNotWriteRecords(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("borrowers.properties");
        BorrowerBook borrowers = new BorrowerBook(file);
        Borrower borrower = new Borrower("A0123456X", "Jamie Tan", "91234567", "jamie@u.nus.edu");
        borrowers.addBorrower(borrower);
        String saved = Files.readString(file);
        BorrowerCommands commands = new BorrowerCommands(borrowers, new LoanBook(), id -> null,
                Clock.systemDefaultZone());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BorrowerCli.run(new Scanner("help\nhelp unknown\nhelp add-borrower\nhelp loans\n"),
                new PrintStream(output), commands);
        assertTrue(output.toString().contains(help.execute("help")));
        assertTrue(output.toString().contains(help.execute("help add-borrower")));
        assertTrue(output.toString().contains(help.execute("help loans")));
        assertEquals(List.of(borrower), borrowers.getBorrowers());
        assertEquals(saved, Files.readString(file));
    }
}
