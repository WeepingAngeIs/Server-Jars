package dev.unyielding.quests.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
                .then(Commands.literal("initialize_new").then(Commands.literal("confirm_no_previous_rewards")
                        .executes(c -> initializeNew(c.getSource()))))
                .then(Commands.literal("list").executes(c -> list(c.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1, 1000000))
                                .executes(c -> list(c.getSource(), IntegerArgumentType.getInteger(c, "page")))))
                .then(Commands.literal("retry").executes(c -> retry(c.getSource())))
                .then(Commands.literal("show").then(Commands.argument("transaction", StringArgumentType.word())
                        .executes(c -> show(c.getSource(), StringArgumentType.getString(c, "transaction")))))
                .then(Commands.literal("export").then(Commands.argument("transaction", StringArgumentType.word())
                        .executes(c -> export(c.getSource(), StringArgumentType.getString(c, "transaction")))))
                .then(Commands.literal("confirm_delivered").then(Commands.argument("transaction", StringArgumentType.word())
                        .executes(c -> confirmDelivered(c.getSource(), StringArgumentType.getString(c, "transaction"), false))))
                .then(Commands.literal("confirm_manually_delivered").then(Commands.argument("transaction", StringArgumentType.word())
                        .executes(c -> confirmDelivered(c.getSource(), StringArgumentType.getString(c, "transaction"), true)))));
    }

    private static int initializeNew(CommandSourceStack source) {
        try {
            DailyLogin.provisionNewJournal(source.getServer());
            source.sendSuccess(() -> Component.literal("New recovery journal initialized. This is not power-loss certification. Keep it with world backups; missing history will pause payouts."), true);
            return 1;
        } catch (IOException | RuntimeException failure) {
            source.sendFailure(Component.literal("Initialization refused: " + failure.getMessage() + ". Do not delete history to bypass this check."));
            return 0;
        }
    }

    private static int retry(CommandSourceStack source) {
        try {
            DailyLogin.retryRecovery();
            source.sendSuccess(() -> Component.literal("Reward journal reconciled. No prizes were replayed or refunded."), true);
            return 1;
        } catch (IOException | RuntimeException failure) {
            source.sendFailure(Component.literal("Recovery is still unavailable: " + failure.getMessage()));
            return 0;
        }
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

    private static int list(CommandSourceStack source, int page) {
        RewardJournal journal = journal(source, true);
        if (journal == null) return 0;
        try {
            var entries = journal.list();
            int pending = 0, start = (page - 1) * 30;
            for (var entry : entries) {
                if (entry.state() == RewardJournal.State.COMPLETE) continue;
                if (pending >= start && pending < start + 30) source.sendSuccess(() -> Component.literal(entry.id() + " player=" + entry.player() + " state=" + entry.state()), false);
                pending++;
            }
            final int total = pending;
            source.sendSuccess(() -> Component.literal("Unresolved reward transactions: " + total + ". Page " + page + "/" + Math.max(1, (total + 29) / 30) + ". Use /spinrecovery list <page> or show <transaction>."), false);
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
            boolean attempted = journal.deliveryAttempted(id);
            source.sendSuccess(() -> Component.literal(attempted
                    ? "A delivery attempt was recorded; this does not prove the prize was received. Verify independently."
                    : "No delivery attempt was recorded. Keep this prize on hold for manual recovery."), false);
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
            if (attempted) source.sendSuccess(() -> Component.literal("Verify delivery independently. To acknowledge a delivered prize without replay or refund: /spinrecovery confirm_delivered " + id), false);
            else source.sendSuccess(() -> Component.literal("This prize was not attempted. Export the record and retain it for manual recovery; it will not be replayed automatically."), false);
            source.sendSuccess(() -> Component.literal("After independently providing the exact prize manually, acknowledge it with /spinrecovery confirm_manually_delivered " + id + ". This command gives no prize."), false);
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

    private static int confirmDelivered(CommandSourceStack source, String rawId, boolean manual) {
        RewardJournal journal = journal(source, false); UUID id = parseId(source, rawId);
        if (journal == null || id == null) return 0;
        try {
            var entry = journal.read(id);
            if (entry == null || entry.state() != RewardJournal.State.HELD && entry.state() != RewardJournal.State.DELIVERING) {
                source.sendFailure(Component.literal("Only an uncertain held or delivering transaction can be acknowledged.")); return 0;
            }
            if (!journal.deliveryAttempted(id) && !manual) {
                source.sendFailure(Component.literal("No delivery attempt was recorded. Verify and provide the exact prize manually before using /spinrecovery confirm_manually_delivered " + id)); return 0;
            }
            if (!DailyLogin.reconcileDebit(id)) {
                source.sendFailure(Component.literal("Debit could not be reconciled. Transaction remains unresolved; check the server log.")); return 0;
            }
            if (entry.state() == RewardJournal.State.DELIVERING)
                journal.transition(id, RewardJournal.State.DELIVERING, RewardJournal.State.HELD);
            journal.transition(id, RewardJournal.State.HELD, RewardJournal.State.COMPLETE);
            source.sendSuccess(() -> Component.literal("Transaction " + id + " acknowledged as " + (manual ? "manually delivered" : "delivered") + " by " + source.getTextName() + ". No prize was replayed or refunded."), true);
            dev.unyielding.quests.UnyieldingQuests.LOGGER.info("Reward transaction {} acknowledged as {} by {}", id, manual ? "manually delivered" : "delivered", source.getTextName());
            return 1;
        } catch (IOException | RuntimeException e) {
            source.sendFailure(Component.literal("Could not complete recovery; transaction remains held or needs review. Check the server log."));
            dev.unyielding.quests.UnyieldingQuests.LOGGER.error("Could not acknowledge reward transaction {}", id, e);
            return 0;
        }
    }
}
