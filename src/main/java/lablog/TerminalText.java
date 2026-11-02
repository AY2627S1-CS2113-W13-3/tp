package lablog;

/** Protects console output from control characters in input and diagnostic messages. */
final class TerminalText {
    private TerminalText() {
    }

    /** Allows tabs as command whitespace while rejecting terminal control characters. */
    static boolean hasUnsafeControls(String text) {
        return text.chars().anyMatch(c -> Character.isISOControl(c) && c != '\t');
    }

    /** Renders controls in diagnostic messages as visible Unicode escapes. */
    static String escapeControls(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        text.chars().forEach(c -> {
            if (Character.isISOControl(c)) {
                result.append(String.format("\\u%04X", c));
            } else {
                result.append((char) c);
            }
        });
        return result.toString();
    }
}
