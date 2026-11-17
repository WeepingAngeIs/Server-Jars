# Bounded synthetic occupied migration validation

No deployment or live world changes. Pokémon remain entirely untouched.

## Verified run

Source `21d37f08ffcdc986860ad9490b6337e6a4dc10ce`, Actions run
https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44116731660:
89 Java unit tests and five real server/client sessions passed.

Candidate SHA-256: `5e2b40e36f2c6036005bed46c62f5da6b5faa7b8f77becec2ec334aa83990cd8`.
Exact predecessor: `c04a0564022ffdcfd1e9c0710ffd024260994498ea786cbab68711159fdc2883`.
Unchanged companion: `61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7`.

The test creates a synthetic occupied HELD transaction with the actual predecessor
JAR, stops its writers, preserves a compressed old-save copy, then starts the actual
candidate. It covers unapproved receipt refusal, prepared interruption/restart,
backup tamper and wrong receipt refusal, pre-commit interruption/restart, repeated
commit, escape death/native no-button respawn, another restart, exact restoration
and no duplicate release. Production classes are not replaced by behavior adapters.
The native startup quarantine audit originally blocked replay; the fix accepts
only a checked append of the exact native missing-chunk quarantine event for the
same binding. The complete original prefix, hash chain, owner, transaction, paths,
digest and anchor remain checked. Releases, relocation and administrator edits are
not permitted by that exception. The interface must now be loaded and intact.

Artifact ZIP digests independently checked:
- runtime artifact 14345081142: `cbe1e9f3092f27fc0b4166f564edde960649ddf0499954adc47ef4cf6131f628`
- build artifact 14344305686: `5a449036bf35aea6721f95d8b6528391689aacfcd3d4b37df7318bc747681fc5`

The prior fixture inherited a stale `nonempty_party_player_admitted...` report
label. It did not seed or inspect Pokémon, so that label is NOT evidence of a
nonempty party test. The label is corrected in the follow-up test source. Follow-up
assertions also require exact injected interruption errors, and preserve raw
transition evidence per phase. Those additional assertions require their own run;
the earlier green result must not be described as having run them.

## Final strengthened run: verified

Source `0bd7f3297d47b7de5e17ae79cba3b95c52b14f97`, Actions run
https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44120884857 is green.
The same candidate JAR SHA-256 was independently verified. All 89 unit tests and
five native sessions passed, including the exact injected-error assertions.
The stale party label is absent from the final report; Pokémon were not touched.

Independent verification replayed checksums in the preserved raw journal frames:
phase 2 PREPARED, phase 3 PREPARED → REMOVAL_STARTED, phase 4 PREPARED →
REMOVAL_STARTED → HELD, phase 5 unchanged. The proposal bytes were identical in
all four phases. Thus the interruption locations and absence of a duplicate
commit are now confirmed from journal evidence, not merely a generic exception.

Final artifact ZIP digests independently checked:
- runtime artifact 14347015982: `8f64de0cb9625b3358a02b4106284a3ef5adeee4e7705f70c0d43b7588b1acbe`
- build artifact 14343924764: `90b13f5d806d63b2eecd0031636c403648af3b6fdd083bdf5cefbf107aa5d050`

## Limits

This is ONLY the explicitly pinned, artifact-bound c04a0564 predecessor. It does
NOT establish migration from legacy 5c2653a9, an old integrated Unyielding JAR, any
unknown binary, or the user's real private world/modpack. Interrupted file writes
before a complete prepared proposal, abrupt process termination and disk loss are
not covered by these injected exception boundaries. Native saved receipts and
quarantine still fail closed; no old receipts or fingerprints are rewritten.
No version bump, installed JAR replacement, live world access or deployment.
