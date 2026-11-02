"""Checks the built CLI in fresh Java 25 processes using disposable application and borrower data."""

from datetime import date, timedelta
from pathlib import Path
import os
import re
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent.parent
JAR = Path(os.environ.get("LABLOG_TEST_JAR", ROOT / "build/libs/lablog.jar"))
GREETING = ("LabLog borrower console. Use add-borrower or loans. Type exit or send EOF to exit.\n"
            "LabLog works best in a terminal at least 100 columns wide and 30 rows high.\n")


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
        return self.run_java(
            ["-cp", str(JAR), "lablog.BorrowerCli", str(file)], user_input
        )

    def test_main_help_examples_and_recovery(self):
        commands = ("help", "add", "retire", "copy", "edit-equipment", "move-equipment", "list", "find",
                    "active-loans", "overview", "why-unavailable", "loan", "renew", "lost", "return", "loan-audit",
                    "reverse-loan", "reverse-return", "history",
                    "reserve", "reservations", "edit-reservation", "cancel-reservation", "available",
                    "wait-request", "waiting-requests", "reservation", "maintenance", "fault", "repair",
                    "add-borrower", "edit-borrower", "loans", "export", "import", "commands", "recall", "exit")
        user_input = "help\nhelp unknown\nhelp loan extra\n"
        user_input += "".join("help " + command + "\n" for command in commands) + "exit\n"
        output = self.run_java(["-jar", str(JAR)], user_input)
        for command in commands:
            self.assertRegex(output, rf"(?m)^    Example\n(?:[ ]{{8,}}.*\n|\n)*?"
                             rf"[ ]{{8}}{re.escape(command)}(?:[ \n]|$)")
        self.assertIn("LabLog commands:", output)
        self.assertIn("Error: Unknown command: unknown.", output)
        self.assertIn("Error: Use: help [COMMAND]", output)
        self.assertNotIn("Enter name:", output)

    def test_main_registration_history_and_unavailable_commands(self):
        output = self.run_java(["-jar", str(JAR)],
            "loan OSC-001 b/A0123456X d/2099-09-18\nloans A0123456X\n"
            "add-borrower A0123456X\n\nOSC 001\n"
            "add OSC-001\nScope\nElectronics\nS1\nLab\nhistory OSC-001\nreturn OSC-001\nexit\n")
        self.assertNotIn("is not available in this console", output)
        self.assertIn("Error: Borrower A0123456X was not found.", output)
        self.assertIn("Error: Equipment OSC-001 was not found.", output)
        self.assertEqual(2, output.count("Error: Unknown command. Use help"))
        self.assertEqual(1, output.count("Equipment added to inventory."))
        self.assertIn("No history found for OSC-001 - Scope.", output)
        self.assertIn("Error: Equipment OSC-001 is not currently on loan.", output)

    def test_main_incomplete_registration(self):
        output = self.run_java(["-jar", str(JAR)], "add OSC-001\nScope\n")
        self.assertIn("Equipment not added: incomplete input.", output)
        self.assertNotIn("Equipment added to inventory.", output)

    def test_main_equipment_loans_and_returns_survive_restart(self):
        with tempfile.TemporaryDirectory(prefix="restart-smoke-", dir=ROOT / "build") as directory:
            file = Path(directory) / "lablog.properties"
            args = ["-jar", str(JAR), str(file)]
            output = self.run_java(args,
                "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                "add OSC-001\nScope\nElectronics\nS1\nLab\n"
                "loan OSC-001 b/A0123456X d/2099-01-01\nexit\n")
            self.assertIn("Loan recorded.\n  Asset     OSC-001\n  Borrower  A0123456X", output)
            output = self.run_java(args, "history OSC-001\nreturn OSC-001 faults/cracked screen\nexit\n")
            self.assertIn("A0123456X", output)
            self.assertIn("cracked screen", output)
            output = self.run_java(args, "history OSC-001\nreturn OSC-001\nexit\n")
            self.assertIn("cracked screen", output)
            self.assertIn("not currently on loan", output)

    def test_integrated_features_survive_export_import_and_restart(self):
        """Exercise documented workflows through the packaged application, including schema 12."""
        with tempfile.TemporaryDirectory(prefix="integrated-smoke-", dir=ROOT / "build") as directory:
            live = Path(directory) / "live.properties"
            backup = Path(directory) / "export with spaces.properties"
            imported = Path(directory) / "imported.properties"
            args = ["-jar", str(JAR), str(live)]
            today = date.today()
            due = today + timedelta(days=7)
            renewed = today + timedelta(days=14)
            start = today + timedelta(days=30)
            end = today + timedelta(days=31)
            output = self.run_java(args,
                "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                "add A n/Scope c/Electronics m/S1 l/Lab\n"
                "copy A B\n"
                "add C n/Probe c/Booked m/P1 l/Lab\n"
                f"loan A B b/A0123456X d/{due}\n"
                f"renew A d/{renewed}\n"
                "lost A\nreturn B\n"
                f"reserve C b/A0123456X from/{start} to/{end}\n"
                f"wait-request b/A0123456X category/Booked from/{start} to/{end}\n"
                f"fault B d/{today} s/severe desc/Cracked screen\nexit\n")
            self.assertNotIn("Error:", output)
            fault = re.search(r"F-[A-Za-z0-9-]+", output.split("lablog> ", 1)[1])
            self.assertIsNotNone(fault, output)
            output = self.run_java(args,
                f"repair {fault.group()} d/{today} desc/Replaced screen\n"
                f'export "{backup}"\nexit\n')
            self.assertNotIn("Error:", output)
            self.assertIn("Resolved fault", output)
            self.assertTrue(backup.is_file())
            queries = ("loan-audit\nhistory A\nhistory B\nreservations\n"
                       "waiting-requests\nlist status/lost\nexit\n")
            before = live.read_bytes()
            expected = self.run_java(args, queries)
            self.assertEqual(before, live.read_bytes(), "Queries must not rewrite the snapshot")
            for detail in ("LOAN", "RETURN", str(renewed), "LOST", "Replaced screen", "WAITING", str(start)):
                self.assertIn(detail, expected)
            imported_args = ["-jar", str(JAR), str(imported)]
            result = self.run_java(imported_args, f'import "{backup}" --replace\nexit\n')
            self.assertIn("All current records were replaced", result)
            self.assertNotIn("Error:", result)
            self.assertEqual(expected, self.run_java(imported_args, queries))

    def test_borrower_exact_output_and_restart(self):
        with tempfile.TemporaryDirectory(prefix="cli-smoke-", dir=ROOT / "build") as directory:
            file = Path(directory) / "borrowers.properties"
            output = self.borrower(file,
                "add-borrower A0123456X n/Jamie Tan p/91234567 e/jamie@u.nus.edu\n"
                "add-borrower A0123456X n/Other p/91234567 e/other@u.nus.edu\nloans A0123456X\n")
            self.assertEqual(GREETING
                + "lablog> [OK] Added borrower A0123456X: Jamie Tan.\n"
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
            self.assertIn("\n        return OSC-001\n", output)
            self.assertIn("\n        add-borrower A0123457X e/alexlim@u.nus.edu", output)
            self.assertIn("Error: Phone must contain exactly 8 digits.", output)
            self.assertIn("No active loans for A0123456X - Jamie Tan.", output)
            self.assertEqual(saved, file.read_bytes())

    def test_recall_only_displays_until_resubmitted(self):
        with tempfile.TemporaryDirectory(prefix="recall-smoke-", dir=ROOT / "build") as directory:
            file = Path(directory) / "lablog.properties"
            args = ["-jar", str(JAR), str(file)]
            output = self.run_java(args,
                "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                "edit-borrower A0123456X n/Updated\nrecall 2\ncommands\nexit\n")
            self.assertEqual(1, output.count("Updated borrower A0123456X: Updated."))
            self.assertIn("Recalled (not executed):", output)
            output = self.run_java(args, "commands\nloans A0123456X\nexit\n")
            self.assertIn("No recent commands.", output)
            self.assertIn("A0123456X - Updated", output)


    def test_main_missing_snapshot_count_stops_before_accepting_commands(self):
        with tempfile.TemporaryDirectory(prefix="corrupt-startup-", dir=ROOT / "build") as directory:
            file = Path(directory) / "lablog.properties"
            args = ["-jar", str(JAR), str(file)]
            self.run_java(args,
                "add OSC-001 n/Scope c/Electronics m/S1 l/Lab\n"
                "add-borrower A0123456X n/Jamie p/91234567 e/jamie@u.nus.edu\n"
                "maintenance OSC-001 d/2026-01-01 desc/Calibrated\n"
                "reserve OSC-001 b/A0123456X from/2099-01-01 to/2099-01-02\nexit\n")
            saved = file.read_bytes()
            damaged = re.sub(rb"(?m)^borrower\.count=[^\r\n]*\r?\n", b"", saved)
            self.assertNotEqual(saved, damaged)
            file.write_bytes(damaged)
            result = subprocess.run(["java", *args],
                input="add OSC-002 n/Probe c/Electronics m/S2 l/Lab\nexit\n",
                capture_output=True, text=True, encoding="utf-8", cwd=ROOT, timeout=30)
            self.assertIn("Error: Cannot load LabLog data.", result.stderr)
            self.assertIn("borrower.count", result.stderr)
            self.assertNotIn("lablog>", result.stdout)
            self.assertEqual(damaged, file.read_bytes())

    def test_main_reserved_equipment_ids_are_rejected_without_saving(self):
        with tempfile.TemporaryDirectory(prefix="reserved-ids-", dir=ROOT / "build") as directory:
            file = Path(directory) / "lablog.properties"
            args = ["-jar", str(JAR), str(file)]
            self.run_java(args, "add OSC-001 n/Scope c/Electronics m/S1 l/Lab\nexit\n")
            saved = file.read_bytes()
            output = self.run_java(args,
                "add A/B n/Scope c/Electronics m/S1 l/Lab\n"
                "add A,B\nScope\nElectronics\nS1\nLab\n"
                "copy OSC-001 A/B\ncopy OSC-001 A,B\nexit\n")
            self.assertEqual(4, output.count("Error: Asset ID must not contain"))
            self.assertNotIn("[OK]", output)
            self.assertEqual(saved, file.read_bytes())
            restarted = self.run_java(args, "find OSC-001\nexit\n")
            self.assertIn("Asset ID: OSC-001", restarted)
            self.assertEqual(saved, file.read_bytes())


if __name__ == "__main__":
    unittest.main(verbosity=2)
