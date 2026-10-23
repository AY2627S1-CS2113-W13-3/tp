package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks corrected state, immutable evidence, migration and atomic persistence through real console sessions. */
class LoanCorrectionPersistenceTest {
    private static final String USER = "A0123456X";

    private String run(LabLog app, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        app.run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }

    private ApplicationSnapshot sample() {
        LocalDate today = LocalDate.now();
        LoanBook book = new LoanBook();
        book.addLoan(new Loan("OSC", USER, today.minusDays(2), today.plusDays(2)));
        book.addLoan(new Loan("MIC", USER, today.minusDays(2), today.plusDays(2)));
        book.returnLoans(book.getLoans(), today.minusDays(1), "cracked screen");
        return new ApplicationSnapshot(List.of(new Equipment("OSC", "Scope", "Electronics", "M1", "Lab"),
                new Equipment("MIC", "Microphone", "Audio", "M2", "Lab")),
                List.of(new Borrower(USER, "Jamie", "91234567", "jamie@u.nus.edu")), book.getLoans(),
                List.of(), List.of(), List.of(), List.of(), book.getAuditEvents());
    }

    @Test
    void run_bothCorrectionsRestartAndTransfers_preserveOriginalsAndEffectiveState(@TempDir Path directory)
            throws Exception {
        Path data = directory.resolve("live.properties");
        ApplicationSnapshot original = sample();
        original.write(data);
        String oscReturn = original.getAuditEvents().get(2).id();
        String micReturn = original.getAuditEvents().get(3).id();
        String micIssue = original.getAuditEvents().get(1).id();
        String output = run(LabLog.load(data), "reverse-return " + oscReturn + " reason/Still with borrower\n"
                + "reverse-return " + micReturn + " reason/Correct return first\n"
                + "reverse-loan " + micIssue + " reason/Wrong loan\n"
                + "loan MIC b/" + USER + " d/" + LocalDate.now().plusDays(1));
        assertFalse(output.contains("Error:"), output);
        ApplicationSnapshot corrected = ApplicationSnapshot.read(data);
        assertEquals("11", ApplicationSnapshot.readProperties(data).getProperty("format"));
        assertEquals(original.getAuditEvents(), corrected.getAuditEvents().subList(0, 4));
        assertEquals(8, corrected.getAuditEvents().size());
        assertTrue(corrected.getLoans().get(0).isActive());
        assertEquals("", corrected.getLoans().get(0).getReturnFaults());
        assertTrue(corrected.getLoans().get(1).isReversed());
        assertNull(corrected.getLoans().get(1).getReturnDate());
        assertTrue(corrected.getLoans().get(2).isActive());
        assertEquals(original.getLoans().get(1).getId(), corrected.getLoans().get(1).getId());
        String before = Files.readString(data);
        String inspection = run(LabLog.load(data), "loan-audit\nhistory MIC\nactive-loans\nfind OSC");
        assertTrue(inspection.contains("REVERSED (loan cancelled)"));
        assertTrue(inspection.contains("Reason: Wrong loan"));
        assertEquals(before, Files.readString(data));
        Path backup = directory.resolve("backup.properties");
        assertFalse(run(LabLog.load(data), "export " + backup).contains("Error:"));
        Path imported = directory.resolve("imported.properties");
        assertFalse(run(LabLog.load(imported), "import " + backup + " --replace").contains("Error:"));
        assertEquals(corrected.toProperties(), ApplicationSnapshot.read(imported).toProperties());
        assertTrue(new LabLogStore(data).getLoans().getLoans().get(1).isReversed());
    }

    @Test
    void run_rejectedCorrectionsDoNotSaveOrAlterOtherBatchMembers(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("live.properties");
        ApplicationSnapshot original = sample();
        original.write(data);
        String saved = Files.readString(data);
        String issue = original.getAuditEvents().getFirst().id();
        String returned = original.getAuditEvents().get(2).id();
        for (String command : List.of("reverse-loan " + issue + " reason/Return still effective",
                "reverse-return " + issue + " reason/Wrong type", "reverse-return E-missing reason/Unknown",
                "reverse-return " + returned + " reason/", "reverse-return " + returned + " extra/Wrong")) {
            assertTrue(run(LabLog.load(data), command).contains("Error:"));
            assertEquals(saved, Files.readString(data));
        }
        assertFalse(run(LabLog.load(data), "reverse-return " + returned + " reason/Still out").contains("Error:"));
        ApplicationSnapshot corrected = ApplicationSnapshot.read(data);
        assertTrue(corrected.getLoans().getFirst().isActive());
        assertEquals(original.getLoans().get(1).getReturnDate(), corrected.getLoans().get(1).getReturnDate());
        assertEquals(original.getLoans().get(1).getReturnFaults(), corrected.getLoans().get(1).getReturnFaults());
        String after = Files.readString(data);
        assertTrue(run(LabLog.load(data), "reverse-return " + returned + " reason/Again").contains("Error:"));
        assertEquals(after, Files.readString(data));
    }

