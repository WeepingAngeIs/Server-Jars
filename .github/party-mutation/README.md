# Test-only durable nickname transactions

Continues 9e9f8571 / tested read-only publication 549ca5db. All changes are confined to this independent disposable test mod. NativeFence, GenerationJournal, PartyCustodyGuard, baseline publication source, certificates and production JARs are unchanged.

A plain-literal nickname (ASCII letters/numbers/space/_/-, 1..32 characters) is the only supported edit. Requests bind the current Session object, owner/epoch, exact generation receipt, exact current Pokemon object and a persistent unique command UUID. The native setter runs on newly constructed detached objects. Complete native state is compared before and after: only the target's Nickname field may differ. Identity, slot layout, every other Pokemon field and the whole PC are unchanged.

A MUTATING generation retaining complete candidate state commits before a native write. The preceding immutable generation preserves the old snapshot. The old access epoch is revoked. A narrow non-inheritable IO capability can only write that frozen payload to the exact native current/temp path. After force/readback and complete equality, READ_ONLY commits and a fresh graph/epoch is published. No asynchronous native queue progress is called a receipt. Old references, requests, iterators and queued native writes remain denied.

Any incomplete mutation is quarantined, even if its current file looks correct. Clean committed state can be independently verified and republished after restart. Original custody snapshots and permanent identity tombstones are never removed. This is not a general unlock or a reusable admission protocol. Bounds: 32 accepted command IDs and 63 epochs; reaching limits refuses without dropping history.

Bounded native manager/factory cache integration and per-entry mixed autosave/shutdown handling are inherited in a private modified copy. Actual native dirty callbacks, queued batches and shutdown are tested. A fresh native save acknowledgment verifies exact already-durable state; it does not permit arbitrary async mutation. Error response adapters are synthetic and are not registered gameplay/network handlers.

No global player provider replacement, client synchronization, battle/trade/riding/send-out, arbitrary setters, held items, populated PCs, provider conversion or real jail admission/release is certified. All party admission stays fail-closed. No production build or deployment. All fixtures and archives are synthetic on GitHub-hosted runners.
