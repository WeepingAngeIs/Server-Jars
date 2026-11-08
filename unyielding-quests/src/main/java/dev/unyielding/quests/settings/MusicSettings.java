package dev.unyielding.quests.settings;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.net.Net;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-owned volume multipliers; audio files and personal sound options stay on clients. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID)
public final class MusicSettings extends SavedData {
    public static final String MASTER = "__master";
    public static final List<String> TRACKS = tracks();
    private long revision;
    private double master = 100;
    private final Map<String, Double> volumes = new TreeMap<>();

    private static List<String> tracks() {
        try (var input = MusicSettings.class.getResourceAsStream("/data/unyieldingquests/music_tracks.txt")) {
            if (input == null) throw new IOException("Missing music catalog");
            return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines()
                    .map(String::trim).filter(s -> s.matches("[a-z0-9_/-]{1,120}")).distinct().sorted().toList();
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    public static MusicSettings get(ServerPlayer p) {
        return p.server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MusicSettings::new, MusicSettings::load, null), "unyielding_music");
    }
    public static MusicSettings load(CompoundTag tag, HolderLookup.Provider provider) {
        MusicSettings s = new MusicSettings();
        s.revision = tag.getLong("revision");
        if (tag.contains("master") && valid(tag.getDouble("master"))) s.master = tag.getDouble("master");
        CompoundTag entries = tag.getCompound("volumes");
        for (String key : TRACKS) if (entries.contains(key) && valid(entries.getDouble(key))) s.volumes.put(key, entries.getDouble(key));
        return s;
    }
    public static boolean valid(double value) { return Double.isFinite(value) && value >= 0 && value <= 200; }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("revision", revision); tag.putDouble("master", master);
        CompoundTag entries = new CompoundTag(); volumes.forEach(entries::putDouble); tag.put("volumes", entries); return tag;
    }
    public CompoundTag snapshot() { return save(new CompoundTag(), null); }
    public record Action(String track, long expectedRevision, double percent) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.parse("unyieldingquests:music_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(120), Action::track, ByteBufCodecs.VAR_LONG, Action::expectedRevision,
                ByteBufCodecs.DOUBLE, Action::percent, Action::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Sync(CompoundTag state, boolean open, String message) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.parse("unyieldingquests:music_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, Sync::state, ByteBufCodecs.BOOL, Sync::open,
                ByteBufCodecs.stringUtf8(500), Sync::message, Sync::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(Action.TYPE, Action.CODEC, (p,c) -> handle((ServerPlayer)c.player(), p));
        r.playToClient(Sync.TYPE, Sync.CODEC, (p,c) -> {
            dev.unyielding.quests.client.MusicClient.apply(p.state());
            dev.unyielding.quests.client.MusicAdminScreen.receive(p);
        });
    }
    public static void handle(ServerPlayer player, Action action) {
        if (!player.hasPermissions(2)) return;
        MusicSettings state = get(player);
        if (action.track().equals("__open")) { reply(player, state, true, "Changes apply to everyone using the matching songpack."); return; }
        if (!action.track().equals(MASTER) && !TRACKS.contains(action.track())) { reply(player,state,false,"Unknown track."); return; }
        if (action.expectedRevision() != state.revision) { reply(player,state,false,"Settings changed. Close and reopen this track before saving."); return; }
        if (!valid(action.percent())) { reply(player,state,false,"Enter a percentage from 0 to 200."); return; }
        if (action.track().equals(MASTER)) state.master = action.percent();
        else if (action.percent() == 100) state.volumes.remove(action.track());
        else state.volumes.put(action.track(), action.percent());
        state.revision++; state.setDirty();
        UnyieldingQuests.LOGGER.info("{} set server music {} to {}%", player.getGameProfile().getName(), action.track(), action.percent());
        for (ServerPlayer viewer : player.server.getPlayerList().getPlayers()) reply(viewer,state,false,"");
        reply(player,state,false,"Saved. Music volume updated for connected players.");
    }
    private static void reply(ServerPlayer p, MusicSettings state, boolean open, String message) {
        Net.sendTo(p, new Sync(state.snapshot(), open, message));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) reply(p,get(p),false,"");
    }
}
