"""Checks the built CLI in fresh Java 25 processes using disposable borrower data."""

from pathlib import Path
import re
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent.parent
JAR = ROOT / "build/libs/lablog.jar"
GREETING = "LabLog borrower console. Use add-borrower or loans. Send EOF to exit.\n"


class CliSmokeTests(unittest.TestCase):
    """Verifies real console output, recovery, and persistence across restarts."""

    @classmethod
    def setUpClass(cls):
        version = subprocess.run(
            ["java", "-version"], capture_output=True, text=True, timeout=15, check=True
        )
        if not re.search(r'version "25(?:\.|\")', version.stdout + version.stderr):
            raise RuntimeError("Put Java 25 on PATH before running these checks.")
        if not JAR.is_file():
            raise RuntimeError("Build the executable JAR with the Gradle shadowJar task first.")

    def run_java(self, arguments, user_input):
        """Runs one session and checks its exit status and error stream."""
        if len(arguments) == 2 and arguments[0] == "-jar":
            with tempfile.TemporaryDirectory(prefix="main-smoke-", dir=ROOT / "build") as directory:
                return self.run_java([*arguments, str(Path(directory) / "lablog.properties")], user_input)
        result = subprocess.run(
            ["java", *arguments], input=user_input, capture_output=True,
            text=True, cwd=ROOT, timeout=20,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("", result.stderr)
        return result.stdout

    def borrower(self, file, user_input):
        return self.run_java(["-cp", str(JAR), "lablog.BorrowerCli", str(file)], user_input)

    def test_main_help_examples_and_recovery(self):
        commands = ("help", "loan", "return", "history", "add-borrower", "loans", "exit")
        user_input = "help\nhelp unknown\nhelp loan extra\n"
        user_input += "".join("help " + command + "\n" for command in commands) + "exit\n"
        output = self.run_java(["-jar", str(JAR)], user_input)
        for command in commands:
            self.assertIn("Example: " + command, output)
        self.assertIn("Common Version 1 commands:", output)
        self.assertIn("Error: Unknown command: unknown.", output)
        self.assertIn("Error: Use: help [COMMAND]", output)
        self.assertNotIn("Enter name:", output)

    def test_main_registration_history_and_unavailable_commands(self):
        output = self.run_java(["-jar", str(JAR)],
            "loan OSC-001 b/A0123456X d/2026-09-18\nloans A0123456X\n"
            "add-borrower A0123456X\n\nOSC 001\n"
            "OSC-001\nScope\nElectronics\nS1\nLab\nhistory OSC-001\nreturn OSC-001\nexit\n")
        self.assertEqual(2, output.count("is not available in this console"))
        self.assertIn("Error: Equipment OSC-001 was not found.", output)
        self.assertEqual(2, output.count("Error: Enter a single asset ID"))
        self.assertEqual(1, output.count("Equipment added to inventory."))
        self.assertIn("No history found for OSC-001 - Scope.", output)
        self.assertIn("Error: Equipment OSC-001 is not currently on loan.", output)

    def test_main_incomplete_registration(self):
        output = self.run_java(["-jar", str(JAR)], "OSC-001\nScope\n")
        self.assertIn("Equipment not added: incomplete input.", output)
        self.assertNotIn("Equipment added to inventory.", output)

    def test_main_equipment_loans_and_returns_survive_restart(self):
        with tempfile.TemporaryDirectory(prefix="restart-smoke-", dir=ROOT / "build") as directory:
            file = Path(directory) / "lablog.properties"
            args = ["-jar", str(JAR), str(file)]
            output = self.run_java(args, "OSC-001\nScope\nElectronics\nS1\nLab\n"
                "loan OSC-001 b/A0123456X d/2099-01-01\nexit\n")
            self.assertIn("Loaned OSC-001", output)
            output = self.run_java(args, "history OSC-001\nreturn OSC-001 faults/cracked screen\nexit\n")
            self.assertIn("A0123456X", output)
            self.assertIn("cracked screen", output)
            output = self.run_java(args, "history OSC-001\nreturn OSC-001\nexit\n")
            self.assertIn("cracked screen", output)
            self.assertIn("not currently on loan", output)

    def test_borrower_exact_output_and_restart(self):
        with tempfile.TemporaryDirectory(prefix="cli-smoke-", dir=ROOT / "build") as directory:
            file = Path(directory) / "borrowers.properties"
            output = self.borrower(file,
                "add-borrower A0123456X n/Jamie Tan p/91234567 e/jamie@u.nus.edu\n"
                "add-borrower A0123456X n/Other p/91234567 e/other@u.nus.edu\nloans A0123456X\n")
            self.assertEqual(GREETING
                + "lablog> Added borrower A0123456X: Jamie Tan.\n"
                + "Phone: 91234567 | Email: jamie@u.nus.edu\n"
                + "lablog> Error: Borrower ID A0123456X already exists. Choose a different borrower ID.\n"
                + "No changes saved.\nlablog> No active loans for A0123456X - Jamie Tan.\nlablog> ", output)
            self.assertEqual(GREETING
                + "lablog> No active loans for A0123456X - Jamie Tan.\nlablog> ",
                self.borrower(file, "loans A0123456X\n"))

    def test_borrower_help_preserves_file_and_handles_invalid_fields(self):
        with tempfile.TemporaryDirectory(prefix="cli-smoke-", dir=ROOT / "build") as directory:
            file = Path(directory) / "borrowers.properties"
            self.borrower(file, "add-borrower A0123456X n/Jamie Tan p/91234567 e/jamie@u.nus.edu\n")
            saved = file.read_bytes()
            output = self.borrower(file,
                "help\nhelp return\nhelp add-borrower\nhelp unknown\nhelp loan extra\n"
                "add-borrower A0123457X n/Alex p/123 e/alex@u.nus.edu\nloans A0123456X\n")
            self.assertIn("Example: return OSC-001\n", output)
            self.assertIn("Example: add-borrower A0123457X e/alexlim@u.nus.edu", output)
            self.assertIn("Error: Phone must contain exactly 8 digits.", output)
            self.assertIn("No active loans for A0123456X - Jamie Tan.", output)
            self.assertEqual(saved, file.read_bytes())


if __name__ == "__main__":
    unittest.main(verbosity=2)
