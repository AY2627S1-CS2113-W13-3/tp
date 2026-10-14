package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/**
 * Verifies borrower command syntax and loan listings against the user guide.
 */
class BorrowerCommandsTest {
    private static final String ID = "A0123456X";
    private static final String ADD = "add-borrower " + ID + " n/Jamie Tan p/91234567 e/jamie@u.nus.edu";
    // This instant is September 16 in Singapore but September 15 in UTC.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-15T16:30:00Z"),
            ZoneId.of("Asia/Singapore"));
    private final BorrowerBook borrowers = new BorrowerBook();
    private final LoanBook loans = new LoanBook();
    private final BorrowerCommands commands = new BorrowerCommands(borrowers, loans,
            Map.of("OSC-001", "Digital Oscilloscope", "OSC-002", "Second Scope")::get, CLOCK);

    @Test
    void execute_reorderedFields_preservesContactDetails() throws IOException {
        assertEquals("Added borrower " + ID + ": Jamie Tan.\nPhone: 01234567 | Email: Jamie.Tan+lab@u.nus.edu",
                commands.execute("add-borrower " + ID + " e/Jamie.Tan+lab@u.nus.edu p/01234567 n/Jamie Tan"));
        assertEquals("01234567", borrowers.getBorrower(ID).getPhone());
    }

    @Test
    void execute_invalidInput_leavesBookEmpty() {
        String[] invalid = {
            "", "ADD-borrower " + ID, "add-borrower", "add-borrower " + ID,
            "add-borrower " + ID + " extra n/Jamie p/91234567 e/jamie@u.nus.edu",
            ADD + " n/Other", ADD + " x/extra", ADD.replace("n/Jamie Tan", "n/"),
            ADD.replace(" p/91234567", ""), ADD.replace(" e/jamie@u.nus.edu", ""),
            ADD.replace(" n/Jamie Tan", ""), ADD.replace("n/Jamie Tan", "N/Jamie Tan"),
            ADD.replace(ID, "a0123456x"), ADD.replace(ID, "A012345X"), ADD.replace(ID, "123456789"),
            ADD.replace("91234567", "9123456"), ADD.replace("91234567", "912345678"),
            ADD.replace("91234567", "9123abcd"), ADD.replace("u.nus.edu", "nus.edu"),
            ADD.replace("jamie@", "@"), ADD.replace("jamie@", "a..b@"),
            ADD.replace("Jamie Tan", "x".repeat(101)), ADD.replace("Jamie Tan", "Jamie\tTan")
        };
        for (String input : invalid) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input), input);
            assertTrue(borrowers.getBorrowers().isEmpty(), input);
        }
    }

    @Test
    void execute_duplicateBorrower_keepsOriginalDetails() throws IOException {
        commands.execute(ADD);
        assertThrows(IllegalArgumentException.class, () -> commands.execute(ADD.replace("Jamie Tan", "Other")));
        assertEquals("Jamie Tan", borrowers.getBorrower(ID).getName());
        assertEquals(1, borrowers.getBorrowers().size());
    }

    @Test
    void execute_nameLengthBoundaries_acceptsBoth() throws IOException {
        commands.execute(ADD.replace("Jamie Tan", "J"));
        commands.execute(ADD.replace(ID, "A0123457X").replace("Jamie Tan", "x".repeat(100)));
        assertEquals(2, borrowers.getBorrowers().size());
    }

    @Test
    void execute_unknownOrMalformedLookup_rejectsInput() {
        for (String input : new String[]{"loans", "loans " + ID, "loans " + ID + " extra", "loans invalid"}) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input));
        }
    }

    @Test
    void execute_noActiveLoans_returnsEmptyMessage() throws IOException {
        commands.execute(ADD);
        assertEquals("No active loans for " + ID + " - Jamie Tan.", commands.execute("loans " + ID));
    }

    @Test
    void execute_mixedLoans_filtersSortsAndUsesLocalDate() throws IOException {
        commands.execute(ADD);
        LocalDate issued = LocalDate.of(2026, 9, 14);
        loans.addLoan(new Loan("OSC-002", ID, issued, LocalDate.of(2026, 9, 16)));
        loans.addLoan(new Loan("OSC-001", ID, issued, LocalDate.of(2026, 9, 16)));
        loans.addLoan(new Loan("OLD-001", ID, issued, LocalDate.of(2026, 9, 15)));
        loans.addLoan(new Loan("OTHER", "A7654321X", issued, issued));
        Loan returned = new Loan("RETURNED", ID, issued, issued);
        returned.markReturned(issued);
        loans.addLoan(returned);
        assertEquals("Active loans for " + ID + " - Jamie Tan:\n"
                + "Asset ID | Equipment | Issued | Due | Overdue\n"
                + "OLD-001 | Unknown equipment | 2026-09-14 | 2026-09-15 | yes\n"
                + "OSC-001 | Digital Oscilloscope | 2026-09-14 | 2026-09-16 | no\n"
                + "OSC-002 | Second Scope | 2026-09-14 | 2026-09-16 | no\n"
                + "3 active loans.", commands.execute("loans " + ID));
        assertEquals("OSC-002", loans.getLoans().get(0).getAssetId());
    }

    @Test
    void execute_returnedAfterFirstListing_disappearsImmediately() throws IOException {
        commands.execute(ADD);
        Loan loan = new Loan("OSC-001", ID, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 18));
        loans.addLoan(loan);
        assertTrue(commands.execute("loans " + ID).endsWith("1 active loan."));
        loan.markReturned(LocalDate.of(2026, 9, 16));
        assertEquals("No active loans for " + ID + " - Jamie Tan.", commands.execute("loans " + ID));
    }

    @Test
    void run_rejectedCommandThenValidInput_continuesUntilEof() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BorrowerCli.run(new Scanner("unknown\n" + ADD + "\nloans " + ID + "\n"),
                new PrintStream(output), commands);
        assertEquals("LabLog borrower console. Use add-borrower or loans. Type exit or send EOF to exit.\n"
                + "lablog> Error: Unknown borrower command. Use add-borrower or loans.\nNo changes saved.\n"
                + "lablog> Added borrower " + ID + ": Jamie Tan.\nPhone: 91234567 | Email: jamie@u.nus.edu\n"
                + "lablog> No active loans for " + ID + " - Jamie Tan.\nlablog> ",
                output.toString().replace("\r\n", "\n"));
    }
}
