package dev.unyielding.jail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** The pack's Onion history is an ordered food log. Only its native provider copies it. */
public final class JailFoodProgress {
    private static final String FOOD = "solonion:foodlist";
    private static final String BENEFIT = "solonion:benefit";
    private static final String HEALTH = "minecraft:generic.max_health";
    private static final String TOUGHNESS = "minecraft:generic.armor_toughness";
    private static final String ACTIVE = "solonion:add_value";
    private static final String BANK = "solcycle:cycle_";
    static final String RECEIPT = "food_progress";
    private static final ThreadLocal<ServerPlayer> KILL = new ThreadLocal<>();
    private static final ThreadLocal<ServerPlayer> CLONE = new ThreadLocal<>();

    private JailFoodProgress() {}
    static boolean attachment(String name) { return name.equals(FOOD) || name.equals(BENEFIT); }
    private static void require(boolean condition, String why) throws IOException {
        if (!condition) throw new IOException(why);
    }

    static CompoundTag scores(ServerPlayer player) throws IOException {
        var board = player.server.getScoreboard();
        var result = new CompoundTag();
        for (String name : List.of("so_cycle", "so_death", "so_bonus", "so_lock")) {
            var objective = board.getObjective(name);
            if (objective == null) continue;
            var criterion = name.equals("so_death") ? ObjectiveCriteria.DEATH_COUNT : ObjectiveCriteria.DUMMY;
            require(objective.getCriteria() == criterion, "Unexpected food-cycle objective type");
            var score = board.getPlayerScoreInfo(player, objective);
            result.putInt(name, score == null ? 0 : score.value());
        }
        return result;
    }

    static boolean candidate(String modifier) {
        return modifier.equals(ACTIVE) || modifier.startsWith(BANK);
    }

