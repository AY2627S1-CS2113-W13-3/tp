package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** Verifies full evidence round trips, legacy migration, malformed imports and correction relationships. */
class LoanAuditPersistenceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 23);
    private static final String USER = "A0123456X";

    private ApplicationSnapshot sample() {
        LoanBook loans = new LoanBook();
        Loan first = new Loan("OSC", USER, DAY.minusDays(2), DAY.plusDays(1));
        loans.addLoan(first);
        loans.returnLoans(List.of(first), DAY, "Inspected = intact");
        loans.addLoan(new Loan("MIC", USER, DAY, DAY.plusDays(2)));
        return new ApplicationSnapshot(List.of(new Equipment("OSC", "Scope", "Electronics", "S1", "Lab"),
                new Equipment("MIC", "Microphone", "Audio", "M1", "Lab")),
                List.of(new Borrower(USER, "Jamie", "91234567", "jamie@u.nus.edu")),
                loans.getLoans(), List.of(), List.of(), List.of(), List.of(), loans.getAuditEvents());
    }

    private String run(LabLog app, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        app.run(new Scanner(input + "\nexit\n"), new PrintStream(output));
        return output.toString();
    }

    @Test
    void snapshot_reloadExportAndImport_preserveAllIdsOrderAndEvidence(@TempDir Path directory) throws Exception {
        ApplicationSnapshot expected = sample();
        Path data = directory.resolve("live.properties");
        expected.write(data);
        ApplicationSnapshot loaded = ApplicationSnapshot.read(data);
        assertEquals("11", ApplicationSnapshot.readProperties(data).getProperty("format"));
        assertEquals(expected.getAuditEvents(), loaded.getAuditEvents());
        assertEquals(expected.getLoans().stream().map(Loan::getId).toList(),
                loaded.getLoans().stream().map(Loan::getId).toList());
        String saved = Files.readString(data);
        assertTrue(run(LabLog.load(data), "loan-audit").contains(expected.getAuditEvents().getFirst().id()));
        assertEquals(saved, Files.readString(data));
        Path backup = directory.resolve("backup.properties");
        assertFalse(run(LabLog.load(data), "export " + backup).contains("Error:"));
        Path imported = directory.resolve("imported.properties");
        assertFalse(run(LabLog.load(imported), "import " + backup + " --replace").contains("Error:"));
        assertEquals(expected.toProperties(), ApplicationSnapshot.read(imported).toProperties());
        assertEquals(expected.getAuditEvents(), new LabLogStore(data).getLoans().getAuditEvents());
        assertThrows(UnsupportedOperationException.class, () -> loaded.getAuditEvents().clear());
    }

    @Test
    void run_batchReturnsAndRejectedInput_publishOnlySuccessfulEvents(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("live.properties");
        LocalDate today = LocalDate.now();
        String setup = "add OSC n/Scope c/Electronics m/S1 l/Lab\n"
                + "add MIC n/Microphone c/Audio m/M1 l/Lab\n"
                + "add-borrower " + USER + " n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                + "loan OSC MIC b/" + USER + " d/" + today.plusDays(1);
        assertFalse(run(LabLog.load(data), setup).contains("Error:"));
        ApplicationSnapshot first = ApplicationSnapshot.read(data);
        assertEquals(2, first.getAuditEvents().size());
        String before = Files.readString(data);
        for (String input : List.of("return OSC UNKNOWN", "return OSC OSC", "loan UNKNOWN b/" + USER
                + " d/" + today.plusDays(1), "loan-audit UNKNOWN", "loan-audit OSC MIC")) {
            assertTrue(run(LabLog.load(data), input).contains("Error:"), input);
            assertEquals(before, Files.readString(data));
        }
        assertFalse(run(LabLog.load(data), "return MIC OSC\nloan-audit").contains("Error:"));
        ApplicationSnapshot returned = ApplicationSnapshot.read(data);
        assertEquals(4, returned.getAuditEvents().size());
        assertEquals(first.getAuditEvents(), returned.getAuditEvents().subList(0, 2));
        assertEquals(List.of("MIC", "OSC"), returned.getAuditEvents().subList(2, 4).stream()
                .map(LoanAuditEvent::assetId).toList());
        assertEquals(List.of(LoanAuditEvent.Type.RETURN, LoanAuditEvent.Type.RETURN),
                returned.getAuditEvents().subList(2, 4).stream().map(LoanAuditEvent::type).toList());
        assertEquals(first.getLoans().getFirst().getId(), returned.getLoans().getFirst().getId());
        String after = Files.readString(data);
        assertTrue(run(LabLog.load(data), "return OSC").contains("Error:"));
        assertEquals(after, Files.readString(data));
    }

    @Test
    void decode_corruptEventsAndIds_rejectBeforeReplacingLiveData(@TempDir Path directory) throws Exception {
        ApplicationSnapshot expected = sample();
        Path data = directory.resolve("live.properties");
        expected.write(data);
        LabLog app = LabLog.load(data);
        String saved = Files.readString(data);
        Path source = directory.resolve("invalid.properties");
        for (String[] change : List.of(new String[]{"loan.0.id", ""}, new String[]{"loan.0.id", "wrong"},
                new String[]{"loan.0.id", "L-"}, new String[]{"loan.0.id", "L-bad id"},
                new String[]{"loan.1.id", expected.getLoans().getFirst().getId()},
                new String[]{"audit.count", "-1"}, new String[]{"audit.count", "100001"},
                new String[]{"audit.count", "overflow"},
                new String[]{"audit.1.id", expected.getAuditEvents().get(0).id()},
                new String[]{"audit.0.loan", "L-unknown"}, new String[]{"audit.0.asset", "MIC"},
                new String[]{"audit.0.borrower", "OTHER"}, new String[]{"audit.0.date", "2026-10-20"},
                new String[]{"audit.0.date", "2026-10-22"}, new String[]{"audit.0.due", "2026-10-25"},
                new String[]{"audit.1.due", "2026-10-20"}, new String[]{"audit.0.type", "LOST"},
                new String[]{"audit.0.faults", "fabricated"}, new String[]{"audit.0.reason", "unexpected"},
                new String[]{"audit.0.related", "E-unknown"}, new String[]{"audit.1.date", "2026-02-30"},
                new String[]{"audit.0.extra", "unknown"}, new String[]{"audit.3.id", "E-out-of-range"})) {
            Properties records = expected.toProperties();
            records.setProperty(change[0], change[1]);
            try (Writer writer = Files.newBufferedWriter(source)) {
                records.store(writer, "invalid audit");
            }
            String original = Files.readString(source);
            assertThrows(IOException.class, () -> ApplicationSnapshot.read(source), change[0]);
            assertTrue(run(app, "import " + source + " --replace").contains("Current records were not changed."));
            assertEquals(saved, Files.readString(data));
            assertEquals(original, Files.readString(source));
        }
        for (String key : List.of("loan.0.id", "audit.count", "audit.0.id", "audit.0.loan", "audit.0.date",
                "audit.0.type", "audit.0.asset", "audit.0.borrower", "audit.0.due", "audit.0.faults",
                "audit.0.related", "audit.0.reason")) {
            Properties records = expected.toProperties();
            records.remove(key);
            assertThrows(IOException.class, () -> ApplicationSnapshot.decode(records), key);
        }
    }

    @Test
    void migration_format10InventsNoEvents_andPersistsNewIdentitiesOnceSaved(@TempDir Path directory) throws Exception {
        Properties legacy = sample().toProperties();
        legacy.setProperty("format", "10");
        legacy.keySet().removeIf(key -> (key.toString().startsWith("loan.") && key.toString().endsWith(".id"))
                || key.toString().startsWith("audit."));
        legacy.setProperty("audit.count", "0");
        ApplicationSnapshot restored = ApplicationSnapshot.decode(legacy);
        assertTrue(restored.getAuditEvents().isEmpty());
        Path data = directory.resolve("migrated.properties");
        restored.write(data);
        assertEquals(restored.getLoans().stream().map(Loan::getId).toList(),
                ApplicationSnapshot.read(data).getLoans().stream().map(Loan::getId).toList());
        assertTrue(ApplicationSnapshot.read(data).getAuditEvents().isEmpty());
        LoanBook book = new LoanBook();
        book.replaceAll(restored.getLoans(), restored.getAuditEvents());
        book.returnLoans(List.of(restored.getLoans().getLast()), DAY, "Checked");
        assertEquals(1, book.getAuditEvents().size());
        assertEquals(LoanAuditEvent.Type.RETURN, book.getAuditEvents().getFirst().type());
        legacy.setProperty("loan.0.id", "L-hidden");
        assertThrows(IOException.class, () -> ApplicationSnapshot.decode(legacy));
        legacy.remove("loan.0.id");
        legacy.setProperty("audit.count", "1");
        assertThrows(IOException.class, () -> ApplicationSnapshot.decode(legacy));
    }

    @Test
    void corrections_roundTripBothTypesAndOriginals_rejectBrokenReferences() throws Exception {
        ApplicationSnapshot snapshot = sample();
        LoanAuditEvent issue = snapshot.getAuditEvents().get(0);
        LoanAuditEvent returned = snapshot.getAuditEvents().get(1);
        LoanAuditEvent undoReturn = correction("E-undo-return", returned, LoanAuditEvent.Type.REVERSE_RETURN);
        LoanAuditEvent undoLoan = correction("E-undo-loan", issue, LoanAuditEvent.Type.REVERSE_LOAN);
        List<LoanAuditEvent> events = new ArrayList<>(snapshot.getAuditEvents());
        events.add(undoReturn);
        events.add(undoLoan);
        ApplicationSnapshot corrected = withAudit(snapshot, events);
        assertEquals(events, ApplicationSnapshot.decode(corrected.toProperties()).getAuditEvents());
        for (String[] change : List.of(new String[]{"audit.3.related", "E-unknown"},
                new String[]{"audit.3.related", undoReturn.id()}, new String[]{"audit.3.related", issue.id()},
                new String[]{"audit.3.related", undoLoan.id()}, new String[]{"audit.3.date", "2026-10-22"},
                new String[]{"audit.3.due", "2026-10-26"}, new String[]{"audit.3.faults", "Changed evidence"},
                new String[]{"audit.3.loan", snapshot.getLoans().getLast().getId()},
                new String[]{"audit.3.reason", ""})) {
            Properties records = corrected.toProperties();
            records.setProperty(change[0], change[1]);
            assertThrows(IOException.class, () -> ApplicationSnapshot.decode(records), change[0]);
        }
        List<LoanAuditEvent> duplicate = new ArrayList<>(events);
        duplicate.add(correction("E-duplicate", returned, LoanAuditEvent.Type.REVERSE_RETURN));
        assertThrows(IllegalArgumentException.class, () -> LoanAuditCodec.validate(snapshot.getLoans(), duplicate));
        List<LoanAuditEvent> repeatedIssue = new ArrayList<>(snapshot.getAuditEvents());
        repeatedIssue.add(LoanAuditEvent.issued(snapshot.getLoans().getFirst()));
        assertThrows(IllegalArgumentException.class, () -> LoanAuditCodec.validate(snapshot.getLoans(), repeatedIssue));
        List<LoanAuditEvent> repeatedReturn = new ArrayList<>(snapshot.getAuditEvents());
        repeatedReturn.add(LoanAuditEvent.returned(snapshot.getLoans().getFirst(), DAY, "Inspected = intact"));
        assertThrows(IllegalArgumentException.class,
                () -> LoanAuditCodec.validate(snapshot.getLoans(), repeatedReturn));
        LoanAuditEvent again = LoanAuditEvent.returned(
                snapshot.getLoans().getFirst(), DAY.plusDays(1), "Checked again");
        events.add(again);
        assertThrows(IllegalArgumentException.class, () -> LoanAuditCodec.validate(snapshot.getLoans(), events));
        List<LoanAuditEvent> pendingReturn = new ArrayList<>(snapshot.getAuditEvents());
        pendingReturn.add(undoLoan);
        assertThrows(IllegalArgumentException.class, () -> LoanAuditCodec.validate(snapshot.getLoans(), pendingReturn));
        List<LoanAuditEvent> redoReturn = new ArrayList<>(snapshot.getAuditEvents());
        redoReturn.add(undoReturn);
        redoReturn.add(again);
        LoanAuditCodec.validate(snapshot.getLoans(), redoReturn);
        Loan other = snapshot.getLoans().getLast();
        List<LoanAuditEvent> crossLoan = new ArrayList<>(snapshot.getAuditEvents());
        crossLoan.add(new LoanAuditEvent("E-wrong-owner", other.getId(), DAY.plusDays(1),
                LoanAuditEvent.Type.REVERSE_RETURN, other.getAssetId(), other.getBorrowerId(),
                returned.dueDate(), returned.faults(), returned.id(), "Wrong loan"));
        assertThrows(IllegalArgumentException.class, () -> LoanAuditCodec.validate(snapshot.getLoans(), crossLoan));
    }

    private LoanAuditEvent correction(String id, LoanAuditEvent original, LoanAuditEvent.Type type) {
        return new LoanAuditEvent(id, original.loanId(), DAY.plusDays(1), type, original.assetId(),
                original.borrowerId(), original.dueDate(), original.faults(), original.id(), "Mistaken operation");
    }

    private ApplicationSnapshot withAudit(ApplicationSnapshot snapshot, List<LoanAuditEvent> events) {
        return new ApplicationSnapshot(snapshot.getEquipment(), snapshot.getBorrowers(), snapshot.getLoans(),
                snapshot.getNotes(), snapshot.getFaults(), snapshot.getReservations(),
                snapshot.getWaitingRequests(), events);
    }

    @Test
    void run_failedSave_suppressesSuccessAndDoesNotPublishEvidenceToDisk(@TempDir Path directory) throws Exception {
        Path data = directory.resolve("live.properties");
        ApplicationSnapshot original = sample();
        original.write(data);
        LabLog app = LabLog.load(data);
        Files.delete(data);
        Files.createDirectory(data);
        Files.writeString(data.resolve("keep.txt"), "keep");
        String output = run(app, "return MIC\nloan-audit");
        assertTrue(output.contains("Changes could not be saved"));
        assertFalse(output.contains("Returned MIC"));
        assertFalse(output.contains("Loan audit:"));
        assertEquals("keep", Files.readString(data.resolve("keep.txt")));
    }

    @Test
    void write_invalidInjectedEvidence_preservesExistingDestination(@TempDir Path directory) throws Exception {
        ApplicationSnapshot original = sample();
        Path data = directory.resolve("existing.properties");
        original.write(data);
        String before = Files.readString(data);
        LoanAuditEvent issue = original.getAuditEvents().getFirst();
        ApplicationSnapshot invalid = withAudit(original, List.of(issue, issue));
        assertThrows(IOException.class, () -> invalid.write(data));
        assertEquals(before, Files.readString(data));
    }
}
