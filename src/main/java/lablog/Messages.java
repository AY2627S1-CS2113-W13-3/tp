package lablog;

/** Shared wording for console feedback so success and failure messages look consistent. */
final class Messages {
    /** Leads every successful change, e.g. "[OK] Copied OSC-001 to OSC-002."; failures start with "Error:". */
    static final String OK_PREFIX = "[OK] ";

    private Messages() {
    }

    /** Marks the result of a command that changed records as successful. */
    static String ok(String result) {
        return OK_PREFIX + result;
    }
}
