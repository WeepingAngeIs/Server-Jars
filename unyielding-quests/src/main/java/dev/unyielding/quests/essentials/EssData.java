package dev.unyielding.quests.essentials;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent data of the Essentials module. */
public final class EssData extends SavedData {
    public static final String NAME = "unyieldingquests_essentials";

    public static final class Kit {
        public final List<ItemStack> items = new ArrayList<>();
        public long cooldownSeconds;
    }

    public final Map<UUID, Map<String, Loc>> homes = new HashMap<>();
    public final Map<String, Loc> warps = new LinkedHashMap<>();
    public final Map<UUID, String> nicks = new HashMap<>();
    public final Set<UUID> muted = new HashSet<>();
    public final Set<UUID> rec = new HashSet<>();
    public final Map<UUID, List<Loc>> back = new HashMap<>();
    public final Map<UUID, Loc> lastSeen = new HashMap<>();
    public final Map<UUID, String> names = new HashMap<>();
    public final Map<String, Kit> kits = new LinkedHashMap<>();
    public final Map<UUID, Map<String, Long>> kitClaims = new HashMap<>();

    public static SavedData.Factory<EssData> factory() {
        return new SavedData.Factory<>(EssData::new, EssData::load, null);
    }

    public static EssData load(CompoundTag tag, HolderLookup.Provider provider) {
        EssData d = new EssData();
        for (Tag e : tag.getList("homes", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            Map<String, Loc> m = new LinkedHashMap<>();
            for (Tag h : c.getList("list", Tag.TAG_COMPOUND)) m.put(((CompoundTag) h).getString("name"), Loc.fromTag(((CompoundTag) h).getCompound("loc")));
            d.homes.put(c.getUUID("u"), m);
        }
        for (Tag e : tag.getList("warps", Tag.TAG_COMPOUND)) d.warps.put(((CompoundTag) e).getString("name"), Loc.fromTag(((CompoundTag) e).getCompound("loc")));
        for (Tag e : tag.getList("nicks", Tag.TAG_COMPOUND)) d.nicks.put(((CompoundTag) e).getUUID("u"), ((CompoundTag) e).getString("n"));
        for (Tag e : tag.getList("muted", Tag.TAG_INT_ARRAY)) d.muted.add(net.minecraft.nbt.NbtUtils.loadUUID(e));
        for (Tag e : tag.getList("rec", Tag.TAG_INT_ARRAY)) d.rec.add(net.minecraft.nbt.NbtUtils.loadUUID(e));
        for (Tag e : tag.getList("back", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            List<Loc> l = new ArrayList<>();
            for (Tag h : c.getList("list", Tag.TAG_COMPOUND)) l.add(Loc.fromTag((CompoundTag) h));
            d.back.put(c.getUUID("u"), l);
        }
        for (Tag e : tag.getList("seen", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            d.lastSeen.put(c.getUUID("u"), Loc.fromTag(c.getCompound("loc")));
            d.names.put(c.getUUID("u"), c.getString("name"));
        }
        for (Tag e : tag.getList("kits", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            Kit k = new Kit();
            k.cooldownSeconds = c.getLong("cd");
            for (Tag it : c.getList("items", Tag.TAG_COMPOUND)) {
                ItemStack.parse(provider, it).ifPresent(k.items::add);
            }
            d.kits.put(c.getString("name"), k);
        }
        for (Tag e : tag.getList("claims", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            Map<String, Long> m = new HashMap<>();
            CompoundTag km = c.getCompound("k");
            for (String key : km.getAllKeys()) m.put(key, km.getLong(key));
            d.kitClaims.put(c.getUUID("u"), m);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag homesT = new ListTag();
        for (var e : homes.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", e.getKey());
            ListTag l = new ListTag();
            for (var h : e.getValue().entrySet()) {
                CompoundTag ht = new CompoundTag();
                ht.putString("name", h.getKey());
                ht.put("loc", h.getValue().toTag());
                l.add(ht);
            }
            c.put("list", l);
            homesT.add(c);
        }
        tag.put("homes", homesT);
        ListTag w = new ListTag();
        for (var e : warps.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("name", e.getKey());
            c.put("loc", e.getValue().toTag());
            w.add(c);
        }
        tag.put("warps", w);
        ListTag n = new ListTag();
        for (var e : nicks.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", e.getKey());
            c.putString("n", e.getValue());
            n.add(c);
        }
        tag.put("nicks", n);
        ListTag mu = new ListTag();
        for (UUID u : muted) mu.add(net.minecraft.nbt.NbtUtils.createUUID(u));
        tag.put("muted", mu);
        ListTag rc = new ListTag();
        for (UUID u : rec) rc.add(net.minecraft.nbt.NbtUtils.createUUID(u));
        tag.put("rec", rc);
        ListTag b = new ListTag();
        for (var e : back.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", e.getKey());
            ListTag l = new ListTag();
            for (Loc loc : e.getValue()) l.add(loc.toTag());
            c.put("list", l);
            b.add(c);
        }
        tag.put("back", b);
        ListTag s = new ListTag();
        for (var e : lastSeen.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", e.getKey());
            c.putString("name", names.getOrDefault(e.getKey(), ""));
            c.put("loc", e.getValue().toTag());
            s.add(c);
        }
        tag.put("seen", s);
        ListTag k = new ListTag();
        for (var e : kits.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("name", e.getKey());
            c.putLong("cd", e.getValue().cooldownSeconds);
            ListTag items = new ListTag();
            for (ItemStack st : e.getValue().items) if (!st.isEmpty()) items.add(st.save(provider));
            c.put("items", items);
            k.add(c);
        }
        tag.put("kits", k);
        ListTag cl = new ListTag();
        for (var e : kitClaims.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", e.getKey());
            CompoundTag km = new CompoundTag();
            for (var kc : e.getValue().entrySet()) km.putLong(kc.getKey(), kc.getValue());
            c.put("k", km);
            cl.add(c);
        }
        tag.put("claims", cl);
        return tag;
    }
}
