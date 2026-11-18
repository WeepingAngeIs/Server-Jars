package dev.unyielding.bundlesmoke;

import dev.unyielding.jail.JailConfig;
import java.nio.file.Files;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;

import static dev.unyielding.bundlesmoke.PlayerOnlySmoke.*;

/** One real full-pack client, to fit a production dedicated server and client on the same runner. */
public final class FullPackSession {
    private FullPackSession() { }
    private static boolean observedFresh;

    static boolean prepared(ServerPlayer player) throws Exception {
        if (!observedFresh) {
            snapshot(player,"fresh");
            noClaims(player);
            Files.writeString(results.resolve("full249-onboarding-start.txt"),"Fresh player sampled.\n");
            observedFresh=true;
        }
        return Files.isRegularFile(results.resolve("full249-onboarding-complete.txt"));
    }

    static void noClaims(ServerPlayer player) throws Exception {
        var value=OriginCustodyPort.type("com.cyberday1.neoorigins.config.AdminConfig")
                .getField("UNIQUE_ORIGIN_LAYERS").get(null);
        require(((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?>)value).get() instanceof java.util.List<?> layers && layers.isEmpty(),
                "The pack's ordinary nonunique origin rules changed");
        Object claims=OriginCustodyPort.call(OriginCustodyPort.CLAIMS,null,"get",
                new Class<?>[]{net.minecraft.server.MinecraftServer.class},player.server);
        var entries=(Map<?,?>)OriginCustodyPort.call(OriginCustodyPort.CLAIMS,claims,"view");
        require(entries.values().stream().allMatch(row->row instanceof Map<?,?> m && m.isEmpty()),
                "Fresh nonunique world contains unexpected origin claims");
    }

