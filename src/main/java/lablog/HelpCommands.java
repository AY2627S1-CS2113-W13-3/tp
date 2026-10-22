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
            + "fault ASSET_ID d/DATE desc/DETAILS - Report a persistent equipment fault.\n"
            + "list [filters] [sort/FIELD] - Filter and sort equipment (main console).\n"
            + "edit-equipment ASSET_ID [n/NAME] [c/CATEGORY] [m/MODEL] [l/LOCATION] - "
            + "Edit equipment details (main console).\n"
            + "move-equipment ASSET_ID[,ASSET_ID...] l/LOCATION - Move selected equipment together (main console).\n"
            + "find ASSET_ID - Show one registered equipment unit (main console).\n"
            + "active-loans - List all active loans and overdue status (main console).\n"
            + "loan ASSET_ID b/BORROWER_ID d/DUE_DATE - Issue a loan (main console).\n"
            + "return ASSET_ID [faults/DESCRIPTION] - Record a return (main console).\n"
            + "return ASSET_ID ASSET_ID ... - Return multiple units (no faults/, main console).\n"
            + "history ASSET_ID - View equipment history (main console).\n"
            + "add-borrower BORROWER_ID n/NAME p/PHONE e/EMAIL - Register a borrower (both consoles).\n"
            + "edit-borrower BORROWER_ID [n/NAME] [p/PHONE] [e/EMAIL] - Edit contacts (both consoles).\n"
            + "export FILE - Export a complete snapshot to a new file (main console).\n"
            + "import FILE --replace - Replace all records from a validated snapshot (main console).\n"
            + "commands | recall INDEX - View recent input without executing it (both consoles).\n"
            + "loans BORROWER_ID - View active loans (both consoles).\n"
            + "exit - Close the main console.\n"
            + "add ASSET_ID [n/NAME c/CATEGORY m/MODEL l/LOCATION] - Register equipment (main console).\n"
            + "copy SOURCE_ASSET_ID NEW_ASSET_ID - Copy details to a new equipment unit (main console).\n"
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
                    + "Snapshot format 3 includes structured faults and current history notes.\n"
                    + "Future domain records and audit data require a schema update; "
                    + "unsupported fields are rejected when decoding format 2, rather than silently dropped.";
        case "import":
            return "Replace all current records after complete snapshot validation.\n"
                    + "Format: import FILE --replace\nExample: import backups/lablog.properties --replace\n"
                    + "Fields: FILE is a format-2 snapshot; --replace explicitly requests replacement,\n"
                    + "not merging.\n"
                    + "Invalid data or a failed save leaves live and saved records unchanged. "
                    + "Export first for a backup.\n"
                    + "Unknown format-2 properties are rejected; the error names the unsupported field.";

        case "commands":
            return "Show the last 20 submitted command lines in this console session.\n"
                    + "Format: commands\nExample: commands\nFields: None. Oldest retained line is index 1.\n"
                    + "Includes invalid input; excludes blank lines, exit, commands, recall and wizard answers.\n"
                    + "History is kept only in memory and may include contact details.";
        case "recall":
            return "Display a recent command for copying or editing; never execute it automatically.\n"
                    + "Format: recall INDEX\nExample: recall 1\nFields: INDEX comes from commands.\n"
                    + "Copy or edit the displayed command, then submit it explicitly to execute.";
        case "add":
            return "Register equipment explicitly.\nFormat: add ASSET_ID n/NAME c/CATEGORY m/MODEL l/LOCATION\n"
                    + "Example: add OSC-001 n/Oscilloscope c/Electronics m/S1 l/Lab 1\n"
                    + "  Register a unit with all four required fields, in any order.\n"
                    + "Example: add OSC-001\n  Start the four-prompt registration wizard instead.\n"
                    + "Unknown commands and bare asset IDs are rejected; use add to begin registration.";
        case "copy":
            return "Copy descriptive details from a registered unit under a new asset ID.\n"
                    + "Format: copy SOURCE_ASSET_ID NEW_ASSET_ID\n"
                    + "Example: copy OSC-001 OSC-002\n"
                    + "  Copy OSC-001's name, category, model, and location to OSC-002.\n"
                    + "The source remains unchanged. The new ID must be unique.\n"
                    + "Loans and history are not copied; the new unit starts without either record.\n"
                    + "Fields: SOURCE_ASSET_ID must identify a registered unit; NEW_ASSET_ID must not already exist.";
        case "edit-equipment":
            return "Edit descriptive details of a registered equipment unit.\n"
                    + "Format: edit-equipment ASSET_ID [n/NAME] [c/CATEGORY] [m/MODEL] [l/LOCATION]\n"
                    + "Example: edit-equipment OSC-001 l/Lab 2 n/Digital oscilloscope\n"
                    + "Fields: Supply at least one field, once each. Omitted values remain unchanged.\n"
                    + "The asset ID stays unchanged so loans and history keep their equipment reference.\n"
                    + "Equipment validation applies to all values; failed edits change nothing.";
        case "move-equipment":
            return "Move selected equipment units to one storage location.\n"
                    + "Format: move-equipment ASSET_ID[,ASSET_ID...] l/LOCATION\n"
                    + "Example: move-equipment OSC-001,MIC-002 l/Lab 3\n"
                    + "Fields: Select one or more distinct registered asset IDs and one non-empty location.\n"
                    + "All IDs and the location are validated before any unit changes; "
                    + "an invalid batch changes nothing.\n"
                    + "The completed batch is saved to the LabLog data file.";
        case "reservation":
        case "fault":
            return "Report a fault for registered equipment.\n"
                    + "Format: fault ASSET_ID d/YYYY-MM-DD desc/DESCRIPTION\n"
                    + "Example: fault OSC-001 d/2026-10-14 desc/Cracked screen\n"
                    + "Use a real date and a non-empty single-line description. Dates may be past or future.\n"
                    + "The fault ID is shown on success and in history; reports survive restarts and exports.\n"
                    + "Reports do not change lending availability. Severity and repair commands are not included.";
        case "reservation":
        case "maintenance":
            return "Record a dated equipment history note.\nFormat: " + parts[1]
                    + " ASSET_ID d/YYYY-MM-DD desc/DESCRIPTION\nExample: " + parts[1]
                    + " OSC-001 d/2026-10-14 desc/Inspection scheduled\n"
                    + "  Add a note for registered equipment; view it with history OSC-001.\n"
                    + "Notes do not change loan availability and are saved in the main application snapshot.";
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
            return "Return one or more equipment units on today's date (main console).\n"
                    + "Format: return ASSET_ID [faults/DESCRIPTION] (single unit)\n"
                    + "Batch format: return ASSET_ID ASSET_ID ...\n"
                    + "Example: return OSC-001 faults/cracked screen\n"
                    + "  Return one unit and record a cracked screen on its loan.\n"
                    + "Example: return OSC-001\n"
                    + "  Return one unit with no new faults reported.\n"
                    + "Example: return OSC-001 OSC-002\n"
                    + "  Return both units together with no new faults reported.\n"
                    + "Choose an example for currently loaned equipment; "
                    + "the same loan cannot be returned twice.\n"
                    + "Fields: Each asset ID must appear once and have exactly one active loan.\n"
                    + "If any selection or return date is invalid, no selected loan is changed.\n"
                    + "faults/ accepts a non-empty, single-line description for one unit only.\n"
                    + "Return units with reported faults individually.\n"
                    + "Omitting faults/ records no new faults; it does not clear existing faults.";
        case "list":
            return "List registered equipment and current availability (main console).\n"
                    + "Format: list [category/VALUE] [location/VALUE] [status/available|on-loan] [sort/FIELD]\n"
                    + "Example: list category/Electronics location/Lab A status/available\n"
                    + "  Match category and location exactly, ignoring case; combine any filters.\n"
                    + "  Status is derived from active loans; reservation and fault notes do not change it.\n"
                    + "Fields: category/ and location/ match exact values; status/ is available or on-loan.\n"
                    + "Supported sort fields: asset-id, name, category, model, location, availability.\n"
                    + "Sorting is ascending and case-insensitive; ties use asset ID, then exact ID case.\n"
                    + "Sorting does not change inventory order. Combine filters with sort/FIELD.";
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
