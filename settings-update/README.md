# Isolated settings runtime verification

This is a separate, test-only mod. Do not include it in a release or install it in a player profile. It checks the actual loaded Unyielding JAR on an otherwise empty disposable dedicated server. It does not need a graphical client or a logged-in account.

Build on the hosted runner with JDK 21 after assembling the final combined JAR:

```sh
gradle -p settings-update build -PcombinedJar=/absolute/path/Unyielding-1.1.10.jar --build-cache
```

Copy `settings-update/build/libs/settings-smoke-test-only-1.0.0.jar` into the isolated smoke server's `mods` folder alongside the combined JAR. Run `settingstest` from RCON after startup. Success returns `SETTINGSTEST PASS` with the assertion count and writes `settings-smoke-result.json` to the server working directory. Failure returns `SETTINGSTEST FAIL`, writes the failure, and logs the stack trace.

Coverage: `/config` and `/quests settings` opening and permissions; actual registered command execution and cached parses after live changes; all eight command feature switches and persisted settings; rejected non-operator edits; preservation of stored homes, warps, nicknames, recording flags, back history, teleport requests and cooldowns while disabled; map scale boundaries, invalid/stale edits, file persistence, operator/non-operator outgoing sync and wire round trip; day/night/weather authorization, allowlist, config and dimension restrictions; optional Open Parties and Claims integration when present. Tests use fake players with an outbound packet recorder. They restore configuration and fixtures in `finally` blocks.

The helper deliberately does not claim a real graphical client rendered the icons. Client receipt/reset/render tests belong to the separate client verification.
