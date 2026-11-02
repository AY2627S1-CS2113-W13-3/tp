# Final submission checklist

Owner: team release coordinator; review with another team member.
Main deadline: **3 November 2026, 14:00 Singapore**.
Demo deadline: **5 November 2026, 14:00 Singapore** (two days later).
Confirm the actual Canvas assignments and settings: Canvas was unavailable here.
Course evidence: supplied Week 12 Project PDF (v2.1 submission section),
Constraints PDF (Single-File, File-Size, Java-Version, External-Software), and
Deliverables PDF (source/documentation consistency and Demo). Do not use this
checklist as evidence of a completed submission.

## Before freezing final source

- [ ] Merge reviewed readiness PRs into develop with required CI passing.
- [ ] Obtain class-wide JLine approval forum link or teaching-team confirmation.
- [ ] Resolve exact JLine/native redistribution notices and include them in JAR.
- [ ] Verify final UG, DG, website and PPPs against the actual final features.
- [ ] Select the final release commit and record its full SHA. Apply the team's
      approved develop-to-master/release process; do not move existing tags.

## Rebuild and verify after all merges

- [ ] On that exact commit run the commands in test/verification-report.md with
      Java 25. Record OS/JDK, test totals, result and CI URLs afresh.
- [ ] Inspect build/libs/lablog.jar manifest: Main-Class, version 2.1 and native
      access attribute. Inspect bundled dependencies and licence resources.
- [ ] Run the single JAR from a fresh writable local folder using Java 25 only.
      Confirm add, loan, return, restart and editable text storage; no server,
      installer, extra JAR download or other Java installation is needed.
- [ ] Check size below 100 MB, compute SHA-256 and create the Canvas-named copy:
      `[CS2113-W13-3][LabLog].jar`. Verify hashes match. The current build copy is
      baseline evidence only and must be regenerated after packaging changes.
- [ ] Rehearse native terminal pager, resize, narrow layout and EOF on available
      target OSes; explicitly record untested platforms. Do not infer manual
      cross-platform verification from the CI matrix.

## Human release/submission actions

- [ ] Publish the approved final GitHub v2.1 release with the exact verified JAR.
      Download it afresh and compare its hash with the Canvas copy.
- [ ] Confirm release tag source, manifest, UG/DG and live Pages match. Current
      latest release is v2.0 while Pages identifies v2.1 (2 November audit).
- [ ] Review every required team/individual Canvas file and its exact filename
      against the assignment. Review final PDF page limits and rendering.
- [ ] One designated member uploads the team JAR; another checks downloaded
      bytes, filename and Canvas timestamp/receipt before the main deadline.
- [ ] Each member checks their own required individual submissions and receipt.
- [ ] Preserve final hashes, receipts, source SHA and release URL. Avoid needless
      reuploads: the supplied course instructions use the latest submission time.
- [ ] Respect the stated 30-day post-deadline code freeze; seek teaching-team
      permission for code changes during that period where required.
- [ ] Record and upload the demo from the submitted executable by its separate
      deadline; inspect the playable uploaded video and submission receipt.

No item above is automatically completed by merging this documentation PR.
