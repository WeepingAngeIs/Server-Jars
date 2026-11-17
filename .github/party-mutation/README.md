# Test-only durable nickname transactions

This directory extends the verified native-fence/read-only-publication experiments without altering them or enabling nonempty-party admission. It is NOT part of a production source set. The original Jail/Unyielding JARs, certificates, generation journal, permanent identity reservations and wallet defaults remain unchanged.

## Bounded implemented contract

A published synthetic successor accepts exactly one new mutation command: a plain-text nickname change (1-32 ASCII letters/digits/spaces/underscore/hyphen). A request binds the current publication epoch, exact journal receipt, exact object reference and an unused action UUID. Direct native setters, stale objects, stale epochs/receipts, repeated action IDs, worker-thread requests and foreign managers remain denied.

The setter runs on privately reconstructed objects. The new complete party NBT must differ only at the matched original Pokemon's `Nickname` field, with the expected UUIDs/slots, all other fields and PC bytes unchanged. JSON encoding and reconstruction remain inside the same non-inheritable constructor-tracked-object capability. Complete native NBT equality and stable native JSON encoding are required; no comparison tolerance or omitted field is used.

The next generation containing full state and the command ID commits as MUTATING before a native write. Old access is withdrawn. Exact synchronous native IO is permitted only to the bound adapter/current temporary file and frozen payload; file/directory force and full native readback precede a completed generation. Only then is a newly constructed graph/epoch made accessible. Any uncertain intent remains quarantined. There is no rollback, replay or retry of a pending mutation.

The current generation, full native current files and graph must still agree on every allowed access or autosave acknowledgment. Native backup loads and other providers cannot select an older graph. The immutable original custody snapshot remains intact; a changed current nickname does not alter its authority. Mutation-owned successor metadata is a separate journal bound to the original exact custody receipt.

Mixed-owner native autosave/disconnect/shutdown and queued-native-batch protections continue through the explicit reviewed scratch manager/factory. No client packet handler or global player provider is replaced. Mutation requests return explicit refusal rather than an empty-party fallback.

## Limits

The new command is not a playable-party handback or a full admission/release path. Send-out, riding, battles, trades, HP/experience/evolution changes, arbitrary text components, populated PC mutation and unrelated gameplay remain blocked/unverified. Permanent identity tombstones are retained. The bounds are 32 accepted command IDs and epochs below 64; exhaustion refuses, with no implicit reset. Real network sessions and private full-modpack/old-save migration remain out of scope.

Run only with the dedicated hosted synthetic workflow. Source hashes and reused passing evidence are checked. Runtime results are authoritative only when every phase and the final exact-commit gate pass.
