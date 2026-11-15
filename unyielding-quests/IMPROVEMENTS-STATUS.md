# Unyielding improvements: implementation and remaining checks

This is a development checklist, not a release or installation instruction. Existing player progress, settings and rank plans must be preserved.

1. Daily rewards have a configurable missed-day grace and a displayed policy. Journaled spin payouts still require reconciliation because Minecraft inventory and Cobblemon storage are not atomic with the journal. Named quest credits use journal identities and reset generations saved with quest progress; recovery never replays a reset. Generic crate/loot credits remain on the legacy path. RETURNED payouts distinguish a successful call from an uncertain attempt, not durable receipt.
2. Homes and warps have an icon list, dimensions and cooldowns; home information is scoped to its owner and selections invoke existing commands. Party meetings remain unavailable until the actual server's claim, rank, dimension and teleport restrictions can be checked together at execution time.
3. The first-time tutorial is reopenable, filters its command entries by permissions, and includes a What's New page.
4. Party rosters support search and paging, quest search has status filters, and parties can pin an informational goal. Rewards remain personal and sharing remains configurable.
5. Party disband and ownership transfers require server-side confirmation and revalidation. Multiplayer race and cancellation testing is still needed.
6. Optional compact vein-mining feedback is off by default. Protection compatibility and in-game presentation still need verification.
7. Some durations accept readable units and presets. A bounded audit covers individual config edits, admin toggles and ground settings. Undo is limited to two allowlisted numeric settings; grouped edits and external changes are not audited.

Ground cleanup has an operator screen, item-age grace, category and dimension settings, countdown, warnings and manual preview. Manual clear covers selected vanilla dimensions, not arbitrary custom-dimension overrides. Do not enable a separate cleanup datapack with conflicting functions. Test real modpack item merging and multiplayer behavior before deployment.

GitHub compilation and isolated smoke tests do not establish physical power-loss behavior, complete modpack compatibility or exactly-once delivery across independently saved systems.
