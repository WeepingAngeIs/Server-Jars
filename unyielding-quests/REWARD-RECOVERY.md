# Reward recovery storage contract

Normal startup opens an existing permanent journal. Missing, empty, corrupt, truncated or locked history pauses payouts. It never creates, truncates or replaces missing history. Legacy nonempty v1 journals are read in place.

For a genuinely new reward installation only, an operator may run:
`/spinrecovery initialize_new confirm_no_previous_rewards`
This is an explicit assertion that no previous journal needs recovery. It refuses existing recovery directories, known prior provisioning and saved debit history. Do not delete or rename an existing recovery directory to bypass refusal. Preserve it and investigate or restore a consistent backup. An existing empty legacy journal also needs review, not automatic conversion.

The new journal is a permanently named, append-only file; all records are forced before their effects proceed. File locking, checksums and legal transitions remain enforced. Missing publication after interruption fails closed on next startup. Provisioning failures retain partial files for review. Provisioning does not recover or refund a prize.

Use `/spinrecovery list [page]` and `/spinrecovery show <transaction>` to inspect held payouts. A recorded delivery attempt does not prove receipt. Unattempted payouts cannot be marked delivered. Verify independently before using `/spinrecovery confirm_delivered <transaction>`; the command never replays or refunds a prize.

## Limits

This removes unsupported Java directory opens on Windows. It does not prove initial filename publication survives physical power loss. Java file-force guarantees apply to local storage; network or cloud-synced world directories are not supported durability targets. Hardware/controller failures or ignored flushes are outside these guarantees. Keep offline consistent world backups, including recovery history and player/Pokemon data.

Tests exercise record/reopen boundaries and corruption, not real power cuts. Minecraft balances, quest credits, player inventories and Cobblemon storage are separate persistence domains. Successful deliveries remain HELD for operator review; uncertain prizes are never automatically replayed or refunded. This is still a conservative draft, not exactly-once cross-system delivery.
