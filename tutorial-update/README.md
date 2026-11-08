# Unyielding 1.1.4

Adds a percentage editor for Lucky Spin rewards to `/quests settings` → **Spin odds**.
Quest and daily spins have separate tabs. Edit all five percentages, make the total 100%,
then save the selected tab. Changes apply to future spins immediately and persist in the
server config. Existing configured chances are preserved until an admin saves a change.

**Very rare** previews Basic 60%, Good 39.84%, Paradox 0.1%, Mythical 0.05%, Legendary 0.01%.
**Items only** previews Basic 60%, Good 40%, and zero Pokemon prizes. Neither preset applies
until Save. Prize item pools and other spin settings are linked from this screen; custom
reward-table odds remain under **Loot tables**. Both reward types use the same item pools.

Five chances save together, and stale edits cannot overwrite another admin's change. Drafts
survive tab switches. More precise existing weights are retained unless explicitly changed.
Pokemon pool additions and removals now refresh immediately without a restart.

The currency bridge and other modules are preserved from verified 1.1.3. The player guide,
compact collection tasks and admin controls from earlier updates remain included.

## Player guide

Both tutorial pages and command search use documented player routes and check available
commands on the server. Unknown registered commands are no longer automatically advertised.
Operators view help without their operator bypasses. No real player is de-opped, and this
update does not grant or revoke gameplay command permissions.

The guide uses the actual active Unyielding rank assignments. Each optional `required_ranks`
field in `config/unyieldingquests/tutorial.json` means:

- Field omitted: public help, still checked against command availability.
- `"required_ranks": []`: hidden until an administrator configures allowed rank IDs.
- `"required_ranks": ["vip"]`: visible to an active `vip` member if the command is available.

The existing rank module defines titles and kits, not command unlocks. The update therefore
does not guess which rank unlocks a tool. Crafting tools, remote PC/healing and other known
rank extras start hidden. Configure exact rank IDs here only after setting the actual command
access on the server. This is guide visibility, not a new permissions system. Reopening
`/tutorial` checks current grants, expiry and removal; `/tutorial reload` reloads edited help.

Old help files migrate once to schema version 2, keeping custom pages and wording and saving
`tutorial.json.before-1.1.2`. The default `/oparties about` entry is removed in favor of
`/oparties member invite <player>`, whose owner/moderator prerequisite is explained.

## Quest book and controls

Multi-item collection quests in Catching and Team Training retain instructions on the first
task and compact subsequent item rows. Requirements, progress, rewards and IDs are unchanged.
The guide points to the inventory's top-left Poke Ball. The quest-book shortcut is editable
under Controls > Key Binds; new installations default to F12. Existing saved key bindings
are preserved, and the inventory tooltip displays the current key or how to bind it.

The first Poke Ball collection quest uses the supplied transparent white-ball animation.
Its original PNG is preserved; rendering metadata selects its four frame regions.

Quest content can be edited separately from the JAR in `config/unyieldingquests/quests/`.
Use `quest_book_source = "config_folder"` in `unyieldingquests-common.toml`, then `/quests reload`
after editing the SNBT files. Keep existing quest/task IDs to retain progress. An in-game
quest edit also exports the built-in book and selects config-folder mode.

## Admin switches

The existing server settings menu has 16 switches. Nine additions control inventory shortcuts,
completion popups, vein mining, its held-item requirement, mass log stripping/tilling/path-making,
operator teleport cooldown exemption, and recording only deaths for new `/back` destinations.
Changes save to their owning configuration files. Non-operators cannot edit the settings.
Existing quest progress, reward history and `/back` history are retained.

## Verification

All compilation and Minecraft checks run on GitHub-hosted runners. The workflow checks the
actual combined JAR, currency regression coverage, rank/operator help filtering, live settings
and persistence, and compact task rendering at normal and narrow GUI widths. These checks
use a focused environment, not a complete public-server modpack walkthrough.
