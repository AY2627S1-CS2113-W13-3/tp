package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

/** Checks filtering, ordering, overdue boundaries, and console dispatch. */
class ActiveLoanCommandsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void execute_mixedLoans_listsOnlyActiveInDueOrder() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);
        LoanBook loans = new LoanBook();
        ActiveLoanCommands commands = new ActiveLoanCommands(loans, clock);
        assertEquals("No active loans.", commands.execute("active-loans"));
        Loan today = new Loan("B", "A0123456X", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14));
        Loan overdue = new Loan("A", "A0123457X", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 13));
        Loan returned = new Loan("C", "A0123456X", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 12));
        returned.markReturned(LocalDate.of(2026, 10, 12));
        loans.addLoan(today);
        loans.addLoan(returned);
        loans.addLoan(overdue);
        String expected = "Asset ID | Borrower ID | Due date | Status\n"
                + "A | A0123457X | 2026-10-13 | OVERDUE\nB | A0123456X | 2026-10-14 | On time";
        assertEquals(expected, TableText.compact(commands.execute("active-loans")));
        assertThrows(IllegalArgumentException.class, () -> commands.execute("active-loans extra"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LabLog(new Inventory(), loans, List::of, clock)
                .run(new Scanner("active-loans\nexit\n"), new PrintStream(output));
        assertTrue(TableText.compact(output.toString()).contains(expected));
        assertEquals(3, loans.getLoans().size());
    }

    @Test
    void execute_overdueOnly_filtersBoundariesAndUsesAssetIdForTies() {
        LoanBook loans = new LoanBook();
        Loan later = new Loan("B", "A0123456X", TODAY.minusDays(7), TODAY.minusDays(1));
        Loan tied = new Loan("A", "A0123457X", TODAY.minusDays(7), TODAY.minusDays(1));
        Loan earlier = new Loan("Z", "A0123456X", TODAY.minusDays(7), TODAY.minusDays(2));
        Loan today = new Loan("TODAY", "A0123456X", TODAY.minusDays(7), TODAY);
        Loan future = new Loan("FUTURE", "A0123456X", TODAY.minusDays(7), TODAY.plusDays(1));
        Loan returned = new Loan("RETURNED", "A0123456X", TODAY.minusDays(7), TODAY.minusDays(3));
        returned.markReturned(TODAY.minusDays(2), "Dented case");
        for (Loan loan : List.of(later, today, returned, tied, future, earlier)) {
            loans.addLoan(loan);
        }
        List<Loan> original = loans.getLoans();
        ActiveLoanCommands commands = new ActiveLoanCommands(loans, CLOCK);

        String expected = "Asset ID | Borrower ID | Due date | Status\n"
                + "Z | A0123456X | 2026-10-12 | OVERDUE\n"
                + "A | A0123457X | 2026-10-13 | OVERDUE\n"
                + "B | A0123456X | 2026-10-13 | OVERDUE";
        assertEquals(expected, TableText.compact(commands.execute("active-loans overdue")));
        assertEquals(expected, TableText.compact(commands.execute("  active-loans\t overdue  ")));
        assertEquals(original, loans.getLoans());
        assertEquals(List.of(true, true, false, true, true, true), loans.getLoans().stream()
                .map(Loan::isActive).toList());
        assertEquals(TODAY.minusDays(2), returned.getReturnDate());
        assertEquals("Dented case", returned.getReturnFaults());
    }

    @Test
    void execute_noOverdueLoans_distinguishesEmptyModeAndExcludesToday() {
        LoanBook loans = new LoanBook();
        ActiveLoanCommands commands = new ActiveLoanCommands(loans, CLOCK);
        assertEquals("No overdue loans.", commands.execute("active-loans overdue"));
        assertEquals("No active loans.", commands.execute("active-loans"));
        loans.addLoan(new Loan("TODAY", "A0123456X", TODAY.minusDays(7), TODAY));
        loans.addLoan(new Loan("FUTURE", "A0123456X", TODAY.minusDays(7), TODAY.plusDays(1)));
        Loan returned = new Loan("RETURNED", "A0123456X", TODAY.minusDays(7), TODAY.minusDays(2));
        returned.markReturned(TODAY.minusDays(1));
        loans.addLoan(returned);

        assertEquals("No overdue loans.", commands.execute("active-loans overdue"));
        assertEquals("Asset ID | Borrower ID | Due date | Status\n"
                + "TODAY | A0123456X | 2026-10-14 | On time\n"
                + "FUTURE | A0123456X | 2026-10-15 | On time", TableText.compact(commands.execute("active-loans")));
    }

    @Test
    void execute_invalidArguments_explainsUsageWithoutChangingLoans() {
        LoanBook loans = new LoanBook();
        Loan loan = new Loan("A", "A0123456X", TODAY.minusDays(7), TODAY.minusDays(1));
        loans.addLoan(loan);
        ActiveLoanCommands commands = new ActiveLoanCommands(loans, CLOCK);
        String[] invalidInputs = {
            null, "", " ", "loans", "active-loans extra", "active-loans OVERDUE",
            "active-loans overdue extra", "active-loans overdue overdue"
        };

        for (String input : invalidInputs) {
            assertEquals("Use: active-loans [overdue]",
                    assertThrows(IllegalArgumentException.class, () -> commands.execute(input)).getMessage());
        }
        assertEquals(List.of(loan), loans.getLoans());
        assertTrue(loan.isActive());
        assertNull(loan.getReturnDate());
    }

    @Test
    void execute_sameInstantInDifferentZones_usesClocksLocalDate() {
        LoanBook loans = new LoanBook();
        loans.addLoan(new Loan("A", "A0123456X", TODAY.minusDays(7), TODAY));
        Instant instant = Instant.parse("2026-10-14T16:30:00Z");
        Clock utc = Clock.fixed(instant, ZoneOffset.UTC);
        Clock singapore = Clock.fixed(instant, ZoneOffset.ofHours(8));

        assertEquals("No overdue loans.", new ActiveLoanCommands(loans, utc).execute("active-loans overdue"));
        assertEquals("Asset ID | Borrower ID | Due date | Status\nA | A0123456X | 2026-10-14 | OVERDUE",
                TableText.compact(new ActiveLoanCommands(loans, singapore).execute("active-loans overdue")));
    }

    @Test
    void execute_returnAfterFirstListing_disappearsFromOverdueResults() {
        LoanBook loans = new LoanBook();
        Loan loan = new Loan("A", "A0123456X", TODAY.minusDays(7), TODAY.minusDays(1));
        loans.addLoan(loan);
        ActiveLoanCommands commands = new ActiveLoanCommands(loans, CLOCK);
        assertEquals("Asset ID | Borrower ID | Due date | Status\nA | A0123456X | 2026-10-13 | OVERDUE",
                TableText.compact(commands.execute("active-loans overdue")));

        loan.markReturned(TODAY);

        assertEquals("No overdue loans.", commands.execute("active-loans overdue"));
        assertEquals("No active loans.", commands.execute("active-loans"));
        assertEquals(List.of(loan), loans.getLoans());
        assertEquals(TODAY, loan.getReturnDate());
    }

    @Test
    void run_invalidThenOverdueAndReturn_usesSharedLoansAndRecovers() {
        Inventory inventory = new Inventory();
        inventory.addEquipment(new Equipment("A", "Scope", "Electronics", "S1", "Lab A"));
        LoanBook loans = new LoanBook();
        Loan overdue = new Loan("A", "A0123456X", TODAY.minusDays(7), TODAY.minusDays(1));
        loans.addLoan(overdue);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new LabLog(inventory, loans, List::of, CLOCK).run(new Scanner("active-loans overdue extra\n"
                + "active-loans overdue\nreturn A\nactive-loans overdue\nexit\n"), new PrintStream(output));

        assertTrue(output.toString().contains("Error: Use: active-loans [overdue]"));
        assertTrue(TableText.compact(output.toString()).contains("A | A0123456X | 2026-10-13 | OVERDUE"));
        assertTrue(output.toString().contains("No overdue loans."));
        assertFalse(overdue.isActive());
        assertEquals(TODAY, overdue.getReturnDate());
        assertEquals(1, inventory.getEquipmentUnits().size());
        assertEquals(List.of(overdue), loans.getLoans());
    }

    @Test
    void execute_reloadedLoans_reportsOverdueWithoutChangingSnapshot(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("records.properties");
        LabLogStore store = new LabLogStore(file);
        store.getInventory().addEquipment(new Equipment("A", "Scope", "Electronics", "S1", "Lab A"));
        Loan overdue = new Loan("A", "A0123456X", TODAY.minusDays(7), TODAY.minusDays(1));
        store.getLoans().addLoan(overdue);
        store.save();
        String originalFile = Files.readString(file);
        LabLogStore reloaded = new LabLogStore(file);

        assertEquals("Asset ID | Borrower ID | Due date | Status\nA | A0123456X | 2026-10-13 | OVERDUE",
                TableText.compact(new ActiveLoanCommands(reloaded.getLoans(), CLOCK).execute("active-loans overdue")));
        assertEquals(originalFile, Files.readString(file));
        assertTrue(reloaded.getLoans().getLoans().get(0).isActive());
        assertNull(reloaded.getLoans().getLoans().get(0).getReturnDate());
    }
}
