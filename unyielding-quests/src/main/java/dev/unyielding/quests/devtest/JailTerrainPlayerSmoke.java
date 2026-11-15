package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.jail.JailData;
import dev.unyielding.quests.jail.JailPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;

/** Real-player terrain and simulated interrupted-placement test, disposable worlds only. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID)
public final class JailTerrainPlayerSmoke {
    private static boolean finished;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (finished || !Boolean.getBoolean("uq.jailTerrainPlayers")) return;
        var server = event.getServer(); var admin = server.getPlayerList().getPlayerByName("Dev");
        var visitor = server.getPlayerList().getPlayerByName("Visitor");
        if (admin == null || visitor == null || server.getTickCount() < 100) return;
        finished = true;
        try {
            require(net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims"), "OPAC missing");
            server.getPlayerList().op(admin.getGameProfile());
            var level = admin.serverLevel(); var commands = server.getCommands(); var source = admin.createCommandSourceStack();
            var data = JailData.get(server);
            Path journal = server.getWorldPath(LevelResource.ROOT).resolve("data/unyielding-jail-placement.before");
            if (Integer.getInteger("uq.jailTerrainPhase", 1) == 2) {
                require(data.location() != null && Files.isRegularFile(journal), "restart lost jail or placement backup");
                BlockPos min = BlockPos.containing(data.location().x(), data.location().y(), data.location().z()).offset(-3,-1,-3);
                require(level.getBlockState(min.offset(3,1,3)).is(Blocks.STONE), "simulated partial terrain not retained");
                require(level.getBlockState(min.offset(0,1,0)).isAir(), "simulated partial wall not retained");
                commands.performPrefixedCommand(source, "jailbuild undo");
                commands.performPrefixedCommand(source, "jailbuild undo-confirm");
                require(!Files.exists(journal) && data.location() == null, "undo failed to remove backup or restore previous jail location");
                require(level.getBlockState(min.offset(3,1,3)).is(Blocks.STONE), "original terrain not restored");
                require(level.getBlockState(min.offset(0,1,0)).isAir() && level.getBlockState(min.offset(12,6,6)).isAir(), "jail blocks survived undo");
                require(level.getBlockState(min.offset(13,0,0)).is(Blocks.GOLD_BLOCK), "undo changed a block outside its bounds");
                Files.writeString(Path.of("/tmp/uq-jail-terrain-result"), "passed: restarted after simulated interrupted placement, protected undo restored the original area\n");
                return;
            }
            require(data.location() == null && !Files.exists(journal), "test world was not fresh");
            BlockPos min = admin.blockPosition().relative(admin.getDirection(), 11).offset(-3,0,-3).immutable();
            visitor.teleportTo(level, min.getX()-8.5, min.getY(), min.getZ()-8.5, 0, 0);
            require(level.hasChunk(min.getX()>>4,min.getZ()>>4) && level.hasChunk((min.getX()+12)>>4,(min.getZ()+6)>>4), "fixture area not loaded");
            level.setBlockAndUpdate(min.offset(13,0,0), Blocks.GOLD_BLOCK.defaultBlockState());
            BlockPos chest = min.offset(1,1,1);
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            var inventory = (ChestBlockEntity) level.getBlockEntity(chest); require(inventory != null, "chest fixture missing");
            inventory.setItem(0, new ItemStack(Items.DIAMOND, 3));
            commands.performPrefixedCommand(source, "jailbuild preview");
            commands.performPrefixedCommand(source, "jailbuild place");
            require(data.location() == null && !Files.exists(journal) && level.getBlockState(chest).is(Blocks.CHEST)
                    && inventory.getItem(0).is(Items.DIAMOND) && inventory.getItem(0).getCount()==3, "preview overwrote a chest or items");
            inventory.clearContent(); level.setBlockAndUpdate(chest, Blocks.AIR.defaultBlockState());
            BlockPos ore = min.offset(4,1,2), stone = min.offset(3,1,3);
            level.setBlockAndUpdate(ore, Blocks.DIAMOND_ORE.defaultBlockState());
            commands.performPrefixedCommand(source, "jailbuild preview"); commands.performPrefixedCommand(source, "jailbuild place");
            require(data.location() == null && !Files.exists(journal) && level.getBlockState(ore).is(Blocks.DIAMOND_ORE), "valuable ore was overwritten");
            level.setBlockAndUpdate(ore, Blocks.AIR.defaultBlockState());
            var apiClass = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            var api = apiClass.getMethod("get", net.minecraft.server.MinecraftServer.class).invoke(null, server);
            var claims = apiClass.getMethod("getServerClaimsManager").invoke(api);
            var claimsApi = Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
            var dim = level.dimension().location(); int cx=min.getX()>>4, cz=min.getZ()>>4;
            claimsApi.getMethod("claim", net.minecraft.resources.ResourceLocation.class, java.util.UUID.class, int.class, int.class, int.class, boolean.class)
                    .invoke(claims, dim, java.util.UUID.randomUUID(), 0, cx, cz, false);
            commands.performPrefixedCommand(source, "jailbuild preview"); commands.performPrefixedCommand(source, "jailbuild place");
            require(data.location() == null && !Files.exists(journal), "claim permission was bypassed");
            claimsApi.getMethod("unclaim", net.minecraft.resources.ResourceLocation.class, int.class, int.class).invoke(claims, dim, cx, cz);
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            commands.performPrefixedCommand(source, "jailbuild preview"); commands.performPrefixedCommand(source, "jailbuild details 1");
            require(level.getBlockState(stone).is(Blocks.STONE) && !Files.exists(journal), "preview changed terrain");
            commands.performPrefixedCommand(source, "jailbuild cancel"); commands.performPrefixedCommand(source, "jailbuild place");
            require(level.getBlockState(stone).is(Blocks.STONE) && !Files.exists(journal), "cancelled preview changed terrain");
            commands.performPrefixedCommand(source, "jailbuild preview"); commands.performPrefixedCommand(source, "jailbuild place");
            require(data.location() != null && data.geometry() == JailData.INTERROGATION_CHAIR_GEOMETRY && Files.isRegularFile(journal), "confirmed placement or recovery backup missing");
            require(level.getBlockState(stone).isAir() && JailPlacement.intact(server, data.location(), data.geometry()), "terrain was not cleared or template is invalid");
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            var later = (ChestBlockEntity)level.getBlockEntity(chest); require(later != null, "later chest missing"); later.setItem(0, new ItemStack(Items.DIAMOND, 2));
            commands.performPrefixedCommand(source, "jailbuild undo"); commands.performPrefixedCommand(source, "jailbuild undo-confirm");
            require(Files.exists(journal) && level.getBlockState(chest).is(Blocks.CHEST) && later.getItem(0).getCount()==2, "undo overwrote a later chest");
            later.clearContent(); level.setBlockAndUpdate(chest, Blocks.AIR.defaultBlockState());
            byte[] validBackup = Files.readAllBytes(journal), corruptBackup = validBackup.clone();
            corruptBackup[corruptBackup.length-1] ^= 1; Files.write(journal, corruptBackup);
            commands.performPrefixedCommand(source, "jailbuild undo"); commands.performPrefixedCommand(source, "jailbuild undo-confirm");
            require(Files.exists(journal) && level.getBlockState(min.offset(0,1,0)).is(Blocks.STONE_BRICKS), "corrupt backup caused world edits");
            Files.write(journal, validBackup);
            // Simulate a process stopping during placement: persist a mix of original
            // and applied states, then restart and use the genuine recovery commands.
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(min.offset(0,1,0), Blocks.AIR.defaultBlockState());
            server.getPlayerList().saveAll(); server.saveEverything(true,true,true);
            Files.writeString(Path.of("/tmp/uq-jail-terrain-result"), "passed: bounded terrain placement, chest/ore/claim protection and interrupted-state fixture saved\n");
        } catch (Throwable failure) {
            UnyieldingQuests.LOGGER.error("Jail terrain player smoke failed", failure);
            try { Files.writeString(Path.of("/tmp/uq-jail-terrain-result"), "failed: " + failure + "\n"); } catch (Exception ignored) { }
        }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
}
