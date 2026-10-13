# Project context

This repository is a starter template for a greenfield Java project used in an introductory software engineering course in an undergraduate computer science program. Students use it as the starting point for their own projects.

# Default user context

Unless the user says otherwise, assume that you are assisting a student working on a project in this repository. If the user identifies themselves as an instructor or another project stakeholder, adapt your response to that role.

# Student profile

* Prior knowledge: Basic Java and OOP concepts.
* Level of programming experience: [to be filled]
* IDE and level of expertise: [to be filled]

# Guidance for interacting with users

* Explain the rationale for significant actions: what you did and why.
* Keep explanations brief but instructive, supporting learning through responsible use of AI. For example:

  * When suggesting a Git command, briefly explain what it does.
  * Add explanatory Javadoc comments to all classes and to nontrivial methods and fields when their purpose or behavior is not obvious.
  * Make generated code as self-explanatory as possible, and include explanatory comments where they improve understanding.
  * When faced with a design choice, choose the simplest option that is sufficient for the requirements, while briefly explaining relevant more advanced alternatives.

# Project-specific requirements

## Java version:

Ensure that Java 25 is used when running the application or build tasks. On macOS, use `sdk use java 25.0.3.fx-zulu` to switch to Java 25 if needed.

## Git

Use lightweight tags unless the user requests an annotated tag.
When proposing or creating a commit message, include enough detail to explain the rationale for the change.
Do not commit or push unless explicitly asked.

### Commit messages

Follow the [SE-EDU Git commit message conventions](https://se-education.org/guides/conventions/git.html):

* Give every commit a meaningful subject. Aim for 50 characters or fewer; do not exceed 72 characters.
* Write the subject in the imperative mood, capitalize its first word, and do not end it with a period.
* Add a scope or category prefix when useful, such as `Inventory: Add equipment units` or `chore: Document branch naming rules`. Capitalize the first word after the prefix.
* For non-trivial commits, include a body after a blank line and wrap it at 72 characters.
* Use the body to explain what changed and why, not how. Give enough context for a reader to judge the change without reading the diff.
* Structure the body as: current situation (present tense), why it needs to change, the change (imperative), why this approach was chosen, and any other relevant context.

### Branch naming

Follow [Conventional Branch 1.1.0](https://conventionalbranch.org/) for new branches. Use lowercase names in the form `<type>/<description>`, with hyphens between words and no spaces or underscores. Include a zero-padded issue number when the work has one, using `issue-NN` before the short description. For example: `feature/issue-08-add-equipment-unit`.

Use these purpose prefixes:

* `feature/` for new features
* `bugfix/` for bug fixes
* `hotfix/` for urgent fixes
* `release/` for release preparation
* `chore/` for maintenance and documentation

Keep descriptions concise and descriptive. Mainline branches such as `main`, `master`, and `develop` do not use a prefix. Prefer the full prefixes listed above over the site's short aliases (`feat/` and `fix/`) for consistency in this project.
