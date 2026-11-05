package dev.unyielding.quests.essentials;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Core of the Essentials module: data, cooldowns, shared helpers and events. */
public final class Essentials {
    private static MinecraftServer server;
    private static EssData data;
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();

    private Essentials() {
    }

    public static EssData data() {
        return data;
    }

    public static MinecraftServer server() {
        return server;
    }

    // ---- helpers ------------------------------------------------------------

    public static void ok(CommandSourceStack s, String msg) {
        s.sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), false);
    }

    public static int fail(CommandSourceStack s, String msg) {
        s.sendFailure(Component.literal(msg));
        return 0;
    }

    /** Returns an error text if the player is still on cooldown, otherwise starts the cooldown. */
    public static String cooldown(ServerPlayer p, String key, int seconds) {
        if (seconds <= 0) return null;
        if (EssConfig.ADMINS_EXEMPT_COOLDOWNS.get() && p.hasPermissions(2)) return null;
        String k = key + "/" + p.getUUID();
        long now = System.currentTimeMillis();
        Long until = COOLDOWNS.get(k);
        if (until != null && until > now) {
            long left = (until - now + 999) / 1000;
            return "You have to wait " + left + "s before using this again.";
        }
        COOLDOWNS.put(k, now + seconds * 1000L);
        return null;
    }

    /** Remembers where the player was so that /back can return there. */
    public static void pushBack(ServerPlayer p) {
        pushBack(p, Loc.of(p));
    }

    public static void pushBack(ServerPlayer p, Loc loc) {
        List<Loc> l = data.back.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        l.add(loc);
        while (l.size() > EssConfig.BACK_MAX.get()) l.remove(0);
        data.setDirty();
    }

    /** Teleport that records the old spot first. */
    public static boolean teleport(ServerPlayer p, Loc to) {
        Loc from = Loc.of(p);
        if (!to.teleport(p)) return false;
        if (!EssConfig.BACK_ONLY_ON_DEATH.get()) pushBack(p, from);
        return true;
    }

    public static void teleport(ServerPlayer p, ServerLevel level, double x, double y, double z) {
        teleport(p, Loc.of(level, x, y, z, p.getYRot(), p.getXRot()));
    }

    /** Finds a safe standing height at x/z, or null. */
    public static Integer safeY(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int min = level.getMinBuildHeight();
        if (level.dimensionType().hasCeiling()) {
            int top = Math.min(level.getMinBuildHeight() + level.getLogicalHeight() - 2, 120);
            for (int y = top; y > Math.max(min + 2, 32); y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir() && isSafeFloor(level, pos.below())) return y;
            }
            return null;
        }
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= min + 1) return null;
        BlockPos pos = new BlockPos(x, y, z);
        if (!isSafeFloor(level, pos.below())) return null;
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return null;
        return y;
    }

    private static boolean isSafeFloor(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        if (s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CAMPFIRE) || s.is(Blocks.FIRE) || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH)) return false;
        return s.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP) && !s.getFluidState().is(FluidTags.LAVA);
    }

    public static MutableComponent clickable(String label, String command, ChatFormatting color) {
        return Component.literal(label).withStyle(s -> s.withColor(color).withBold(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, command)));
    }

    /** Registers a command unless it is switched off in the config. */
    public static void reg(CommandDispatcher<CommandSourceStack> d, String name, LiteralArgumentBuilder<CommandSourceStack> b) {
        if (EssConfig.disabled(name)) return;
        d.register(b);
    }

    // ---- events ----------------------------------------------------------------

    public static final class Events {
        @SubscribeEvent
        public void onStarting(ServerStartingEvent e) {
            server = e.getServer();
            data = server.overworld().getDataStorage().computeIfAbsent(EssData.factory(), EssData.NAME);
        }

        @SubscribeEvent
        public void onStopped(ServerStoppedEvent e) {
            server = null;
            data = null;
            COOLDOWNS.clear();
        }

        @SubscribeEvent
        public void onCommands(RegisterCommandsEvent e) {
            TeleCmds.register(e.getDispatcher());
            AdminCmds.register(e.getDispatcher());
            MiscCmds.register(e.getDispatcher());
        }

        @SubscribeEvent
        public void onDeath(LivingDeathEvent e) {
            if (data != null && e.getEntity() instanceof ServerPlayer p) pushBack(p);
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
            if (data != null && e.getEntity() instanceof ServerPlayer p) {
                data.lastSeen.put(p.getUUID(), Loc.of(p));
                data.names.put(p.getUUID(), p.getGameProfile().getName());
                data.setDirty();
            }
        }

        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (data != null && e.getEntity() instanceof ServerPlayer p) {
                data.names.put(p.getUUID(), p.getGameProfile().getName());
                p.refreshDisplayName();
                p.refreshTabListName();
            }
        }

        @SubscribeEvent
        public void onChat(ServerChatEvent e) {
            if (data != null && data.muted.contains(e.getPlayer().getUUID())) {
                e.setCanceled(true);
                e.getPlayer().sendSystemMessage(Component.literal("You are muted.").withStyle(ChatFormatting.RED));
            }
        }

        @SubscribeEvent
        public void onName(PlayerEvent.NameFormat e) {
            if (data == null) return;
            UUID u = e.getEntity().getUUID();
            String nick = data.nicks.get(u);
            net.minecraft.network.chat.MutableComponent c;
            if (nick != null) c = Component.literal(dev.unyielding.quests.Text.codes(nick));
            else if (data.rec.contains(u)) c = e.getDisplayname().copy();
            else return;
            if (data.rec.contains(u)) c = Component.literal("[REC] ").withStyle(ChatFormatting.RED).append(c);
            e.setDisplayname(c);
        }

        @SubscribeEvent
        public void onTabName(PlayerEvent.TabListNameFormat e) {
            if (data == null) return;
            UUID u = e.getEntity().getUUID();
            String nick = data.nicks.get(u);
            if (nick == null && !data.rec.contains(u)) return;
            MutableComponent c = Component.literal(nick != null ? dev.unyielding.quests.Text.codes(nick) : e.getEntity().getGameProfile().getName());
            if (data.rec.contains(u)) c = Component.literal("[REC] ").withStyle(ChatFormatting.RED).append(c);
            e.setDisplayName(c);
        }
    }
}
