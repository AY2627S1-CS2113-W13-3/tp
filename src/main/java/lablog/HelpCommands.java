package lablog;

/** Displays command guidance without accessing or changing application records. */
public class HelpCommands {
    private static final String USAGE = "Use: help [COMMAND]";
    private static final String SUMMARY = "Common Version 1 commands:\n"
            + "help [COMMAND] - Show common commands or detailed help.\n"
            + "loan ASSET_ID b/BORROWER_ID d/DUE_DATE - Issue a loan (not yet connected).\n"
            + "return ASSET_ID [faults/DESCRIPTION] - Record a return (main console).\n"
            + "history ASSET_ID - View equipment history (main console).\n"
            + "add-borrower BORROWER_ID n/NAME p/PHONE e/EMAIL - Register a borrower (borrower console).\n"
            + "loans BORROWER_ID - View active loans (borrower console).\n"
            + "exit - Close the main console.\n"
            + "To register equipment, enter its asset ID at the main prompt and follow the prompts.\n"
            + "Use help COMMAND for syntax, fields, and an example.";

    /**
     * Returns the common command list or guidance for exactly one command name.
     *
     * @param input complete help command; command names are case-sensitive
     * @return read-only command guidance
     * @throws IllegalArgumentException if the syntax or command name is invalid
     */
    public String execute(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+");
        if (parts.length == 0 || parts.length > 2 || !parts[0].equals("help")) {
            throw new IllegalArgumentException(USAGE);
        }
        if (parts.length == 1) {
            return SUMMARY;
        }
        switch (parts[1]) {
        case "help":
            return "Show common commands or detailed help for one command.\n"
                    + "Format: help [COMMAND]\n"
                    + "Example: help loan\n"
                    + "Fields: COMMAND is optional; omit it for the concise list.";
        case "loan":
            return "Issue a loan for an available equipment item.\n"
                    + "Format: loan ASSET_ID b/BORROWER_ID d/DUE_DATE\n"
                    + "Example: loan OSC-001 b/A0123456X d/2026-09-18\n"
                    + "Fields: ASSET_ID identifies the equipment; b/ identifies the borrower; "
                    + "d/ gives the due date in YYYY-MM-DD format.\n"
                    + "Loan creation is not yet connected to either console.";
        case "return":
            return "Record an equipment return on today's date (main console).\n"
                    + "Format: return ASSET_ID [faults/DESCRIPTION]\n"
                    + "Example: return OSC-001 faults/cracked screen\n"
                    + "Fields: ASSET_ID identifies equipment with exactly one active loan; "
                    + "faults/ optionally records a non-empty, single-line damage description.\n"
                    + "Omitting faults/ records no new faults; it does not clear existing faults.";
        case "history":
            return "Show an equipment item's records, oldest first (main console).\n"
                    + "Format: history ASSET_ID\n"
                    + "Example: history OSC-001\n"
                    + "Fields: ASSET_ID is the case-sensitive identifier of registered equipment.";
        case "add-borrower":
            return "Register a borrower and save their contact details (borrower console).\n"
                    + "Format: add-borrower BORROWER_ID n/NAME p/PHONE e/EMAIL\n"
                    + "Example: add-borrower A0123456X n/Jamie Tan p/91234567 e/jamietan@u.nus.edu\n"
                    + "Fields: BORROWER_ID is an uppercase letter, seven digits, and an uppercase letter; "
                    + "n/ is a name of 1-100 characters; p/ is an eight-digit phone number; "
                    + "e/ is an email at u.nus.edu.\n"
                    + "All three prefixes are required exactly once, in any order. IDs must be unique.";
        case "loans":
            return "Show a borrower's active loans, ordered by due date and asset ID (borrower console).\n"
                    + "Format: loans BORROWER_ID\n"
                    + "Example: loans A0123456X\n"
                    + "Fields: BORROWER_ID identifies a registered borrower.\n"
                    + "Returned loans are excluded. A loan is overdue only after its due date.";
        case "exit":
            return "Close the main console.\nFormat: exit\nExample: exit\nFields: None.\n"
                    + "In the borrower console, send EOF to exit instead.";
        default:
            throw new IllegalArgumentException("Unknown command: " + parts[1] + ". Use help for the command list.");
        }
    }
}
