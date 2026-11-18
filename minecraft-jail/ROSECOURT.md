# Rosecourt Detention Center

Minecraft Jail 1.3.0-beta.1 adds one six-cell facility per world save. Rosecourt occupies 19 blocks east to west, 30 north to south and 10 vertically. Cells 01/02, 03/04 and 05/06 face each other across a vaulted hall. A skylight runs along its ridge. Staff approach the six custody lockers from the hall. The south courtyard has two permanent cherry sculptures and benches. Confinement uses each prisoner's own cell as its boundary. The hall and courtyard are staff-accessible spaces.

| Custom block | Use in the building |
|---|---|
| Pearl Ashlar | Light masonry on walls, cell floors and courtyard paving. |
| Smoked Chestnut | Dark lintels, roof framing, end posts and courtyard piers. |
| Rose Inlay | Pink stone outlines the gallery walks and marks each cell's landing. |
| Ward Grille | Fixed, brass-strapped metalwork secures cell windows and fronts. |
| Petal Lantern | Full-brightness lights illuminate cells, vaulting and courtyard posts. |
| Ceramic Blossom | Permanent pink canopy around stripped cherry trunks. |
| Enamel Cell Plates 01–06 | Six individually registered number blocks identify cells from the hall. |

These twelve decorative blocks have inventory items in Building Blocks. The existing custody vault keeps its original registry ID and gains a lock-faced texture. The production mod contains no Pokémon custody routines.

## Using a disposable, certified installation

Install matching Jail builds on the test server and clients. The target is Java 21, Minecraft 1.21.1, NeoForge 21.1.255 and Cobblemon 1.8.4. Custody additionally checks the exact loaded mod files, permissions, claim access, origin data, inventory and wallet. See the compatibility report for the private profiles.

1. As a staff player, stand facing a clear, supported area and run `/jailbuild preview`. Review the corners and `/jailbuild details [page]`, then use `/jailbuild place`. The build keeps its north–south orientation. Facing determines which side of you the preview appears on.
2. For a fresh test world only, initialize the histories from console with `jailcustody init` and `jailintegrated init`. Admissions default off. Check an online player with `jailintegrated check <player>`. The console command `jailintegrated mode supported` enables eligible admissions. The packed-wallet option is a separate opt-in for the existing narrow wallet contract.
3. Staff can run `/jail Alex 4` for cell 04 or `/jail Alex` for the first available cell. `/jailcells` lists available, reserved and occupied cells. An offline occupant keeps their place. An interrupted admission reserves its cell pending recovery.
4. A verified escape triggers a player death and server-initiated respawn at the assigned cell. Client input and the global immediate-respawn gamerule are unnecessary. A cancelled death, damaged cell or conflicting custody receipt can put the transaction into review.
5. Use `/unjail Alex` for verified return. A completed release frees the locker for reuse. To restore the building site after clearing custody, preview `/jailbuild undo`, then use `/jailbuild undo-confirm` after checking the area.

## Existing saves

Existing assignments retain their recorded geometry and destinations. Placement does not remodel an occupied jail. For an attached-1.2.0 occupied-save migration rehearsal, retain an offline backup with the exact old JAR, mod set, player data and custody journals. Test on a copy, with admissions off. The eligible case is a first, fully held supported or packed-wallet transaction with intact receipts and no previous escape or return. For this case the existing console workflow uses the old file at `<copy-of-world>/jail-migration-inputs/old.jar`, then `jailmigration prepare <online-owner> <old-SHA512> <candidate-SHA512>` and `jailmigration commit <transaction-UUID> <proposal-receipt>`. Previously migrated transactions and retained-XP migrations require separate coverage. Original geometry and custody history remain bound to the transaction until release. For a world with a valid older placement backup, undo the old placement after custody is cleared before previewing Rosecourt.

## Building the source

The downloadable source archive includes the module, its Gradle wrapper dependency and the checksum-verifying dependency fetcher. From the extracted archive root with Java 21 and Python 3:

```sh
python3 .github/scripts/fetch-build-dependencies.py minecraft-jail/libs
sh minecraft-jail/gradlew --no-daemon clean test jar
```

The full hosted server/client harness is tracked on the project branch. The module's README and RUNTIME-VALIDATION describe historical development stages and their evidence.