    private static boolean banked(String attribute, String id, int cycles) {
        if (!attribute.equals(HEALTH) || !id.startsWith(BANK)) return false;
        String suffix = id.substring(BANK.length());
        try {
            int index = Integer.parseInt(suffix);
            return index >= 1 && index <= cycles && Integer.toString(index).equals(suffix);
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static boolean positiveModifier(CompoundTag modifier) {
        return modifier.getAllKeys().equals(Set.of("amount", "operation", "id"))
                && modifier.contains("amount",Tag.TAG_DOUBLE) && Double.isFinite(modifier.getDouble("amount"))
                && modifier.getDouble("amount") > 0 && modifier.getString("operation").equals("add_value");
    }

    // The native provider serializes its applied-attribute HashMap as an unordered list.
    // Key by attribute, retaining every typed field and rejecting duplicate or unfamiliar entries.
    static CompoundTag benefits(CompoundTag attachments) throws IOException {
        require(!attachments.contains(BENEFIT) || attachments.contains(BENEFIT,Tag.TAG_COMPOUND), "Malformed Onion benefits");
        var envelope = attachments.getCompound(BENEFIT);
        var result = new CompoundTag();
        if (envelope.isEmpty()) return result;
        require(envelope.getAllKeys().equals(Set.of("att")) && envelope.get("att") instanceof ListTag, "Unknown Onion benefit type");
        for (Tag entry : (ListTag)envelope.get("att")) {
            require(entry instanceof CompoundTag, "Malformed Onion attribute benefit");
            var row = (CompoundTag)entry;
            String attribute = row.getString("att");
            var mod = row.getCompound("mod");
            require(row.getAllKeys().equals(Set.of("att","op","mod")) && row.contains("op",Tag.TAG_INT)
                    && row.getInt("op")==0 && (attribute.equals(HEALTH) || attribute.equals(TOUGHNESS))
                    && mod.getString("id").equals(ACTIVE) && positiveModifier(mod) && !result.contains(attribute),
                    "Unrecognized Onion attribute benefit");
            result.put(attribute,row.copy());
        }
        return result;
    }

    static CompoundTag captured(CompoundTag saved, CompoundTag scores) throws IOException {
        var attachments = saved.getCompound("neoforge:attachments");
        require(!attachments.contains(FOOD) || attachments.contains(FOOD,Tag.TAG_LIST), "Malformed Onion food history");
        ListTag food = attachments.contains(FOOD) ? (ListTag)attachments.get(FOOD) : new ListTag();
        require(food.isEmpty() || food.getElementType()==Tag.TAG_COMPOUND, "Malformed Onion food log entry");
        var active = benefits(attachments);
        var history = new CompoundTag();
        history.put("foods",food);
        history.put("benefits",active);
        history.putBoolean("applied_attribute_map",attachments.getCompound(BENEFIT).contains("att"));
        var modifiers = new CompoundTag();
        for (Tag tag : saved.getList("attributes",Tag.TAG_COMPOUND)) {
            var row = (CompoundTag)tag;
            String attribute = row.getString("id");
            for (Tag m : row.getList("modifiers",Tag.TAG_COMPOUND)) {
                var mod = (CompoundTag)m;
                String id = mod.getString("id");
                if (!candidate(id)) continue;
                boolean owned = id.equals(ACTIVE) ? active.contains(attribute)
                        && mod.equals(active.getCompound(attribute).getCompound("mod"))
                        : banked(attribute,id,scores.getInt("so_cycle"));
                require(owned && positiveModifier(mod), "Food modifier differs from its provider's records");
                if (!modifiers.contains(attribute)) modifiers.put(attribute,new CompoundTag());
                var group = modifiers.getCompound(attribute);
                require(!group.contains(id), "Duplicate food modifier");
                group.put(id,mod.copy());
            }
        }
        for (String key : active.getAllKeys())
            require(modifiers.getCompound(key).contains(ACTIVE), "Missing permanent food benefit");
        var result = new CompoundTag();
        result.putByteArray("history",JailOriginTransaction.hash(JailNbt.encode(history)));
        result.put("scores",scores.copy());
        result.put("modifiers",modifiers);
        return result;
    }

    private static CompoundTag capture(ServerPlayer player) throws IOException {
        return captured(player.saveWithoutId(new CompoundTag()),scores(player));
    }

    static void validate(ServerPlayer player, boolean preflight) throws IOException {
        var proof = capture(player);
        if (preflight) require(proof.getCompound("scores").getInt("so_death")==0,
                "A prior ordinary death is awaiting native food-cycle cleanup");
    }

    // One immutable observation per synchronous audit. Established players may have many cycles.
    static final class ModifierAudit {
        private final ServerPlayer player;
        private CompoundTag modifiers;
        ModifierAudit(ServerPlayer player) { this.player=player; }
        boolean owns(String attribute, String id, double amount, String operation) throws IOException {
            if (!operation.equals("ADD_VALUE")) return false;
            if (modifiers==null) modifiers=JailHumanAudit.hasItems249Contract(player)
                    ? capture(player).getCompound("modifiers") : new CompoundTag();
            var mods = modifiers.getCompound(attribute);
            return mods.contains(id) && Double.compare(amount,mods.getCompound(id).getDouble("amount"))==0;
        }
    }

    static void record(ServerPlayer player, CompoundTag attempt) throws IOException {
        if (JailHumanAudit.hasItems249Contract(player)) attempt.put(RECEIPT,capture(player));
    }

    static void beginKill(ServerPlayer player, CompoundTag attempt) {
        if (attempt.contains(RECEIPT,Tag.TAG_COMPOUND)) KILL.set(player);
    }
    static void endKill() { KILL.remove(); }
    public static boolean protectsScore(ServerPlayer player) { return KILL.get()==player && JailEscapeCustody.ownDamage(player); }

    static void beginClone(ServerPlayer old, boolean food) {
        if (food) CLONE.set(old);
    }
    static void endClone() { CLONE.remove(); }
    public static boolean protectsClone(PlayerEvent.Clone event) {
        return event.getOriginal() instanceof ServerPlayer old && CLONE.get()==old && JailAutoRespawn.isAutomaticInvocation(old)
                && event.getEntity() instanceof ServerPlayer next && next.server==old.server && next.getUUID().equals(old.getUUID());
    }
    public static boolean awaitVerifiedRespawn(ServerPlayer player) {
        return !player.isAlive() && !JailAutoRespawn.isAutomaticInvocation(player)
                && JailData.get(player.server).assignment(player.getUUID())!=null && JailEscapeCustody.pendingFoodDeath(player);
    }

    static void copyModifiers(ServerPlayer old, ServerPlayer next, CompoundTag attempt) throws IOException {
        if (!attempt.contains(RECEIPT)) return;
        var receipt = attempt.getCompound(RECEIPT);
        require(CLONE.get()==old && JailAutoRespawn.isAutomaticInvocation(old), "Unverified food clone");
        var modifiers = receipt.getCompound("modifiers");
        // Check every destination first. Accept an existing native copy only when it agrees exactly.
        var missing = new HashMap<AttributeInstance,List<AttributeModifier>>();
        for (String attribute : modifiers.getAllKeys()) {
            var holder = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(attribute)).orElseThrow();
            var live = next.getAttribute(holder);
            require(live!=null, "Replacement lacks food attribute");
            var group = modifiers.getCompound(attribute);
            for (String id : group.getAllKeys()) {
                var wanted = AttributeModifier.load(group.getCompound(id));
                require(wanted!=null, "Unreadable food modifier");
                var existing = live.getModifier(wanted.id());
                require(existing==null || existing.equals(wanted), "Conflicting food modifier on replacement");
                if (existing==null) missing.computeIfAbsent(live,key -> new ArrayList<>()).add(wanted);
            }
        }
        missing.forEach((attribute,values) -> values.forEach(attribute::addPermanentModifier));
    }

    static void verifyClone(ServerPlayer player, CompoundTag attempt, CompoundTag disk) throws IOException {
        if (!attempt.contains(RECEIPT)) return;
        var receipt = attempt.getCompound(RECEIPT);
        require(receipt.equals(capture(player)) && receipt.equals(captured(disk,scores(player))),
                "Food history, benefits, banked hearts or food-cycle scores changed during jail respawn");
    }
}
