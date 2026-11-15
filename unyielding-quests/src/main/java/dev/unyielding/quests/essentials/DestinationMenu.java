package dev.unyielding.quests.essentials;

import dev.unyielding.quests.net.Net;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.Map;

/** The server sends a player's own destinations; selecting one still uses the normal command. */
public final class DestinationMenu {
    private DestinationMenu() {}
    public record Sync(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.parse("unyieldingquests:destinations"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Sync::data, Sync::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("1").playToClient(Sync.TYPE, Sync.CODEC, (p,c) -> dev.unyielding.quests.client.DestinationScreen.receive(p.data()));
    }
    public static void open(CommandSourceStack source, ServerPlayer player, Map<String, Loc> entries, String kind, int seconds) {
        if (!kind.equals("home") && !kind.equals("warp")) return;
        var root = source.getServer().getCommands().getDispatcher().getRoot().getChild(kind);
        boolean allowed = root != null && root.canUse(source) && EssConfig.featureSwitch(kind).get();
        CompoundTag data = new CompoundTag();
        data.putString("kind", kind); data.putBoolean("allowed", allowed);
        data.putLong("remaining", Essentials.cooldownRemaining(player, kind));
        data.putInt("cooldown", seconds);
        data.putBoolean("exempt", EssConfig.ADMINS_EXEMPT_COOLDOWNS.get() && player.hasPermissions(2));
        ListTag rows = new ListTag();
        for (var e : entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (rows.size() >= DestinationPolicy.MAX_ROWS) break;
            if (!DestinationPolicy.fitsPayload(e.getKey(), e.getValue().dim())) continue;
            CompoundTag row = new CompoundTag(); row.putString("name", e.getKey()); row.putString("dimension", e.getValue().dim()); rows.add(row);
        }
        data.putInt("omitted", entries.size() - rows.size());
        data.put("entries", rows);
        Net.sendTo(player, new Sync(data));
    }
}
