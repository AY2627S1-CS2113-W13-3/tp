package lablog;

import java.util.ArrayList;
import java.util.List;

/** Keeps 20 recent submitted lines in memory; recall displays input without executing it. */
public class CommandHistory {
    private static final int LIMIT = 20;
    private final List<String> commands = new ArrayList<>();

    /** Returns display-only output, or null when the caller should dispatch the submitted command. */
    public String accept(String input) {
        String[] parts = input.strip().split("\\s+", 2);
        if (parts[0].equals("commands")) {
            if (parts.length != 1) {
                throw new IllegalArgumentException("Use: commands");
            }
            if (commands.isEmpty()) {
                return "No recent commands.";
            }
            StringBuilder result = new StringBuilder("Recent commands (oldest first):");
            for (int index = 0; index < commands.size(); index++) {
                result.append('\n').append(index + 1).append(": ").append(commands.get(index));
            }
            return result.toString();
        }
        if (parts[0].equals("recall")) {
            int index;
            try {
                index = parts.length == 2 ? Integer.parseInt(parts[1]) : 0;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Use: recall INDEX (from commands)");
            }
            if (index < 1 || index > commands.size()) {
                throw new IllegalArgumentException("No command at that index. Use commands to view recent input.");
            }
            return "Recalled (not executed):\n" + commands.get(index - 1)
                    + "\nCopy or edit this line, then submit it as a new command to execute.";
        }
        if (!input.isBlank() && !input.strip().equals("exit")) {
            if (commands.size() == LIMIT) {
                commands.remove(0);
            }
            commands.add(input.strip());
        }
        return null;
    }
}
