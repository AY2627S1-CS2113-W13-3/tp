# Historical RepoSense authorship verification

For the current team-wide audit and outstanding evidence, see
[Contribution evidence](team/contribution-evidence.md). The counts below describe
the stated October revision only; they are not current totals.

## Local audit: Zeph (`zephtzk`)

Checked on 19 October 2026 against commit
`cba189183920ed238922d1ed057f3d1407c3ac05`. This audit concerns the borrower
enhancement (issues #10 and #11), not a claim to other team members' features.

The repository's configured Git author name is `zephtzk`, which matches the
GitHub account. Borrower implementation commits `d54e4e9` and `5de883b` and
supporting commits `8671ac7` and `d94e284` use that name. No alternate author
name was found for Zeph in the available Git history.

The following counts include blank lines and comments. They are local Git blame
counts, not RepoSense's graded code totals.

| File | Lines attributed to `zephtzk` | Other attribution |
| --- | ---: | --- |
| `src/main/java/lablog/Borrower.java` | 62 | None |
| `src/main/java/lablog/BorrowerBook.java` | 117 | None |
| `src/main/java/lablog/BorrowerCommands.java` | 135 | 2 lines by `YamYamui` |
| `src/main/java/lablog/BorrowerCli.java` | 59 | None |
| `src/test/java/lablog/BorrowerBookTest.java` | 67 | None |
| `src/test/java/lablog/BorrowerCommandsTest.java` | 129 | None |

The two lines in `BorrowerCommands` are the `help` switch case and its call to
`HelpCommands`, introduced by commit `6ae4d8a5`. Their attribution is consistent
with that teammate's change and has been preserved.

No `@@author` annotations or standalone configuration were added: the audited
code already has the expected Git attribution. Blanket file annotations would
incorrectly claim the teammate's help integration. AI-assisted code should remain
attributed to the student responsible for it, subject to the course rules.

## Repeating the local check

Run these read-only commands from the repository root:

```powershell
git config user.name
git log --all --format="%an <%ae>" | Sort-Object -Unique
git log --author=zephtzk --oneline
git blame --line-porcelain -- src/main/java/lablog/BorrowerCommands.java |
    Select-String '^author ' | Group-Object Line | Select-Object Count, Name
```

The first command checks the identity for future commits; changing it would not
repair existing commits. The log commands inspect recorded authors, and blame
shows the current line ownership. Repeat blame for each changed source or test
file after integration. Git metadata is evidence of attribution, not independent
proof of who originally wrote a line; confirm any disputed ownership with the
team before overriding it.

## Published dashboard check: still required

The course dashboard and its author configuration could not be retrieved from
the working environment on 19 October 2026. Therefore this audit does **not**
confirm the green-highlighted lines in the published report.

1. Open the [tP Code Dashboard](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/).
2. Find team `W13-3` and `zephtzk`, then open the `</>` code panel.
3. Check the reported branch, analysis dates, and latest analyzed revision so
   that the report includes the borrower commits.
4. Compare the highlighted borrower implementation and test lines with the local
   audit above. The two teammate-authored help lines should retain their owner.
5. If commits are missing because an author alias is unknown, inspect the
   [course author configuration](https://github.com/NUS-CS2113-AY2627-S1/tp-dashboard/blob/master/configs/author-config.csv)
   and send the missing recorded name to the professor, as the handout directs.
   A stale report or a different analyzed branch should be resolved before
   changing attribution.
6. If the team confirms actual line ownership differs from blame, annotate only
   the affected region using the official
   [RepoSense author-tag guide](https://reposense.org/ug/usingAuthorTags.html), then
   inspect the next generated report.

RepoSense analyzes committed content. The new design documentation will only
appear in a remote report after it is committed, published to the analyzed
repository/branch, and the report refreshes. This historical check did not commit or push.

If a standalone `_reposense/config.json` is later needed, follow the
[official configuration reference](https://reposense.org/ug/configFiles.html).
Its author list must include all team members to be analyzed, and the course
report can be configured to ignore standalone settings. Do not add a personal-only
author list to this shared repository as a workaround for an unverified dashboard
problem.
