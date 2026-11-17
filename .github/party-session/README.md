# Test-only global player sessions

This increment composes the exact passing nickname transaction and native-fence source into an independent disposable test mod. Production JARs, production source sets, certificates, wallet defaults and permanent identity reservations are unchanged. No nonempty-party /jail or /unjail authorization is added.

The selected provider is Cobblemon's actual global singleton and its existing configured native NBT factory, not a replacement manager or scratch provider. The profile accepts one exact FileBackedPokemonStoreFactory, one NBTStoreAdapter, and its single pinned ReforgedConversion child. It binds concrete object identities and the full canonical world-relative layout. Conversion/native fallback access for enrolled identities is still denied. A checked copy of HandoffCoordinator permits only this additional explicit profile in the test harness; prior source is untouched.

A real Visitor connection gets a connection-specific nonce bound to the actual ServerPlayer and packet-listener objects, a current mutation session, and the complete saved-generation receipt. Nickname requests use the existing complete-state transaction. Caller-supplied owners, identities, epochs and receipts are never sufficient authority. Disconnect revokes the connection before native suspension. Queued actions retain the original session/object rather than resolving a newer session at execution.

Synchronization uses a custom test payload containing complete detached NBT plus the exact receipt and source digest. It does not populate or certify the ordinary Cobblemon party UI. The graphical client validates complete state and nickname-only differences, actually disconnects/reconnects to the loopback server, and exercises stale tokens, generations, action replay and an actual native swap packet. Native gameplay packet entry points for enrolled owners are rejected rather than allowed to mutate a read-only successor. This is instrumentation breadth, not exhaustive gameplay certification.

The global factory's real autosave/disconnect/shutdown path remains enabled. Unrelated synthetic owners must continue saving, while quarantined entries are not falsely acknowledged. Two independent server starts must preserve edits and duplicate-action refusal. The previous crash/storage component evidence is reused, not counted as new tests.

All tests run on GitHub-hosted disposable worlds and synthetic identities. The helper packets and integration classes are not built into either production JAR. Gameplay handback, normal UI/client-cache integration, death/respawn custody, generalized provider support, reservation retirement/reuse and private full-pack/old-save migration remain unproven. No deployment is authorized.

The pinned pre-player MoLang callback (`CallbackHandler.setup$lambda$75`) is deferred
only for enrolled synthetic identities before constructing its context or reading
PC structures. This is test-only denial containment: native `getStruct` remains
forbidden, and callbacks for unrelated players continue. The verifier requires
both paths to be observed in each server phase. This does not certify normal
Cobblemon gameplay or enable production admissions.

The per-second `ServerTickHandler.onTick` consumer receives a temporary filtered
player-list snapshot excluding enrolled identities before party lookup. The global
player list and battle tick are unchanged. Non-enrolled players remain in that
loop; both branches must be observed by each phase's verifier. This does not grant
`onSecondPassed` mutation authority or claim playable party support.

The native swap denial probe uses the pinned 1.8.4 constructor with both actual
Pokemon UUIDs and positions (0 and 5), not an obsolete positions-only signature.
The compiler checks this API; the server must still reject the real packet before
mutation, and subsequent complete-state checks remain mandatory.
