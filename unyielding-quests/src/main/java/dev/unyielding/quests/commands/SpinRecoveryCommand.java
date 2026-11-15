package dev.unyielding.quests.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.unyielding.quests.spin.DailyLogin;
import dev.unyielding.quests.spin.RewardJournal;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Operator review only: uncertain prizes are never automatically replayed or refunded. */
public final class SpinRecoveryCommand {
    private SpinRecoveryCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spinrecovery").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("show").then(Commands.argument("transaction", StringArgumentType.word())
                        .executes(c -> show(c.getSource(), StringArgumentType.getString(c, "transaction")))))
                .then(Commands.literal("export").then(Commands.argument("transaction", StringArgumentType.word())
                        .executes(c -> export(c.getSource(), StringArgumentType.getString(c, "transaction")))))
                .then(Commands.literal("confirm_delivered").then(Commands.argument("transaction", StringArgumentType.word())
                        .executes(c -> confirmDelivered(c.getSource(), StringArgumentType.getString(c, "transaction"))))));
    }

    private static RewardJournal journal(CommandSourceStack source, boolean reviewOnly) {
        RewardJournal journal = reviewOnly ? DailyLogin.journalForReview() : DailyLogin.journal();
        if (journal == null) source.sendFailure(Component.literal("Reward journal is unavailable; no recovery action was taken. Check the server log."));
        return journal;
    }

    private static UUID parseId(CommandSourceStack source, String value) {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException e) { source.sendFailure(Component.literal("Enter a valid transaction UUID.")); return null; }
    }

    private static int list(CommandSourceStack source) {
        RewardJournal journal = journal(source, true);
        if (journal == null) return 0;
        try {
            var entries = journal.list();
            int pending = 0, shown = 0;
            for (var entry : entries) {
                if (entry.state() == RewardJournal.State.COMPLETE) continue;
                pending++;
                if (shown++ < 30) source.sendSuccess(() -> Component.literal(entry.id() + " player=" + entry.player() + " state=" + entry.state()), false);
            }
            final int total = pending;
            source.sendSuccess(() -> Component.literal("Unresolved reward transactions: " + total + (total > 30 ? " (first 30 shown)" : "") + ". Use /spinrecovery show <transaction>."), false);
            return 1;
        } catch (IOException | RuntimeException e) {
            source.sendFailure(Component.literal("Could not read the reward journal; no action was taken. Check the server log."));
            dev.unyielding.quests.UnyieldingQuests.LOGGER.error("Could not list reward transactions", e);
            return 0;
        }
    }

    private static int show(CommandSourceStack source, String rawId) {
        RewardJournal journal = journal(source, true); UUID id = parseId(source, rawId);
        if (journal == null || id == null) return 0;
        try {
            var entry = journal.read(id);
            if (entry == null) { source.sendFailure(Component.literal("No such reward transaction.")); return 0; }
            source.sendSuccess(() -> Component.literal("Transaction " + entry.id() + " player=" + entry.player() + " source=" + (entry.slot() == 2 ? "daily" : "quest") + " state=" + entry.state()), false);
            String detail = new String(entry.payload(), StandardCharsets.UTF_8);
            try {
                var prize = net.minecraft.nbt.TagParser.parseTag(detail);
                String summary = "Prize: " + prize.getString("name") + " (" + prize.getString("kind") + ")";
                if (prize.getString("kind").equals("pokemon")) {
                    var pokemon = prize.getCompound("pokemon");
                    summary += " species=" + prize.getString("species") + " level=" + prize.getInt("level") + " shiny=" + prize.getBoolean("shiny");
                    if (pokemon.hasUUID("UUID")) summary += " uuid=" + pokemon.getUUID("UUID");
                    else if (pokemon.hasUUID("uuid")) summary += " uuid=" + pokemon.getUUID("uuid");
                }
                final String printableSummary = summary;
                source.sendSuccess(() -> Component.literal(printableSummary), false);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException malformed) {
                source.sendFailure(Component.literal("Stored prize could not be decoded; preserve the journal and inspect the server log."));
                dev.unyielding.quests.UnyieldingQuests.LOGGER.error("Malformed stored prize for reward transaction {}", id, malformed);
                return 0;
            }
            if (detail.length() > 3500) detail = detail.substring(0, 3500) + " … [truncated; use /spinrecovery export " + id + "]";
            final String printable = detail;
            source.sendSuccess(() -> Component.literal("Recorded prize: " + printable), false);
            source.sendSuccess(() -> Component.literal("Verify delivery independently. To acknowledge a delivered prize without replay or refund: /spinrecovery confirm_delivered " + id), false);
            return 1;
        } catch (IOException | RuntimeException e) {
            source.sendFailure(Component.literal("Could not read the reward journal; no action was taken. Check the server log."));
            dev.unyielding.quests.UnyieldingQuests.LOGGER.error("Could not inspect reward transaction {}", id, e);
            return 0;
        }
    }

    /** Write the exact recorded payload for manual review; never modify an existing export. */
    private static int export(CommandSourceStack source, String rawId) {
        RewardJournal journal = journal(source, true); UUID id = parseId(source, rawId);
        if (journal == null || id == null) return 0;
        try {
            var entry = journal.read(id);
            if (entry == null) { source.sendFailure(Component.literal("No such reward transaction.")); return 0; }
            var directory = source.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("unyielding-recovery").resolve("exports");
            java.nio.file.Files.createDirectories(directory);
            var path = directory.resolve(id.toString() + ".snbt");
            java.nio.file.Files.write(path, entry.payload(), java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
            source.sendSuccess(() -> Component.literal("Exact prize record exported to " + path + ". No delivery was attempted."), false);
            return 1;
        } catch (java.nio.file.FileAlreadyExistsException e) {
            source.sendFailure(Component.literal("That export already exists; inspect it or move it before exporting again.")); return 0;
        } catch (IOException | RuntimeException e) {
            source.sendFailure(Component.literal("Could not export the prize record. Check the server log."));
            dev.unyielding.quests.UnyieldingQuests.LOGGER.error("Could not export reward transaction {}", id, e);
            return 0;
        }
    }

    private static int confirmDelivered(CommandSourceStack source, String rawId) {
        RewardJournal journal = journal(source, false); UUID id = parseId(source, rawId);
        if (journal == null || id == null) return 0;
        try {
            var entry = journal.read(id);
            if (entry == null || entry.state() != RewardJournal.State.HELD && entry.state() != RewardJournal.State.DELIVERING) {
                source.sendFailure(Component.literal("Only an uncertain held or delivering transaction can be acknowledged.")); return 0;
            }
            if (!DailyLogin.reconcileDebit(id)) {
                source.sendFailure(Component.literal("Debit could not be reconciled. Transaction remains unresolved; check the server log.")); return 0;
            }
            if (entry.state() == RewardJournal.State.DELIVERING)
                journal.transition(id, RewardJournal.State.DELIVERING, RewardJournal.State.HELD);
            journal.transition(id, RewardJournal.State.HELD, RewardJournal.State.COMPLETE);
            source.sendSuccess(() -> Component.literal("Transaction " + id + " acknowledged as delivered by " + source.getTextName() + ". No prize was replayed or refunded."), true);
            dev.unyielding.quests.UnyieldingQuests.LOGGER.info("Reward transaction {} acknowledged as delivered by {}", id, source.getTextName());
            return 1;
        } catch (IOException | RuntimeException e) {
            source.sendFailure(Component.literal("Could not complete recovery; transaction remains held or needs review. Check the server log."));
            dev.unyielding.quests.UnyieldingQuests.LOGGER.error("Could not acknowledge reward transaction {}", id, e);
            return 0;
        }
    }
}
