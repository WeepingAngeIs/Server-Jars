# Split companion certificate and bounded runtime validation

This follow-up preserves the extracted implementation. Production changes are a
narrowly extended runtime certificate and corrected loader metadata for the exact
pinned Cobblemon version `1.8.4+1.21.1`. The initial runtime attempt found that
`[1.8.4]` rejected this binary before startup. The constraint is corrected to
`[1.8.4+1.21.1]`, not broadened to a version wildcard. All 39 relocated jail classes,
four mixins, admission switches, runtime audit code, custody journals and original
fixture sources remain unchanged from the extraction.

## Companion identity

`companion-certificate.json` records two reviewed byte-identities of the same
jail-free Unyielding contents: the previously delivered split JAR and a
reproducible repack. The repack changes ZIP ordering, timestamps and compression,
not class/resource contents. The raw SHA-512 check remains the runtime boundary;
there is no filename, version, namespace, wildcard or on-the-fly trust exemption.
Any different companion requires a new review. Other dependency, embedded-JAR,
Minecraft and NeoForge pins are unchanged. The duplicate jail-owner guard remains.

## Evidence and isolation

Use the exact-commit `runtime-verification.json` from a successful
`Split jail certificate and disposable runtime` run. A successful code-only build
is not runtime proof. Final verified delivery is gated on all runtime slices.
The fixture adapter uses two unchanged final JARs, not production source sets.
A separate test mod loads preserved fixtures and records both actual loaded paths
and hashes at every startup. Its hooks remain restricted to the existing
GitHub-hosted disposable-world guards. The production artifacts contain no test
mod, source fixtures or added runtime bypass.

The bounded slices are startup/restart plus terrain recovery, Human/empty-accessory
transaction faults, supported zero-XP native custody, and retained-XP native death
and respawn. Two real local client sessions exercise the player cases. The
integrated slice also loads a genuine unapproved dependency and then a byte-altered
companion, verifies refused restoration, restores the exact approved bytes, and
requires explicit saved-phase recovery without duplicated inventory.

The default admission switch remains off. Tests explicitly use the existing
supported-mode command where required. Supported native class/equipment cases are
not a promise of arbitrary origin, addon, accessory or wallet compatibility.
Nonempty Pokemon parties, occupied wallets, unknown native state and uncertain
unreceipted XP transfers remain blocked; the tests check preservation, not deletion.

## Not established

This is not a migration test for an occupied pre-split jail or a certificate for
the user's entire modpack. A changed runtime fingerprint must not be rewritten in
old custody records to force recovery. There were no installations, live server
connections or edits to real saves. Full manual graphical/UI behavior and art are
not tested here. The room/chair art phase is deferred for Astra Ultra.
