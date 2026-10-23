package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies explicit corrections, unchanged original evidence and shared availability after each transition. */
class LoanCorrectionCommandsTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 23);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T16:30:00Z"), ZoneOffset.UTC);
    private static final String USER = "A0123456X";
    private final Inventory inventory = new Inventory();
    private final LoanBook loans = new LoanBook();
    private final BorrowerBook borrowers = new BorrowerBook();
    private final FaultBook faults = new FaultBook(inventory);
    private ReservationBook bookings;
    private LoanCorrectionCommands commands;
    private Loan loan;
    private LoanAuditEvent issue;

    @BeforeEach
    void seed() throws Exception {
        for (String asset : List.of("OSC", "MIC")) {
            inventory.addEquipment(new Equipment(asset, asset + " unit", "Electronics", "M1", "Lab"));
        }
        borrowers.addBorrower(new Borrower(USER, "Jamie", "91234567", "jamie@u.nus.edu"));
        loan = new Loan("OSC", USER, DAY.minusDays(2), DAY.plusDays(2));
        loans.addLoan(loan);
        issue = loans.getAuditEvents().getFirst();
        bookings = new ReservationBook(inventory, borrowers, loans, faults, CLOCK);
        commands = new LoanCorrectionCommands(loans, bookings.getAvailability(), CLOCK);
    }

    private String reverse(String command, LoanAuditEvent event) {
        return commands.execute(command + " " + event.id() + " reason/Wrong unit selected");
    }

    private LoanAuditEvent returnLoan() {
        loans.returnLoans(List.of(loan), DAY.minusDays(1), "cracked screen");
        return loans.getAuditEvents().getLast();
    }

    @Test
    void reverseLoan_preservesOriginalAndRestoresAvailabilityAcrossConsumers() throws Exception {
        String output = reverse("reverse-loan", issue);
        LoanAuditEvent correction = loans.getAuditEvents().getLast();
        assertTrue(output.contains("Reversed LOAN event " + issue.id() + " for OSC."));
        assertTrue(output.contains(correction.id()));
        assertEquals(LoanAuditEvent.Type.REVERSE_LOAN, correction.type());
        assertEquals(issue.id(), correction.relatedEventId());
        assertEquals(issue.loanId(), correction.loanId());
        assertEquals(DAY, correction.date());
        assertEquals("Wrong unit selected", correction.reason());
        assertEquals(List.of(issue, correction), loans.getAuditEvents());
        assertEquals(List.of(loan), loans.getLoans());
        assertTrue(loan.isReversed());
        assertFalse(loan.isActive());
        assertNull(loan.getReturnDate());
        assertFalse(loans.hasActiveLoan("OSC"));
        assertTrue(new EquipmentFindCommands(inventory, loans).execute("find OSC").endsWith("Available"));
        assertEquals("No active loans.", new ActiveLoanCommands(loans, CLOCK).execute("active-loans"));
        assertTrue(new BorrowerCommands(borrowers, loans, id -> id, CLOCK).execute("loans " + USER)
                .startsWith("No active loans"));
        String history = new HistoryCommands(inventory, loans, List::of).execute("history OSC");
        assertTrue(history.contains("REVERSED (loan cancelled)"));
        assertFalse(history.contains("returned null"));
        bookings.getAvailability().requireAvailable("OSC", new DateWindow(DAY, DAY.plusDays(1)), null, null);
        assertThrows(IllegalArgumentException.class, () -> loan.renew(DAY.plusDays(3), DAY));
        assertThrows(IllegalArgumentException.class, () -> loan.markLost(DAY));
        assertThrows(IllegalArgumentException.class, () -> loans.returnLoans(List.of(loan), DAY, ""));
        new LoanCommands(inventory, loans, CLOCK, borrowers, faults, bookings.getAvailability())
                .execute("loan OSC b/" + USER + " d/2026-10-24");
        assertNotEquals(loan.getId(), loans.findActiveLoan("OSC").orElseThrow().getId());
        assertEquals(3, loans.getAuditEvents().size());
    }

    @Test
    void reverseReturn_reopensSameLoanAndRetainsRenewalsAndOriginalDamageEvidence() {
        loan.renew(DAY.plusDays(3), DAY.minusDays(1));
        LoanAuditEvent returned = returnLoan();
        List<LoanAuditEvent> originals = loans.getAuditEvents();
        assertTrue(reverse("reverse-return", returned).contains("Reversed RETURN event"));
        assertTrue(loan.isActive());
        assertNull(loan.getReturnDate());
        assertEquals("", loan.getReturnFaults());
        assertEquals(USER, loan.getBorrowerId());
        assertEquals(issue.loanId(), loan.getId());
        assertEquals(DAY.plusDays(3), loan.getDueDate());
        assertEquals(1, loan.getRenewals().size());
        assertEquals(originals, loans.getAuditEvents().subList(0, 2));
        LoanAuditEvent correction = loans.getAuditEvents().getLast();
        assertEquals("cracked screen", correction.faults());
        assertEquals(returned.dueDate(), correction.dueDate());
        assertTrue(new EquipmentFindCommands(inventory, loans).execute("find OSC").endsWith("On loan"));
        assertTrue(new LoanAuditCommands(inventory, loans).execute("loan-audit OSC").contains("Related event: "
                + returned.id()));
        loans.returnLoans(List.of(loan), DAY, "Checked again");
        assertEquals("Checked again", loan.getReturnFaults());
        assertEquals(List.of(LoanAuditEvent.Type.LOAN, LoanAuditEvent.Type.RETURN,
                LoanAuditEvent.Type.REVERSE_RETURN, LoanAuditEvent.Type.RETURN),
                loans.getAuditEvents().stream().map(LoanAuditEvent::type).toList());
        LoanAuditCodec.validate(loans.getLoans(), loans.getAuditEvents());
    }

    @Test
    void reverseLoan_requiresDependentReturnToBeReversedFirst() {
        LoanAuditEvent returned = returnLoan();
        List<LoanAuditEvent> before = loans.getAuditEvents();
        assertThrows(IllegalArgumentException.class, () -> reverse("reverse-loan", issue));
        assertEquals(before, loans.getAuditEvents());
        assertEquals(returned.date(), loan.getReturnDate());
        reverse("reverse-return", returned);
        reverse("reverse-loan", issue);
        assertTrue(loan.isReversed());
        assertEquals(4, loans.getAuditEvents().size());
        LoanBook restored = new LoanBook();
        restored.replaceAll(loans.getLoans(), loans.getAuditEvents());
        assertTrue(restored.getLoans().getFirst().copy().isReversed());
    }

    @Test
    void execute_invalidSyntaxIdsTypesAndDuplicateCorrections_leaveAllRecordsUntouched() {
        List<LoanAuditEvent> before = loans.getAuditEvents();
        assertThrows(IllegalArgumentException.class, () -> commands.execute(null));
        for (String input : List.of("", "reverse-loan", "reverse-loan " + issue.id(),
                "reverse-loan " + issue.id() + " reason/", "reverse-loan " + issue.id() + " reason/   ",
                "reverse-loan " + issue.id() + " reason/First reason/Second",
                "reverse-loan " + issue.id() + " reason/First extra/Unknown",
                "reverse-loan " + issue.id() + " reason/Line\nbreak",
                "reverse-loan " + issue.id() + " Wrong unit", "reverse-loan E-unknown reason/Typo",
                "reverse-loan " + issue.id().toLowerCase() + " reason/Typo",
                "reverse-return " + issue.id() + " reason/Wrong type", "reverse " + issue.id() + " reason/Typo")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input), input);
            assertEquals(before, loans.getAuditEvents());
            assertTrue(loan.isActive());
        }
        reverse("reverse-loan", issue);
        List<LoanAuditEvent> corrected = loans.getAuditEvents();
        assertThrows(IllegalArgumentException.class, () -> reverse("reverse-loan", issue));
        assertThrows(IllegalArgumentException.class, () -> reverse("reverse-loan", corrected.getLast()));
        assertEquals(corrected, loans.getAuditEvents());
        assertTrue(loan.isReversed());
    }

    @Test
    void reverseLoan_renewedLostAndFutureLoansRejectWithoutDiscardingDependencies() {
        loan.renew(DAY.plusDays(3), DAY);
        List<LoanAuditEvent> before = loans.getAuditEvents();
        assertThrows(IllegalArgumentException.class, () -> reverse("reverse-loan", issue));
        loan.markLost(DAY);
        assertThrows(IllegalArgumentException.class, () -> reverse("reverse-loan", issue));
        assertTrue(loan.copy().isLost());
        assertTrue(loan.isLost());
        assertEquals(1, loan.getRenewals().size());
        assertEquals(before, loans.getAuditEvents());
        Loan future = new Loan("MIC", USER, DAY.plusDays(1), DAY.plusDays(2));
        loans.addLoan(future);
        assertThrows(IllegalArgumentException.class, () -> reverse("reverse-loan", loans.getAuditEvents().getLast()));
        assertTrue(future.isActive());
    }

    @Test
    void reverseReturn_laterLoanMustBeCorrectedFirstEvenWhenItHasAlreadyBeenReturned() {
        LoanAuditEvent returned = returnLoan();
        Loan later = new Loan("OSC", USER, DAY, DAY);
        loans.addLoan(later);
        loans.returnLoans(List.of(later), DAY, "");
        List<LoanAuditEvent> before = loans.getAuditEvents();
        assertThrows(IllegalArgumentException.class, () -> reverse("reverse-return", returned));
        assertEquals(before, loans.getAuditEvents());
        assertEquals(returned.date(), loan.getReturnDate());
        LoanAuditEvent laterIssue = before.get(2);
        reverse("reverse-return", before.getLast());
        reverse("reverse-loan", laterIssue);
        reverse("reverse-return", returned);
        assertTrue(loan.isActive());
        assertTrue(later.isReversed());
    }

    @Test
    void reverseReturn_sameDayLaterIssueIsDetectedByAcceptanceOrder() {
        LoanAuditEvent returned = returnLoan();
        Loan later = new Loan("OSC", USER, returned.date(), DAY);
        loans.addLoan(later);
        loans.returnLoans(List.of(later), DAY, "");
        List<LoanAuditEvent> before = loans.getAuditEvents();
        assertThrows(IllegalArgumentException.class, () -> reverse("reverse-return", returned));
        assertEquals(before, loans.getAuditEvents());
        assertEquals(returned.date(), loan.getReturnDate());
    }

    @Test
    void reverseReturn_reservationBoundariesAndBlockingFaultsRejectWithoutClearingDamage() throws Exception {
        LoanAuditEvent returned = returnLoan();
        Reservation booking = bookings.reserve("OSC", USER, new DateWindow(DAY.plusDays(2), DAY.plusDays(3)));
        List<LoanAuditEvent> before = loans.getAuditEvents();
        assertThrows(IllegalArgumentException.class, () -> reverse("reverse-return", returned));
        assertEquals(before, loans.getAuditEvents());
        assertEquals("cracked screen", loan.getReturnFaults());
        bookings.cancel(booking.id(), "No longer needed");
        Fault fault = faults.report("OSC", DAY, "Broken", Fault.Severity.SEVERE);
        assertThrows(IllegalArgumentException.class, () -> reverse("reverse-return", returned));
        faults.resolve(fault.getId(), DAY, "Repaired");
        reverse("reverse-return", returned);
        assertTrue(loan.isActive());
    }

    @Test
    void reverseReturn_overdueLoanBlocksAllFutureBookingsUntilConflictIsRemoved() throws Exception {
        Loan overdue = new Loan("MIC", USER, DAY.minusDays(5), DAY.minusDays(2));
        loans.addLoan(overdue);
        loans.returnLoans(List.of(overdue), DAY.minusDays(1), "");
        LoanAuditEvent returned = loans.getAuditEvents().getLast();
        Reservation booking = bookings.reserve("MIC", USER, new DateWindow(DAY.plusYears(1), DAY.plusYears(1)));
        assertThrows(IllegalArgumentException.class, () -> reverse("reverse-return", returned));
        assertFalse(overdue.isActive());
        bookings.cancel(booking.id(), "No longer needed");
        reverse("reverse-return", returned);
        assertTrue(overdue.isActive());
        assertTrue(bookings.getAvailability().conflict("MIC",
                new DateWindow(DAY.plusYears(2), DAY.plusYears(2)), null, null).isPresent());
    }

    @Test
    void reverseReturn_legacyLoanWithoutIssueEvidenceIsSupportedButCannotInventLoanReversal() {
        Loan legacy = new Loan("MIC", USER, DAY.minusDays(4), DAY.plusDays(1));
        loans.replaceAll(List.of(legacy));
        loans.returnLoans(List.of(legacy), DAY, "");
        LoanAuditEvent returned = loans.getAuditEvents().getFirst();
        reverse("reverse-return", returned);
        assertTrue(legacy.isActive());
        assertEquals(2, loans.getAuditEvents().size());
        assertThrows(IllegalArgumentException.class, () -> reverse("reverse-loan", returned));
    }

    @Test
    void reverseReturn_laterLegacyLoanCannotBeBypassedByMissingIssueEvidence() {
        LoanAuditEvent returned = returnLoan();
        Loan legacy = new Loan("OSC", USER, DAY, DAY.plusDays(1));
        legacy.markReturned(DAY);
        loans.replaceAll(List.of(loan, legacy), loans.getAuditEvents());
        assertThrows(IllegalArgumentException.class, () -> reverse("reverse-return", returned));
        assertFalse(loan.isActive());
        assertEquals(2, loans.getAuditEvents().size());
    }

    @Test
    void reverseReturn_earlierLegacyLoanDoesNotBlockReopening() {
        Loan legacy = new Loan("OSC", USER, DAY.minusDays(5), DAY.minusDays(4));
        legacy.markReturned(DAY.minusDays(3));
        loans.replaceAll(List.of(legacy, loan), loans.getAuditEvents());
        LoanAuditEvent returned = returnLoan();
        reverse("reverse-return", returned);
        assertTrue(loan.isActive());
        assertFalse(legacy.isActive());
    }

    @Test
    void reverse_datesAndRepeatedTransitionsValidateBeforeAnyPublication() {
        assertThrows(IllegalArgumentException.class,
                () -> loans.reverse(issue.id(), LoanAuditEvent.Type.LOAN, null, "Typo", bookings.getAvailability()));
        loan.renew(DAY.plusDays(3), DAY.plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> reverse("reverse-loan", issue));
        Loan fresh = new Loan("MIC", USER, DAY, DAY);
        assertThrows(IllegalArgumentException.class, fresh::reverseReturn);
        fresh.reverseIssue();
        assertThrows(IllegalArgumentException.class, fresh::reverseReturn);
        assertThrows(IllegalArgumentException.class, fresh::reverseIssue);
        assertThrows(IllegalArgumentException.class, () -> loans.reverse(issue.id(),
                LoanAuditEvent.Type.REVERSE_LOAN, DAY, "Typo", bookings.getAvailability()));
    }

    @Test
    void reverse_invalidReasonsDoNotPublishDetachedCandidateAndLocalDateUsesClockZone() {
        List<LoanAuditEvent> before = loans.getAuditEvents();
        for (String reason : new String[]{null, "", " ", "Line\nbreak"}) {
            assertThrows(IllegalArgumentException.class, () -> loans.reverse(issue.id(), LoanAuditEvent.Type.LOAN,
                    DAY, reason, bookings.getAvailability()));
            assertEquals(before, loans.getAuditEvents());
            assertTrue(loan.isActive());
        }
        new LoanCorrectionCommands(loans, bookings.getAvailability(), CLOCK.withZone(ZoneOffset.ofHours(8)))
                .execute("reverse-loan " + issue.id() + " reason/Not handed over");
        assertEquals(DAY.plusDays(1), loans.getAuditEvents().getLast().date());
        LoanAuditEvent correction = loans.getAuditEvents().getLast();
        assertThrows(IllegalArgumentException.class, () -> loans.reverse(correction.id(), correction.type(),
                DAY.plusDays(1), "Cannot reverse corrections", bookings.getAvailability()));
    }

    @Test
    void reopenedLoan_clockMovingBackwardRejectsReturnRenewalAndLossBeforeMutation() {
        LoanAuditEvent returned = returnLoan();
        new LoanCorrectionCommands(loans, bookings.getAvailability(), CLOCK.withZone(ZoneOffset.ofHours(8)))
                .execute("reverse-return " + returned.id() + " reason/Still out");
        List<LoanAuditEvent> before = loans.getAuditEvents();
        assertThrows(IllegalArgumentException.class, () -> loans.returnLoans(List.of(loan), DAY, ""));
        assertThrows(IllegalArgumentException.class, () -> new RenewCommands(loans, bookings.getAvailability(), CLOCK)
                .execute("renew OSC d/2026-10-30"));
        assertThrows(IllegalArgumentException.class,
                () -> new LostCommands(inventory, loans, CLOCK).execute("lost OSC"));
        assertEquals(before, loans.getAuditEvents());
        assertTrue(loan.isActive());
        assertFalse(loan.isLost());
        assertTrue(loan.getRenewals().isEmpty());
        assertEquals(DAY.plusDays(2), loan.getDueDate());
    }

    @Test
    void mainConsole_dispatchesBothCorrectionsAndHelpWithoutExecutingHelpExamples() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LabLog app = new LabLog(inventory, loans, List::of, CLOCK, borrowers);
        app.run(new Scanner("help reverse-loan\nhelp reverse-return\nreverse-return E-unknown reason/Typo\n"
                + "reverse-loan " + issue.id() + " reason/Not handed over\nloan-audit OSC\nhistory OSC\nexit\n"),
                new PrintStream(output));
        String text = output.toString();
        assertTrue(text.contains("Format: reverse-loan EVENT_ID reason/TEXT"));
        assertTrue(text.contains("Format: reverse-return EVENT_ID reason/TEXT"));
        assertTrue(text.contains("Error: Audit event E-unknown was not found."));
        assertTrue(text.contains("Reversed LOAN event " + issue.id()));
        assertTrue(text.contains("Reason: Not handed over"));
        assertTrue(text.contains("REVERSED (loan cancelled)"));
        assertEquals(2, loans.getAuditEvents().size());
    }
}
