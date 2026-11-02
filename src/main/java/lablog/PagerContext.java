package lablog;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Derives compact pager breadcrumbs from fixed application headings, never record values. */
final class PagerContext {
    private static final Map<String, String> HELP_CATEGORIES = Map.ofEntries(
            Map.entry("== Equipment ==", "EQUIPMENT"),
            Map.entry("== Loans ==", "LOANS"),
            Map.entry("== Borrowers ==", "BORROWERS"),
            Map.entry("== Reservations ==", "RESERVATIONS"),
            Map.entry("== Faults ==", "FAULTS"),
            Map.entry("== Faults and maintenance ==", "FAULTS AND MAINTENANCE"),
            Map.entry("== Records ==", "RECORDS"),
            Map.entry("== Records and overview ==", "RECORDS AND OVERVIEW"),
            Map.entry("== General ==", "GENERAL"));

    private PagerContext() {
    }

    /**
     * Finds the result title and latest recognised section at or before the first visible row.
     * Only the first nonblank row can establish a result type, preventing table values and
     * recalled command text from changing the breadcrumb later in the result.
     *
     * @param renderedLines plain, already wrapped rows of one command result
     * @param offset index of the first visible result row; out-of-range values are clamped
     * @return a canonical breadcrumb, or RESULT when the result has no recognised heading
     */
    static String at(List<String> renderedLines, int offset) {
        if (renderedLines == null || renderedLines.isEmpty()) {
            return "RESULT";
        }
        int last = Math.max(0, Math.min(offset, renderedLines.size() - 1));
        String title = "RESULT";
        String section = "";
        boolean foundFirstLine = false;
        for (int index = 0; index <= last; index++) {
            String original = renderedLines.get(index);
            String line = original.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (!foundFirstLine) {
                title = titleOf(line);
                foundFirstLine = true;
                continue;
            }
            int indent = original.length() - original.stripLeading().length();
            String next = sectionOf(title, line, indent);
            if (!next.isEmpty()) {
                section = next;
            }
        }
        return section.isEmpty() ? title : title + " / " + section;
    }

    /** Identifies generated section headings using the same result-aware rules as breadcrumbs. */
    static boolean isSectionStart(List<String> lines, int index) {
        String title = lines.stream().map(String::strip).filter(line -> !line.isEmpty())
                .findFirst().map(PagerContext::titleOf).orElse("RESULT");
        String original = lines.get(index);
        int indent = original.length() - original.stripLeading().length();
        return !sectionOf(title, original.strip(), indent).isEmpty();
    }

    /** Matches complete generated title layouts and returns only canonical, safe labels. */
    private static String titleOf(String line) {
        if (line.equals("LabLog commands:")) {
            return "HELP";
        }
        if (line.matches("Help: [a-z][a-z-]{0,39}")) {
            return "HELP " + line.substring("Help: ".length()).toUpperCase(Locale.ROOT);
        }
        if (line.equals("LOAN / Issue equipment to a borrower")) {
            return "HELP LOAN";
        }
        if (line.matches("OVERVIEW / \\d{4}-\\d{2}-\\d{2}")) {
            return "OVERVIEW";
        }
        if (line.matches("EQUIPMENT / \\d+ units")) {
            return "EQUIPMENT";
        }
        if (line.startsWith("Asset ID: ")) {
            return "EQUIPMENT";
        }
        if (line.equals("Loan audit:") || line.matches("Loan audit for \\S+:")) {
            return "LOAN AUDIT";
        }
        if (line.startsWith("Active loans for ") && line.endsWith(":")) {
            return "ACTIVE LOANS";
        }
        if (line.startsWith("History for ") && line.endsWith(":")) {
            return "HISTORY";
        }
        if (line.equals("Reservations (inclusive dates):")) {
            return "RESERVATIONS";
        }
        if (line.matches("Available throughout \\d{4}-\\d{2}-\\d{2} through "
                + "\\d{4}-\\d{2}-\\d{2} \\(inclusive\\):")) {
            return "AVAILABLE EQUIPMENT";
        }
        if (line.equals("WAITING requests (not confirmed reservations):")) {
            return "WAITING REQUESTS";
        }
        if (line.equals("Recent commands (oldest first):")) {
            return "RECENT COMMANDS";
        }
        if (line.equals("Recalled (not executed):")) {
            return "RECALLED COMMAND";
        }
        // Some commands begin directly with a fixed table header rather than a title.
        return switch (line.replaceAll(" {2,}", "|")) {
        case "Asset ID|Name|Category|Model|Location|Availability",
                "Asset ID|Name|Category|Model|Location" -> "EQUIPMENT";
        case "Asset ID|Borrower ID|Due date|Status",
                "Asset ID|Equipment|Issued|Due|Overdue|Status" -> "ACTIVE LOANS";
        case "Date|Type|Details" -> "HISTORY";
        default -> "RESULT";
        };
    }

    /** Recognises sections only within their corresponding result and heading indentation. */
    private static String sectionOf(String title, String line, int indent) {
        if (title.equals("HELP") && indent <= 1) {
            return HELP_CATEGORIES.getOrDefault(line, "");
        }
        if (title.startsWith("HELP ") && indent >= 4 && indent <= 5) {
            return switch (line) {
            case "Usage" -> "USAGE";
            case "Example" -> "EXAMPLE";
            case "Required" -> "REQUIRED";
            case "Before issuing" -> "BEFORE ISSUING";
            default -> "";
            };
        }
        if (title.equals("OVERVIEW") && indent <= 1) {
            if (line.matches("OVERDUE LOANS \\(\\d+\\)")) {
                return "OVERDUE LOANS";
            }
            if (line.matches("TODAY'S RESERVATIONS \\(\\d+\\)")) {
                return "TODAY'S RESERVATIONS";
            }
            if (line.matches("SEVERE FAULTS \\(\\d+\\)")) {
                return "SEVERE FAULTS";
            }
        }
        return "";
    }
}
