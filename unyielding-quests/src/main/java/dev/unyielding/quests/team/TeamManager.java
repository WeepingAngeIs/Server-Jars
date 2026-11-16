package dev.unyielding.quests.team;

import dev.unyielding.quests.Config;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.QuestManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Server-side team logic: personal teams, parties, server teams, invites and team chat. */
public final class TeamManager {
    private static final int[] PALETTE = {0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF, 0xAA55FF, 0xFF8B59, 0x59FF8B};

    private static MinecraftServer server;
    private static TeamData data;
    private record PendingConfirmation(UUID teamId, String teamName, UUID targetId, String targetName, boolean adminDelete, java.util.Set<UUID> members, long expiresAt) {}
    private static final Map<UUID, PendingConfirmation> pending = new java.util.HashMap<>();

    private TeamManager() {
    }

    public static void onServerStart(MinecraftServer s) {
        pending.clear();
        server = s;
        data = s.overworld().getDataStorage().computeIfAbsent(TeamData.factory(), TeamData.NAME);
    }

    public static void onServerStop() {
        pending.clear();
        server = null;
        data = null;
    }

    public static TeamData data() {
        return data;
    }

    // =====================================================================
    // Queries (also used as small public API)
    // =====================================================================

    public static UUID teamIdOf(UUID player) {
        if (data == null) return player;
        UUID t = data.playerTeam.get(player);
        return t != null ? t : player;
    }

    public static Team teamOf(UUID player) {
        return data == null ? null : data.teams.get(teamIdOf(player));
    }

    public static Team get(UUID teamId) {
        return data == null ? null : data.teams.get(teamId);
    }

    public static boolean sameTeam(UUID a, UUID b) {
        return a.equals(b) || teamIdOf(a).equals(teamIdOf(b));
    }

    public static Team findParty(String nameOrId) {
        if (data == null) return null;
        try {
            Team t = data.teams.get(UUID.fromString(nameOrId));
            if (t != null) return t;
        } catch (IllegalArgumentException ignored) {
        }
        for (Team t : data.teams.values()) {
            if (t.type != Team.Type.PLAYER && t.name.equalsIgnoreCase(nameOrId)) return t;
        }
        return null;
    }

    // =====================================================================
    // Personal team
    // =====================================================================

    private static Team ensurePersonal(UUID player, String name) {
        Team t = data.teams.get(player);
        if (t == null) {
            t = new Team();
            t.id = player;
            t.type = Team.Type.PLAYER;
            t.owner = player;
            t.color = PALETTE[Math.floorMod(player.hashCode(), PALETTE.length)];
            t.members.put(player, Team.Rank.OWNER);
            data.teams.put(player, t);
        }
        t.name = name;
        t.names.put(player, name);
        if (!data.playerTeam.containsKey(player)) data.playerTeam.put(player, player);
        data.setDirty();
        return t;
    }

    public static void onLogin(ServerPlayer p) {
        if (data == null) return;
        ensurePersonal(p.getUUID(), p.getGameProfile().getName());
        UUID tid = data.playerTeam.get(p.getUUID());
        Team t = data.teams.get(tid);
        if (t == null) {
            data.playerTeam.put(p.getUUID(), p.getUUID());
            t = data.teams.get(p.getUUID());
        }
        if (t != null && t.type != Team.Type.PLAYER) {
            t.names.put(p.getUUID(), p.getGameProfile().getName());
            data.setDirty();
        }
        syncAllPlayers();
    }

    public static void onLogout(ServerPlayer p) {
        pending.remove(p.getUUID());
        syncAllPlayers();
    }

    // =====================================================================
    // Sync
    // =====================================================================

    public static void syncAllPlayers() {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
    }

