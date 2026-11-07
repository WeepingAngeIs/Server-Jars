# Unyielding Quests

The quest / teams / essentials / vein-mine mod for the Cobblemon Unyielding modpack
(Minecraft 1.21.1, NeoForge 21.1). Replaces FTB Quests, FTB Teams, FTB Essentials and FTB Ultimine.

* `src/main/resources/defaultquests/` - the quest book that ships in the jar (FTB Quests SNBT format).
* `src/main/resources/assets/unyieldingquests/textures/quest_art/` - Astra's quest pictures (file name = quest id).
* `tools/questgen/` - Python scripts: `migrate.py` applies the edits to the team's FTB export, `build.py` adds the generated chapters.
* `libs/` is not in the repo: put `cobblemon.jar`, `emi.jar` and `kotlin-stdlib.jar` there to build.

Build: `./gradlew build` -> `build/libs/UnyieldingQuests-<version>.jar`. Releases on this repo carry the built jar.

## In-game server settings (1.6.2)

Operators (permission level 2) can use **Server settings** in the quest-book sidebar,
**Admin** in the Teams window, or `/quests settings`. Changes apply live and save to
`config/unyieldingquests-common.toml`; the server checks permission on every request.

**Share quest progress** defaults to **OFF**. Parties still support membership, invitations
and chat, but each player earns their own tasks, completions and rewards. Joining, leaving,
being kicked or moving to another party cannot grant that party's quest progress while OFF.
The Teams screen and tutorial reflect the active sharing mode.

On upgrade, current party members keep the progress they already had, including offline
members. Turning sharing OFF also preserves current members' existing progress; future
joiners do not inherit it. Turning it ON intentionally combines current members' progress.
Per-player reward and Lucky Spin claims persist across parties, mode changes and restarts.
Only explicit admin quest resets clear the corresponding claim history.

The menu also controls new-party creation, the automatic join tutorial, quest completion
messages, quest Lucky Spins, daily login rewards and Pokemon-prize announcements. Turning
off the automatic tutorial leaves `/tutorial` available. These switches can also be edited
in the server's normal TOML config. Open Parties and Claims is a separate mod and is not
controlled by these switches.

## Player tutorial (1.6.1)

When enabled, the guide opens on each join after NeoOrigins reports that all origin choices are complete,
followed by a short interval with no other menu open. It yields to other screens and resumes
at the same page after they close. Players can close it with Later or Escape and reopen it
with `/tutorial`. The last page reads Cobblemon's live Summary key binding to explain starter selection.

The server creates `config/unyieldingquests/tutorial.json` from the bundled default on first
startup. Edit that file to add pages or command explanations, then run `/tutorial reload` as
an operator. Existing valid content stays active if an edit is invalid. Players use `/tutorial`
to see the refreshed guide. Each entry has a registered command-node path in `command`, a
readable syntax in `usage`, and its explanation in `description`. Use the existing file as a template.

Curated entries appear only when their registered command path permits a player with
permission level zero. The searchable index is built from the live command tree on each
opening, so new public commands appear automatically. Permission checks still apply to
every command when it runs, including party-owner and rank restrictions.

GitHub Actions builds and tests the JAR, checks the packaged artwork, and exercises a real
client and server under a virtual display. The release requires the same version on client
and server.
