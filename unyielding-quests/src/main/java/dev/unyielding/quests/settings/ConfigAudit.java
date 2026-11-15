package dev.unyielding.quests.settings;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Recent single-value config edits, persisted with normal world SavedData. */
public final class ConfigAudit extends SavedData {
    private static final int LIMIT = 100;
    private final List<CompoundTag> entries = new ArrayList<>();
    private static ConfigAudit get(ServerPlayer player) {
        return player.server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ConfigAudit::new, ConfigAudit::load, null), "unyieldingquests_config_audit");
    }
    private static ConfigAudit load(CompoundTag tag, HolderLookup.Provider provider) {
        ConfigAudit audit = new ConfigAudit();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = ConfigAuditPolicy.retainedStart(list.size(), LIMIT); i < list.size(); i++) audit.entries.add(list.getCompound(i).copy());
        return audit;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (CompoundTag entry : entries) list.add(entry.copy());
        tag.put("entries", list);
        return tag;
    }
    static void record(ServerPlayer player, String key, Object before, Object after) {
        ConfigAudit audit = get(player);
        CompoundTag entry = new CompoundTag();
        entry.putString("actor", player.getGameProfile().getName());
        entry.putUUID("actorId", player.getUUID());
        entry.putString("key", key);
        entry.putLong("time", System.currentTimeMillis());
        // Avoid persisting potentially sensitive or very large string/list configuration values.
        entry.putString("before", ConfigAuditPolicy.printable(before));
        entry.putString("after", ConfigAuditPolicy.printable(after));
        audit.entries.add(entry);
        while (audit.entries.size() > LIMIT) audit.entries.remove(0);
        audit.setDirty();
    }
    public static int show(ServerPlayer player) {
        if (!player.hasPermissions(2)) return 0;
        ConfigAudit audit = get(player);
        player.sendSystemMessage(Component.literal("Recent single-setting edits (newest first):"));
        for (int i = audit.entries.size() - 1; i >= Math.max(0, audit.entries.size() - 10); i--) {
            CompoundTag e = audit.entries.get(i);
            player.sendSystemMessage(Component.literal(Instant.ofEpochMilli(e.getLong("time")) + " " + e.getString("actor") + ": " + e.getString("key") + " " + e.getString("before") + " → " + e.getString("after")));
        }
        if (audit.entries.isEmpty()) player.sendSystemMessage(Component.literal("No individual config edits recorded yet."));
        return 1;
    }
}
