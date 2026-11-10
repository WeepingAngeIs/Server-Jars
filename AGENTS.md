# Unyielding release numbering

Keep the pending CurseForge version for every test build, hotfix and GitHub artifact until the user publishes that version on CurseForge or explicitly requests a bump. Use commits and checksums to distinguish builds. The current pending version is 1.1.8 following CurseForge 1.1.7.

Split independent checks between the user's PC and GitHub Actions when that saves time. Use the PC for checks against the actual CurseForge test profile, assets, installed mod IDs, and delivered JAR. Prefer GitHub-hosted runners for heavy builds and automated game tests.