    @Test
    void import_inconsistentCorrectedStateRejectsBeforeReplacingLiveOrSavedRecords(@TempDir Path directory)
            throws Exception {
        Path data = directory.resolve("live.properties");
        ApplicationSnapshot original = sample();
        original.write(data);
        String returned = original.getAuditEvents().get(2).id();
        assertFalse(run(LabLog.load(data), "reverse-return " + returned + " reason/Still out").contains("Error:"));
        ApplicationSnapshot corrected = ApplicationSnapshot.read(data);
        String saved = Files.readString(data);
        LabLog app = LabLog.load(data);
        Path source = directory.resolve("invalid.properties");
        for (String[] change : List.of(new String[]{"loan.0.returned", LocalDate.now().toString()},
                new String[]{"loan.1.faults", "Changed evidence"},
                new String[]{"loan.1.due", LocalDate.now().plusDays(4).toString()},
                new String[]{"audit.4.date", LocalDate.now().minusDays(3).toString()})) {
            Properties records = corrected.toProperties();
            records.setProperty(change[0], change[1]);
            try (Writer writer = Files.newBufferedWriter(source)) {
                records.store(writer, "invalid correction projection");
            }
            assertThrows(IOException.class, () -> ApplicationSnapshot.read(source), change[0]);
            assertTrue(run(app, "import " + source + " --replace").contains("Current records were not changed."));
            assertEquals(saved, Files.readString(data));
        }
        assertTrue(run(app, "find OSC").contains("Availability: On loan"));
    }

    @Test
    void write_voidedLoansRequireEvidenceAndCannotRetainDependentActions(@TempDir Path directory) throws Exception {
        ApplicationSnapshot original = sample();
        Path data = directory.resolve("unchanged.properties");
        original.write(data);
        String saved = Files.readString(data);
        Loan cancelled = original.getLoans().getFirst().copy();
        cancelled.reverseReturn();
        cancelled.reverseIssue();
        assertThrows(IllegalArgumentException.class, () -> new LoanBook().replaceAll(List.of(cancelled)));
        LoanAuditEvent issue = original.getAuditEvents().getFirst();
        LoanAuditEvent returned = original.getAuditEvents().get(2);
        List<LoanAuditEvent> events = new ArrayList<>(original.getAuditEvents());
        events.add(LoanAuditEvent.reversed(returned, LocalDate.now(), "Wrong return"));
        events.add(LoanAuditEvent.reversed(issue, LocalDate.now(), "Wrong loan"));
        ApplicationSnapshot invalid = new ApplicationSnapshot(original.getEquipment(), original.getBorrowers(),
                original.getLoans(), List.of(), List.of(), List.of(), List.of(), events);
        assertThrows(IOException.class, () -> invalid.write(data));
        assertEquals(saved, Files.readString(data));
        Loan renewed = new Loan(issue.loanId(), "OSC", USER, issue.date(), issue.dueDate());
        renewed.renew(issue.dueDate().plusDays(1), LocalDate.now());
        assertThrows(IllegalArgumentException.class, () -> LoanAuditCodec.validate(List.of(renewed),
                List.of(issue, LoanAuditEvent.reversed(issue, LocalDate.now(), "Wrong loan"))));
        Loan lost = new Loan(issue.loanId(), "OSC", USER, issue.date(), issue.dueDate());
        lost.markLost(LocalDate.now());
        assertThrows(IllegalArgumentException.class, () -> LoanAuditCodec.validate(List.of(lost),
                List.of(issue, LoanAuditEvent.reversed(issue, LocalDate.now(), "Wrong loan"))));
    }

