package dev.unyielding.pack249;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** Constructor state only. No player login, registration, gameplay or persistent save. */
final class SyntheticPlayerProjection {
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

    static void record(MinecraftServer server, Path destination) throws Exception {
        if (!"true".equals(System.getenv("GITHUB_ACTIONS")) || !"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))
                || !server.getWorldPath(LevelResource.ROOT).toRealPath().getFileName().toString().equals("disposable_pack249"))
            throw new IllegalStateException("Synthetic projection requires the disposable census world");
        var profile=new GameProfile(UUID.randomUUID(), "JailProbe");
        var player=new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        var nbt=player.saveWithoutId(new CompoundTag());
        var all=nbt.getCompound("neoforge:attachments");
        var selected=new CompoundTag();
        for(String key : OBSERVED) {
            Tag value=all.get(key);
            if(value==null) continue;
            if(key.equals("carryon:carry_on_data") && (!(value instanceof CompoundTag t)
                    || t.getAllKeys().stream().anyMatch(k->k.matches("(?i).*(type|block|entity|tile).*")))) continue;
            selected.put(key,value.copy());
        }
        var output=new CompoundTag();
        output.putString("provenance","synthetic unconnected ServerPlayer; constructor state only");
        output.put("observed_neutral",selected);
        output.put("attributes",nbt.getList("attributes",Tag.TAG_COMPOUND).copy());
        var modifiers=new ListTag();
        for(var attribute : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            var attributeId=attribute.key().location();
            var instance=player.getAttribute(attribute);
            if(instance==null) continue;
            for(var modifier : instance.getModifiers()) {
                var allowed=Set.of("spell_power","apotheosis","apothic_attributes");
                if(!allowed.contains(attributeId.getNamespace()) && !allowed.contains(modifier.id().getNamespace())) continue;
                var entry=new CompoundTag();
                entry.putString("attribute",attributeId.toString());
                entry.putString("id",modifier.id().toString());
                entry.putString("operation",modifier.operation().name());
                entry.putDouble("amount",modifier.amount());
                modifiers.add(entry);
            }
        }
        output.put("live_modifiers",modifiers);
        Files.writeString(destination,output.toString());
    }
    private SyntheticPlayerProjection() {}
}
