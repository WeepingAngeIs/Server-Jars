# Jail origin-integration research

These findings come from read-only inspection of the test profile's NeoOrigins
2.2.27+1.21.1 and its Classes, Fantasy and Furries addon JARs. They describe
that particular version; they do not establish current production versions or
prove runtime behavior. No player data or settings were modified.

## Origin identity and layers

The bundled Human is `neoorigins:human`, and the canonical main layer is
`neoorigins:origin`. The separate canonical class layer is `neoorigins:class`.
Human has tier-based powers at higher evolution tiers, and class, dynamic and
global powers may also remain active. A Human assignment is not equivalent to
removing all powers.

The three inspected addons define foreign namespace `origin` or `class` layers
without `standalone: true`. NeoOrigins' `LayerDataManager` folds such foreign
same-path layers into the canonical layers when those exist. Runtime registries
and the player's actual layer map are authoritative; do not assume JAR resource
paths are separate selected layers. Preserve non-main layers and evolution
state when temporarily changing an origin.

## Relevant installed APIs

* `OriginAttachments.originData()` obtains the player attachment type.
  `ServerPlayer.getData(...)` yields `PlayerOriginData`.
* `PlayerOriginData.getOrigins()` returns an unmodifiable view: copy it for a
  snapshot. `getOrigin(ResourceLocation layer)` reads the selection;
  `setOrigin(ResourceLocation layer, ResourceLocation origin)` updates that
  layer and increments a version, but does not itself perform lifecycle,
  claim, networking or durable transaction work.
* `OriginDataManager.INSTANCE.hasOrigin(id)` and
  `LayerDataManager.INSTANCE.hasLayer(id)` can check loaded registries.
* `ActiveOriginService.applyOriginPowers(ServerPlayer, layer, oldOrigin,
  newOrigin)` applies origin power lifecycle changes;
  `NeoOriginsNetwork.syncToPlayer(ServerPlayer)` sends the resulting state.
* For unique-layer ownership, `OriginClaimsData.get(server)` exposes
  `getOwner(layer, origin)`, `isClaimedByOther(layer, origin, player)`,
  `claim(layer, origin, player)` and `releaseIfOwner(layer, origin, player)`.
  The inspected `claim` implementation overwrites an existing owner; it is
  not a compare-and-set and must never be used to seize another player's claim.

The inspected admin origin-set command reads the old selection, writes the new
selection, releases the old unique-origin claim if owned, claims the new one,
applies powers and synchronizes the client. Calling it for a temporary Human
swap could release the original unique-origin reservation, making restoration
unsafe if someone else acquires it. Directly changing only the attachment
would instead skip the command's other required effects.

## Safety and admission gate

`applyOriginPowers` invokes power revoke/grant callbacks and events, invokes
origin-change integration hooks, adjusts health and may run third-party or
datapack-defined behavior. Those callbacks are not shown to be reversible or
idempotent and may affect inventory or world state. Origin selection through
the mod's network/UI can also change state while a player is jailed. Merely
snapshotting the origin ID does not provide safe rollback.

`PlayerOriginData` contains additional persisted state including evolution
progress, cooldowns, equipment-grant markers, toggles and dynamic/global
powers. Replacing the whole attachment on release could roll back unrelated
state or create duplicate grants. The player attachment, unique-claim data and
jail escrow live in separate stores; `SavedData.setDirty()` does not make a
cross-store change durable or atomic.

Until exact installed-version integration and crash-stage recovery are tested,
**refuse admission before changing the player** when the required origin or
inventory escrow integration is unavailable, unsupported, ambiguous or cannot
be handled safely. Do not silently substitute Human, clear accessories, invoke
the admin set command, grant a unique origin, overwrite an existing claim, or
automatically replay an uncertain transaction. Preserve the original layer ID,
origin ID and relevant ownership evidence in a durable journal; if observed
state does not match the expected transaction phase, hold for explicit
authorized recovery. The protocol must also address lifecycle callbacks and
origin changes initiated by other mods or network messages while jailed.

Before enabling the feature, test real installed integration APIs with the
actual origin and addon versions, unique claims, class and other layers,
nonempty inventory/accessories, callbacks, logout and server restart at each
transaction phase, conflicting changes, and restoration failure. Verify that
original selections and claims remain reserved, no unrelated state is reset,
and no items or grants are duplicated or lost. These tests have not yet been
performed.
