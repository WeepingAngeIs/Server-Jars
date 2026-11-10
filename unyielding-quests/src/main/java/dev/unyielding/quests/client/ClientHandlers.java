package dev.unyielding.quests.client;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.QuestFile;
import dev.unyielding.quests.quest.TeamProgress;
import dev.unyielding.quests.team.Team;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Packet handlers on the client (always run on the main thread). */
public final class ClientHandlers {
    private ClientHandlers() {
    }

    public static void onQuestFile(Net.QuestFileChunk c) {
        if (c.index() == 0 || ClientData.pending == null || ClientData.pending.length != c.total()) {
            ClientData.pending = new byte[c.total()][];
            ClientData.pendingCount = 0;
        }
        if (ClientData.pending[c.index()] == null) ClientData.pendingCount++;
        ClientData.pending[c.index()] = c.data();
        if (ClientData.pendingCount == ClientData.pending.length) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                for (byte[] b : ClientData.pending) out.write(b);
                CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(out.toByteArray()), NbtAccounter.unlimitedHeap());
                var conn = net.minecraft.client.Minecraft.getInstance().getConnection();
                if (conn != null) QuestFile.REGS = conn.registryAccess();
                ClientData.file = QuestFile.fromTag(tag);
                ClientData.fileVersion++;
            } catch (IOException | RuntimeException e) {
                UnyieldingQuests.LOGGER.error("Could not read quest book from server", e);
            }
            ClientData.pending = null;
            ClientData.pendingCount = 0;
        }
    }

    public static void onEditApply(Net.EditApply p) {
        if (ClientData.file == null) return;
        String err = dev.unyielding.quests.quest.QuestEdit.apply(ClientData.file, p.tag());
        if (err != null) UnyieldingQuests.LOGGER.warn("[quests] edit not applied on client: {}", err);
        ClientData.fileVersion++;
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof QuestScreen qs) qs.onBookChanged();
    }

    public static void onProgress(Net.ProgressSync p) {
        CompoundTag t = p.tag();
        ClientData.progressTeam = t.getUUID("team");
        ClientData.progress = TeamProgress.fromTag(t.getCompound("p"));
        ClientData.progressVersion++;
    }

    public static void onAdminSettings(Net.AdminSettingsSync packet) {
        CompoundTag tag = packet.tag();
        ClientData.adminSettings = tag.copy();
        ClientData.adminSettingsVersion++;
        ClientData.shareQuestProgress = tag.getBoolean("shareProgress");
        ClientData.inventoryButtonsEnabled = !tag.contains("inventoryButtons") || tag.getBoolean("inventoryButtons");
        ClientData.questToastsEnabled = !tag.contains("questPopups") || tag.getBoolean("questPopups");
        ClientData.adminInventoryControls = !tag.contains("adminInventoryControls") || tag.getBoolean("adminInventoryControls");
        double mapScale = tag.contains("customMapIconScale") ? tag.getDouble("customMapIconScale") : 1.0;
        ClientData.customMapIconScale = Double.isFinite(mapScale) ? Math.clamp(mapScale, 0.25, 4.0) : 1.0;
        ClientData.ultimineEnabled = !tag.contains("veinMining") || tag.getBoolean("veinMining");
        ClientData.ultimineRequiresItem = tag.getBoolean("veinMiningRequiresItem");
        ClientData.teamVersion++;
        dev.unyielding.quests.client.tutorial.TutorialClient.setAutomaticEnabled(tag.getBoolean("tutorialOnJoin"));
        Minecraft mc = Minecraft.getInstance();
        if (packet.open() && tag.getBoolean("allowed")) {
            mc.setScreen(new AdminSettingsScreen(mc.screen));
        } else if (tag.getBoolean("error") && !(mc.screen instanceof AdminSettingsScreen) && mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(tag.getString("message"))
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
        }
    }

    public static void onTeams(Net.TeamSync p) {
        CompoundTag t = p.tag();
        ClientData.myTeamId = t.getUUID("me");
        ClientData.teams.clear();
        for (Tag e : t.getList("teams", Tag.TAG_COMPOUND)) {
            Team team = Team.fromTag((CompoundTag) e);
            ClientData.teams.put(team.id, team);
        }
        ClientData.invites.clear();
        for (Tag e : t.getList("invites", Tag.TAG_INT_ARRAY)) ClientData.invites.add(NbtUtils.loadUUID(e));
        ClientData.online.clear();
        for (Tag e : t.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            ClientData.online.put(c.getUUID("u"), c.getString("n"));
        }
        ClientData.canCreateParty = t.getBoolean("canCreate");
        ClientData.shareQuestProgress = t.getBoolean("shareProgress");
        ClientData.maxPartySize = t.getInt("maxSize");
        ClientData.teamVersion++;
    }

    public static void onLootTables(CompoundTag tag, boolean open) {
        ClientData.lootTables = tag.copy();
        ClientData.lootTablesVersion++;
        Minecraft mc = Minecraft.getInstance();
        if (open && tag.getBoolean("allowed") && !(mc.screen instanceof dev.unyielding.quests.client.edit.LootTablesScreen)) {
            mc.setScreen(new dev.unyielding.quests.client.edit.LootTablesScreen(mc.screen));
        } else if (tag.getBoolean("error") && mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(tag.getString("message"))
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
        }
    }

    public static void onNotify(Net.Notify n) {
        Minecraft mc = Minecraft.getInstance();
        if (n.kind() == 0) {
            if (!ClientData.questToastsEnabled) return;
            QuestFile f = ClientData.file;
            if (f != null && f.quests.containsKey(n.id())) mc.getToasts().addToast(new QuestToast(f.quests.get(n.id())));
        } else {
            ClientData.notice = n.text();
            ClientData.noticeTime = System.currentTimeMillis();
        }
    }

    public static void onSpinInfo(Net.SpinInfo i) {
        ClientData.spinEnabled = i.enabled();
        if (i.weights() != null && i.weights().length == 5) ClientData.spinWeights = i.weights();
        ClientData.progressVersion++;
    }

    public static void onSpinResult(Net.SpinResult r) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof QuestScreen qs) {
            qs.queueSpins(r.spins());
        } else {
            for (var s : r.spins()) mc.getToasts().addToast(new SpinToast(s));
        }
    }

    public static void onOpen(Net.OpenScreen o) {
        Minecraft mc = Minecraft.getInstance();
        if (o.which() == 0) {
            if (ClientData.file == null) {
                if (mc.player != null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("The quest book has not been received from the server yet."), true);
                return;
            }
            mc.setScreen(new QuestScreen());
        } else {
            mc.setScreen(new TeamScreen());
        }
    }
}
