package lablog;

/**
 * Represents a registered borrower with immutable identifying and contact details.
 */
public class Borrower {
    private final String borrowerId;
    private final String name;
    private final String phone;
    private final String email;

    /**
     * Creates a borrower after validating the user guide's required fields.
     *
     * @throws IllegalArgumentException If any field is invalid.
     */
    public Borrower(String borrowerId, String name, String phone, String email) {
        validateId(borrowerId);
        if (name == null || name.isBlank() || name.length() > 100
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Name must contain 1-100 characters and no control characters.");
        }
        if (phone == null || !phone.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("Phone must contain exactly 8 digits.");
        }
        if (email == null || !email.matches("[A-Za-z0-9_+%-]+(?:\\.[A-Za-z0-9_+%-]+)*@u\\.nus\\.edu")) {
            throw new IllegalArgumentException("Email must use the domain u.nus.edu, for example jamietan@u.nus.edu.");
        }
        this.borrowerId = borrowerId;
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    /**
     * Checks the nine-character NUS student identifier without changing its case.
     *
     * @param borrowerId Identifier supplied by the user.
     * @throws IllegalArgumentException If the identifier is not a letter, seven digits, and a letter.
     */
    public static void validateId(String borrowerId) {
        if (borrowerId == null || !borrowerId.matches("[A-Z][0-9]{7}[A-Z]")) {
            throw new IllegalArgumentException("Borrower ID must be a NUS Student ID, for example A0123456X.");
        }
    }

    public String getBorrowerId() {
        return borrowerId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }
}
