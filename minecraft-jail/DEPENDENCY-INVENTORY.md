# Extraction dependency inventory

Baseline: `dc3699ab04e775bd772c38b9ff24646e0791f81a`.

| Dependency | Extraction treatment |
| --- | --- |
| 39 classes in `dev.unyielding.quests.jail` | Moved to `dev.unyielding.jail`; behavior preserved by normalized-source equality. |
| Four jail mixins | Moved to `dev.unyielding.jail.mixin` and a dedicated required mixin descriptor. Removed from the Pokemon descriptor and assembled Unyielding. |
| Essentials command/permission registration | Exactly three jail-only lines removed. New entry point registers the same commands and permission node. |
| Config | Only the jail field and `jail` section removed. Identical admission setting moved into a separate, default-off config. |
| Registration | Only the custody block and its otherwise-empty block registry removed. Standalone registration retains the old block ID. Quest items, loot crate and creative tab stay unchanged. |
| Logger and mod ownership | Logger references and event-subscriber ownership now use `MinecraftJail`; no dependency on the Unyielding entry point. |
| `essentials.Loc` | Copied with only a package change. The existing Essentials helper is untouched. |
| `Essentials.fail` | Same failure-message behavior in a jail-only helper; no Essentials dependency. |
| Runtime allowlist | Certificate bytes unchanged; self-artifact lookup now identifies `minecraftjail`. Separate Unyielding is not automatically trusted. |
| Four resource files | Advancement, vault blockstate/model and runtime certificate moved with unchanged bytes and identifiers. |
| 14 JUnit classes | Moved with namespace-only changes. Non-jail JUnit source unchanged. |
| Launch-based fixtures | Sixteen originals retained unmodified outside active source sets; original geometry-check method retained separately. No runtime launches claimed. |
| Release assembler | Retains the exact 57 non-jail overlay roots. Strips old jail members from the baseline and removes jail mixins instead of merging them back. |
| Other repository modules | No source changes to Pokemon/model, riding, currency, settings or tutorial update modules. |

Source provenance is checked against Git, not an approximate source copy.
Binary preservation is checked against an independently rebuilt original
assembled JAR, not only against the older binary baseline used by the assembler.
The only permitted non-jail binary differences are the Config, Registration and
Essentials class families and the removal of jail names from the mixin descriptor.
