package dev.unyielding.quests.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.QuestFile;
import dev.unyielding.quests.quest.QuestManager;
import dev.unyielding.quests.team.Team;
import dev.unyielding.quests.team.TeamManager;
import dev.unyielding.quests.tutorial.TutorialManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class ModCommands {
    private ModCommands() {
    }

    private static int result(CommandContext<CommandSourceStack> c, String err, String ok) {
        if (err != null) {
            c.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        if (ok != null) c.getSource().sendSuccess(() -> Component.literal(ok).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return c.getSource().getPlayerOrException();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> teamAction(String name, String argName, Function<CommandContext<CommandSourceStack>, String> f, String ok) {
        return Commands.literal(name).then(Commands.argument(argName, StringArgumentType.greedyString())
                .executes(c -> result(c, f.apply(c), ok)));
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("tutorial")
                .executes(c -> {
                    TutorialManager.open(player(c));
                    return 1;
                })
                .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(c -> {
                    boolean loaded = TutorialManager.reload();
                    return result(c, loaded ? null : "Tutorial JSON could not load. Valid content is still active; see the server log.",
                            "Tutorial content reloaded. Use /tutorial to view it.");
                })));
        LiteralArgumentBuilder<CommandSourceStack> team = Commands.literal("team");
        team.then(teamAction("create", "name", c -> {
            try {
                return TeamManager.createParty(player(c), StringArgumentType.getString(c, "name"));
            } catch (CommandSyntaxException e) {
                return "Players only.";
            }
        }, "Party created."));
        team.then(Commands.literal("invite").then(Commands.argument("player", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getServer().getPlayerNames(), b))
                .executes(c -> result(c, safe(c, p -> TeamManager.invite(p, StringArgumentType.getString(c, "player"))), "Invitation sent."))));
        team.then(teamAction("join", "team", c -> safe(c, p -> TeamManager.join(p, StringArgumentType.getString(c, "team"))), "Joined."));
        team.then(teamAction("decline", "team", c -> safe(c, p -> TeamManager.decline(p, StringArgumentType.getString(c, "team"))), null));
        team.then(Commands.literal("leave").executes(c -> result(c, safe(c, TeamManager::leave), null)));
        team.then(Commands.literal("disband").executes(c -> result(c, safe(c, TeamManager::disband), null)));
        team.then(memberAction("kick", c -> safe(c, p -> TeamManager.kick(p, StringArgumentType.getString(c, "player")))));
        team.then(memberAction("promote", c -> safe(c, p -> TeamManager.setRank(p, StringArgumentType.getString(c, "player"), Team.Rank.OFFICER))));
        team.then(memberAction("demote", c -> safe(c, p -> TeamManager.setRank(p, StringArgumentType.getString(c, "player"), Team.Rank.MEMBER))));
        team.then(memberAction("transfer", c -> safe(c, p -> TeamManager.setRank(p, StringArgumentType.getString(c, "player"), Team.Rank.OWNER))));
        team.then(Commands.literal("set").then(Commands.argument("setting", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("name", "description", "color", "free"), b))
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(c -> result(c, safe(c, p -> TeamManager.setProperty(p, StringArgumentType.getString(c, "setting"), StringArgumentType.getString(c, "value"))), "Updated.")))));
        team.then(teamAction("msg", "text", c -> safe(c, p -> TeamManager.chat(p, StringArgumentType.getString(c, "text"))), null));
        team.then(Commands.literal("gui").executes(c -> {
            Net.sendTo(player(c), new Net.OpenScreen(1));
            return 1;
        }));
        team.then(Commands.literal("info").executes(c -> info(c, safeTeam(c))));
        team.then(Commands.literal("info").then(Commands.argument("team", StringArgumentType.greedyString())
                .executes(c -> info(c, TeamManager.findParty(StringArgumentType.getString(c, "team"))))));
        team.then(Commands.literal("list").executes(c -> {
            StringBuilder sb = new StringBuilder("Teams:");
            for (Team t : TeamManager.data().teams.values()) {
                if (t.type == Team.Type.PLAYER) continue;
                sb.append("\n - ").append(t.name).append(" (").append(t.members.size()).append(" members)");
            }
            c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
            return 1;
        }));
        team.then(Commands.literal("server").requires(s -> s.hasPermission(2))
                .then(teamAction("create", "name", c -> TeamManager.adminCreateServerTeam(StringArgumentType.getString(c, "name")), "Server team created.")));
        team.then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                .then(Commands.literal("add").then(Commands.argument("team", StringArgumentType.string())
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                            ServerPlayer t = EntityArgument.getPlayer(c, "player");
                            return result(c, TeamManager.adminAdd(StringArgumentType.getString(c, "team"), t), "Added.");
                        }))))
                .then(Commands.literal("remove").then(Commands.argument("player", EntityArgument.player()).executes(c ->
                        result(c, TeamManager.adminRemove(EntityArgument.getPlayer(c, "player")), "Removed."))))
                .then(Commands.literal("delete").then(Commands.argument("team", StringArgumentType.greedyString()).executes(c ->
                        result(c, TeamManager.adminDelete(StringArgumentType.getString(c, "team")), "Deleted.")))));
        d.register(team);

        LiteralArgumentBuilder<CommandSourceStack> quests = Commands.literal("quests");
        quests.executes(c -> {
            Net.sendTo(player(c), new Net.OpenScreen(0));
            return 1;
        });
        quests.then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(c -> {
            List<String> w = QuestManager.reload(false);
            c.getSource().sendSuccess(() -> Component.literal("Quest book reloaded: " + QuestManager.file.quests.size() + " quests"
                    + (w.isEmpty() ? "" : ", " + w.size() + " warnings (see log)")), true);
            for (String s : w) dev.unyielding.quests.UnyieldingQuests.LOGGER.warn("[quests] {}", s);
            return 1;
        }));
        quests.then(Commands.literal("import_ftb").requires(s -> s.hasPermission(2)).executes(c -> {
            List<String> w = QuestManager.reload(true);
            c.getSource().sendSuccess(() -> Component.literal("Re-imported config/ftbquests/quests: " + QuestManager.file.quests.size() + " quests"
                    + ("builtin".equals(dev.unyielding.quests.Config.QUEST_BOOK_SOURCE.get()) ? ". Set quest_book_source = \"config_folder\" in unyieldingquests-common.toml to keep using it after a restart." : "")), true);
            for (String s : w) dev.unyielding.quests.UnyieldingQuests.LOGGER.warn("[quests] {}", s);
            return 1;
        }));
        quests.then(Commands.literal("complete").requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("quest", StringArgumentType.word()).executes(c -> result(c,
                                QuestManager.adminComplete(EntityArgument.getPlayer(c, "player"), QuestFile.parseId(StringArgumentType.getString(c, "quest"))),
                                "Quest completed.")))));
        quests.then(Commands.literal("reset").requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> result(c, QuestManager.adminReset(EntityArgument.getPlayer(c, "player"), 0), "All progress reset."))
                        .then(Commands.argument("quest", StringArgumentType.word()).executes(c -> result(c,
                                QuestManager.adminReset(EntityArgument.getPlayer(c, "player"), QuestFile.parseId(StringArgumentType.getString(c, "quest"))),
                                "Quest reset.")))));
        quests.then(Commands.literal("stats").executes(c -> {
            ServerPlayer p = player(c);
            int done = QuestManager.completedCount(p);
            c.getSource().sendSuccess(() -> Component.literal("Completed " + done + " / " + QuestManager.file.quests.size() + " quests."), false);
            return 1;
        }));
        d.register(quests);

        LiteralArgumentBuilder<CommandSourceStack> daily = Commands.literal("daily");
        daily.executes(c -> {
            ServerPlayer p = player(c);
            c.getSource().sendSuccess(() -> Component.literal(dev.unyielding.quests.spin.DailyLogin.status(p)).withStyle(ChatFormatting.GOLD), false);
            return 1;
        });
        daily.then(Commands.literal("give").requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    boolean ok = dev.unyielding.quests.spin.DailyLogin.give(EntityArgument.getPlayer(c, "player"));
                    return result(c, ok ? null : "That player already got today's reward.", "Daily reward given.");
                })));
        d.register(daily);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> memberAction(String name, Function<CommandContext<CommandSourceStack>, String> f) {
        return Commands.literal(name).then(Commands.argument("player", StringArgumentType.word())
                .suggests((c, b) -> {
                    try {
                        Team t = TeamManager.teamOf(c.getSource().getPlayerOrException().getUUID());
                        if (t != null) return SharedSuggestionProvider.suggest(t.names.values(), b);
                    } catch (CommandSyntaxException ignored) {
                    }
                    return SharedSuggestionProvider.suggest(List.of(), b);
                })
                .executes(c -> result(c, f.apply(c), "Done.")));
    }

    private interface PlayerFn {
        String run(ServerPlayer p);
    }

    private static String safe(CommandContext<CommandSourceStack> c, PlayerFn f) {
        try {
            return f.run(c.getSource().getPlayerOrException());
        } catch (CommandSyntaxException e) {
            return "This command can only be used by players.";
        }
    }

    private static Team safeTeam(CommandContext<CommandSourceStack> c) {
        try {
            return TeamManager.teamOf(c.getSource().getPlayerOrException().getUUID());
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    private static int info(CommandContext<CommandSourceStack> c, Team t) {
        if (t == null) {
            c.getSource().sendFailure(Component.literal("No such team."));
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(t.name).append(" (").append(t.type.name().toLowerCase()).append(")");
        if (!t.description.isEmpty()) sb.append("\n").append(t.description);
        sb.append("\nMembers:");
        for (Map.Entry<UUID, Team.Rank> e : t.members.entrySet()) {
            sb.append("\n - ").append(t.nameOf(e.getKey())).append(" [").append(e.getValue().name().toLowerCase()).append("]");
        }
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }
}
