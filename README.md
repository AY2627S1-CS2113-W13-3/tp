# LabLog

LabLog is a command-line application for laboratory technicians who manage loanable electronics equipment. The v1.0 scope covers equipment and borrower registration, loans and returns, and equipment history. Implementation is in progress.

## Borrower features

This increment adds the command handlers for issues #10 and #11:

* `add-borrower BORROWER_ID n/NAME p/PHONE e/EMAIL` registers a borrower, rejects duplicate IDs, and saves validated contact details.
* `loans BORROWER_ID` lists a registered borrower's active loans, ordered by due date and then asset ID. Returned loans are excluded, and a loan is overdue only after its due date.

Borrower IDs follow the form `A0123456X`: an uppercase letter, seven digits, and an uppercase letter. Names contain 1-100 characters, phone numbers contain exactly eight digits, and email addresses use `u.nus.edu`. All three prefixes are required, may appear in any order, and must occur only once.

The borrower commands have a standalone console while the team's shared command dispatcher is being developed. This console starts with an empty loan book and does not issue loans. The loan-listing handler accepts the application's shared loan book and equipment-name lookup; tests exercise it with active and returned loans. Connecting these handlers to the shared LabLog prompt remains an integration step.

## Setting up in IntelliJ IDEA

Prerequisite: JDK 25. Configure both the project SDK and Gradle JVM to use Java 25.

1. **Ensure JDK 25 is defined as an SDK**, as described [here](https://www.jetbrains.com/help/idea/sdk.html#set-up-jdk).
1. **Import the project _as a Gradle project_**, as described [here](https://se-education.org/guides/tutorials/intellijImportGradleProject.html).
1. **Verify the setup**: Run the Gradle `classes` task, then run `lablog.BorrowerCli.main()` using the repository root as the working directory. Set its program argument to `build/borrowers-demo.properties` for a disposable demonstration.

**Warning:** Keep the `src\main\java` folder as the root folder for Java files (i.e., don't rename those folders or move Java files to another folder outside of this folder path), as this is the default location some tools (e.g., Gradle) expect to find Java files.

## Running the borrower console

From the repository root in PowerShell:

```powershell
java -version
.\gradlew.bat classes
java -cp build/classes/java/main lablog.BorrowerCli build/borrowers-demo.properties
```

On macOS or Linux, use `./gradlew classes` for the build step; the Java command is the same. Ensure `java -version` reports Java 25.

Example input:

```text
add-borrower A0123456X n/Jamie Tan p/91234567 e/jamietan@u.nus.edu
loans A0123456X
```

Expected responses, following the `lablog>` prompts:

```text
Added borrower A0123456X: Jamie Tan.
Phone: 91234567 | Email: jamietan@u.nus.edu
No active loans for A0123456X - Jamie Tan.
```

End input with Ctrl+Z followed by Enter on Windows, or Ctrl+D on macOS/Linux. Restart with the same file to reload borrowers. Repeating the registration command will report a duplicate ID.

The file argument is optional; the default is `data/borrowers.properties`. The example uses `build/`, which Git ignores and Gradle `clean` removes. Use a location outside `build/` for records you want to retain, and do not commit borrower data. Invalid commands and failed saves do not register a borrower.

Use this explicit Java command for the borrower console. The existing Gradle `run` task has not been redirected to it.

## Build automation using Gradle

* This project uses Gradle for build automation and dependency management. It includes a basic build script as well (i.e. the `build.gradle` file).
* If you are new to Gradle, refer to the [Gradle Tutorial at se-education.org/guides](https://se-education.org/guides/tutorials/gradle.html).

## Testing

### JUnit and Checkstyle

Run the JUnit and Checkstyle checks with Java 25:

```powershell
.\gradlew.bat test checkstyleMain checkstyleTest
```

Borrower tests cover validation, duplicate IDs, persistence, failed saves, command syntax, loan filtering and ordering, overdue dates, and console output. See the [JUnit tutorial](https://se-education.org/guides/tutorials/junit.html) for background.

### Console tests

After compiling, run the recorded console cases with PowerShell 7:

```powershell
pwsh -File test/run-borrower-ui-tests.ps1
```

The runner uses a fresh demonstration file under `build/`, verifies exact output, and checks persistence across two separate Java processes. It stops on the first failure and writes the results to [the recorded transcript](test/borrower-ui-transcript.md). See the [test plan](test/ui-test-plan.md) for inputs and expected output. The optional `-Classes` argument selects an alternate compiled-class directory.

Run these console checks explicitly: the Gradle checks and GitHub Actions workflow do not invoke the PowerShell runner. Each run updates the transcript, so review that change before including it in a commit.

## Checkstyle

* A sample CheckStyle rule configuration is provided in this project.
* If you are new to Checkstyle, refer to the [Checkstyle Tutorial at se-education.org/guides](https://se-education.org/guides/tutorials/checkstyle.html).

## CI using GitHub Actions

The project uses [GitHub actions](https://github.com/features/actions) for CI. When you push a commit to this repo or PR against it, GitHub actions will run automatically to build and verify the code as updated by the commit/PR.

## Documentation

See [borrower implementation notes](docs/BorrowerImplementation.md) for shared-application integration and verification details. The repository's [User Guide](docs/UserGuide.md) and [Developer Guide](docs/DeveloperGuide.md) remain starter templates and do not yet document the complete application.

Steps for publishing documentation to the public: 
1. If you are using this project template for an individual project, go your fork on GitHub.<br>
   If you are using this project template for a team project, go to the team fork on GitHub.
1. Click on the `settings` tab.
1. Scroll down to the `GitHub Pages` section.
1. Set the `source` as `master branch /docs folder`.
1. Optionally, use the `choose a theme` button to choose a theme for your documentation.
