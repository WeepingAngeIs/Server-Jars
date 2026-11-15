# Reward recovery storage contract

Normal startup opens an existing permanent journal. Missing, empty, corrupt, truncated or locked history pauses payouts. It never creates, truncates or replaces missing history. Legacy nonempty v1 journals are read in place.

For a genuinely new reward installation only, an operator may run:
`/spinrecovery initialize_new confirm_no_previous_rewards`
This is an explicit assertion that no previous journal needs recovery. It refuses existing recovery directories, known prior provisioning and saved debit history. Do not delete or rename an existing recovery directory to bypass refusal. Preserve it and investigate or restore a consistent backup. An existing empty legacy journal also needs review, not automatic conversion.

The new journal is a permanently named, append-only file; all records are forced before their effects proceed. File locking, checksums and legal transitions remain enforced. Missing publication after interruption fails closed on next startup. Provisioning failures retain partial files for review. Provisioning does not recover or refund a prize.

Use `/spinrecovery list [page]` and `/spinrecovery show <transaction>` to inspect held payouts. A recorded delivery attempt does not prove receipt. Unattempted payouts cannot be marked delivered. Verify independently before using `/spinrecovery confirm_delivered <transaction>`; the command never replays or refunds a prize.

If an operator has independently verified and manually provided the exact recorded prize, `/spinrecovery confirm_manually_delivered <transaction>` acknowledges that action. It does not give the prize. The acknowledgment is an operator assertion, not proof that external player or Pokémon storage was saved.

## Limits

This removes unsupported Java directory opens on Windows. It does not prove initial filename publication survives physical power loss. Java file-force guarantees apply to local storage; network or cloud-synced world directories are not supported durability targets. Hardware/controller failures or ignored flushes are outside these guarantees. Keep offline consistent world backups, including recovery history and player/Pokemon data.

Tests exercise record/reopen boundaries and corruption, not real power cuts. Minecraft balances, quest credits, player inventories and Cobblemon storage are separate persistence domains. Successful deliveries remain HELD for operator review; uncertain prizes are never automatically replayed or refunded. This is still a conservative draft, not exactly-once cross-system delivery.


## Named quest credits and reset generations

Named quest-spin credits now have an immutable journal identity: player UUID, spin reward ID and quest-reset generation. Credits are written before claimed flags. Applied credit markers and balances share one SavedData snapshot, so restart reapplication cannot credit the same identity twice. Missing or malformed credit-marker history fails closed. Legacy claimed rewards and balances are retained; historical rewards without an identity cannot be retroactively reconstructed.

Reset generations live in QuestData with the progress and claim ledger being reset. There are NO reset journal records and NO reset replay. Recovery only reconciles credits, so later progress earned during a journal outage cannot be erased by deferred reset recovery. An unsaved reset may roll back along with its progress snapshot; an already-journaled credit remains auditable. A deliberate subsequent admin reset uses a fresh generation. Party membership does not own the credit identity; each player remains independent. All-player and single-quest resets update the same snapshot, including personal copies.

Generic crate/loot credits still use their legacy path and are not covered by named-quest deduplication. Do not downgrade after new credit records have been written: old binaries reject the unknown journal record type rather than safely understanding it.

## Delivery return is not durable receipt

`RETURNED` means the delivery call and its presentation completed without an exception. It is distinct from `DELIVERING`/`HELD`, but is not `COMPLETE` and is not evidence that Minecraft/Cobblemon independently saved the prize. Administrator acknowledgment remains necessary for completion. No state is automatically replayed or refunded. Older HELD entries are not reclassified based on guesses. Physical power cuts and full modpack multiplayer are not certified by unit or smoke tests.
