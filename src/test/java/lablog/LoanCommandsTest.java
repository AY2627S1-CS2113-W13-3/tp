package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies loan syntax, equipment and due-date checks, and the warning for units already out. */
class LoanCommandsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T08:00:00Z"), ZoneOffset.UTC);

    private Inventory inventory;
    private LoanBook loans;
    private LoanCommands commands;

    /** Registers one equipment unit and an empty loan book for each test. */
    @BeforeEach
    void setUp() {
        inventory = new Inventory();
        inventory.addEquipment(new Equipment("OSC-001", "Oscilloscope", "Measurement", "DS1054Z", "Lab 1"));
        loans = new LoanBook();
        commands = new LoanCommands(inventory, loans, CLOCK);
    }

    @Test
    public void execute_validCommand_recordsLoanDatedToday() {
        String message = commands.execute("loan OSC-001 b/A0123456X d/2026-10-30");

        assertTrue(message.contains("OSC-001"));
        assertTrue(message.contains("Oscilloscope"));
        assertTrue(message.contains("A0123456X"));
        assertTrue(message.contains("2026-10-30"));
        assertEquals(1, loans.getLoans().size());
        Loan loan = loans.getLoans().get(0);
        assertEquals("A0123456X", loan.getBorrowerId());
        assertEquals(TODAY, loan.getLoanDate());
        assertEquals(LocalDate.of(2026, 10, 30), loan.getDueDate());
        assertTrue(loan.isActive());
    }

    @Test
    public void execute_prefixesInEitherOrder_recordsLoan() {
        commands.execute("loan OSC-001 d/2026-10-30 b/A0123456X");

        assertEquals("A0123456X", loans.getLoans().get(0).getBorrowerId());
    }

    @Test
    public void execute_dueToday_isAccepted() {
        commands.execute("loan OSC-001 b/A0123456X d/2026-10-13");

        assertEquals(TODAY, loans.getLoans().get(0).getDueDate());
    }

    @Test
    public void execute_unitAlreadyOnLoan_warnsAndRecordsNothing() {
        commands.execute("loan OSC-001 b/A0123456X d/2026-10-30");

        String message = assertThrows(IllegalArgumentException.class,
                () -> commands.execute("loan OSC-001 b/A7654321Y d/2026-11-05")).getMessage();

        assertTrue(message.contains("already on loan"));
        assertTrue(message.contains("A0123456X"), "警告要指出目前借用人");
        assertTrue(message.contains("2026-10-30"), "警告要指出到期日");
        assertEquals(1, loans.getLoans().size());
    }

    @Test
    public void execute_unitReturned_canBeLentAgain() {
        commands.execute("loan OSC-001 b/A0123456X d/2026-10-30");
        loans.getLoans().get(0).markReturned(TODAY);

        commands.execute("loan OSC-001 b/A7654321Y d/2026-11-05");

        assertEquals(2, loans.getLoans().size());
        assertEquals("A7654321Y", loans.findActiveLoan("OSC-001").orElseThrow().getBorrowerId());
    }

    @Test
    public void execute_unknownEquipment_isRejected() {
        String message = assertThrows(IllegalArgumentException.class,
                () -> commands.execute("loan NO-SUCH b/A0123456X d/2026-10-30")).getMessage();

        assertTrue(message.contains("NO-SUCH"));
        assertTrue(loans.getLoans().isEmpty());
    }

    @Test
    public void execute_dueDateInThePast_isRejected() {
        String message = assertThrows(IllegalArgumentException.class,
                () -> commands.execute("loan OSC-001 b/A0123456X d/2026-10-12")).getMessage();

        assertTrue(message.contains("before today"));
        assertTrue(loans.getLoans().isEmpty());
    }

    @Test
    public void execute_malformedDueDate_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> commands.execute("loan OSC-001 b/A0123456X d/30-10-2026"));
        assertThrows(IllegalArgumentException.class,
                () -> commands.execute("loan OSC-001 b/A0123456X d/2026-02-30"));
        assertTrue(loans.getLoans().isEmpty());
    }

    @Test
    public void execute_missingOrRepeatedFields_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> commands.execute("loan OSC-001"));
        assertThrows(IllegalArgumentException.class, () -> commands.execute("loan OSC-001 b/A0123456X"));
        assertThrows(IllegalArgumentException.class,
                () -> commands.execute("loan OSC-001 b/A0123456X b/A7654321Y d/2026-10-30"));
        assertThrows(IllegalArgumentException.class,
                () -> commands.execute("loan OSC-001 b/A0123456X d/2026-10-30 x/extra"));
        assertThrows(IllegalArgumentException.class,
                () -> commands.execute("loan b/A0123456X d/2026-10-30"));
        assertTrue(loans.getLoans().isEmpty());
    }
}
