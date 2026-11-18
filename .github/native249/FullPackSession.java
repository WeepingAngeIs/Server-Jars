package dev.unyielding.bundlesmoke;

import dev.unyielding.jail.JailConfig;
import java.nio.file.Files;
import java.util.Set;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

import static dev.unyielding.bundlesmoke.PlayerOnlySmoke.*;

/** One real full-pack client, to fit a production dedicated server and client on the same runner. */
public final class FullPackSession {
    private FullPackSession() { }

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
        var persisted = player.getPersistentData();
        var keyNames = new net.minecraft.nbt.ListTag();
        for (String key : persisted.getAllKeys()) {
            if (!key.toLowerCase(java.util.Locale.ROOT).contains("poke")
                    && !key.toLowerCase(java.util.Locale.ROOT).contains("cobble"))
                keyNames.add(net.minecraft.nbt.StringTag.valueOf(key));
        }
        result.put("persistent_keys",keyNames);
        if (stage.equals("fresh")) {
            if (persisted.contains("dangerous_first_join", Tag.TAG_ANY_NUMERIC))
                result.put("dangerous_first_join",persisted.get("dangerous_first_join").copy());
            var insanity = persisted.getCompound("insanelib");
            var neutralInsanity = new CompoundTag();
            if (insanity.contains("in_combat",Tag.TAG_ANY_NUMERIC))
                neutralInsanity.put("in_combat",insanity.get("in_combat").copy());
            var extra = insanity.get("nbt_tags");
            if (extra instanceof CompoundTag c && c.isEmpty() || extra instanceof net.minecraft.nbt.ListTag l && l.isEmpty())
                neutralInsanity.put("nbt_tags",extra.copy());
            result.put("insanelib",neutralInsanity);
        }
        Files.writeString(results.resolve("synthetic-player-"+stage+".snbt"),result.toString());
    }
    static void begin(ServerPlayer player) throws Exception {
        snapshot(player,"fresh");
        Custody249Proof.runtime();
        CenterBuildProof.place(player,player);
        flag("preview_place_undo_and_six_intact_cells");
        player.server.getPlayerList().deop(player.getGameProfile());
        require(!player.hasPermissions(2), "Still an administrator after fixture setup");
        require(!JailConfig.JAIL_CERTIFIED_ADMISSIONS.get() && !JailConfig.JAIL_PACKED_WALLETS.get(), "Admission defaults changed");
        seed(player,true);
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

        require(cmd("jail Visitor 6")==1,"Supported packed-wallet admission refused");
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
        Custody249Proof.runtime();
        require(!JailConfig.JAIL_CERTIFIED_ADMISSIONS.get(), "Restart enabled admission");
        cell(player,6);
        focus(player);
        PackedWalletProof.occupiedDestination(player);
        flag("occupied_return_kept_conflict_and_escrow");
        release(player);
        flag("full_wallet_and_equipment_return_after_restart");
        pass();
    }
}
