package lablog;

import java.util.ArrayList;
import java.util.List;

/** Extracts executable commands from the help hierarchy without treating explanations as commands. */
final class HelpText {
    private HelpText() {
    }

    /** Returns labelled examples for the existing command-execution assertions. */
    static List<String> examples(String text) {
        List<String> examples = new ArrayList<>();
        boolean inExamples = false;
        String command = "";
        for (String line : text.split("\n")) {
            if (line.startsWith("Help: ")) {
                command = line.substring(6);

            }
            if (line.startsWith("    ") && !line.startsWith("        ")) {
                inExamples = line.equals("    Example");
            } else if (inExamples && line.startsWith("        ") && !line.startsWith("            ")) {
                String value = line.strip();
                if (value.equals(command) || value.startsWith(command + " ")) {
                    examples.add("Example: " + value);
                }
            }
        }
        return examples;
    }
}
