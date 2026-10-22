package lablog;

import java.util.logging.Logger;

/** Displays command guidance without accessing or changing application records. */
public class HelpCommands {
    /** Records help requests at FINE level without including raw user input. */
    private static final Logger LOGGER = Logger.getLogger(HelpCommands.class.getName());
    private static final String USAGE = "Use: help [COMMAND]";
    private static final String SUMMARY = "Common Version 1 commands:\n"
            + "help [COMMAND] - Show common commands or detailed help.\n"
            + "reservation|maintenance ASSET_ID d/DATE desc/DETAILS - Record a history note (main console).\n"
            + "fault ASSET_ID d/DATE [s/SEVERITY] desc/DETAILS - Report a persistent equipment fault.\n"
            + "repair FAULT_ID d/DATE desc/DETAILS - Record a repair and resolve one fault.\n"
            + "list - List registered equipment and availability (main console).\n"
            + "edit-equipment ASSET_ID [n/NAME] [c/CATEGORY] [m/MODEL] [l/LOCATION] - "
            + "Edit equipment details (main console).\n"
            + "find ASSET_ID - Show one registered equipment unit (main console).\n"
            + "active-loans - List all active loans and overdue status (main console).\n"
            + "loan ASSET_ID b/BORROWER_ID d/DUE_DATE - Issue a loan (main console).\n"
            + "return ASSET_ID [faults/DESCRIPTION] - Record a return (main console).\n"
            + "history ASSET_ID - View equipment history (main console).\n"
            + "add-borrower BORROWER_ID n/NAME p/PHONE e/EMAIL - Register a borrower (both consoles).\n"
            + "edit-borrower BORROWER_ID [n/NAME] [p/PHONE] [e/EMAIL] - Edit contacts (both consoles).\n"
            + "export FILE - Export a complete snapshot to a new file (main console).\n"
            + "loans BORROWER_ID - View active loans (both consoles).\n"
            + "exit - Close the main console.\n"
            + "add ASSET_ID [n/NAME c/CATEGORY m/MODEL l/LOCATION] - Register equipment (main console).\n"
            + "Use help COMMAND for syntax, fields, and usage examples.";

    /**
     * Returns the common command list or guidance for exactly one command name.
     *
     * @param input complete help command; command names are case-sensitive
     * @return read-only command guidance
     * @throws IllegalArgumentException if the syntax or command name is invalid
     */
    public String execute(String input) {
        String guidance = describe(input);
        String separator = "............................................................";
        return "\n" + separator + "\n" + guidance + "\n" + separator + "\n";
    }

