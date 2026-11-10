# Unyielding 1.1.8 TEST: manual Lucky Spin and custom map icons

Test JAR: `releases/Unyielding-1.1.8-test.jar`. Install on both client and server, replacing the old Unyielding JAR. Restart Minecraft. No separate Mawonna resource pack is required.

Inventory now has a Lucky Spin button with a horizontal prize reel. Daily login, quest bonus, and reward-table spins are banked for manual redemption. Daily and quest odds remain separate. A real Cobblemon starter-selection check blocks redemption before starter selection. One-use tokens reject duplicate spin requests; saved balances survive reloads. Existing automatically granted prizes are retained.

Custom Xaero artwork now selects the bundled texture without requiring a generated model icon or an external definition entry. Existing Mawonna portraits and all model/species/walk assets are preserved.

Validation: 28 unit tests; 237 runtime assertions; ten actual client screenshots including the production reel; seven applied Xaero mixin selection cases with no model/icon manager. Server and client hosted checks passed. The final reel screenshot was visually reviewed after correcting a background-blur issue. Live map rendering in the full user modpack still needs the user's in-game test; this is a TEST build, not a claim of full-pack playtesting.

Run: https://github.com/WeepingAngeIs/unyielding-mod/actions/runs/42895285667
SHA256: `c76304bb8db8bd6367fe0c4a7e4354d83a8a0e90c64e25bcbd42dbbbd8458b43`

Version stays in the requested 1.1.8 line. The TEST installation uses filename `Unyielding-1.1.8.jar`; the internal build label is `1.1.8-test`. Earlier GitHub releases are preserved.
