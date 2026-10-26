package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

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

    @Test
    void execute_multipleUnits_recordsSameBorrowerAndDatesInSelectionOrder() throws Exception {
        inventory.addEquipment(new Equipment("MIC-002", "Microscope", "Optics", "M2", "Lab 2"));
        BorrowerBook borrowers = new BorrowerBook();
        borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
        FaultBook faults = new FaultBook(inventory);
        ReservationBook bookings = new ReservationBook(inventory, borrowers, loans, faults, CLOCK);
        LoanCommands batch = new LoanCommands(inventory, loans, CLOCK, borrowers, faults, bookings.getAvailability());

        String message = batch.execute("  loan MIC-002\tOSC-001 d/2026-10-13 b/A0123456X  ");

        assertEquals("Loaned MIC-002, OSC-001 to A0123456X on 2026-10-13. Due 2026-10-13.", message);
        assertEquals(List.of("MIC-002", "OSC-001"), loans.getLoans().stream().map(Loan::getAssetId).toList());
        for (Loan loan : loans.getLoans()) {
            assertEquals("A0123456X", loan.getBorrowerId());
            assertEquals(TODAY, loan.getLoanDate());
            assertEquals(TODAY, loan.getDueDate());
            assertTrue(loan.isActive());
            assertNull(loan.getReturnDate());
        }
        assertEquals(2, inventory.getEquipmentUnits().size());
    }

    @Test
    void execute_duplicateOrMalformedBatch_rejectsBeforeRecordingLoans() {
        inventory.addEquipment(new Equipment("MIC-002", "Microscope", "Optics", "M2", "Lab 2"));
        String[] invalidInputs = {
            null, "", "loan", "list OSC-001", "loan OSC-001 MIC-002",
            "loan b/A0123456X d/2026-10-30",
            "loan OSC-001 MIC-002 OSC-001 b/A0123456X d/2026-10-30",
            "loan OSC-001 MIC-002 b/A0123456X", "loan OSC-001 MIC-002 d/2026-10-30",
            "loan OSC-001 MIC-002 b/ d/2026-10-30",
            "loan OSC-001 MIC-002 b/A0123456X b/A7654321Y d/2026-10-30",
            "loan OSC-001 MIC-002 b/A0123456X d/2026-10-30 d/2026-11-01",
            "loan OSC-001 MIC-002 b/A0123456X d/2026-10-30 x/extra"
        };
        for (String input : invalidInputs) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input), input);
            assertTrue(loans.getLoans().isEmpty(), input);
        }
        assertEquals("Duplicate asset ID: OSC-001.", assertThrows(IllegalArgumentException.class,
                () -> commands.execute("loan OSC-001 OSC-001 b/A0123456X d/2026-10-30")).getMessage());
    }

    @Test
    void execute_laterUnitUnknown_changesNeitherInventoryNorLoans() {
        List<Equipment> original = inventory.getEquipmentUnits();

        String message = assertThrows(IllegalArgumentException.class,
                () -> commands.execute("loan OSC-001 UNKNOWN b/A0123456X d/2026-10-30")).getMessage();

        assertTrue(message.contains("UNKNOWN"));
        assertTrue(loans.getLoans().isEmpty());
        assertEquals(original, inventory.getEquipmentUnits());
    }

    @Test
    void execute_laterUnitOnLoan_preservesOriginalAndRecordsNoPartialBatch() {
        inventory.addEquipment(new Equipment("MIC-002", "Microscope", "Optics", "M2", "Lab 2"));
        Loan original = new Loan("MIC-002", "A7654321Y", TODAY.minusDays(1), TODAY.plusDays(1));
        loans.addLoan(original);

        String message = assertThrows(IllegalArgumentException.class,
                () -> commands.execute("loan OSC-001 MIC-002 b/A0123456X d/2026-10-30")).getMessage();

        assertTrue(message.contains("already on loan to A7654321Y"));
        assertEquals(List.of(original), loans.getLoans());
        assertFalse(loans.hasActiveLoan("OSC-001"));
        assertTrue(original.isActive());
        assertNull(original.getReturnDate());
    }

    @Test
    void execute_invalidBorrowerOrDate_recordsNoBatch() throws Exception {
        inventory.addEquipment(new Equipment("MIC-002", "Microscope", "Optics", "M2", "Lab 2"));
        BorrowerBook borrowers = new BorrowerBook();
        borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
        LoanCommands batch = new LoanCommands(inventory, loans, CLOCK, borrowers);

        for (String fields : List.of("b/A7654321Y d/2026-10-30", "b/A0123456X extra d/2026-10-30",
                "b/A0123456X d/2026-10-12", "b/A0123456X d/2026-02-30", "b/A0123456X d/not-a-date")) {
            assertThrows(IllegalArgumentException.class,
                    () -> batch.execute("loan OSC-001 MIC-002 " + fields), fields);
            assertTrue(loans.getLoans().isEmpty(), fields);
        }
        assertEquals(1, borrowers.getBorrowers().size());
    }

    @Test
    void execute_laterUnitHasSevereFault_rejectsWholeBatchUntilResolved() {
        inventory.addEquipment(new Equipment("MIC-002", "Microscope", "Optics", "M2", "Lab 2"));
        FaultBook faults = new FaultBook(inventory);
        Fault fault = faults.report("MIC-002", TODAY, "Broken lens", Fault.Severity.SEVERE);
        LoanCommands batch = new LoanCommands(inventory, loans, CLOCK, null, faults);

        String message = assertThrows(IllegalArgumentException.class,
                () -> batch.execute("loan OSC-001 MIC-002 b/A0123456X d/2026-10-30")).getMessage();

        assertTrue(message.contains("MIC-002"));
        assertTrue(message.contains("unresolved severe fault"));
        assertTrue(loans.getLoans().isEmpty());
        assertEquals(List.of(fault), faults.getFaults());
        faults.resolve(fault.getId(), TODAY, "Replaced lens");
        batch.execute("loan OSC-001 MIC-002 b/A0123456X d/2026-10-30");
        assertEquals(2, loans.getLoans().size());
        assertTrue(faults.getUnresolvedSevereFaults().isEmpty());
    }

    @Test
    void execute_laterUnitReservationTouchesDueDate_rejectsWholeBatch() throws Exception {
        inventory.addEquipment(new Equipment("MIC-002", "Microscope", "Optics", "M2", "Lab 2"));
        BorrowerBook borrowers = new BorrowerBook();
        borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
        FaultBook faults = new FaultBook(inventory);
        ReservationBook bookings = new ReservationBook(inventory, borrowers, loans, faults, CLOCK);
        Reservation reservation = bookings.reserve("MIC-002", "A0123456X",
                new DateWindow(TODAY.plusDays(2), TODAY.plusDays(3)));
        LoanCommands batch = new LoanCommands(inventory, loans, CLOCK, borrowers, faults, bookings.getAvailability());

        String message = assertThrows(IllegalArgumentException.class,
                () -> batch.execute("loan OSC-001 MIC-002 b/A0123456X d/2026-10-15")).getMessage();

        assertTrue(message.contains(reservation.id()));
        assertTrue(loans.getLoans().isEmpty());
        assertEquals(List.of(reservation), bookings.getReservations());
        batch.execute("loan OSC-001 MIC-002 b/A0123456X d/2026-10-14");
        assertEquals(2, loans.getLoans().size());
        assertEquals(List.of(reservation), bookings.getReservations());
    }

    @Test
    void execute_historicalReturn_preservesHistoryWhileIssuingNewBatch() throws Exception {
        inventory.addEquipment(new Equipment("MIC-002", "Microscope", "Optics", "M2", "Lab 2"));
        Loan historical = new Loan("OSC-001", "A7654321Y", TODAY.minusDays(7), TODAY.minusDays(2));
        historical.markReturned(TODAY.minusDays(1), "Scratched case");
        loans.addLoan(historical);
        BorrowerBook borrowers = new BorrowerBook();
        borrowers.addBorrower(new Borrower("A0123456X", "Jamie", "91234567", "jamie@u.nus.edu"));
        FaultBook faults = new FaultBook(inventory);
        ReservationBook bookings = new ReservationBook(inventory, borrowers, loans, faults, CLOCK);
        LoanCommands batch = new LoanCommands(inventory, loans, CLOCK, borrowers, faults, bookings.getAvailability());

        batch.execute("loan OSC-001 MIC-002 b/A0123456X d/2026-10-30");

        assertEquals(3, loans.getLoans().size());
        assertEquals(historical, loans.getLoans().getFirst());
        assertEquals(TODAY.minusDays(1), historical.getReturnDate());
        assertEquals("Scratched case", historical.getReturnFaults());
        assertEquals("A7654321Y", historical.getBorrowerId());
        assertFalse(historical.isActive());
        assertEquals(List.of("OSC-001", "MIC-002"), loans.getLoans().stream().filter(Loan::isActive)
                .map(Loan::getAssetId).toList());
    }

    @Test
    void execute_localDateBoundary_usesOneDateForWholeBatch() {
        inventory.addEquipment(new Equipment("MIC-002", "Microscope", "Optics", "M2", "Lab 2"));
        Clock singapore = Clock.fixed(Instant.parse("2026-10-13T16:30:00Z"), ZoneOffset.ofHours(8));
        LoanCommands batch = new LoanCommands(inventory, loans, singapore);

        assertThrows(IllegalArgumentException.class,
                () -> batch.execute("loan OSC-001 MIC-002 b/A0123456X d/2026-10-13"));
        assertTrue(loans.getLoans().isEmpty());
        batch.execute("loan OSC-001 MIC-002 b/A0123456X d/2026-10-14");
        assertEquals(List.of(TODAY.plusDays(1), TODAY.plusDays(1)),
                loans.getLoans().stream().map(Loan::getLoanDate).toList());
        assertEquals(List.of(TODAY.plusDays(1), TODAY.plusDays(1)),
                loans.getLoans().stream().map(Loan::getDueDate).toList());
    }
}