    public static void syncTeam(Team t) {
        if (server == null || t == null) return;
        for (UUID u : t.members.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) sync(p);
        }
    }

    public static void sync(ServerPlayer p) {
        if (data == null) return;
        UUID tid = teamIdOf(p.getUUID());
        Team mine = data.teams.get(tid);
        if (mine == null) return;
        CompoundTag t = new CompoundTag();
        t.putUUID("me", tid);
        ListTag teams = new ListTag();
        teams.add(mine.toTag(true));
        java.util.Set<UUID> added = new java.util.HashSet<>();
        added.add(tid);
        ListTag inv = new ListTag();
        for (Map.Entry<UUID, Map<UUID, Long>> e : data.invites.entrySet()) {
            if (e.getValue().containsKey(p.getUUID())) {
                Team it = data.teams.get(e.getKey());
                if (it != null) {
                    inv.add(NbtUtils.createUUID(it.id));
                    if (added.add(it.id)) teams.add(it.toTag(false));
                }
            }
        }
        int open = 0;
        for (Team pt : data.teams.values()) {
            if (pt.type == Team.Type.PARTY && pt.freeToJoin && open < 60 && added.add(pt.id)) {
                teams.add(pt.toTag(false));
                open++;
            }
        }
        t.put("teams", teams);
        t.put("invites", inv);
        ListTag players = new ListTag();
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", o.getUUID());
            c.putString("n", o.getGameProfile().getName());
            players.add(c);
        }
        t.put("players", players);
        t.putBoolean("canCreate", Config.ALLOW_PARTIES.get());
        t.putBoolean("shareProgress", Config.SHARE_QUEST_PROGRESS.get());
        t.putInt("maxSize", Config.MAX_PARTY_SIZE.get());
        Net.sendTo(p, new Net.TeamSync(t));
    }

    // =====================================================================
    // Operations (return an error message or null on success)
    // =====================================================================

    private static void changeTeam(UUID player, Team from, Team to) {
        data.playerTeam.put(player, to.id);
        QuestManager.onTeamChanged(player, from, to);
        data.setDirty();
    }

    private static void broadcast(Team t, Component msg) {
        for (UUID u : t.members.keySet()) {
            ServerPlayer m = server.getPlayerList().getPlayer(u);
            if (m != null) m.sendSystemMessage(msg);
        }
    }

    private static MutableComponent tag(Team t) {
        return Component.literal("[" + t.name + "] ").withStyle(Style.EMPTY.withColor(t.color));
    }

    private static String cleanName(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    public static String createParty(ServerPlayer p, String rawName) {
        if (!Config.ALLOW_PARTIES.get()) return "Creating parties is disabled on this server.";
        Team cur = teamOf(p.getUUID());
        if (cur != null && cur.type != Team.Type.PLAYER) return "Leave your current team first.";
        String name = cleanName(rawName);
        if (name.length() < 3) return "A party name needs at least 3 characters.";
        if (name.length() > Config.MAX_NAME_LENGTH.get()) return "That name is too long.";
        for (Team t : data.teams.values()) {
            if (t.type != Team.Type.PLAYER && t.name.equalsIgnoreCase(name)) return "A team with that name already exists.";
        }
        Team party = new Team();
        party.id = UUID.randomUUID();
        party.type = Team.Type.PARTY;
        party.name = name;
        party.owner = p.getUUID();
        party.color = PALETTE[Math.floorMod(party.id.hashCode(), PALETTE.length)];
        party.members.put(p.getUUID(), Team.Rank.OWNER);
        party.names.put(p.getUUID(), p.getGameProfile().getName());
        data.teams.put(party.id, party);
        changeTeam(p.getUUID(), cur, party);
        party.addMessage(null, "", p.getGameProfile().getName() + " created the party.");
        syncTeam(party);
        return null;
    }

    public static String invite(ServerPlayer actor, String targetName) {
        Team t = teamOf(actor.getUUID());
        if (t == null || t.type != Team.Type.PARTY) return "You are not in a party.";
        if (!t.rankOf(actor.getUUID()).atLeast(Team.Rank.OFFICER)) return "Only officers and the owner can invite.";
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) return "Player '" + targetName + "' is not online.";
        if (t.isMember(target.getUUID())) return "They are already in your party.";
        int max = Config.MAX_PARTY_SIZE.get();
        if (max > 0 && t.members.size() >= max) return "The party is full.";
        Team theirs = teamOf(target.getUUID());
        if (theirs != null && theirs.type != Team.Type.PLAYER) return "They are already in another team.";
        data.invites.computeIfAbsent(t.id, k -> new java.util.HashMap<>()).put(target.getUUID(), System.currentTimeMillis());
        data.setDirty();
        MutableComponent msg = Component.literal(actor.getGameProfile().getName() + " invited you to the party '" + t.name + "'. ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("[Accept]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team join " + t.id))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Join " + t.name)))))
                .append(Component.literal(" "))
                .append(Component.literal("[Decline]").withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team decline " + t.id))));
        target.sendSystemMessage(msg);
        sync(target);
        sync(actor);
        return null;
    }

    public static String join(ServerPlayer p, String nameOrId) {
        Team t = findParty(nameOrId);
        if (t == null || t.type == Team.Type.PLAYER) return "No such team.";
        Team cur = teamOf(p.getUUID());
        if (cur != null && cur.id.equals(t.id)) return "You are already in that team.";
        if (cur != null && cur.type != Team.Type.PLAYER) return "Leave your current team first.";
        boolean invited = data.invites.getOrDefault(t.id, Map.of()).containsKey(p.getUUID());
        if (!invited && !t.freeToJoin) return "You need an invitation to join this team.";
        int max = Config.MAX_PARTY_SIZE.get();
        if (max > 0 && t.members.size() >= max) return "That team is full.";
        t.members.put(p.getUUID(), Team.Rank.MEMBER);
        t.names.put(p.getUUID(), p.getGameProfile().getName());
        Map<UUID, Long> inv = data.invites.get(t.id);
        if (inv != null) {
            inv.remove(p.getUUID());
            if (inv.isEmpty()) data.invites.remove(t.id);
        }
        changeTeam(p.getUUID(), cur, t);
        t.addMessage(null, "", p.getGameProfile().getName() + " joined the team.");
        broadcast(t, tag(t).append(Component.literal(p.getGameProfile().getName() + " joined the team.").withStyle(ChatFormatting.YELLOW)));
        syncTeam(t);
        return null;
    }

    public static String decline(ServerPlayer p, String nameOrId) {
        Team t = findParty(nameOrId);
        if (t == null) return "No such team.";
        Map<UUID, Long> inv = data.invites.get(t.id);
        if (inv != null) {
            inv.remove(p.getUUID());
            if (inv.isEmpty()) data.invites.remove(t.id);
            data.setDirty();
        }
        sync(p);
        return null;
    }

    private static void removeMember(Team t, UUID player, String reason) {
        Team.Rank rank = t.members.remove(player);
        Team personal = data.teams.get(player);
        if (personal == null) personal = ensurePersonal(player, t.nameOf(player));
        changeTeam(player, t, personal);
        ServerPlayer sp = server.getPlayerList().getPlayer(player);
        if (sp != null) {
            sp.sendSystemMessage(Component.literal(reason).withStyle(ChatFormatting.YELLOW));
            sync(sp);
        }
        if (t.members.isEmpty()) {
            deleteTeam(t);
            return;
        }
        if (rank == Team.Rank.OWNER && t.type == Team.Type.PARTY) {
            UUID next = null;
            for (Map.Entry<UUID, Team.Rank> e : t.members.entrySet()) {
                if (e.getValue() == Team.Rank.OFFICER) {
                    next = e.getKey();
                    break;
                }
            }
            if (next == null) next = t.members.keySet().iterator().next();
            t.members.put(next, Team.Rank.OWNER);
            t.owner = next;
            t.addMessage(null, "", t.nameOf(next) + " is the new owner.");
            broadcast(t, tag(t).append(Component.literal(t.nameOf(next) + " is the new owner.").withStyle(ChatFormatting.YELLOW)));
        }
        syncTeam(t);
    }

    private static void deleteTeam(Team t) {
        data.teams.remove(t.id);
        data.invites.remove(t.id);
        QuestManager.onTeamDeleted(t.id);
        data.setDirty();
    }

    public static String leave(ServerPlayer p) {
        Team t = teamOf(p.getUUID());
        if (t == null || t.type == Team.Type.PLAYER) return "You are not in a party.";
        if (t.type == Team.Type.PARTY && t.rankOf(p.getUUID()) == Team.Rank.OWNER) {
            if (t.members.size() == 1) return disband(p);
            return "Transfer ownership before leaving the party.";
        }
        String n = p.getGameProfile().getName();
        t.addMessage(null, "", n + " left the team.");
        broadcast(t, tag(t).append(Component.literal(n + " left the team.").withStyle(ChatFormatting.YELLOW)));
        removeMember(t, p.getUUID(), "You left '" + t.name + "'.");
        return null;
    }

    private static UUID memberByName(Team t, String name) {
        for (Map.Entry<UUID, String> e : t.names.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name) && t.members.containsKey(e.getKey())) return e.getKey();
        }
        return null;
    }

    public static String kick(ServerPlayer actor, String targetName) {
        Team t = teamOf(actor.getUUID());
        if (t == null || t.type != Team.Type.PARTY) return "You are not in a party.";
        Team.Rank mine = t.rankOf(actor.getUUID());
        if (!mine.atLeast(Team.Rank.OFFICER)) return "Only officers and the owner can kick.";
        UUID target = memberByName(t, targetName);
        if (target == null) return "No such member.";
        if (target.equals(actor.getUUID())) return "Use /team leave to leave.";
        if (t.rankOf(target).atLeast(mine)) return "You cannot kick someone of equal or higher rank.";
        t.addMessage(null, "", t.nameOf(target) + " was removed from the team.");
        broadcast(t, tag(t).append(Component.literal(t.nameOf(target) + " was removed from the team.").withStyle(ChatFormatting.YELLOW)));
        removeMember(t, target, "You were removed from '" + t.name + "'.");
        return null;
    }

    public static String setRank(ServerPlayer actor, String targetName, Team.Rank rank) {
        if (rank != Team.Rank.OWNER) return setRankNow(actor, targetName, rank);
        pending.remove(actor.getUUID());
        Team t = teamOf(actor.getUUID());
        if (t == null || t.type != Team.Type.PARTY) return "You are not in a party.";
        if (t.rankOf(actor.getUUID()) != Team.Rank.OWNER) return "Only the owner can change ranks.";
        UUID target = memberByName(t, targetName);
        if (target == null) return "No such member.";
        if (target.equals(actor.getUUID())) return "That is you.";
        String name = t.nameOf(target);
        pending.put(actor.getUUID(), new PendingConfirmation(t.id, t.name, target, name, false, java.util.Set.copyOf(t.members.keySet()), System.nanoTime() + 60_000_000_000L));
        actor.sendSystemMessage(Component.literal("Transfer ownership of '" + t.name + "' to " + name + "? You will become an officer. Quest progress remains with the party; reward claims remain personal. Use /team confirm or /team cancel within 60 seconds.").withStyle(ChatFormatting.YELLOW));
        return null;
    }

    private static String setRankNow(ServerPlayer actor, String targetName, Team.Rank rank) {
        Team t = teamOf(actor.getUUID());
        if (t == null || t.type != Team.Type.PARTY) return "You are not in a party.";
        if (t.rankOf(actor.getUUID()) != Team.Rank.OWNER) return "Only the owner can change ranks.";
        UUID target = memberByName(t, targetName);
        if (target == null) return "No such member.";
        return setRankNow(actor, target, rank);
    }

    private static String setRankNow(ServerPlayer actor, UUID target, Team.Rank rank) {
        Team t = teamOf(actor.getUUID());
        if (t == null || t.type != Team.Type.PARTY) return "You are not in a party.";
        if (t.rankOf(actor.getUUID()) != Team.Rank.OWNER) return "Only the owner can change ranks.";
        if (!t.isMember(target)) return "No such member.";
        if (target.equals(actor.getUUID())) return "That is you.";
        if (rank == Team.Rank.OWNER) {
            t.members.put(actor.getUUID(), Team.Rank.OFFICER);
            t.owner = target;
            t.addMessage(null, "", t.nameOf(target) + " is the new owner.");
        }
        t.members.put(target, rank);
        data.setDirty();
        broadcast(t, tag(t).append(Component.literal(t.nameOf(target) + " is now " + rank.name().toLowerCase(Locale.ROOT) + ".").withStyle(ChatFormatting.YELLOW)));
        syncTeam(t);
        return null;
    }

    public static String disband(ServerPlayer actor) {
        pending.remove(actor.getUUID());
        Team t = teamOf(actor.getUUID());
        if (t == null || t.type != Team.Type.PARTY) return "You are not in a party.";
        if (t.rankOf(actor.getUUID()) != Team.Rank.OWNER) return "Only the owner can disband the party.";
        pending.put(actor.getUUID(), new PendingConfirmation(t.id, t.name, null, "", false, java.util.Set.copyOf(t.members.keySet()), System.nanoTime() + 60_000_000_000L));
        actor.sendSystemMessage(Component.literal("Disband '" + t.name + "' and return its " + t.members.size() + " members to personal teams? The party is deleted. Where quest sharing is enabled, its progress is merged into each member's personal progress; reward claims stay per player. Use /team confirm or /team cancel within 60 seconds.").withStyle(ChatFormatting.YELLOW));
        return null;
    }

    public static String cancelConfirmation(ServerPlayer actor) {
        pending.remove(actor.getUUID());
        return null;
    }

    public static String confirm(ServerPlayer actor, String expectedTeam, String expectedTarget) {
        PendingConfirmation request = pending.remove(actor.getUUID());
        if (request == null || System.nanoTime() - request.expiresAt() >= 0) return "No current confirmation; request the action again.";
        if (!expectedTeam.isEmpty() && !request.teamId().toString().equals(expectedTeam)) return "Party changed; request the action again.";
        if (!expectedTeam.isEmpty() && !(request.targetId() == null ? "" : request.targetId().toString()).equals(expectedTarget)) return "Target changed; request the action again.";
        Team t = request.adminDelete() ? data.teams.get(request.teamId()) : teamOf(actor.getUUID());
        if (t == null || !t.members.keySet().equals(request.members())) return "Party membership changed; request the action again.";
        if (request.adminDelete()) {
            if (!expectedTeam.isEmpty()) return "Request changed; try again.";
            if (!actor.hasPermissions(2) || t == null || t.type == Team.Type.PLAYER || !t.name.equals(request.teamName())) return "Permission or team changed; request the action again.";
            return adminDeleteNow(t.name);
        }
        if (t == null || !t.id.equals(request.teamId()) || !t.name.equals(request.teamName()) || t.type != Team.Type.PARTY || t.rankOf(actor.getUUID()) != Team.Rank.OWNER)
            return "Party or ownership changed; request the action again.";
        if (request.targetId() == null) return disbandNow(actor);
        if (!t.isMember(request.targetId()) || !t.nameOf(request.targetId()).equals(request.targetName())) return "Member changed; request the action again.";
        return setRankNow(actor, request.targetId(), Team.Rank.OWNER);
    }

    private static String disbandNow(ServerPlayer actor) {
        Team t = teamOf(actor.getUUID());
        if (t == null || t.type != Team.Type.PARTY) return "You are not in a party.";
        if (t.rankOf(actor.getUUID()) != Team.Rank.OWNER) return "Only the owner can disband the party.";
        for (UUID u : new ArrayList<>(t.members.keySet())) {
            Team personal = data.teams.get(u);
            if (personal == null) personal = ensurePersonal(u, t.nameOf(u));
            changeTeam(u, t, personal);
            ServerPlayer sp = server.getPlayerList().getPlayer(u);
            if (sp != null) sp.sendSystemMessage(Component.literal("The party '" + t.name + "' was disbanded.").withStyle(ChatFormatting.YELLOW));
        }
        List<UUID> members = new ArrayList<>(t.members.keySet());
        t.members.clear();
        deleteTeam(t);
        for (UUID u : members) {
            ServerPlayer sp = server.getPlayerList().getPlayer(u);
            if (sp != null) sync(sp);
        }
        return null;
    }

    public static String setProperty(ServerPlayer actor, String prop, String value) {
        Team t = teamOf(actor.getUUID());
        if (t == null || t.type == Team.Type.PLAYER) return "You are not in a party.";
        if (!t.rankOf(actor.getUUID()).atLeast(Team.Rank.OFFICER)) return "Only officers and the owner can change settings.";
        value = cleanName(value);
        switch (prop.toLowerCase(Locale.ROOT)) {
            case "name" -> {
                if (value.length() < 3 || value.length() > Config.MAX_NAME_LENGTH.get()) return "Invalid name length.";
                for (Team o : data.teams.values()) {
                    if (o != t && o.type != Team.Type.PLAYER && o.name.equalsIgnoreCase(value)) return "A team with that name already exists.";
                }
                t.name = value;
            }
            case "description", "desc" -> t.description = value.length() > 200 ? value.substring(0, 200) : value;
            case "color" -> {
                String hex = value.startsWith("#") ? value.substring(1) : value;
                try {
                    t.color = Integer.parseInt(hex, 16) & 0xFFFFFF;
                } catch (NumberFormatException e) {
                    return "Colour must be a hex value like #55AAFF.";
                }
            }
            case "free", "free_to_join" -> t.freeToJoin = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equals("1");
            default -> {
                return "Unknown setting. Use name, description, color or free.";
            }
        }
        data.setDirty();
        syncTeam(t);
        return null;
    }

    public static String pinQuest(ServerPlayer p, String rawId) {
        Team t = teamOf(p.getUUID());
        if (t == null || t.type != Team.Type.PARTY || !t.isMember(p.getUUID())) return "Join a party to pin a goal.";
        long id;
        try { id = Long.parseLong(rawId); } catch (NumberFormatException ex) { return "Invalid quest."; }
        if (id != 0) {
            var quest = QuestManager.file.quests.get(id);
            if (quest == null) return "That quest no longer exists.";
            var progress = QuestManager.progressOf(p);
            if (quest.hideUntilDeps && !progress.questDone(id) && !QuestManager.depsDone(QuestManager.file, quest, progress)) return "That quest is hidden.";
        }
        t.pinnedQuest = id;
        data.setDirty();
        syncTeam(t);
        return null;
    }

    public static String chat(ServerPlayer p, String text) {
        Team t = teamOf(p.getUUID());
        if (t == null || t.type == Team.Type.PLAYER) return "You are not in a party.";
        text = text.trim();
        if (text.isEmpty()) return "Message is empty.";
        if (text.length() > 300) text = text.substring(0, 300);
        String n = p.getGameProfile().getName();
        t.addMessage(p.getUUID(), n, text);
        data.setDirty();
        Component msg = tag(t).append(Component.literal(n + ": ").withStyle(ChatFormatting.GRAY)).append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        broadcast(t, msg);
        syncTeam(t);
        return null;
    }

    // ---- admin -------------------------------------------------------------

    public static String adminCreateServerTeam(String rawName) {
        String name = cleanName(rawName);
        if (name.length() < 3) return "Name too short.";
        for (Team t : data.teams.values()) {
            if (t.type != Team.Type.PLAYER && t.name.equalsIgnoreCase(name)) return "A team with that name already exists.";
        }
        Team t = new Team();
        t.id = UUID.randomUUID();
        t.type = Team.Type.SERVER;
        t.name = name;
        t.color = PALETTE[Math.floorMod(t.id.hashCode(), PALETTE.length)];
        data.teams.put(t.id, t);
        data.setDirty();
        return null;
    }

    public static String adminAdd(String teamName, ServerPlayer target) {
        Team t = findParty(teamName);
        if (t == null || t.type == Team.Type.PLAYER) return "No such team.";
        Team cur = teamOf(target.getUUID());
        if (cur != null && cur.type == Team.Type.PARTY && cur.rankOf(target.getUUID()) == Team.Rank.OWNER)
            return "Transfer ownership or delete the party with confirmation before moving its owner.";
        if (cur != null && cur.id.equals(t.id)) return "Already a member.";
        if (cur != null && cur.type != Team.Type.PLAYER) {
            removeMember(cur, target.getUUID(), "You were moved to another team by an admin.");
        }
        cur = teamOf(target.getUUID());
        Team.Rank r = t.members.isEmpty() && t.type == Team.Type.PARTY ? Team.Rank.OWNER : Team.Rank.MEMBER;
        t.members.put(target.getUUID(), r);
        if (r == Team.Rank.OWNER) t.owner = target.getUUID();
        t.names.put(target.getUUID(), target.getGameProfile().getName());
        changeTeam(target.getUUID(), cur, t);
        syncTeam(t);
        return null;
    }

    public static String adminRemove(ServerPlayer target) {
        Team t = teamOf(target.getUUID());
        if (t == null || t.type == Team.Type.PLAYER) return "That player is not in a team.";
        if (t.type == Team.Type.PARTY && t.rankOf(target.getUUID()) == Team.Rank.OWNER)
            return "Transfer ownership or delete the party with confirmation before removing its owner.";
        removeMember(t, target.getUUID(), "You were removed from '" + t.name + "' by an admin.");
        return null;
    }

    public static String adminDelete(ServerPlayer actor, String teamName) {
        pending.remove(actor.getUUID());
        if (!actor.hasPermissions(2)) return "Only admins can delete teams.";
        Team t = findParty(teamName);
        if (t == null || t.type == Team.Type.PLAYER) return "No such team.";
        pending.put(actor.getUUID(), new PendingConfirmation(t.id, t.name, null, "", true, java.util.Set.copyOf(t.members.keySet()), System.nanoTime() + 60_000_000_000L));
        actor.sendSystemMessage(Component.literal("Delete team '" + t.name + "' and return " + t.members.size() + " members to personal teams? Team data is deleted; where sharing is enabled, quest progress is merged into personal progress. Use /team confirm or /team cancel within 60 seconds.").withStyle(ChatFormatting.YELLOW));
        return null;
    }

    private static String adminDeleteNow(String teamName) {
        Team t = findParty(teamName);
        if (t == null || t.type == Team.Type.PLAYER) return "No such team.";
        for (UUID u : new ArrayList<>(t.members.keySet())) {
            Team personal = data.teams.get(u);
            if (personal == null) personal = ensurePersonal(u, t.nameOf(u));
            changeTeam(u, t, personal);
        }
        List<UUID> members = new ArrayList<>(t.members.keySet());
        t.members.clear();
        deleteTeam(t);
        for (UUID u : members) {
            ServerPlayer sp = server.getPlayerList().getPlayer(u);
            if (sp != null) sync(sp);
        }
        return null;
    }

    // =====================================================================
    // GUI actions
    // =====================================================================

    public static void onAction(ServerPlayer p, Net.TeamAction a) {
        if (data == null) return;
        String err = switch (a.action()) {
            case "create" -> createParty(p, a.a());
            case "invite" -> invite(p, a.a());
            case "join" -> join(p, a.a());
            case "decline" -> decline(p, a.a());
            case "leave" -> leave(p);
            case "kick" -> kick(p, a.a());
            case "promote" -> setRank(p, a.a(), Team.Rank.OFFICER);
            case "demote" -> setRank(p, a.a(), Team.Rank.MEMBER);
            case "transfer" -> setRank(p, a.a(), Team.Rank.OWNER);
            case "disband" -> disband(p);
            case "confirm" -> confirm(p, a.a(), a.b());
            case "cancel_confirmation" -> cancelConfirmation(p);
            case "set" -> setProperty(p, a.a(), a.b());
            case "pin_quest" -> pinQuest(p, a.a());
            case "chat" -> chat(p, a.a());
            case "refresh" -> {
                sync(p);
                yield null;
            }
            default -> "Unknown action.";
        };
        if (err != null) {
            Net.sendTo(p, new Net.Notify(1, 0, err));
            p.sendSystemMessage(Component.literal(err).withStyle(ChatFormatting.RED));
        }
    }
}