    static void seedEquipment(ServerPlayer player) throws Exception {
        require(CertifiedJailSmoke.choices().equals(Map.of(
                ResourceLocation.parse("neoorigins:origin"),ResourceLocation.parse("neoorigins:human"),
                ResourceLocation.parse("neoorigins:class"),ResourceLocation.parse("neoorigins:class_warrior"))),
                "Native onboarding did not select Human and Warrior");
        noClaims(player);
        // Test gear in the newly created player's vanilla slots. All accessory slots
        // retain the pack's natural state and native tag/provider restrictions.
        VanillaCustodySmoke.seed(player);
        player.getInventory().selected=2;
        var coin=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("lightmanscurrency:coin_gold")),7);
        coin.set(DataComponents.CUSTOM_NAME,Component.literal("Exact packed coin stack"));
        player.getInventory().setItem(23,coin);
        require(player.getMaxHealth()==22,"Warrior was not applied by native origin selection");
        player.setHealth(player.getMaxHealth());
        PackedWalletProof.seed(player);
        CertifiedJailSmoke.flush();
        CertifiedJailSmoke.saved();
        snapshot(player,"seeded-before-preflight");
        var port=CertifiedJailSmoke.items(UUID.randomUUID(),null);
        port.validateRemoval();
        Files.write(evidence(player,"equipment"),port.current().inventory());
    }

    private static final Set<String> OBSERVED = Set.of(
        "aether:aether_player", "apotheosis:radial_mining_mode", "apotheosis:tier_augments_applied",
        "apotheosis:world_tier", "apothic_attributes:cooldowns", "ars_nouveau:mana_cap",
        "ars_nouveau:player_cap", "carryon:carry_on_data", "cataclysm:hook_falling",
        "eternal_starlight:abyssal_fire_ticks", "eternal_starlight:guidebook_listening_namespaces",
        "eternal_starlight:in_ether", "eternal_starlight:in_ether_ticks", "eternal_starlight:meteor_cooldown",
        "eternal_starlight:numbness_damage", "eternal_starlight:special_item_cooldowns",
        "eternal_starlight:stranghoul_hiring_cooldown", "iceandfire:chain_data", "iceandfire:misc_data",
        "l2core:conditionals", "l2core:flags", "solonion:benefit", "solonion:foodlist",
        "twilightforest:feather_fan_falling", "twilightforest:has_double_jump",
        "twilightforest:is_gradually_gliding", "twilightforest:last_damage_armor_time",
        "twilightforest:slimy_soles_bounce_info", "wands:player_data"
    );

    static void snapshot(ServerPlayer player, String stage) throws Exception {
        if (!"true".equals(System.getenv("GITHUB_ACTIONS")) || !"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))
                || !CertifiedJailSmoke.world.toRealPath().getFileName().toString().equals("jail_certified_smoke")
                || !player.getGameProfile().getName().equals("Visitor"))
            throw new IllegalStateException("Diagnostics require the disposable Visitor fixture");
        var nbt = player.saveWithoutId(new CompoundTag());
        var attachments = nbt.getCompound("neoforge:attachments");
        var data = new CompoundTag();
        var shapes = new CompoundTag();
        for (String key : attachments.getAllKeys()) {
            if (key.contains("cobble") || key.contains("poke")) continue;
            Tag value = attachments.get(key);
            shapes.putString(key,value.getType().getName());
            if (!stage.equals("fresh") || !OBSERVED.contains(key)) continue;
            if (key.equals("carryon:carry_on_data") && (!(value instanceof CompoundTag t)
                    || t.getAllKeys().stream().anyMatch(k->k.matches("(?i).*(type|block|entity|tile).*")))) continue;
            data.put(key,value.copy());
        }
        var result = new CompoundTag();
        result.put("observed_neutral",data);
        result.put("shapes",shapes);
        result.put("attributes",nbt.getList("attributes",Tag.TAG_COMPOUND).copy());
        if (stage.equals("fresh")) {
            var live = new net.minecraft.nbt.ListTag();
            for (var row : player.getAttributes().save()) {
                var attribute = ResourceLocation.parse(((CompoundTag)row).getString("id"));
                var holder = BuiltInRegistries.ATTRIBUTE.getHolder(attribute).orElseThrow();
                var instance = player.getAttribute(holder);
                if (instance == null) continue;
                for (var modifier : instance.getModifiers()) {
                    var namespaces = Set.of("spell_power","apotheosis","apothic_attributes");
                    if (!namespaces.contains(attribute.getNamespace()) && !namespaces.contains(modifier.id().getNamespace())) continue;
                    var value = new CompoundTag();
                    value.putString("attribute",attribute.toString());
                    value.putString("id",modifier.id().toString());
                    value.putDouble("amount",modifier.amount());
                    value.putString("operation",modifier.operation().name());
                    live.add(value);
                }
            }
            result.put("live_modifiers",live);
        }
        var persisted = player.getPersistentData();
        var keyNames = new net.minecraft.nbt.ListTag();
        for (String key : persisted.getAllKeys()) {
            if (!key.toLowerCase(java.util.Locale.ROOT).contains("poke")
                    && !key.toLowerCase(java.util.Locale.ROOT).contains("cobble"))
                keyNames.add(net.minecraft.nbt.StringTag.valueOf(key));
        }
        result.put("persistent_keys",keyNames);
        if (stage.equals("fresh") || stage.equals("seeded-before-preflight") || stage.equals("seeded")) {
            if (persisted.contains("dangerous_first_join", Tag.TAG_ANY_NUMERIC))
                result.put("dangerous_first_join",persisted.get("dangerous_first_join").copy());
            var insanity = persisted.getCompound("insanelib");
            var sanityTypes = new CompoundTag();
            for (String key : insanity.getAllKeys()) {
                String lower = key.toLowerCase(java.util.Locale.ROOT);
                if (!lower.contains("cobble") && !lower.contains("poke"))
                    sanityTypes.putString(key,insanity.get(key).getType().getName());
            }
            result.put("insanelib_shapes",sanityTypes);
            var neutralInsanity = new CompoundTag();
            if (insanity.contains("in_combat",Tag.TAG_ANY_NUMERIC))
                neutralInsanity.put("in_combat",insanity.get("in_combat").copy());
            var extra = insanity.get("nbt_tags");
            if (extra instanceof CompoundTag c && c.isEmpty() || extra instanceof net.minecraft.nbt.ListTag l && l.isEmpty())
                neutralInsanity.put("nbt_tags",extra.copy());
            result.put("insanelib",neutralInsanity);
            var carry = attachments.getCompound("carryon:carry_on_data");
            var inputs = new CompoundTag();
            for (String key : Set.of("tick", "selected", "keyPressed"))
                if (carry.contains(key,Tag.TAG_ANY_NUMERIC)) inputs.put(key,carry.get(key).copy());
            result.put("carryon_control_inputs",inputs);
            var mana = attachments.getCompound("ars_nouveau:mana_cap");
            if (mana.contains("current",Tag.TAG_ANY_NUMERIC))
                result.put("ars_mana_current",mana.get("current").copy());
        }
        Files.writeString(results.resolve("synthetic-player-"+stage+".snbt"),result.toString());
    }
    static void begin(ServerPlayer player) throws Exception {
        Custody249Proof.runtime();
        CenterBuildProof.place(player,player);
        flag("preview_place_undo_and_six_intact_cells");
        player.server.getPlayerList().deop(player.getGameProfile());
        require(!player.hasPermissions(2), "Still an administrator after fixture setup");
        require(!JailConfig.JAIL_CERTIFIED_ADMISSIONS.get() && !JailConfig.JAIL_PACKED_WALLETS.get(), "Admission defaults changed");
        seedEquipment(player);
        snapshot(player,"seeded");
        require(cmd("jailcustody init")==1 && cmd("jailintegrated init")==1, "Cannot initialize fresh custody journals");
        require(cmd("jail Visitor 6")==0, "Default-disabled admission accepted");
        require(cmd("jailintegrated mode supported")==1,"Supported mode unavailable");
        JailConfig.JAIL_PACKED_WALLETS.set(false);
        require(cmd("jail Visitor 6")==0,"Wallet accepted without opt-in");
        JailConfig.JAIL_PACKED_WALLETS.set(true);
        JailConfig.JAIL_PACKED_WALLETS.save();
        Custody249Proof.refusals(player);
        var persistent = player.getPersistentData();
        boolean alreadyPersisted = persistent.contains("PlayerPersisted");
        var original = persistent.getCompound("PlayerPersisted").copy();
        var pending = original.copy();
        require(!pending.contains("TFCharmInventory"), "Fresh fixture already has pending Twilight items");
        var savedSlots = new net.minecraft.nbt.ListTag();
        savedSlots.add(PackedWalletProof.encoded(player, player.getInventory().getItem(PackedWalletProof.SLOT)));
        pending.put("TFCharmInventory", savedSlots);
        persistent.put("PlayerPersisted", pending.copy());
        Custody249Proof.refused(player, "twilight_pending_charm_inventory");
        require(pending.equals(persistent.getCompound("PlayerPersisted")), "Refusal consumed the Twilight recovery");
        if (alreadyPersisted) persistent.put("PlayerPersisted",original); else persistent.remove("PlayerPersisted");
        String marker = java.util.UUID.randomUUID().toString();
        var progression = original.copy();
        progression.putString("uq_disposable_progress",marker);
        persistent.put("PlayerPersisted",progression);
        Files.writeString(evidence(player,"progress"),marker);

        require(cmd("jail Visitor 6")==1,"Supported packed-wallet admission refused");
        noClaims(player);
        flag("native_human_warrior_selection_without_unique_claims");
        remember(player);
        cell(player,6);
        snapshot(player,"held");
        require(!player.serverLevel().getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN), "Global immediate respawn changed");
        corpse=player;
        firstDeaths=deaths(player);
        player.setInvulnerable(false);
        CertifiedJailSmoke.cross();
        require(!player.isAlive() && deaths(player)==firstDeaths+1, "Escape did not cause exactly one death");
        deadline=player.server.getTickCount()+2400;
    }
    static void resumed(ServerPlayer player) throws Exception {
        snapshot(player,"reconnect");
        require(Files.readString(evidence(player,"progress")).equals(
                player.getPersistentData().getCompound("PlayerPersisted").getString("uq_disposable_progress")),
                "Native player progression lost during escape/restart");
        flag("native_persisted_progress_survived_clone_and_restart");
        Custody249Proof.runtime();
        require(!JailConfig.JAIL_CERTIFIED_ADMISSIONS.get(), "Restart enabled admission");
        cell(player,6);
        focus(player);
        PackedWalletProof.occupiedDestination(player);
        flag("occupied_return_kept_conflict_and_escrow");
        release(player);
        noClaims(player);
        flag("full_wallet_and_equipment_return_after_restart");
        pass();
    }
}
