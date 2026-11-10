package dev.unyielding.quests.spin;

import dev.unyielding.quests.Config;
import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Daily login reward: one Lucky Spin per real-world day, with a streak bonus for logging in day after day. */
public final class DailyLogin {
    /** Per player: last rewarded day (epoch day) and current streak. */
    public static final class Data extends SavedData {
        public static final String NAME = "unyieldingquests_daily";
        public final Map<UUID, long[]> players = new HashMap<>();

        public static SavedData.Factory<Data> factory() {
            return new SavedData.Factory<>(Data::new, Data::load, null);
        }

        public static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data d = new Data();
            for (Tag e : tag.getList("players", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                d.players.put(c.getUUID("p"), new long[]{c.getLong("day"), c.getLong("streak"), c.getLong("spins"), c.getLong("questSpins")});
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag l = new ListTag();
            for (Map.Entry<UUID, long[]> e : players.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putUUID("p", e.getKey());
                c.putLong("day", e.getValue()[0]);
                c.putLong("streak", e.getValue()[1]);
                c.putLong("spins", e.getValue()[2]);
                c.putLong("questSpins", e.getValue()[3]);
                l.add(c);
            }
            tag.put("players", l);
            return tag;
        }
    }

    private static Data data;
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    private DailyLogin() {
    }

    public static void onServerStart(MinecraftServer s) {
        data = s.overworld().getDataStorage().computeIfAbsent(Data.factory(), Data.NAME);
        PENDING.clear();
        SpinMenu.clear();
    }

    public static void onServerStop() {
        data = null;
        PENDING.clear();
        SpinMenu.clear();
    }

    private static long today() {
        ZoneId zone;
        try {
            zone = ZoneId.of(Config.DAILY_TIMEZONE.get());
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        return LocalDate.now(zone).toEpochDay();
    }

    /** Called on login: queues the reward for a couple of seconds so the player is fully in the world. */
    public static void onLogin(ServerPlayer p) {
        if (data == null || !Config.DAILY_ENABLED.get()) return;
        long[] rec = data.players.get(p.getUUID());
        if (rec != null && rec[0] == today()) return;
        PENDING.put(p.getUUID(), 60);
    }

    public static void onLogout(ServerPlayer p) {
        PENDING.remove(p.getUUID());
        SpinMenu.logout(p);
    }

    public static void onTick(MinecraftServer s) {
        if (PENDING.isEmpty()) return;
        List<UUID> ready = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : PENDING.entrySet()) {
            int n = e.getValue() - 1;
            e.setValue(n);
            if (n <= 0) ready.add(e.getKey());
        }
        for (UUID u : ready) {
            PENDING.remove(u);
            ServerPlayer p = s.getPlayerList().getPlayer(u);
            if (p != null) give(p);
        }
    }

    /** Gives today's reward if it has not been given yet. @return false when already claimed today. */
    public static boolean give(ServerPlayer p) {
        if (data == null || !Config.DAILY_ENABLED.get()) return false;
        long day = today();
        long[] rec = data.players.computeIfAbsent(p.getUUID(), k -> new long[]{0, 0, 0, 0});
        if (rec[0] == day) return false;
        long streak = rec[0] == day - 1 ? rec[1] + 1 : 1;
        rec[0] = day;
        rec[1] = streak;
        data.setDirty();
        int[] w = {Config.DAILY_BASIC.get(), Config.DAILY_GOOD.get(), Config.DAILY_PARADOX.get(), Config.DAILY_MYTHICAL.get(), Config.DAILY_LEGENDARY.get()};
        int every = Config.DAILY_STREAK_EVERY.get();
        int spins = 1;
        if (every > 0 && streak % every == 0) spins += Config.DAILY_STREAK_BONUS_SPINS.get();
        rec[2] = Math.addExact(rec[2], spins);
        data.setDirty();
        Component msg = Component.literal("Daily login reward! ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Streak: " + streak + " day" + (streak == 1 ? "" : "s")).withStyle(ChatFormatting.YELLOW));
        if (spins > 1) msg = msg.copy().append(Component.literal(" — streak bonus: " + spins + " spins!").withStyle(ChatFormatting.LIGHT_PURPLE));
        else if (every > 0) msg = msg.copy().append(Component.literal(" (bonus spin every " + every + " days)").withStyle(ChatFormatting.GRAY));
        p.sendSystemMessage(msg);
        p.sendSystemMessage(Component.literal("Spins saved! Open your inventory and press Lucky Spin."));
        return true;
    }

    public static long available(ServerPlayer p) {
        long[] rec=data == null ? null : data.players.get(p.getUUID());
        return rec == null ? 0 : (Config.DAILY_ENABLED.get() ? rec[2] : 0) + (LuckySpin.enabled() ? rec[3] : 0);
    }

    public static void creditQuest(ServerPlayer p) {
        if (data == null) throw new IllegalStateException("Spin storage is not ready");
        long[] rec=data.players.computeIfAbsent(p.getUUID(), k -> new long[]{0,0,0,0});
        rec[3]=Math.addExact(rec[3],1);
        data.setDirty();
        p.sendSystemMessage(Component.literal("Lucky Spin earned! Open your inventory and press Lucky Spin."));
    }

    /** Only called after a server-validated manual request. */
    public static LuckySpin.Spin redeem(ServerPlayer p) {
        if (available(p)<=0 || !LuckySpin.starterReady(p)) return null;
        long[] rec=data.players.get(p.getUUID());
        int slot=Config.DAILY_ENABLED.get() && rec[2]>0 ? 2 : 3;
        rec[slot]--;
        data.setDirty();
        return LuckySpin.rollWith(p,slot==2?"Daily login":"Earned spin",slot==2?new int[]{Config.DAILY_BASIC.get(),Config.DAILY_GOOD.get(),Config.DAILY_PARADOX.get(),Config.DAILY_MYTHICAL.get(),Config.DAILY_LEGENDARY.get()}:LuckySpin.weights());
    }

    /** "Streak 3 days, next reward tomorrow" style status. */
    public static String status(ServerPlayer p) {
        if (data == null) return "Daily rewards are not available.";
        if (!Config.DAILY_ENABLED.get()) return "Daily login rewards are turned off on this server.";
        long[] rec = data.players.get(p.getUUID());
        long day = today();
        if (rec == null) return "No daily reward yet; it arrives a few seconds after you log in.";
        String streak = "Login streak: " + rec[1] + " day" + (rec[1] == 1 ? "" : "s") + ".";
        return rec[0] == day ? "Today's spins are saved. " + streak + " Come back tomorrow!" : streak + " Today's reward is on its way.";
    }
}
