# Historical release verification: 2 November 2026

## Dependency-free replacement

Verified locally on 2 November 2026 on Java 25.0.4: compilation, main/test
Checkstyle, and 38 focused tests passed. After final tab/style fixes, the six
pager tests and Checkstyle passed again; the executable JAR rebuilt successfully.
A short packaged help/list/exit run passed. A captured-output Java probe exercised
next/back/quit and EOF through the injected interactive implementation, with and
without NO_COLOR; the colour-disabled transcript contained no ANSI escapes.
The JAR contains no JLine classes. Physical-terminal appearance was not manually
verified. Probe artifacts are under build/ and are untracked.

The results below describe
the previous implementation and must not be used as evidence for this replacement.
Full regression, broad packaged workflows and cross-platform terminal checks are
deferred until publication preparation. Current launches require no native-access
flag and the replacement does not bundle JLine.

## Historical scope and baseline

Fetched upstream before creating isolated branch `chore/release-verification`.
Verified source: `daff5f9acd73693303d5fc22e827fcacc4053ccc` (develop,
PR156 UI refresh, PR157 verification fixes and PR159 pager navigation).
Upstream master remains `5c6c12d`. These are baseline results, not evidence
for a future merged release. No release, tag or Canvas submission was changed.

## Executed checks

Windows, Temurin OpenJDK 25.0.4+7, Gradle wrapper 9.6.1:

| Check | Result |
| --- | --- |
| `gradlew.bat clean check shadowJar --console=plain` | PASS: 350 JUnit tests, zero failures/errors/skips; main/test Checkstyle |
| `python test/run-cli-smoke-tests.py` | PASS: ten packaged checks including restart, export/import, invalid data and command recovery |
| `pwsh -File test/run-borrower-ui-tests.ps1` | PASS: both packaged borrower cases |
| `python release/prepare-demo.py JAR NEW_SNAPSHOT` | PASS in local Windows Temp: 20 units and five fictional borrowers |
| Manifest inspection | Main-Class lablog.LabLog; Implementation-Version 2.1; Enable-Native-Access ALL-UNNAMED |
| Single bundled executable | build/libs/lablog.jar: 1,095,093 bytes, below 100 MB |
| Canvas filename copy | `[CS2113-W13-3][LabLog].jar`; byte-identical SHA-256 below |

Baseline JAR SHA-256:
`ff400516dc2825ee5cec384baa6faf07a804c2ddd0ac5bafd5cee5fee65fe935`.
Generated JARs, demo snapshots and reports under build/ are intentionally untracked.
Rebuilding after any source or packaging change invalidates this hash.

The JAR runs using `java -jar` on Java 25 without a separate classpath,
installer or server. Runtime dependencies are bundled. Source stores records in
local UTF-8 properties text (format 12), not a DBMS. The smoke suite verifies
persistence and reload. Native libraries used by JLine are included in the JAR;
this does not establish native terminal compatibility on every OS.

The demo setup initially exposed borrower validation mistakes, which were fixed
before the successful run. Some repeated saves in the OneDrive worktree failed
with atomic replacement/file-access errors; the same seed passed outside
OneDrive. Use an unsynchronised writable local folder and rehearse there.
Do not claim concurrent or shared-file use is supported.

## Dependency and external evidence

Resolved runtime dependencies: JLine reader, terminal, terminal-jni and transitive
native, all 4.4.6. Cached parent POM declares BSD licence and links BSD-3-Clause.
The baseline fat JAR and all four cached dependency JARs contain no files named
LICENSE/NOTICE/COPYING. Before distribution, obtain the exact upstream copyright
and licence text (including any native-component notices) and bundle it; the
licence declaration alone does not settle redistribution obligations.
The attempted exact-version upstream tag/licence lookup did not yield evidence.
No teaching-team class approval for JLine was found in repository documentation.
A permissive licence is not evidence of course approval: obtain a forum approval
link or written teaching-team confirmation before marking this item complete.

Authenticated GitHub read-only audit on 2 November:
latest published release is v2.0 targeting master, asset lablog.jar 167,604 bytes.
Live Pages home, UserGuide.html and DeveloperGuide.html return HTTP 200 and
contain v2.1. The guide pages also mention v2.0 historically; this substring alone
is not a stale-version defect. Live documentation and the latest published binary
are not yet an aligned final v2.1 deliverable. No release binary was replaced.

CI configuration runs Gradle check with Java 25 on Ubuntu/macOS/Windows, but
currently does not build shadowJar or execute the separate smoke scripts.
These local results do not claim remote CI success or manual macOS/Linux terminal
verification. Check PR CI before merge, then rerun release packaging on final HEAD.
Canvas is not connected here; assignment settings, uploads and receipts remain
unverified. See [the final checklist](../release/readiness-checklist.md) and
[the demo plan](../release/demo-plan.md).

## Reproduce

Use Java 25, Python 3 and PowerShell 7 from repository root:

```powershell
java -version
.\gradlew.bat clean check shadowJar --console=plain
python test/run-cli-smoke-tests.py
pwsh -File test/run-borrower-ui-tests.ps1
python release/prepare-demo.py build/libs/lablog.jar "$env:TEMP/lablog-new-demo.properties"
Copy-Item -LiteralPath build/libs/lablog.jar -Destination 'build/libs/[CS2113-W13-3][LabLog].jar'
Get-FileHash -LiteralPath 'build/libs/[CS2113-W13-3][LabLog].jar' -Algorithm SHA256
```

Choose a fresh demo path each time. On macOS/Linux use ./gradlew and a writable
local snapshot path; the borrower runner still requires PowerShell 7.
