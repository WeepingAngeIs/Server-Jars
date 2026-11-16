# Private-copy compatibility and migration preparation

This tool is read-only. It does not extract an archive, launch Minecraft, change a
certificate, modify a save or approve migration. Do not run it against installed
directories. It accepts ZIP copies and the exact previously verified Jail JAR.
The private-input CLI refuses GitHub Actions; CI runs synthetic unit tests only.
No input save, playerdata, configuration secrets or database dump belongs in this
public repository or public Actions artifacts.

## Inputs, when available

* Exact current server runtime ZIP with actual mod binaries, loader identity,
  configuration/defaultconfigs, scripts and datapacks. A CurseForge manifest alone
  is not a runtime copy.
* Separate matching old-runtime ZIP containing the original integrated Unyielding
  JAR, with its independently known full SHA-256.
* A consistent pre-split world ZIP, including playerdata, data, serverconfig,
  jail journals and all file-backed Pokemon/economy storage. External databases
  require consistent exports and isolated test endpoints, not live credentials.

The backups must share a known stopped-server or consistent-snapshot boundary.
Presence of a database file does not prove all external state was captured.

Example private read-only inspection (not a server launch):

```text
python tools/jail-migration/plan.py --pack private/server-copy.zip --old-runtime private/old-runtime-copy.zip --save private/pre-split-world.zip --jail-jar private/Minecraft-Jail-1.2.0.jar --old-integrated-sha256 <full-old-jar-sha256> --report private/new-plan.json
```

The report must be a new file. Certificate pins are read from the exact reviewed
Jail artifact, never from a hand-edited certificate. Unknown binary identities,
loader differences, mixed/duplicate Jail ownership, incomplete inputs and unsafe
archive paths are blockers. The report deliberately omits local paths, arbitrary
pack names and individual player filenames. Keep the report private nonetheless.

## Prepared execution matrix, not yet executed on a real copy

1. Keep originals immutable and record full archive hashes. Make independent old
   control and split-candidate working copies; never run directly from originals.
2. Before launch, isolate networking and inspect copied configurations for any live
   database, webhook or service endpoints. Do not connect even read-only to live
   Pokemon/economy stores. A missing external snapshot blocks the relevant case.
3. Establish the old-runtime control: complete Pokemon identities/order/state,
   physical wallet components/contents and canonical locations, relevant player
   state, custody journals and unrelated mod data. Stop and restart the control.
4. On an empty-custody split copy, check startup, the separate loaded JAR identities,
   release/death/disconnect/restart paths and non-jail data conservation. Unknown mods
   must not be silently removed or auto-added to the safety certificate.
5. On an occupied old-custody copy, changed runtime fingerprints and missing or
   uncertain receipts must remain quarantined. Never rewrite old fingerprints,
   clear journals or generate replacement Pokemon/items to force a pass.
6. Exercise destination collisions, missing providers, conflicting resource owners
   and interrupted saves. Actual abrupt-termination stages need a supported adapter
   and an independently verified save backend before any recovery is authorized.

Every case starts as NOT_RUN. Inventory and synthetic fixture success do not count
as current-pack compatibility or occupied-save migration success. This is not yet
an automatic migration executor or a production resource-custody adapter.
