package dev.unyielding.quests.essentials;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static dev.unyielding.quests.essentials.Essentials.*;

/** Teleport commands: home, back, warp, spawn, rtp, tpa, jump, tpl, tpx. */
final class TeleCmds {
    private TeleCmds() {
    }

    private record Request(UUID from, boolean here, long time) {
    }

    /** target -> requests made to them */
    private static final Map<UUID, List<Request>> REQUESTS = new HashMap<>();
    private static final Random RNG = new Random();

    static void clearRequests() { REQUESTS.clear(); }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        registerHomes(d);
        registerWarps(d);
        registerSimple(d);
        registerTpa(d);
        registerAdmin(d);
    }

    private static List<String> homeNames(CommandSourceStack s) {
        try {
            Map<String, Loc> m = data().homes.get(s.getPlayerOrException().getUUID());
            return m == null ? List.of() : new ArrayList<>(m.keySet());
        } catch (Exception e) {
            return List.of();
        }
    }

    private static void registerHomes(CommandDispatcher<CommandSourceStack> d) {
        reg(d, "home", Commands.literal("home")
                .executes(c -> goHome(c.getSource(), "home"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(homeNames(c.getSource()), b))
                        .executes(c -> goHome(c.getSource(), StringArgumentType.getString(c, "name")))));
        reg(d, "sethome", Commands.literal("sethome")
                .executes(c -> setHome(c.getSource(), "home"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> setHome(c.getSource(), StringArgumentType.getString(c, "name")))));
        reg(d, "delhome", Commands.literal("delhome")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(homeNames(c.getSource()), b))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Map<String, Loc> m = data().homes.get(p.getUUID());
                            String n = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
                            if (m == null || m.remove(n) == null) return fail(c.getSource(), "You have no home called '" + n + "'.");
                            data().setDirty();
                            ok(c.getSource(), "Home '" + n + "' deleted.");
                            return 1;
                        })));
        reg(d, "listhomes", Commands.literal("listhomes").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            Map<String, Loc> m = data().homes.get(p.getUUID());
            if (m == null || m.isEmpty()) return fail(c.getSource(), "You have no homes. Use /sethome.");
            StringBuilder sb = new StringBuilder("Homes (" + m.size() + "/" + EssConfig.HOME_MAX.get() + "):");
            for (var e : m.entrySet()) sb.append("\n - ").append(e.getKey()).append("  ").append(e.getValue().pretty());
            c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
            return 1;
        }));
    }

    private static int goHome(CommandSourceStack s, String rawName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        String name = rawName.toLowerCase(Locale.ROOT);
        Map<String, Loc> m = data().homes.get(p.getUUID());
        Loc loc = m == null ? null : m.get(name);
        if (loc == null) return fail(s, "You have no home called '" + name + "'. Use /sethome " + (name.equals("home") ? "" : name));
        String err = cooldown(p, "home", EssConfig.HOME_COOLDOWN.get());
        if (err != null) return fail(s, err);
        if (!teleport(p, loc)) return fail(s, "That dimension no longer exists.");
        ok(s, "Welcome home!");
        return 1;
    }

    private static int setHome(CommandSourceStack s, String rawName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        String name = rawName.toLowerCase(Locale.ROOT);
        Map<String, Loc> m = data().homes.computeIfAbsent(p.getUUID(), k -> new java.util.LinkedHashMap<>());
        if (!m.containsKey(name) && m.size() >= EssConfig.HOME_MAX.get() && !p.hasPermissions(2)) {
            return fail(s, "You can only have " + EssConfig.HOME_MAX.get() + " home(s). Delete one with /delhome <name>.");
        }
        m.put(name, Loc.of(p));
        data().setDirty();
        ok(s, "Home '" + name + "' set.");
        return 1;
    }

    private static void registerWarps(CommandDispatcher<CommandSourceStack> d) {
        reg(d, "warp", Commands.literal("warp")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(data().warps.keySet(), b))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            String n = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
                            Loc loc = data().warps.get(n);
                            if (loc == null) return fail(c.getSource(), "No warp called '" + n + "'.");
                            String err = cooldown(p, "warp", EssConfig.WARP_COOLDOWN.get());
                            if (err != null) return fail(c.getSource(), err);
                            if (!teleport(p, loc)) return fail(c.getSource(), "That dimension no longer exists.");
                            ok(c.getSource(), "Warped to " + n + ".");
                            return 1;
                        })));
        reg(d, "setwarp", Commands.literal("setwarp").requires(s -> s.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String n = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
                    data().warps.put(n, Loc.of(p));
                    data().setDirty();
                    ok(c.getSource(), "Warp '" + n + "' set.");
                    return 1;
                })));
        reg(d, "delwarp", Commands.literal("delwarp").requires(s -> s.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(data().warps.keySet(), b))
                        .executes(c -> {
                            String n = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
                            if (data().warps.remove(n) == null) return fail(c.getSource(), "No warp called '" + n + "'.");
                            data().setDirty();
                            ok(c.getSource(), "Warp '" + n + "' deleted.");
                            return 1;
                        })));
        reg(d, "listwarps", Commands.literal("listwarps").executes(c -> {
            if (data().warps.isEmpty()) return fail(c.getSource(), "There are no warps yet.");
            c.getSource().sendSuccess(() -> Component.literal("Warps: " + String.join(", ", data().warps.keySet())), false);
            return 1;
        }));
    }

    private static void registerSimple(CommandDispatcher<CommandSourceStack> d) {
        reg(d, "back", Commands.literal("back").executes(c -> {
            CommandSourceStack s = c.getSource();
            ServerPlayer p = s.getPlayerOrException();
            List<Loc> l = data().back.get(p.getUUID());
            if (l == null || l.isEmpty()) return fail(s, "Nowhere to go back to.");
            String err = cooldown(p, "back", EssConfig.BACK_COOLDOWN.get());
            if (err != null) return fail(s, err);
            Loc to = l.remove(l.size() - 1);
            data().setDirty();
            if (!to.teleport(p)) return fail(s, "That dimension no longer exists.");
            ok(s, "Teleported back.");
            return 1;
        }));
        reg(d, "spawn", Commands.literal("spawn").executes(c -> {
            CommandSourceStack s = c.getSource();
            ServerPlayer p = s.getPlayerOrException();
            String err = cooldown(p, "spawn", EssConfig.SPAWN_COOLDOWN.get());
            if (err != null) return fail(s, err);
            ServerLevel o = s.getServer().overworld();
            BlockPos sp = o.getSharedSpawnPos();
            Integer y = safeY(o, sp.getX(), sp.getZ());
            teleport(p, o, sp.getX() + 0.5, y != null ? y : sp.getY(), sp.getZ() + 0.5);
            ok(s, "Teleported to spawn.");
            return 1;
        }));
        reg(d, "playerspawn", Commands.literal("playerspawn").executes(c -> {
            CommandSourceStack s = c.getSource();
            ServerPlayer p = s.getPlayerOrException();
            BlockPos pos = p.getRespawnPosition();
            ServerLevel lvl = s.getServer().getLevel(p.getRespawnDimension());
            if (pos == null || lvl == null) return fail(s, "You have no bed or respawn anchor set.");
            String err = cooldown(p, "playerspawn", EssConfig.PLAYERSPAWN_COOLDOWN.get());
            if (err != null) return fail(s, err);
            Integer y = safeY(lvl, pos.getX(), pos.getZ());
            teleport(p, lvl, pos.getX() + 0.5, y != null ? Math.max(y, pos.getY()) : pos.getY() + 1, pos.getZ() + 0.5);
            ok(s, "Teleported to your spawn point.");
            return 1;
        }));
        reg(d, "rtp", Commands.literal("rtp").executes(c -> rtp(c.getSource())));
    }

    private static int rtp(CommandSourceStack s) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        ServerLevel level = p.serverLevel();
        String dim = level.dimension().location().toString();
        if (EssConfig.RTP_BLACKLIST.get().contains(dim)) return fail(s, "Random teleport does not work in this dimension.");
        if (!EssConfig.RTP_WHITELIST.get().isEmpty() && !EssConfig.RTP_WHITELIST.get().contains(dim)) return fail(s, "Random teleport does not work in this dimension.");
        String err = cooldown(p, "rtp", EssConfig.RTP_COOLDOWN.get());
        if (err != null) return fail(s, err);
        int min = Math.min(EssConfig.RTP_MIN.get(), EssConfig.RTP_MAX.get());
        int max = EssConfig.RTP_MAX.get();
        BlockPos center = level.getSharedSpawnPos();
        for (int i = 0; i < EssConfig.RTP_TRIES.get(); i++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double dist = min + RNG.nextDouble() * (max - min);
            int x = center.getX() + (int) (Math.cos(ang) * dist);
            int z = center.getZ() + (int) (Math.sin(ang) * dist);
            if (!level.getWorldBorder().isWithinBounds(x, z)) continue;
            Integer y = safeY(level, x, z);
            if (y == null) continue;
            teleport(p, level, x + 0.5, y, z + 0.5);
            ok(s, "Teleported to " + x + ", " + y + ", " + z + ".");
            return 1;
        }
        // nothing found: give the cooldown back
        return fail(s, "Could not find a safe spot. Try again.");
    }

    // ---- tpa ----------------------------------------------------------------

    private static void registerTpa(CommandDispatcher<CommandSourceStack> d) {
        reg(d, "tpa", Commands.literal("tpa").then(Commands.argument("player", EntityArgument.player())
                .executes(c -> request(c.getSource(), EntityArgument.getPlayer(c, "player"), false))));
        reg(d, "tpahere", Commands.literal("tpahere").then(Commands.argument("player", EntityArgument.player())
                .executes(c -> request(c.getSource(), EntityArgument.getPlayer(c, "player"), true))));
        reg(d, "tpaccept", Commands.literal("tpaccept")
                .executes(c -> answer(c.getSource(), null, true))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> answer(c.getSource(), EntityArgument.getPlayer(c, "player"), true))));
        reg(d, "tpdeny", Commands.literal("tpdeny")
                .executes(c -> answer(c.getSource(), null, false))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> answer(c.getSource(), EntityArgument.getPlayer(c, "player"), false))));
    }

    private static int request(CommandSourceStack s, ServerPlayer target, boolean here) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        if (target == p) return fail(s, "You cannot teleport to yourself.");
        String err = cooldown(p, "tpa", EssConfig.TPA_COOLDOWN.get());
        if (err != null) return fail(s, err);
        List<Request> l = REQUESTS.computeIfAbsent(target.getUUID(), k -> new ArrayList<>());
        l.removeIf(r -> r.from.equals(p.getUUID()));
        l.add(new Request(p.getUUID(), here, System.currentTimeMillis()));
        String name = p.getGameProfile().getName();
        target.sendSystemMessage(Component.literal(name + (here ? " wants you to teleport to them. " : " wants to teleport to you. "))
                .withStyle(ChatFormatting.YELLOW)
                .append(clickable("[Accept]", "/tpaccept " + name, ChatFormatting.GREEN))
                .append(Component.literal(" "))
                .append(clickable("[Deny]", "/tpdeny " + name, ChatFormatting.RED)));
        ok(s, "Request sent to " + target.getGameProfile().getName() + ". It expires in 60 seconds.");
        return 1;
    }

    private static int answer(CommandSourceStack s, ServerPlayer from, boolean accept) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer me = s.getPlayerOrException();
        List<Request> l = REQUESTS.get(me.getUUID());
        long now = System.currentTimeMillis();
        if (l != null) l.removeIf(r -> now - r.time > 60_000);
        if (l == null || l.isEmpty()) return fail(s, "You have no teleport requests.");
        Request req = null;
        for (Request r : l) {
            if (from == null || r.from.equals(from.getUUID())) {
                req = r;
                if (from != null) break;
            }
        }
        if (req == null) return fail(s, "No request from that player.");
        l.remove(req);
        ServerPlayer requester = s.getServer().getPlayerList().getPlayer(req.from);
        if (requester == null) return fail(s, "That player is no longer online.");
        if (!accept) {
            requester.sendSystemMessage(Component.literal(me.getGameProfile().getName() + " denied your teleport request.").withStyle(ChatFormatting.RED));
            ok(s, "Request denied.");
            return 1;
        }
        ServerPlayer mover = req.here ? me : requester;
        ServerPlayer dest = req.here ? requester : me;
        teleport(mover, dest.serverLevel(), dest.getX(), dest.getY(), dest.getZ());
        ok(s, "Teleport accepted.");
        requester.sendSystemMessage(Component.literal("Teleport request accepted.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    // ---- admin teleports ------------------------------------------------------

    private static void registerAdmin(CommandDispatcher<CommandSourceStack> d) {
        reg(d, "jump", Commands.literal("jump").requires(s -> s.hasPermission(2)).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            HitResult hit = p.pick(256, 1f, false);
            if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) return fail(c.getSource(), "You are not looking at a block.");
            BlockPos pos = bhr.getBlockPos().relative(bhr.getDirection());
            teleport(p, p.serverLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            ok(c.getSource(), "Jumped.");
            return 1;
        }));
        LiteralArgumentBuilder<CommandSourceStack> tpl = Commands.literal("tpl").requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(data().names.values(), b))
                        .executes(c -> tpToLast(c.getSource(), StringArgumentType.getString(c, "player"))));
        reg(d, "tpl", tpl);
        reg(d, "tp_offline", Commands.literal("tp_offline").requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(data().names.values(), b))
                        .executes(c -> tpToLast(c.getSource(), StringArgumentType.getString(c, "player")))));
        reg(d, "tpx", Commands.literal("tpx").requires(s -> s.hasPermission(2))
                .then(Commands.argument("dimension", DimensionArgument.dimension()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ServerLevel lvl = DimensionArgument.getDimension(c, "dimension");
                    int x = p.getBlockX(), z = p.getBlockZ();
                    Integer y = safeY(lvl, x, z);
                    if (y == null) y = Math.max(lvl.getMinBuildHeight() + 1, Math.min(p.getBlockY(), lvl.getMaxBuildHeight() - 2));
                    teleport(p, lvl, x + 0.5, y, z + 0.5);
                    ok(c.getSource(), "Teleported to " + lvl.dimension().location() + ".");
                    return 1;
                })));
    }

    private static int tpToLast(CommandSourceStack s, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        ServerPlayer online = s.getServer().getPlayerList().getPlayerByName(name);
        Loc to = null;
        if (online != null) {
            to = Loc.of(online);
        } else {
            for (var e : data().names.entrySet()) {
                if (e.getValue().equalsIgnoreCase(name)) to = data().lastSeen.get(e.getKey());
            }
        }
        if (to == null) return fail(s, "No known location for '" + name + "'.");
        if (!teleport(p, to)) return fail(s, "That dimension no longer exists.");
        ok(s, "Teleported to " + name + "'s last known location.");
        return 1;
    }
}
