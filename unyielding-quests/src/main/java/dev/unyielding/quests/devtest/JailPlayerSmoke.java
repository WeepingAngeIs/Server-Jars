package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.jail.JailData;
import dev.unyielding.quests.jail.JailPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;

/** Disposable two-client integration smoke, excluded from the released overlay. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID)
public final class JailPlayerSmoke {
    private static boolean finished;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (finished || !Boolean.getBoolean("uq.jailPlayers")) return;
        var server = event.getServer();
        var admin = server.getPlayerList().getPlayerByName("Dev");
        var prisoner = server.getPlayerList().getPlayerByName("Visitor");
        if (admin == null || prisoner == null || server.getTickCount() < 100) return;
        finished = true;
        try {
            require(net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims"), "OPAC missing");
            if (Integer.getInteger("uq.jailPhase", 1) == 2) {
                var data = JailData.get(server);
                var assignment = data.assignment(prisoner.getUUID());
                require(assignment != null, "jail assignment missing after restart");
                var jail = assignment.jail();
                require(prisoner.distanceToSqr(jail.x(), jail.y(), jail.z()) < 0.0001, "login did not retain confinement after restart");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "unjail Visitor");
                require(data.assignment(prisoner.getUUID()) == null, "console release failed after restart");
                Files.writeString(Path.of("/tmp/uq-jail-player-result"), "passed: assignment and confinement survived restart; console released player\n");
                return;
            }
            server.getPlayerList().op(admin.getGameProfile());
            var commands = server.getCommands();
            require(!commands.getDispatcher().getRoot().getChild("jailbuild").canUse(prisoner.createCommandSourceStack()), "non-admin could access jail building");
            var level = admin.serverLevel();
            // The test world is a flat, generated grass surface: build on top of it,
            // never clearing or replacing its supporting blocks.
            admin.teleportTo(level, admin.getX(), admin.getY(), admin.getZ(), 0, 0);
            BlockPos min = admin.blockPosition().relative(admin.getDirection(), 11).offset(-3, 0, -3);
            // Claim one candidate chunk as a stranger and verify the real OPAC API denies
            // this admin's placement rather than relying on a fabricated claim response.
            var apiClass = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            var api = apiClass.getMethod("get", net.minecraft.server.MinecraftServer.class).invoke(null, server);
            var claims = apiClass.getMethod("getServerClaimsManager").invoke(api);
            var claimsApi = Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
            int cx = min.getX() >> 4, cz = min.getZ() >> 4;
            var dim = level.dimension().location();
            claimsApi.getMethod("claim", net.minecraft.resources.ResourceLocation.class, java.util.UUID.class,
                    int.class, int.class, int.class, boolean.class).invoke(claims, dim, java.util.UUID.randomUUID(), 0, cx, cz, false);
            commands.performPrefixedCommand(admin.createCommandSourceStack(), "jailbuild preview");
            commands.performPrefixedCommand(admin.createCommandSourceStack(), "jailbuild place");
            require(JailData.get(server).location() == null && level.getBlockState(min).isAir(), "stranger claim allowed jail placement");
            claimsApi.getMethod("unclaim", net.minecraft.resources.ResourceLocation.class, int.class, int.class).invoke(claims, dim, cx, cz);
            // A previously placed 7×7×7 jail must remain recognizable under geometry v1.
            for (int x=0; x<7; x++) for (int y=0; y<7; y++) for (int z=0; z<7; z++) {
                if (x!=0 && x!=6 && y!=0 && y!=6 && z!=0 && z!=6) continue;
                level.setBlockAndUpdate(min.offset(x,y,z), (y==0 && x==3 && z==3 ? Blocks.SEA_LANTERN : Blocks.STONE_BRICKS).defaultBlockState());
            }
            var legacy = dev.unyielding.quests.essentials.Loc.of(level, min.getX()+3.5, min.getY()+1, min.getZ()+3.5, 0, 0);
            require(JailPlacement.intact(server, legacy, JailData.LEGACY_GEOMETRY), "legacy jail geometry not recognized");
            for (int x=0; x<7; x++) for (int y=0; y<7; y++) for (int z=0; z<7; z++) {
                if (x==0 || x==6 || y==0 || y==6 || z==0 || z==6) level.setBlockAndUpdate(min.offset(x,y,z), Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(min, Blocks.CHEST.defaultBlockState());
            commands.performPrefixedCommand(admin.createCommandSourceStack(), "jailbuild preview");
            require(level.getBlockState(min).is(Blocks.CHEST), "preview changed existing chest");
            level.setBlockAndUpdate(min, Blocks.AIR.defaultBlockState());
            commands.performPrefixedCommand(admin.createCommandSourceStack(), "jailbuild preview");
            commands.performPrefixedCommand(admin.createCommandSourceStack(), "jailbuild place");
            var data = JailData.get(server);
            var jail = data.location();
            require(jail != null && JailPlacement.intact(server, jail), "jail template is not intact");
            require(data.geometry() == JailData.INTERROGATION_GEOMETRY, "new jail geometry not stored");
            require(level.getBlockState(min.offset(6,2,3)).is(Blocks.IRON_BARS), "prison/admin divider lacks bars");
            require(level.getBlockState(min.offset(12,1,3)).isAir() && level.getBlockState(min.offset(12,2,3)).isAir(), "admin doorway not accessible");
            require(level.getBlockState(min.below()).is(Blocks.GRASS_BLOCK), "placement replaced ground");
            commands.performPrefixedCommand(admin.createCommandSourceStack(), "jail Dev");
            require(data.assignment(admin.getUUID()) == null, "admin self-jail allowed");
            var sword = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD);
            sword.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Distinctive test item"));
            prisoner.getInventory().setItem(0, sword);
            prisoner.getInventory().setItem(39, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
            prisoner.getInventory().setItem(40, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
            var beforeInventory = new java.util.ArrayList<net.minecraft.nbt.Tag>();
            for (int i=0; i<prisoner.getInventory().getContainerSize(); i++) {
                var stack = prisoner.getInventory().getItem(i);
                beforeInventory.add(stack.isEmpty() ? new net.minecraft.nbt.CompoundTag() : stack.save(prisoner.registryAccess()));
            }
            var prisonerPrior = dev.unyielding.quests.essentials.Loc.of(prisoner);
            commands.performPrefixedCommand(admin.createCommandSourceStack(), "jail Visitor");
            require(data.assignment(prisoner.getUUID()) == null, "admission must fail closed until inventory and origin escrow are available");
            require(prisoner.distanceToSqr(prisonerPrior.x(), prisonerPrior.y(), prisonerPrior.z()) < 0.0001, "refused admission moved player");
            for (int i=0; i<beforeInventory.size(); i++) {
                var stack = prisoner.getInventory().getItem(i);
                var after = stack.isEmpty() ? new net.minecraft.nbt.CompoundTag() : stack.save(prisoner.registryAccess());
                require(beforeInventory.get(i).equals(after), "refused admission changed inventory or components in slot " + i);
            }
            // Continue testing existing prisoners with a disposable synthetic assignment;
            // this does not bypass the admission guard in the packaged mod.
            prisoner.teleportTo(level, jail.x(), jail.y(), jail.z(), jail.yaw(), jail.pitch());
            data.assign(prisoner.getUUID(), new JailData.Assignment(jail, prisonerPrior, data.geometry()));
            require(data.assignment(prisoner.getUUID()) != null, "synthetic assignment missing");
            require(prisoner.distanceToSqr(jail.x(), jail.y(), jail.z()) < 0.0001, "prisoner did not reach jail");
            var commandEvent = new CommandEvent(commands.getDispatcher().parse("say escape", prisoner.createCommandSourceStack()));
            NeoForge.EVENT_BUS.post(commandEvent);
            require(commandEvent.isCanceled(), "jailed command event not cancelled");
            commands.performPrefixedCommand(prisoner.createCommandSourceStack(), "say escape");
            var teleport = new EntityTeleportEvent.TeleportCommand(prisoner, jail.x()+50, jail.y(), jail.z());
            NeoForge.EVENT_BUS.post(teleport);
            require(teleport.isCanceled(), "teleport command event not cancelled");
            var dimension = new EntityTravelToDimensionEvent(prisoner, Level.NETHER);
            NeoForge.EVENT_BUS.post(dimension);
            require(dimension.isCanceled(), "dimension event not cancelled");
            var breaking = new BlockEvent.BreakEvent(level, min, level.getBlockState(min), prisoner);
            NeoForge.EVENT_BUS.post(breaking);
            require(breaking.isCanceled(), "jailed block break not cancelled");
            var snapshot = net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, min);
            var placing = new BlockEvent.EntityPlaceEvent(snapshot, Blocks.AIR.defaultBlockState(), prisoner);
            NeoForge.EVENT_BUS.post(placing);
            require(placing.isCanceled(), "jailed block placement not cancelled");
            var itemUse = new PlayerInteractEvent.RightClickItem(prisoner, net.minecraft.world.InteractionHand.MAIN_HAND);
            NeoForge.EVENT_BUS.post(itemUse);
            require(itemUse.isCanceled(), "jailed item use not cancelled");
            var boat = new net.minecraft.world.entity.vehicle.Boat(level, jail.x(), jail.y(), jail.z());
            var mount = new EntityMountEvent(prisoner, boat, level, true);
            NeoForge.EVENT_BUS.post(mount);
            require(mount.isCanceled(), "jailed vehicle mount not cancelled");
            // Simulate a direct same-dimension teleport API that does not fire a teleport event.
            var origin = data.assignment(prisoner.getUUID()).prior();
            prisoner.teleportTo(level, origin.x(), origin.y(), origin.z(), origin.yaw(), origin.pitch());
            require(prisoner.distanceToSqr(origin.x(), origin.y(), origin.z()) < 0.0001, "test setup could not relocate player");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(prisoner, false));
            require(prisoner.distanceToSqr(jail.x(), jail.y(), jail.z()) < 0.0001, "respawn event did not restore confinement");
            var prisonerId = prisoner.getUUID();
            prisoner.setHealth(0);
            prisoner.die(prisoner.damageSources().genericKill());
            // Use vanilla's client-command handler: it updates the network listener's
            // player reference after PlayerList.respawn returns. Calling respawn directly
            // leaves later connection.teleport calls operating on the removed player.
            prisoner.connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                    net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            prisoner = server.getPlayerList().getPlayer(prisonerId);
            require(prisoner != null && prisoner.connection.player == prisoner, "respawn did not rebind player connection");
            require(data.assignment(prisoner.getUUID()) != null, "death lost jail assignment");
            require(prisoner.distanceToSqr(jail.x(), jail.y(), jail.z()) < 0.0001, "actual death respawn escaped jail");
            // A damaged cage is never used as a teleport destination: preserve assignment,
            // then restore the block and verify recontainment resumes safely.
            var originalShell = level.getBlockState(min);
            level.setBlockAndUpdate(min, Blocks.AIR.defaultBlockState());
            require(!JailPlacement.intact(server, jail), "damaged jail appeared intact");
            prisoner.teleportTo(level, origin.x(), origin.y(), origin.z(), origin.yaw(), origin.pitch());
            require(prisoner.distanceToSqr(origin.x(), origin.y(), origin.z()) < 0.0001, "damaged-jail test setup failed to relocate player");
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(prisoner));
            require(prisoner.distanceToSqr(origin.x(), origin.y(), origin.z()) < 0.0001, "unsafe jail was used for teleport");
            require(data.assignment(prisoner.getUUID()) != null, "unsafe jail lost assignment");
            level.setBlockAndUpdate(min, originalShell);
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(prisoner));
            require(prisoner.distanceToSqr(jail.x(), jail.y(), jail.z()) < 0.0001, "restored jail did not recontain player");
            server.getPlayerList().saveAll();
            server.saveEverything(true, true, true);
            Files.writeString(Path.of("/tmp/uq-jail-player-result"), "passed: permission, claim and preview denial, placement, self-jail, command/teleport/dimension/break/place/item/mount events, direct teleport, death respawn, damaged-jail recovery\n");
        } catch (Throwable failure) {
            UnyieldingQuests.LOGGER.error("Jail player smoke failed", failure);
            try { Files.writeString(Path.of("/tmp/uq-jail-player-result"), "failed: " + failure + "\n"); } catch (Exception ignored) { }
        }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
}
