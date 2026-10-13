package lablog;

import java.util.Scanner;

/**
 * Main entry point for the LabLog command-line application.
 */
public class LabLog {
    private final Inventory inventory = new Inventory();

    /**
     * Starts LabLog and adds one equipment unit using five lines of user input.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        new LabLog().run();
    }

    /**
     * Displays the welcome message and adds an equipment unit to the inventory.
     */
    public void run() {
        System.out.println("Welcome to LabLog!");
        System.out.println("Manage your laboratory equipment and loans here.");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                Equipment equipment = readEquipment(scanner);
                inventory.addEquipment(equipment);
                System.out.println("Equipment added to inventory.");
            }
        }
    }

    /**
     * Prompts for the five details needed to create an equipment unit.
     *
     * @param scanner reads the user's input
     * @return equipment unit created from the user's input
     */
    private Equipment readEquipment(Scanner scanner) {
        System.out.println("Enter asset ID:");
        String assetId = scanner.nextLine();

        System.out.println("Enter name:");
        String name = scanner.nextLine();

        System.out.println("Enter category:");
        String category = scanner.nextLine();

        System.out.println("Enter model:");
        String model = scanner.nextLine();

        System.out.println("Enter location:");
        String location = scanner.nextLine();

        return new Equipment(assetId, name, category, model, location);
    }
}
