package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

/** Verifies immutable evidence, acceptance order, rejected batches and read-only console queries. */
class LoanAuditCommandsTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 23);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T16:30:00Z"), ZoneOffset.UTC);
    private static final String USER = "A0123456X";
    private final Inventory inventory = new Inventory();
    private final LoanBook loans = new LoanBook();
    private final BorrowerBook borrowers = new BorrowerBook();
    private final LoanAuditCommands audit = new LoanAuditCommands(inventory, loans);

    @BeforeEach
    void seed() throws Exception {
        for (String id : List.of("OSC", "MIC", "FREE")) {
            inventory.addEquipment(new Equipment(id, id + " unit", "Electronics", "M1", "Lab"));
        }
        borrowers.addBorrower(new Borrower(USER, "Jamie", "91234567", "jamie@u.nus.edu"));
    }

    @Test
    void issueRenewAndReturn_eventsKeepOriginalFactsAndStableLoanIdentity() {
        new LoanCommands(inventory, loans, CLOCK, borrowers).execute("loan OSC b/" + USER + " d/2026-10-24");
        Loan loan = loans.getLoans().getFirst();
        LoanAuditEvent issue = loans.getAuditEvents().getFirst();
        List<LoanAuditEvent> earlier = loans.getAuditEvents();
        loan.renew(DAY.plusDays(2), DAY);
        new ReturnCommands(inventory, loans, CLOCK).execute("return OSC faults/cracked screen");
        assertEquals(2, loans.getAuditEvents().size());
        LoanAuditEvent returned = loans.getAuditEvents().getLast();
        assertEquals(LoanAuditEvent.Type.LOAN, issue.type());
        assertEquals(DAY.plusDays(1), issue.dueDate());
        assertEquals("", issue.faults());
        assertEquals(LoanAuditEvent.Type.RETURN, returned.type());
        assertEquals(DAY.plusDays(2), returned.dueDate());
        assertEquals("cracked screen", returned.faults());
        assertEquals(loan.getId(), issue.loanId());
        assertEquals(issue.loanId(), returned.loanId());
        assertNotEquals(issue.id(), returned.id());
        assertEquals(1, earlier.size());
        assertThrows(UnsupportedOperationException.class, () -> earlier.clear());
        String output = audit.execute("loan-audit OSC");
        assertTrue(output.indexOf(issue.id()) < output.indexOf(returned.id()));
        assertTrue(output.startsWith("Loan audit for OSC:\n\n[1] LOAN — 2026-10-23\n"));
        assertTrue(output.contains("\n\n[2] RETURN — 2026-10-23\n"));
        assertTrue(output.contains("\n    Asset: OSC\n    Borrower: " + USER + "\n    Due: 2026-10-24"));
        assertTrue(output.contains("\n    Faults: cracked screen\n    Event ID: " + returned.id()));
        assertTrue(output.contains("\n    Event ID: " + issue.id() + "\n    Loan ID: " + loan.getId()));
        String issueBlock = output.substring(0, output.indexOf("\n\n[2]"));
        assertFalse(issueBlock.contains("Faults:"));
        assertFalse(output.contains("Related event:"));
        assertFalse(output.contains("Reason:"));
        assertFalse(output.contains("Date | Event ID"));
        assertFalse(loan.isActive());
    }

    @Test
    void execute_dateOrderingAndSameDayAcceptanceOrder_doNotReorderStoredEvidence() {
        Loan later = new Loan("OSC", USER, DAY, DAY.plusDays(1));
        Loan earlier = new Loan("MIC", USER, DAY.minusDays(1), DAY.plusDays(1));
        loans.addLoan(later);
        loans.addLoan(earlier);
        new ReturnCommands(inventory, loans, CLOCK).execute("return MIC OSC");
        List<LoanAuditEvent> stored = loans.getAuditEvents();
        String output = audit.execute("  loan-audit\t ");
        assertTrue(output.indexOf(stored.get(1).id()) < output.indexOf(stored.get(0).id()));
        assertTrue(output.indexOf(stored.get(0).id()) < output.indexOf(stored.get(2).id()));
        assertTrue(output.indexOf(stored.get(2).id()) < output.indexOf(stored.get(3).id()));
        assertEquals(stored, loans.getAuditEvents());
        String filtered = audit.execute("loan-audit OSC");
        assertTrue(filtered.contains(later.getId()));
        assertFalse(filtered.contains(earlier.getId()));
        assertTrue(audit.execute("loan-audit FREE").startsWith("No loan audit events for FREE."));
    }

    @Test
    void execute_emptyLegacyUnknownAndMalformedRequests_doNotInventEvents() {
        Loan legacy = new Loan("OSC", USER, DAY.minusDays(1), DAY.plusDays(1));
        loans.replaceAll(List.of(legacy));
        assertTrue(audit.execute("loan-audit").contains("Legacy loans may have no recorded events"));
        assertTrue(audit.execute("loan-audit OSC").startsWith("No loan audit events for OSC."));
        assertThrows(IllegalArgumentException.class, () -> audit.execute(null));
        for (String input : List.of("", "loan-audit OSC MIC", "history OSC", "LOAN-AUDIT", "loan-audit osc")) {
            assertThrows(IllegalArgumentException.class, () -> audit.execute(input), input);
        }
        assertTrue(loans.getAuditEvents().isEmpty());
        assertEquals(List.of(legacy), loans.getLoans());
    }

    @Test
    void rejectedOperationsAndLaterLostUnit_leaveEventsAndEveryLoanUnchanged() {
        loans.addLoan(new Loan("OSC", USER, DAY, DAY.plusDays(1)));
        Loan lost = new Loan("MIC", USER, DAY, DAY.plusDays(1));
        loans.addLoan(lost);
        lost.markLost(DAY);
        List<LoanAuditEvent> before = loans.getAuditEvents();
        ReturnCommands returns = new ReturnCommands(inventory, loans, CLOCK);
        for (String command : List.of("return OSC MIC", "return OSC UNKNOWN", "return OSC OSC",
                "return OSC faults/", "return OSC MIC faults/damage")) {
            assertThrows(IllegalArgumentException.class, () -> returns.execute(command));
            assertEquals(before, loans.getAuditEvents());
            assertTrue(loans.getLoans().getFirst().isActive());
        }
        assertThrows(IllegalArgumentException.class, () -> new LoanCommands(inventory, loans, CLOCK, borrowers)
                .execute("loan FREE b/" + USER + " d/2026-02-30"));
        assertEquals(before, loans.getAuditEvents());
        assertThrows(IllegalArgumentException.class, () -> loans.returnLoans(List.of(lost), DAY, ""));
        Loan otherBookLoan = new Loan("FREE", USER, DAY, DAY.plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> loans.returnLoans(List.of(otherBookLoan), DAY, ""));
        Loan ordinary = loans.getLoans().getFirst();
        assertThrows(IllegalArgumentException.class, () -> loans.returnLoans(List.of(ordinary, ordinary), DAY, ""));
        assertEquals(before, loans.getAuditEvents());
    }

    @Test
    void sameAssetReborrowOnSameDay_keepsDistinctLoanAndEventIdentities() {
        Loan first = new Loan("OSC", USER, DAY, DAY.plusDays(1));
        loans.addLoan(first);
        loans.returnLoans(List.of(first), DAY, "");
        Loan second = new Loan("OSC", USER, DAY, DAY.plusDays(2));
        loans.addLoan(second);
        assertNotEquals(first.getId(), second.getId());
        assertEquals(List.of(first.getId(), first.getId(), second.getId()),
                loans.getAuditEvents().stream().map(LoanAuditEvent::loanId).toList());
        assertEquals(3, loans.getAuditEvents().stream().map(LoanAuditEvent::id).distinct().count());
    }

    @Test
    void batchIssue_successRecordsEachSelection_andRejectedBatchAddsNoEvidence() {
        LoanCommands commands = new LoanCommands(inventory, loans, CLOCK, borrowers);
        commands.execute("loan MIC OSC b/" + USER + " d/2026-10-24");
        List<LoanAuditEvent> before = loans.getAuditEvents();
        assertEquals(List.of("MIC", "OSC"), before.stream().map(LoanAuditEvent::assetId).toList());
        assertEquals(List.of(LoanAuditEvent.Type.LOAN, LoanAuditEvent.Type.LOAN),
                before.stream().map(LoanAuditEvent::type).toList());
        assertEquals(loans.getLoans().stream().map(Loan::getId).toList(),
                before.stream().map(LoanAuditEvent::loanId).toList());
        for (String input : List.of("loan FREE UNKNOWN b/" + USER + " d/2026-10-24",
                "loan FREE OSC b/" + USER + " d/2026-10-24")) {
            assertThrows(IllegalArgumentException.class, () -> commands.execute(input));
            assertEquals(before, loans.getAuditEvents());
            assertEquals(2, loans.getLoans().size());
            assertTrue(loans.findActiveLoan("FREE").isEmpty());
        }
    }

    @Test
    void run_fixedLocalClockAndConsoleDispatcher_recordAndDisplayAcceptedEvents() {
        Clock local = CLOCK.withZone(ZoneOffset.ofHours(8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog(inventory, loans, List::of, local, borrowers).run(new Scanner(
                "loan OSC b/" + USER + " d/2026-10-25\nreturn OSC\nloan-audit OSC\nexit\n"),
                new PrintStream(output));
        assertFalse(output.toString().contains("Error:"), output.toString());
        assertTrue(output.toString().contains("Loan audit for OSC:"));
        assertEquals(List.of(DAY.plusDays(1), DAY.plusDays(1)),
                loans.getAuditEvents().stream().map(LoanAuditEvent::date).toList());
    }

    @Test
    void correctionsDisplayAlongsideOriginals_withoutRemovingOrRewritingEitherEvent() {
        Loan loan = new Loan("OSC", USER, DAY, DAY.plusDays(1));
        LoanAuditEvent issue = LoanAuditEvent.issued(loan);
        LoanAuditEvent correction = new LoanAuditEvent("E-correct", loan.getId(), DAY,
                LoanAuditEvent.Type.REVERSE_LOAN, "OSC", USER, issue.dueDate(), "", issue.id(), "Wrong unit");
        loans.replaceAll(List.of(loan), List.of(issue, correction));
        String output = audit.execute("loan-audit OSC");
        assertTrue(output.contains("REVERSE_LOAN"));
        assertTrue(output.contains("\n    Related event: " + issue.id() + "\n    Reason: Wrong unit"));
        assertFalse(output.contains("Faults:"));
        assertEquals(List.of(issue, correction), loans.getAuditEvents());
        assertTrue(output.indexOf(issue.id()) < output.indexOf(correction.id()));
    }

    @Test
    void helpExamples_executeAsReadOnlyQueries() {
        inventory.addEquipment(new Equipment("OSC-001", "Scope", "Electronics", "S1", "Lab"));
        String detail = new HelpCommands().execute("help loan-audit");
        int examples = 0;
        for (String line : detail.lines().toList()) {
            if (line.startsWith("Example: ")) {
                assertTrue(audit.execute(line.substring(9)).startsWith("No loan audit events"));
                examples++;
            }
        }
        assertEquals(2, examples);
        assertTrue(detail.contains("Format: loan-audit [ASSET_ID]"));
        assertTrue(new HelpCommands().execute("help").contains("loan-audit [ASSET_ID]"));
        assertTrue(loans.getAuditEvents().isEmpty());
    }
}
