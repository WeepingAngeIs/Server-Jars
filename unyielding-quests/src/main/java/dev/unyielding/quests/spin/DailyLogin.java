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
import java.util.HashSet;
import java.util.Set;
import java.io.IOException;

/** Daily login reward: one Lucky Spin per real-world day, with a streak bonus for logging in day after day. */
public final class DailyLogin {
    /** Per player: last rewarded day (epoch day) and current streak. */
    public static final class Data extends SavedData {
        public static final String NAME = "unyieldingquests_daily";
        public final Map<UUID, long[]> players = new HashMap<>();
        public final Set<UUID> appliedDebits = new HashSet<>();
        private Tag invalidDebitHistory;
        boolean hasValidDebitHistory() { return invalidDebitHistory == null; }

        public static SavedData.Factory<Data> factory() {
            return new SavedData.Factory<>(Data::new, Data::load, null);
        }

        public static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data d = new Data();
            for (Tag e : tag.getList("players", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                d.players.put(c.getUUID("p"), new long[]{c.getLong("day"), c.getLong("streak"), c.getLong("spins"), c.getLong("questSpins"), c.getLong("lastClaimMillis")});
            }
            if (tag.contains("appliedDebits")) {
                Tag raw = tag.get("appliedDebits");
                if (!(raw instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
                    d.invalidDebitHistory = raw.copy();
                } else {
                    for (Tag e : (ListTag)raw) {
                        CompoundTag c = (CompoundTag)e;
                        if (!c.hasUUID("id") || !d.appliedDebits.add(c.getUUID("id"))) {
                            d.invalidDebitHistory = raw.copy();
                            break;
                        }
                    }
                }
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
                c.putLong("lastClaimMillis", e.getValue()[4]);
                l.add(c);
            }
            tag.put("players", l);
            ListTag debits = new ListTag();
            for (UUID id : appliedDebits) { CompoundTag c = new CompoundTag(); c.putUUID("id", id); debits.add(c); }
            tag.put("appliedDebits", invalidDebitHistory == null ? debits : invalidDebitHistory.copy());
            return tag;
        }
    }

    private static Data data;
    private static RewardJournal rewardJournal;
    private static boolean recoveryReady;
    public static RewardJournal journal() { return recoveryReady ? rewardJournal : null; }
    public static RewardJournal journalForReview() { return rewardJournal; }
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    private DailyLogin() {
    }

    public static void onServerStart(MinecraftServer s) {
        data = s.overworld().getDataStorage().computeIfAbsent(Data.factory(), Data.NAME);
        recoveryReady = false;
        try {
            rewardJournal = new RewardJournal(s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("unyielding-recovery"));
            retryRecovery();
        } catch (IOException | RuntimeException failure) {
            UnyieldingQuests.LOGGER.error("Lucky Spin recovery unavailable; payouts are disabled pending admin review", failure);
        }
        PENDING.clear();
        SpinMenu.clear();
    }

    public static void onServerStop() {
        recoveryReady = false;
        if (rewardJournal != null) try { rewardJournal.close(); } catch (IOException e) { UnyieldingQuests.LOGGER.error("Could not close reward journal", e); }
        rewardJournal = null;
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
        if (rec != null && rec[0] >= today()) return;
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
            ServerPlayer p = s.getPlayerList().getPlayer(u);
            if (p == null || !Config.DAILY_ENABLED.get()) PENDING.remove(u);
            else if (give(p)) PENDING.remove(u);
            else if (data == null || data.players.get(u) == null || data.players.get(u)[0] >= today()) PENDING.remove(u);
            else PENDING.put(u, 1200); // Retry after a minute if the minimum interval has not passed.
        }
    }

    /** Gives today's reward if it has not been given yet. @return false when already claimed today. */
    public static boolean give(ServerPlayer p) {
        if (data == null || data.invalidDebitHistory != null || !Config.DAILY_ENABLED.get()) return false;
        long day = today();
        long now = System.currentTimeMillis();
        long[] rec = data.players.computeIfAbsent(p.getUUID(), k -> new long[]{0, 0, 0, 0, 0});
        if (!DailyRewardPolicy.eligible(rec[0], rec[4], day, now)) return false;
        long streak = DailyRewardPolicy.nextStreak(rec[0], rec[1], day, Config.DAILY_GRACE_DAYS.get());
        int every = Config.DAILY_STREAK_EVERY.get();
        int spins = 1;
        if (every > 0 && streak % every == 0) spins += Config.DAILY_STREAK_BONUS_SPINS.get();
        long balance = Math.addExact(rec[2], spins);
        rec[0] = day;
        rec[1] = streak;
        rec[4] = now;
        rec[2] = balance;
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
        long[] rec=data == null || data.invalidDebitHistory != null ? null : data.players.get(p.getUUID());
        return rec == null ? 0 : (Config.DAILY_ENABLED.get() ? rec[2] : 0) + (LuckySpin.enabled() ? rec[3] : 0);
    }

    public static void creditQuest(ServerPlayer p) {
        if (data == null || data.invalidDebitHistory != null) throw new IllegalStateException("Spin storage is not ready; debit history requires review");
        long[] rec=data.players.computeIfAbsent(p.getUUID(), k -> new long[]{0,0,0,0,0});
        rec[3]=Math.addExact(rec[3],1);
        data.setDirty();
        p.sendSystemMessage(Component.literal("Lucky Spin earned! Open your inventory and press Lucky Spin."));
    }