    /** Resolves help text before adding presentation so validation errors stay concise. */
    private String describe(String input) {
        String[] parts = input == null ? new String[0] : input.strip().split("\\s+");
        if (parts.length == 0 || parts.length > 2 || !parts[0].equals("help")) {
            LOGGER.fine("Rejected help request: invalid syntax");
            throw new IllegalArgumentException(USAGE);
        }
        if (parts.length == 1) {
            LOGGER.fine("Displaying command summary");
            return SUMMARY;
        }
        LOGGER.fine("Looking up detailed command help");
        switch (parts[1]) {
        case "edit-borrower":
            return "Edit a registered borrower's contacts; the ID and loan references stay unchanged.\n"
                    + "Format: edit-borrower BORROWER_ID [n/NAME] [p/PHONE] [e/EMAIL]\n"
                    + "Example: edit-borrower A0123456X p/92345678\n"
                    + "Fields: Supply at least one field, once each. Omitted values remain unchanged.\n"
                    + "The same validation as add-borrower applies. Failed edits change nothing.";
        case "export":
            return "Export supported equipment, borrowers, loans and history notes.\n"
                    + "Format: export FILE\nExample: export backups/lablog.properties\n"
                    + "Fields: FILE must be a new destination; spaces and enclosing double quotes are accepted.\n"
                    + "Snapshot format 5 includes fault severity, repairs and current history notes.\n"
                    + "Future domain records and audit data require a schema update; "
                    + "unsupported fields are rejected.";
        case "add":
            return "Register equipment explicitly.\nFormat: add ASSET_ID n/NAME c/CATEGORY m/MODEL l/LOCATION\n"
                    + "Example: add OSC-001 n/Oscilloscope c/Electronics m/S1 l/Lab 1\n"
                    + "  Register a unit with all four required fields, in any order.\n"
                    + "Example: add OSC-001\n  Start the four-prompt registration wizard instead.\n"
                    + "Unknown commands and bare asset IDs are rejected; use add to begin registration.";
        case "edit-equipment":
            return "Edit descriptive details of a registered equipment unit.\n"
                    + "Format: edit-equipment ASSET_ID [n/NAME] [c/CATEGORY] [m/MODEL] [l/LOCATION]\n"
                    + "Example: edit-equipment OSC-001 l/Lab 2 n/Digital oscilloscope\n"
                    + "Fields: Supply at least one field, once each. Omitted values remain unchanged.\n"
                    + "The asset ID stays unchanged so loans and history keep their equipment reference.\n"
                    + "Equipment validation applies to all values; failed edits change nothing.";
        case "fault":
            return "Report a fault for registered equipment.\n"
                    + "Format: fault ASSET_ID d/YYYY-MM-DD [s/SEVERITY] desc/DESCRIPTION\n"
                    + "Example: fault OSC-001 d/2026-10-14 s/severe desc/Cracked screen\n"
                    + "Use a real date and a non-empty single-line description. Dates may be past or future.\n"
                    + "The fault ID is shown on success and in history; reports survive restarts and exports.\n"
                    + "Severity: minor, moderate, or severe (case-insensitive); omission means unspecified.\n"
                    + "Put s/ before desc/; repeated fields are rejected.\n"
                    + "Severe faults block new loans immediately, even for future report dates.\n"
                    + "Returns remain allowed and do not clear faults. Use repair to resolve an identified fault.";
        case "repair":
            return "Record a completed repair and resolve one unresolved fault.\n"
                    + "Format: repair FAULT_ID d/YYYY-MM-DD desc/DETAILS\n"
                    + "Example: repair F-123 d/2026-10-22 desc/Replaced cracked screen\n"
                    + "Replace F-123 with the fault ID shown by fault reporting or equipment history.\n"
                    + "The date must be on or after the report and no later than today in the local time zone.\n"
                    + "Details must be non-empty and single-line; supply each field once in the shown order.\n"
                    + "Unknown IDs and already resolved faults are rejected without changes.\n"
                    + "History retains the report and a dated repair entry. Other severe faults still block loans.";
        case "reservation":
        case "maintenance":
            return "Record a dated equipment history note.\nFormat: " + parts[1]
                    + " ASSET_ID d/YYYY-MM-DD desc/DESCRIPTION\nExample: " + parts[1]
                    + " OSC-001 d/2026-10-14 desc/Inspection scheduled\n"
                    + "  Add a note for registered equipment; view it with history OSC-001.\n"
                    + "Notes do not change loan availability and are held only for this session.";
        case "active-loans":
            return "List active loans across all borrowers.\nFormat: active-loans\nExample: active-loans\n"
                    + "  Show asset ID, borrower ID, due date, and overdue status in due-date order.\n"
                    + "Returned loans are excluded. Loans due today are not overdue.";
        case "help":
            return "Show common commands or detailed help for one command.\n"
                    + "Format: help [COMMAND]\n"
                    + "Example: help loan\n"
                    + "  Show loan syntax, fields, and a worked example without issuing a loan.\n"
                    + "Example: help\n"
                    + "  Show the concise command list.\n"
                    + "Example: help return\n"
                    + "  Show return examples with and without reported faults.\n"
                    + "Fields: COMMAND is optional; omit it for the concise list.";
        case "loan":
            return "Issue a loan for an available equipment item.\n"
                    + "Format: loan ASSET_ID b/BORROWER_ID d/DUE_DATE\n"
                    + "Example: loan OSC-001 b/A0123456X d/2026-09-18\n"
                    + "  Lend OSC-001 to borrower A0123456X with a due date of 18 September 2026.\n"
                    + "  Substitute the actual asset ID, borrower ID, and due date when issuing a loan.\n"
                    + "Fields: ASSET_ID identifies the equipment; b/ identifies the borrower; "
                    + "d/ gives the due date in YYYY-MM-DD format.\n"
                    + "Register the borrower with add-borrower before issuing a loan.";
        case "return":
            return "Record an equipment return on today's date (main console).\n"
                    + "Format: return ASSET_ID [faults/DESCRIPTION]\n"
                    + "Example: return OSC-001 faults/cracked screen\n"
                    + "  Return the unit and record a cracked screen on its loan.\n"
                    + "Example: return OSC-001\n"
                    + "  Return the unit with no new faults reported.\n"
                    + "Choose one example for a unit that is currently on loan; "
                    + "the same loan cannot be returned twice.\n"
                    + "Fields: ASSET_ID identifies equipment with exactly one active loan; "
                    + "faults/ optionally records a non-empty, single-line damage description.\n"
                    + "Omitting faults/ records no new faults; it does not clear existing faults.";
        case "list":
            return "List registered equipment and current availability (main console).\n"
                    + "Format: list\nExample: list\n"
                    + "  Show asset IDs, names, categories, models, locations, and availability.\n"
                    + "Availability is derived from active loans and severe faults.\n"
                    + "Fields: None.";
        case "find":
            return "Show one registered equipment unit by its exact asset ID.\n"
                    + "Format: find ASSET_ID\n"
                    + "Example: find OSC-001\n"
                    + "  Display the unit's name, category, model, location, and current availability.\n"
                    + "Fields: ASSET_ID is the case-sensitive identifier of registered equipment.\n"
                    + "Unknown IDs produce an error. This lookup does not change records.";
        case "history":
            return "Show an equipment item's records, oldest first (main console).\n"
                    + "Format: history ASSET_ID\n"
                    + "Example: history OSC-001\n"
                    + "  After registering OSC-001, show its records, including active and returned loans.\n"
                    + "  If no records have been supplied, report no history found.\n"
                    + "Fields: ASSET_ID is the case-sensitive identifier of registered equipment.";
        case "add-borrower":
            return "Register a borrower and save their contact details (both consoles).\n"
                    + "Format: add-borrower BORROWER_ID n/NAME p/PHONE e/EMAIL\n"
                    + "Example: add-borrower A0123456X n/Jamie Tan p/91234567 e/jamietan@u.nus.edu\n"
                    + "  Register Jamie Tan, including a name containing a space.\n"
                    + "Example: add-borrower A0123457X e/alexlim@u.nus.edu n/Alex Lim p/92345678\n"
                    + "  Register another borrower with the required fields in a different order.\n"
                    + "Fields: BORROWER_ID is an uppercase letter, seven digits, and an uppercase letter; "
                    + "n/ is a name of 1-100 characters; p/ is an eight-digit phone number; "
                    + "e/ is an email at u.nus.edu.\n"
                    + "All three prefixes are required exactly once, in any order. IDs must be unique.";
        case "loans":
            return "Show a borrower's active loans, ordered by due date and asset ID (both consoles).\n"
                    + "Format: loans BORROWER_ID\n"
                    + "Example: loans A0123456X\n"
                    + "  After registering Jamie Tan, show Jamie's active loans and overdue status.\n"
                    + "Example: loans A0123457X\n"
                    + "  After registering Alex Lim, show Alex's active loans. "
                    + "If none exist, report no active loans.\n"
                    + "Fields: BORROWER_ID identifies a registered borrower.\n"
                    + "Returned loans are excluded. A loan is overdue only after its due date.";
        case "exit":
            return "Close the main console.\nFormat: exit\nExample: exit\n"
                    + "  Enter exit at the initial main prompt to end the session.\nFields: None.\n"
                    + "In the borrower console, type exit or send EOF to exit.";
        default:
            LOGGER.fine("Rejected help request: unknown command");
            throw new IllegalArgumentException("Unknown command: " + parts[1] + ". Use help for the command list.");
        }
    }
}
