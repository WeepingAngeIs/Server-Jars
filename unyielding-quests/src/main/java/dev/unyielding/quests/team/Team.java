package dev.unyielding.quests.team;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A team: every player has a personal one; players can also form parties; admins can create server teams. */
public final class Team {
    public enum Type {PLAYER, PARTY, SERVER}

    public enum Rank {
        MEMBER, OFFICER, OWNER;

        public boolean atLeast(Rank other) {
            return ordinal() >= other.ordinal();
        }
    }

    public static final class Message {
        public UUID from;
        public String fromName = "";
        public long time;
        public String text = "";
    }

    public static final int MAX_HISTORY = 100;

    public UUID id;
    public Type type = Type.PLAYER;
    public String name = "";
    public String description = "";
    public int color = 0x55AAFF;
    public boolean freeToJoin;
    public UUID owner;
    public final Map<UUID, Rank> members = new LinkedHashMap<>();
    public final Map<UUID, String> names = new LinkedHashMap<>();
    public long created = System.currentTimeMillis();
    public final List<Message> history = new ArrayList<>();

    public boolean isParty() {
        return type == Type.PARTY;
    }

    public Rank rankOf(UUID player) {
        Rank r = members.get(player);
        if (r != null) return r;
        return type == Type.PLAYER && player.equals(id) ? Rank.OWNER : null;
    }

    public boolean isMember(UUID player) {
        return rankOf(player) != null;
    }

    public String nameOf(UUID player) {
        return names.getOrDefault(player, player.toString().substring(0, 8));
    }

    public void addMessage(UUID from, String fromName, String text) {
        Message m = new Message();
        m.from = from;
        m.fromName = fromName;
        m.time = System.currentTimeMillis();
        m.text = text;
        history.add(m);
        while (history.size() > MAX_HISTORY) history.remove(0);
    }

    public CompoundTag toTag(boolean withHistory) {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("type", type.name());
        t.putString("name", name);
        t.putString("desc", description);
        t.putInt("color", color);
        t.putBoolean("free", freeToJoin);
        if (owner != null) t.putUUID("owner", owner);
        t.putLong("created", created);
        ListTag ms = new ListTag();
        for (Map.Entry<UUID, Rank> e : members.entrySet()) {
            CompoundTag m = new CompoundTag();
            m.putUUID("u", e.getKey());
            m.putString("r", e.getValue().name());
            m.putString("n", names.getOrDefault(e.getKey(), ""));
            ms.add(m);
        }
        t.put("members", ms);
        if (withHistory) {
            ListTag hs = new ListTag();
            for (Message m : history) {
                CompoundTag h = new CompoundTag();
                if (m.from != null) h.putUUID("f", m.from);
                h.putString("n", m.fromName);
                h.putLong("t", m.time);
                h.putString("x", m.text);
                hs.add(h);
            }
            t.put("history", hs);
        }
        return t;
    }

    public static Team fromTag(CompoundTag t) {
        Team team = new Team();
        team.id = t.getUUID("id");
        try {
            team.type = Type.valueOf(t.getString("type"));
        } catch (IllegalArgumentException e) {
            team.type = Type.PARTY;
        }
        team.name = t.getString("name");
        team.description = t.getString("desc");
        team.color = t.getInt("color");
        team.freeToJoin = t.getBoolean("free");
        if (t.hasUUID("owner")) team.owner = t.getUUID("owner");
        team.created = t.getLong("created");
        for (Tag e : t.getList("members", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) e;
            UUID u = m.getUUID("u");
            Rank r;
            try {
                r = Rank.valueOf(m.getString("r"));
            } catch (IllegalArgumentException ex) {
                r = Rank.MEMBER;
            }
            team.members.put(u, r);
            team.names.put(u, m.getString("n"));
        }
        for (Tag e : t.getList("history", Tag.TAG_COMPOUND)) {
            CompoundTag h = (CompoundTag) e;
            Message m = new Message();
            if (h.hasUUID("f")) m.from = h.getUUID("f");
            m.fromName = h.getString("n");
            m.time = h.getLong("t");
            m.text = h.getString("x");
            team.history.add(m);
        }
        return team;
    }
}