    private static boolean applyDebit(RewardJournal.Entry entry) {
        if (!RewardDebit.apply(data.players.get(entry.player()), data.appliedDebits, entry.id(), entry.slot())) return false;
        data.setDirty();
        return true;
    }
    public static boolean reconcileDebit(UUID id) throws IOException {
        if (journal() == null || data == null) return false;
        RewardJournal.Entry entry = rewardJournal.read(id);
        return entry != null && applyDebit(entry);
    }
    /** Recheck existing records only; never deliver, refund, or replay a prize. */
    public static void retryRecovery() throws IOException {
        recoveryReady = false;
        if (data == null || rewardJournal == null) throw new IOException("Reward journal is unavailable");
        if (data.invalidDebitHistory != null) throw new IOException("Saved debit history is malformed; restore it before retrying");
        var entries = rewardJournal.list();
        Set<UUID> journalIds = new HashSet<>();
        for (RewardJournal.Entry entry : entries) journalIds.add(entry.id());
        if (!RewardDebit.journalCoversMarkers(data.appliedDebits, journalIds))
            throw new IOException("Reward journal is missing previously recorded transactions; restore it before retrying");
        for (RewardJournal.Entry entry : entries) {
            if (!applyDebit(entry)) throw new IOException("Cannot reconcile debit " + entry.id());
            if (entry.state() == RewardJournal.State.PREPARED || entry.state() == RewardJournal.State.DEBIT_APPLIED || entry.state() == RewardJournal.State.DELIVERING)
                rewardJournal.transition(entry.id(), entry.state(), RewardJournal.State.HELD);
        }
        recoveryReady = true;
    }

    /** Only called after a server-validated manual request. */
    public static LuckySpin.Spin redeem(ServerPlayer p) {
        if (available(p)<=0 || !LuckySpin.starterReady(p)) return null;
        if (journal() == null) throw new IllegalStateException("Reward recovery is unavailable; contact an administrator.");
        long[] rec=data.players.get(p.getUUID());
        int slot=Config.DAILY_ENABLED.get() && rec[2]>0 ? 2 : 3;
        int[] weights=slot==2?new int[]{Config.DAILY_BASIC.get(),Config.DAILY_GOOD.get(),Config.DAILY_PARADOX.get(),Config.DAILY_MYTHICAL.get(),Config.DAILY_LEGENDARY.get()}:LuckySpin.weights();
        CompoundTag selected = LuckySpin.prepare(p, weights);
        UUID id=UUID.randomUUID();
        try {
            RewardJournal.Entry entry=rewardJournal.begin(id,p.getUUID(),slot,selected.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!applyDebit(entry)) throw new IOException("Cannot apply payout debit " + id);
            rewardJournal.transition(id,RewardJournal.State.PREPARED,RewardJournal.State.DEBIT_APPLIED);
            rewardJournal.transition(id,RewardJournal.State.DEBIT_APPLIED,RewardJournal.State.DELIVERING);
            LuckySpin.Spin result=LuckySpin.deliverPrepared(p,selected,slot==2?"Daily login":"Earned spin",weights);
            // Player and Pokemon storage are saved separately; an operator must reconcile this record.
            rewardJournal.transition(id,RewardJournal.State.DELIVERING,RewardJournal.State.HELD);
            return result;
        } catch (IOException failure) {
            recoveryReady=false;
            throw new IllegalStateException("Payout requires administrator recovery ("+id+").",failure);
        } catch (RuntimeException failure) {
            try {
                RewardJournal.Entry entry=rewardJournal.read(id);
                if(entry!=null && entry.state()!=RewardJournal.State.HELD && entry.state()!=RewardJournal.State.COMPLETE)
                    rewardJournal.transition(id,entry.state(),RewardJournal.State.HELD);
            } catch (IOException journalFailure) { recoveryReady=false; failure.addSuppressed(journalFailure); }
            throw failure;
        }
    }

    /** "Streak 3 days, next reward tomorrow" style status. */
    public static String status(ServerPlayer p) {
        if (data == null) return "Daily rewards are not available.";
        if (!Config.DAILY_ENABLED.get()) return "Daily login rewards are turned off on this server.";
        long[] rec = data.players.get(p.getUUID());
        long day = today();
        if (rec == null) return "No daily reward yet; it arrives a few seconds after you log in.";
        String streak = "Login streak: " + rec[1] + " day" + (rec[1] == 1 ? "" : "s") + ".";
        String policy = " Up to " + Config.DAILY_GRACE_DAYS.get() + " missed days keep your streak; missed days do not give spins. Claims require a new calendar day and 20 hours since the previous claim.";
        if (rec[0] > day) return "The server's date is behind your last claim. " + streak + policy;
        if (rec[0] == day) return "Today's spins are saved. " + streak + policy;
        if (rec[4] > 0 && System.currentTimeMillis() - rec[4] < DailyRewardPolicy.MIN_CLAIM_MILLIS) return streak + " Your next reward is waiting for the 20-hour interval." + policy;
        return streak + " Today's reward is on its way." + policy;
    }
}
