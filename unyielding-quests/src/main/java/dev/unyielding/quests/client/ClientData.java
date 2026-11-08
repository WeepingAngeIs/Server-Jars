package dev.unyielding.quests.client;

import dev.unyielding.quests.quest.QuestFile;
import dev.unyielding.quests.quest.TeamProgress;
import dev.unyielding.quests.team.Team;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything the client knows about quests and teams (filled by server packets). */
public final class ClientData {
    public static volatile QuestFile file;
    public static TeamProgress progress = new TeamProgress();
    public static UUID progressTeam;
    public static int fileVersion;
    public static int progressVersion;

    public static UUID myTeamId;
    public static final Map<UUID, Team> teams = new LinkedHashMap<>();
    public static final Set<UUID> invites = new HashSet<>();
    public static final Map<UUID, String> online = new LinkedHashMap<>();
    public static boolean canCreateParty = true;
    public static int maxPartySize;
    public static int teamVersion;
    public static boolean shareQuestProgress;
    public static boolean inventoryButtonsEnabled = true;
    public static boolean questToastsEnabled = true;
    public static boolean ultimineEnabled = true;
    public static boolean ultimineRequiresItem;

    /** Operator-only settings and authoritative save acknowledgements from the server. */
    public static CompoundTag adminSettings = new CompoundTag();
    public static int adminSettingsVersion;
    public static CompoundTag lootTables = new CompoundTag();
    public static int lootTablesVersion;

    public static String notice = "";
    public static long noticeTime;

    /** Lucky Spin settings from the server. */
    public static boolean spinEnabled;
    public static int[] spinWeights = {50, 44, 3, 2, 1};

    private static int unclaimedCache = -1, unclaimedFile = -1, unclaimedProg = -1;

    static byte[][] pending;
    static int pendingCount;

    private ClientData() {
    }

    public static Team myTeam() {
        return myTeamId == null ? null : teams.get(myTeamId);
    }

    public static List<Team> openParties() {
        List<Team> l = new ArrayList<>();
        for (Team t : teams.values()) if (t.type == Team.Type.PARTY && t.freeToJoin && !t.id.equals(myTeamId)) l.add(t);
        return l;
    }

    /** True when this player can still use the quest's Lucky Spin. */
    public static boolean spinAvailable(QuestFile.Quest q, UUID me) {
        return spinEnabled && progress.questDone(q.id) && !progress.isClaimed(me, dev.unyielding.quests.spin.LuckySpin.spinId(q.id));
    }

    /** Rewards and spins waiting to be claimed (cached per sync). */
    public static int unclaimedCount() {
        QuestFile f = file;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (f == null || mc.player == null) return 0;
        if (unclaimedCache >= 0 && unclaimedFile == fileVersion && unclaimedProg == progressVersion) return unclaimedCache;
        UUID me = mc.player.getUUID();
        int n = 0;
        for (QuestFile.Quest q : f.quests.values()) {
            if (!progress.questDone(q.id)) continue;
            for (QuestFile.Reward r : q.rewards) if (!progress.isClaimed(me, r.id) && !r.excludeFromClaimAll) n++;
            if (spinAvailable(q, me)) n++;
        }
        unclaimedCache = n;
        unclaimedFile = fileVersion;
        unclaimedProg = progressVersion;
        return n;
    }

    public static void clear() {
        file = null;
        progress = new TeamProgress();
        progressTeam = null;
        myTeamId = null;
        teams.clear();
        invites.clear();
        online.clear();
        pending = null;
        pendingCount = 0;
        spinEnabled = false;
        shareQuestProgress = false;
        inventoryButtonsEnabled = true;
        questToastsEnabled = true;
        ultimineEnabled = true;
        ultimineRequiresItem = false;
        adminSettings = new CompoundTag();
        adminSettingsVersion++;
        lootTables = new CompoundTag();
        lootTablesVersion++;
        unclaimedCache = -1;
        fileVersion++;
        progressVersion++;
        teamVersion++;
    }
}