    @Test
    void validate_returnProjectionAndPerLoanChronologyCannotContradictTheLedger() {
        ApplicationSnapshot snapshot = sample();
        Loan loan = snapshot.getLoans().getFirst().copy();
        loan.reverseReturn();
        assertThrows(IllegalArgumentException.class, () -> LoanAuditCodec.validate(
                List.of(loan, snapshot.getLoans().getLast()), snapshot.getAuditEvents()));
        loan.renew(loan.getDueDate().plusDays(1), LocalDate.now());
        loan.markReturned(snapshot.getLoans().getFirst().getReturnDate().plusDays(1), "cracked screen");
        assertThrows(IllegalArgumentException.class, () -> LoanAuditCodec.validate(
                List.of(loan, snapshot.getLoans().getLast()), snapshot.getAuditEvents()));
        Loan changedDeadline = snapshot.getLoans().getFirst().copy();
        changedDeadline.reverseReturn();
        changedDeadline.renew(changedDeadline.getDueDate().plusDays(1), LocalDate.now().minusDays(1));
        changedDeadline.markReturned(snapshot.getLoans().getFirst().getReturnDate(), "cracked screen");
        assertThrows(IllegalArgumentException.class, () -> LoanAuditCodec.validate(
                List.of(changedDeadline, snapshot.getLoans().getLast()), snapshot.getAuditEvents()));
        Loan current = snapshot.getLoans().getFirst().copy();
        LoanAuditEvent returned = snapshot.getAuditEvents().get(2);
        List<LoanAuditEvent> events = new ArrayList<>(snapshot.getAuditEvents());
        events.add(LoanAuditEvent.reversed(returned, LocalDate.now(), "Still out"));
        events.add(LoanAuditEvent.returned(current, LocalDate.now().minusDays(1), "cracked screen"));
        assertThrows(IllegalArgumentException.class, () -> LoanAuditCodec.validate(snapshot.getLoans(), events));
    }

    @Test
    void migration_legacyLoanCanReverseNewReturnWithoutFabricatingPastIssue(@TempDir Path directory) throws Exception {
        Properties legacy = sample().toProperties();
        legacy.setProperty("format", "10");
        legacy.keySet().removeIf(key -> key.toString().startsWith("audit.")
                || (key.toString().startsWith("loan.") && key.toString().endsWith(".id")));
        legacy.setProperty("audit.count", "0");
        legacy.setProperty("loan.0.returned", "");
        legacy.setProperty("loan.0.faults", "");
        Path data = directory.resolve("legacy.properties");
        try (Writer writer = Files.newBufferedWriter(data)) {
            legacy.store(writer, "legacy loan");
        }
        assertFalse(run(LabLog.load(data), "return OSC").contains("Error:"));
        ApplicationSnapshot returned = ApplicationSnapshot.read(data);
        assertEquals(1, returned.getAuditEvents().size());
        String event = returned.getAuditEvents().getFirst().id();
        assertFalse(run(LabLog.load(data), "reverse-return " + event + " reason/Not actually returned")
                .contains("Error:"));
        ApplicationSnapshot reopened = ApplicationSnapshot.read(data);
        assertEquals(returned.getLoans().getFirst().getId(), reopened.getLoans().getFirst().getId());
        assertTrue(reopened.getLoans().getFirst().isActive());
        assertEquals(List.of(LoanAuditEvent.Type.RETURN, LoanAuditEvent.Type.REVERSE_RETURN),
                reopened.getAuditEvents().stream().map(LoanAuditEvent::type).toList());
    }

    @Test
    void run_failedCorrectionSaveSuppressesSuccessAndStopsSession(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("live.properties");
        ApplicationSnapshot original = sample();
        original.write(data);
        String saved = Files.readString(data);
        LabLog app = LabLog.load(data);
        Path previous = directory.resolve("previous.properties");
        Files.move(data, previous);
        Files.createDirectory(data);
        Files.writeString(data.resolve("keep.txt"), "keep");
        String event = original.getAuditEvents().get(2).id();
        String output = run(app, "reverse-return " + event + " reason/Still out\nloan-audit");
        assertTrue(output.contains("Changes could not be saved"));
        assertFalse(output.contains("Reversed RETURN event"));
        assertFalse(output.contains("Loan audit:"));
        assertEquals(saved, Files.readString(previous));
        assertEquals("keep", Files.readString(data.resolve("keep.txt")));
    }
}
