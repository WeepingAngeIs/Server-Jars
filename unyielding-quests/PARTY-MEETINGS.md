# Protected party meeting points

Commands: `/partymeet`, `/partymeet set`, `/partymeet go`, `/partymeet clear`.

Travel is opt-in and uses Unyielding's own teleport policy with Open Parties and Claims. Other teleport restriction mods require separate verification.

Server opt-in: `[party_meeting] enabled = true` in the existing Essentials config. Default is disabled, preserving existing server/rank policy. To grant all members access set `allow_all_members = true`; otherwise grant `unyieldingquests.partymeet` through the server's active NeoForge permission handler. Operators are the default permission only, not a bypass for membership or claims. A provider denial overrides the default. Do not assume a command-only rank plugin implements NeoForge permissions.

Only current party officers/owners may set or clear. Both setter and visitor must be online for current permission and player-specific claim checks. Every visit checks membership and permission, setter's continued officer role, dimension allowlist, both players' OPAC access, loaded destination/neighbour chunks, world border, collision, fluids/hazards and solid floor. Same-dimension only. Missing OPAC or incompatible APIs deny travel. No access grants, chunk loading/search or private-home/back-history copying. A point stays stored while disabled; disbanded party IDs cannot be selected by commands. Data is separate server-only SavedData and never part of public team sync.

`cooldown_seconds` defaults to 60; `dimensions` defaults to the Overworld. Both are server settings. `/partymeet` help explains use. This initial implementation is command-based, not a visual selector. Claims must remain loaded by normal gameplay. The conservative full-access rule can deny visitors who only have limited interaction exceptions; it deliberately does not treat those exceptions as teleport permission.

Validation scope: disposable two-player candidate with OPAC 0.33.0 and isolated regressions. Full modpack and production permission providers need separate verification. API contract: https://thexaero.github.io/open-parties-and-claims/javadoc/xaero/pac/common/server/claims/protection/api/IChunkProtectionAPI.html .
