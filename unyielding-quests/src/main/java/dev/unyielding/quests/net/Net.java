package dev.unyielding.quests.net;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.client.ClientHandlers;
import dev.unyielding.quests.quest.QuestManager;
import dev.unyielding.quests.spin.LuckySpin;
import dev.unyielding.quests.team.TeamManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/** All network payloads of the mod. */
public final class Net {
    private Net() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> mkType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(UnyieldingQuests.MODID, path));
    }

    // ---- server -> client ------------------------------------------------

    public record LootTablesSync(CompoundTag tag, boolean open) implements CustomPacketPayload {
        public static final Type<LootTablesSync> TYPE = mkType("loot_tables");
        public static final StreamCodec<RegistryFriendlyByteBuf, LootTablesSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, LootTablesSync::tag,
                ByteBufCodecs.BOOL, LootTablesSync::open, LootTablesSync::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record LootTablesAction(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<LootTablesAction> TYPE = mkType("loot_tables_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, LootTablesAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, LootTablesAction::tag, LootTablesAction::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server-authoritative settings; only operators receive the editable list. */
    public record AdminSettingsSync(CompoundTag tag, boolean open) implements CustomPacketPayload {
        public static final Type<AdminSettingsSync> TYPE = mkType("admin_settings");
        public static final StreamCodec<RegistryFriendlyByteBuf, AdminSettingsSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, AdminSettingsSync::tag,
                ByteBufCodecs.BOOL, AdminSettingsSync::open, AdminSettingsSync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TutorialDismissed() implements CustomPacketPayload {
        public static final Type<TutorialDismissed> TYPE = mkType("tutorial_dismissed");
        public static final StreamCodec<RegistryFriendlyByteBuf, TutorialDismissed> CODEC = StreamCodec.unit(new TutorialDismissed());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Permission-filtered onboarding pages and the server's current public commands. */
    public record TutorialSync(CompoundTag tag, boolean automatic) implements CustomPacketPayload {
        public static final Type<TutorialSync> TYPE = mkType("tutorial");
        public static final StreamCodec<RegistryFriendlyByteBuf, TutorialSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, TutorialSync::tag,
                ByteBufCodecs.BOOL, TutorialSync::automatic,
                TutorialSync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Part of the (compressed) quest book. */
    public record QuestFileChunk(int index, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<QuestFileChunk> TYPE = mkType("quest_file");
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestFileChunk> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, QuestFileChunk::index,
                ByteBufCodecs.VAR_INT, QuestFileChunk::total,
                ByteBufCodecs.byteArray(1 << 20), QuestFileChunk::data,
                QuestFileChunk::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Progress of the receiving player's team. */
    public record ProgressSync(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<ProgressSync> TYPE = mkType("progress");
        public static final StreamCodec<RegistryFriendlyByteBuf, ProgressSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, ProgressSync::tag, ProgressSync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Teams the receiving player may see + their invites. */
    public record TeamSync(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<TeamSync> TYPE = mkType("teams");
        public static final StreamCodec<RegistryFriendlyByteBuf, TeamSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, TeamSync::tag, TeamSync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** kind 0 = quest completed (id = quest), 1 = message. */
    public record Notify(int kind, long id, String text) implements CustomPacketPayload {
        public static final Type<Notify> TYPE = mkType("notify");
        public static final StreamCodec<RegistryFriendlyByteBuf, Notify> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Notify::kind,
                ByteBufCodecs.VAR_LONG, Notify::id,
                ByteBufCodecs.STRING_UTF8, Notify::text,
                Notify::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** which: 0 = quest book, 1 = teams screen. */
    public record OpenScreen(int which) implements CustomPacketPayload {
        public static final Type<OpenScreen> TYPE = mkType("open");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreen> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenScreen::which, OpenScreen::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Lucky Spin settings (sent on login): on/off and the five tier weights. */
    public record SpinInfo(boolean enabled, int[] weights) implements CustomPacketPayload {
        public static final Type<SpinInfo> TYPE = mkType("spin_info");
        public static final StreamCodec<RegistryFriendlyByteBuf, SpinInfo> CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeBoolean(v.enabled());
                    buf.writeVarIntArray(v.weights());
                },
                buf -> new SpinInfo(buf.readBoolean(), buf.readVarIntArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Finished Lucky Spins (prizes already given): what to show on the client. */
    public record SpinResult(List<LuckySpin.Spin> spins) implements CustomPacketPayload {
        public static final Type<SpinResult> TYPE = mkType("spin_result");
        public static final StreamCodec<RegistryFriendlyByteBuf, SpinResult> CODEC = StreamCodec.of(SpinResult::write, SpinResult::read);

        private static void write(RegistryFriendlyByteBuf buf, SpinResult v) {
            buf.writeVarInt(v.spins().size());
            for (LuckySpin.Spin s : v.spins()) {
                buf.writeVarInt(s.tier().ordinal());
                buf.writeBoolean(s.pokemon());
                buf.writeUtf(s.name(), 256);
                buf.writeVarInt(s.index());
                buf.writeVarInt(s.strip().size());
                for (LuckySpin.Entry e : s.strip()) {
                    buf.writeVarInt(e.tier().ordinal());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, e.stack());
                }
            }
        }

        private static SpinResult read(RegistryFriendlyByteBuf buf) {
            int n = Math.min(64, buf.readVarInt());
            List<LuckySpin.Spin> spins = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                LuckySpin.Tier tier = LuckySpin.Tier.of(buf.readVarInt());
                boolean pokemon = buf.readBoolean();
                String name = buf.readUtf(256);
                int index = buf.readVarInt();
                int len = Math.min(64, buf.readVarInt());
                List<LuckySpin.Entry> strip = new ArrayList<>(len);
                for (int k = 0; k < len; k++) strip.add(new LuckySpin.Entry(LuckySpin.Tier.of(buf.readVarInt()), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));
                spins.add(new LuckySpin.Spin(tier, pokemon, name, index, strip));
            }
            return new SpinResult(spins);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One applied quest-book edit (server -> every client). */
    public record EditApply(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<EditApply> TYPE = mkType("edit_apply");
        public static final StreamCodec<RegistryFriendlyByteBuf, EditApply> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, EditApply::tag, EditApply::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- client -> server ------------------------------------------------

    /** An allowlisted boolean change, or __open to request the operator menu. */
    public record AdminSettingsAction(String key, boolean value) implements CustomPacketPayload {
        public static final Type<AdminSettingsAction> TYPE = mkType("admin_settings_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, AdminSettingsAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), AdminSettingsAction::key,
                ByteBufCodecs.BOOL, AdminSettingsAction::value, AdminSettingsAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** A quest-book edit request from an operator's in-game editor. */
    public record EditRequest(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<EditRequest> TYPE = mkType("edit_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, EditRequest> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, EditRequest::tag, EditRequest::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** action 0 = tick a checkmark task, 1 = submit items to a task. */
    public record TaskAction(long taskId, int action) implements CustomPacketPayload {
        public static final Type<TaskAction> TYPE = mkType("task_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, TaskAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, TaskAction::taskId,
                ByteBufCodecs.VAR_INT, TaskAction::action,
                TaskAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** mode 0 = one reward (id), 1 = all rewards of a quest (id), 2 = everything claimable. */
    public record Claim(long id, int mode) implements CustomPacketPayload {
        public static final Type<Claim> TYPE = mkType("claim");
        public static final StreamCodec<RegistryFriendlyByteBuf, Claim> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Claim::id,
                ByteBufCodecs.VAR_INT, Claim::mode,
                Claim::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Team management request (create, invite, join, leave, ...). */
    public record TeamAction(String action, String a, String b) implements CustomPacketPayload {
        public static final Type<TeamAction> TYPE = mkType("team_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, TeamAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), TeamAction::action,
                ByteBufCodecs.stringUtf8(512), TeamAction::a,
                ByteBufCodecs.stringUtf8(512), TeamAction::b,
                TeamAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Vein-mine key state: held + selected shape. */
    public record UltimineState(boolean held, int shape) implements CustomPacketPayload {
        public static final Type<UltimineState> TYPE = mkType("ultimine_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, UltimineState> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, UltimineState::held,
                ByteBufCodecs.VAR_INT, UltimineState::shape,
                UltimineState::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        dev.unyielding.quests.settings.PokemonEditor.register(event);
        dev.unyielding.quests.settings.MusicSettings.register(event);
        dev.unyielding.quests.settings.ConfigEditor.register(event);
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(TutorialDismissed.TYPE, TutorialDismissed.CODEC,
                (p, c) -> dev.unyielding.quests.tutorial.TutorialManager.dismissed((ServerPlayer)c.player()));
        r.playToClient(LootTablesSync.TYPE, LootTablesSync.CODEC,
                (p, c) -> ClientHandlers.onLootTables(p.tag(), p.open()));
        r.playToServer(LootTablesAction.TYPE, LootTablesAction.CODEC,
                (p, c) -> dev.unyielding.quests.loot.LootTables.onAction((ServerPlayer)c.player(), p.tag()));
        r.playToClient(AdminSettingsSync.TYPE, AdminSettingsSync.CODEC, (p, c) -> ClientHandlers.onAdminSettings(p));
        r.playToServer(AdminSettingsAction.TYPE, AdminSettingsAction.CODEC,
                (p, c) -> dev.unyielding.quests.settings.AdminSettings.onAction((ServerPlayer) c.player(), p));
        r.playToClient(TutorialSync.TYPE, TutorialSync.CODEC,
                (p, c) -> dev.unyielding.quests.client.tutorial.TutorialClient.receive(p.tag(), p.automatic()));
        r.playToClient(QuestFileChunk.TYPE, QuestFileChunk.CODEC, (p, c) -> ClientHandlers.onQuestFile(p));
        r.playToClient(ProgressSync.TYPE, ProgressSync.CODEC, (p, c) -> ClientHandlers.onProgress(p));
        r.playToClient(TeamSync.TYPE, TeamSync.CODEC, (p, c) -> ClientHandlers.onTeams(p));
        r.playToClient(Notify.TYPE, Notify.CODEC, (p, c) -> ClientHandlers.onNotify(p));
        r.playToClient(OpenScreen.TYPE, OpenScreen.CODEC, (p, c) -> ClientHandlers.onOpen(p));
        r.playToClient(SpinInfo.TYPE, SpinInfo.CODEC, (p, c) -> ClientHandlers.onSpinInfo(p));
        r.playToClient(SpinResult.TYPE, SpinResult.CODEC, (p, c) -> ClientHandlers.onSpinResult(p));
        r.playToClient(EditApply.TYPE, EditApply.CODEC, (p, c) -> ClientHandlers.onEditApply(p));
        r.playToServer(EditRequest.TYPE, EditRequest.CODEC, (p, c) -> QuestManager.onEdit((ServerPlayer) c.player(), p));
        r.playToServer(TaskAction.TYPE, TaskAction.CODEC, (p, c) -> QuestManager.onTaskAction((ServerPlayer) c.player(), p));
        r.playToServer(Claim.TYPE, Claim.CODEC, (p, c) -> QuestManager.onClaim((ServerPlayer) c.player(), p));
        r.playToServer(UltimineState.TYPE, UltimineState.CODEC, (p, c) -> dev.unyielding.quests.ultimine.Ultimine.onState((ServerPlayer) c.player(), p));
        r.playToServer(TeamAction.TYPE, TeamAction.CODEC, (p, c) -> TeamManager.onAction((ServerPlayer) c.player(), p));
    }

    public static void sendTo(ServerPlayer p, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(p, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
