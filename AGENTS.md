# Unyielding release numbering

Keep the pending CurseForge version for every test build, hotfix and GitHub artifact until the user publishes that version on CurseForge or explicitly requests a bump. Use commits and checksums to distinguish builds. The user explicitly requested 1.1.9 for the origin creative flight slider update.

Split independent checks between the user's PC and GitHub Actions when that saves time. Use the PC for checks against the actual CurseForge test profile, assets, installed mod IDs, and delivered JAR. Prefer GitHub-hosted runners for heavy builds and automated game tests.

## Preserve release progress

Treat the newest source branch, pending release version, and test JAR as the baseline for every follow-up. Before restoring or repairing a feature, inspect the current Git branch, working-tree changes, and installed JAR version. Preserve all uncommitted work and newer features.

Never use an older JAR or stale checkout as the base for a current update. Port or reimplement only the missing feature into the newest source, then build forward from that source. Do not roll back the release version or overwrite newer artifacts to recover an older feature. Keep the pending version unchanged until CurseForge publication or an explicit request to bump it.

For the current 1.1.8 Pokémon editor restoration, keep the local source changes and test JAR in place. Do not discard or replace them during later work. The user requested that no tests be run for this restoration.

The 1.1.8 build with Substitute and UwU IV Rebalance is SHA-256 638ded0f08d806939ed3ce8efafae841386907ec4a33687568a1b00565acc518. Its base was the Mega editor build (88ba5554...). Carry forward iv-substitute-update, the raid settings UI, the egg handler, and the restored unyielding:substitute species and battle hooks. Use one raid reward injection: existing tier 1-6 odds and the UwU tier 7 bonus. Do not package the standalone UwU raid mixin alongside it.
