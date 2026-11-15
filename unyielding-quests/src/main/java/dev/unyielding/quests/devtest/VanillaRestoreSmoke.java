package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.jail.JailEscrowJournal;
import dev.unyielding.quests.jail.JailVanillaCustody;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Disposable real-player restoration fixtures, excluded from the released mod. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID)
public final class VanillaRestoreSmoke {
    private static final UUID BEFORE = UUID.fromString("d5bfaa04-c188-4f72-9df6-ff1ce2e7b101");
    private static final UUID SAVED = UUID.fromString("d5bfaa04-c188-4f72-9df6-ff1ce2e7b102");
    private static final UUID DONE = UUID.fromString("d5bfaa04-c188-4f72-9df6-ff1ce2e7b103");
    private static boolean finished;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (finished || !Boolean.getBoolean("uq.vanillaRestore")) return;
        var server = event.getServer();
        var admin = server.getPlayerList().getPlayerByName("Dev");
        var visitor = server.getPlayerList().getPlayerByName("Visitor");
        if (admin == null || visitor == null || server.getTickCount() < 100) return;
        finished = true;
        try {
            require("true".equals(System.getenv("GITHUB_ACTIONS")) && "github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")), "CI fixtures forbidden here");
            require(server.getWorldPath(LevelResource.ROOT).toRealPath().getFileName().toString().equals("jail_restore_smoke"), "not disposable restoration world");
            require(Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath().equals(
                    net.neoforged.fml.ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()), "not assembled JAR");
            require(!System.getProperty("java.class.path", "").contains("unyielding-quests/build/classes"), "development classes leaked");
            Path directory = server.getWorldPath(LevelResource.ROOT).resolve("restore-tests");
            int phase = Integer.getInteger("uq.restorePhase", 1);
            if (phase == 1) {
                Files.createDirectory(directory);
                for (var boundary : JailVanillaCustody.RestoreBoundary.values()) {
                    VanillaCustodySmoke.seed(admin); Path path = directory.resolve(boundary.name());
                    UUID id = UUID.randomUUID(); prepare(path, id, admin);
                    var port = new TracePort(admin);
                    try (var journal = new JailEscrowJournal(path)) {
                        expectFailure(() -> JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {
                            if (at == boundary) throw new IOException("injected " + at);
                        }), "injected " + boundary);
                    }
                    try (var journal = new JailEscrowJournal(path)) {
                        var expected = switch (boundary) {
                            case BEFORE_PHASE -> JailVanillaCustody.Inspection.HELD_VERIFIED;
                            case STARTED, BEFORE_MUTATION -> JailVanillaCustody.Inspection.RESTORE_NOT_PERSISTED_REVIEW;
                            case MUTATED, BEFORE_SAVE -> JailVanillaCustody.Inspection.REVIEW_REQUIRED;
                            case PLAYER_DURABLE, BEFORE_COMPLETE -> JailVanillaCustody.Inspection.RESTORE_PERSISTED_REVIEW;
                            case COMPLETE -> JailVanillaCustody.Inspection.COMPLETE_RECORDED;
                        };
                        require(JailVanillaCustody.inspect(journal, id, port) == expected, "wrong restoration boundary " + boundary);
                        byte[] unchanged = port.current().inventory(); int grants = port.grants;
                        if (boundary == JailVanillaCustody.RestoreBoundary.BEFORE_PHASE) {
                            require(grants == 0, "pre-intent grant");
                            require(JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}) == JailVanillaCustody.RestoreResult.RESTORED_VERIFIED, "fresh retry failed");
                            assertFixture(admin, journal.read(id).snapshot());
                        } else if (boundary == JailVanillaCustody.RestoreBoundary.COMPLETE) {
                            require(JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}) == JailVanillaCustody.RestoreResult.ALREADY_COMPLETE, "repeat complete not rejected");
                            require(port.grants == grants && Arrays.equals(unchanged, port.current().inventory()), "complete regranted");
                        } else {
                            for (int i=0; i<2; i++) expectFailure(() -> JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}), "review");
                            require(port.grants == grants && Arrays.equals(unchanged, port.current().inventory()), "interrupted restore replayed");
                        }
                        require(port.grants <= 1 && journal.read(id).snapshot().length > 100, "source lost or duplicated");
                    }
                }
                negatives(directory, admin);
                VanillaCustodySmoke.seed(admin); VanillaCustodySmoke.seed(visitor);
                prepare(directory.resolve("before"), BEFORE, admin); prepare(directory.resolve("saved"), SAVED, visitor);
                try (var journal = new JailEscrowJournal(directory.resolve("before"))) {
                    expectFailure(() -> JailVanillaCustody.restoreForDisposableTest(journal, BEFORE, new TracePort(admin), at -> {
                        if (at == JailVanillaCustody.RestoreBoundary.STARTED) throw new IOException("before restart");
                    }), "before restart");
                }
                try (var journal = new JailEscrowJournal(directory.resolve("saved"))) {
                    expectFailure(() -> JailVanillaCustody.restoreForDisposableTest(journal, SAVED, new TracePort(visitor), at -> {
                        if (at == JailVanillaCustody.RestoreBoundary.PLAYER_DURABLE) throw new IOException("saved before restart");
                    }), "saved before restart");
                    assertFixture(visitor, journal.read(SAVED).snapshot());
                }
                server.saveEverything(true, true, true);
            } else if (phase == 2) {
                try (var journal = new JailEscrowJournal(directory.resolve("before"))) {
                    var port = new TracePort(admin);
                    require(JailVanillaCustody.inspect(journal, BEFORE, port) == JailVanillaCustody.Inspection.RESTORE_NOT_PERSISTED_REVIEW, "pre-mutation restart misclassified");
                    expectFailure(() -> JailVanillaCustody.restoreForDisposableTest(journal, BEFORE, port, at -> {}), "review");
                    expectFailure(() -> JailVanillaCustody.confirmRestoredForDisposableTest(journal, BEFORE, port), "No exact");
                    require(port.grants == 0 && admin.getInventory().isEmpty(), "pre-mutation replay granted items");
                }
                try (var journal = new JailEscrowJournal(directory.resolve("saved"))) {
                    var port = new TracePort(visitor);
                    require(JailVanillaCustody.inspect(journal, SAVED, port) == JailVanillaCustody.Inspection.RESTORE_PERSISTED_REVIEW, "post-save restart misclassified");
                    assertFixture(visitor, journal.read(SAVED).snapshot());
                    expectFailure(() -> JailVanillaCustody.restoreForDisposableTest(journal, SAVED, port, at -> {}), "review");
                    JailVanillaCustody.confirmRestoredForDisposableTest(journal, SAVED, port);
                    require(port.grants == 0 && port.saves == 0, "confirmation transferred items");
                }
                // Separate new destructive fixture; the unresolved earlier record
                // remains intact. This never grants from the interrupted record.
                VanillaCustodySmoke.seed(admin); prepare(directory.resolve("done"), DONE, admin);
                try (var journal = new JailEscrowJournal(directory.resolve("done"))) {
                    var port = new TracePort(admin);
                    require(JailVanillaCustody.restoreForDisposableTest(journal, DONE, port, at -> {}) == JailVanillaCustody.RestoreResult.RESTORED_VERIFIED, "successful restore failed");
                    assertFixture(admin, journal.read(DONE).snapshot());
                    require(port.grants == 1, "successful grant count");
                }
                server.saveEverything(true, true, true);
            } else if (phase == 3) {
                for (var player : new ServerPlayer[]{admin, visitor}) {
                    UUID id = player == admin ? DONE : SAVED;
                    try (var journal = new JailEscrowJournal(directory.resolve(player == admin ? "done" : "saved"))) {
                        assertFixture(player, journal.read(id).snapshot());
                        // A consumed/moved item after completion must never be replaced.
                        player.getInventory().setItem(2, ItemStack.EMPTY);
                        var port = new TracePort(player); port.base.persistAndVerify();
                        byte[] changed = port.current().inventory();
                        for (int i=0; i<3; i++) require(JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}) == JailVanillaCustody.RestoreResult.ALREADY_COMPLETE, "completion forgotten after restart");
                        require(port.grants == 0 && Arrays.equals(changed, port.current().inventory()), "completed restore duplicated consumed item");
                        require(journal.read(id).snapshot().length > 100, "terminal recovery history discarded");
                    }
                }
            } else throw new IOException("bad phase");
            Files.writeString(Path.of("/tmp/uq-vanilla-restore-result"), "passed: assembled vanilla restoration phase " + phase + "; original slots/components, strict capacity, no replay, forced saves and retained recovery history\n");
        } catch (Throwable failure) {
            UnyieldingQuests.LOGGER.error("Vanilla restoration smoke failed", failure);
            try { Files.writeString(Path.of("/tmp/uq-vanilla-restore-result"), "failed: " + failure + "\n"); } catch (Exception ignored) {}
        }
    }
    private static void prepare(Path path, UUID id, ServerPlayer player) throws Exception {
        JailEscrowJournal.provisionNew(path);
        try (var journal = new JailEscrowJournal(path)) { JailVanillaCustody.removeForDisposableTest(journal, id, new VanillaCustodySmoke.PlayerPort(player), at -> {}); }
    }
    private static void assertFixture(ServerPlayer player, byte[] snapshot) throws IOException {
        require(Arrays.equals(snapshot, new VanillaCustodySmoke.PlayerPort(player).current().inventory()), "original slots/components lost");
        ItemStack sword = player.getInventory().getItem(2);
        require(sword.is(Items.NETHERITE_SWORD) && sword.getDamageValue() == 17 && sword.get(DataComponents.CUSTOM_NAME) != null
                && sword.get(DataComponents.CUSTOM_DATA) != null, "sword components lost");
        require(player.getInventory().getItem(36).is(Items.DIAMOND_BOOTS) && player.getInventory().getItem(40).is(Items.SHIELD), "armor/offhand source slots lost");
        require(player.getInventory().getItem(8).get(DataComponents.CONTAINER) != null, "nested contents lost");
    }
    private static void negatives(Path directory, ServerPlayer player) throws Exception {
        for (String problem : new String[]{"occupied", "full", "offline", "overflow", "saveBefore", "saveAfter", "partial", "changed"}) {
            VanillaCustodySmoke.seed(player);
            if (problem.equals("overflow")) { player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 65)); new VanillaCustodySmoke.PlayerPort(player).persistAndVerify(); }
            Path path = directory.resolve(problem); UUID id = UUID.randomUUID(); prepare(path, id, player);
            var port = new TracePort(player);
            if (problem.equals("occupied")) player.getInventory().setItem(2, new ItemStack(Items.DIRT));
            if (problem.equals("full")) for (int slot=0; slot<player.getInventory().getContainerSize(); slot++) player.getInventory().setItem(slot, new ItemStack(Items.DIRT));
            if (problem.equals("occupied") || problem.equals("full")) port.base.persistAndVerify();
            port.offline = problem.equals("offline"); port.saveBefore = problem.equals("saveBefore"); port.saveAfter = problem.equals("saveAfter"); port.partial = problem.equals("partial");
            try (var journal = new JailEscrowJournal(path)) {
                byte[] captured = journal.read(id).snapshot(), untouched = port.current().inventory();
                expectFailure(() -> JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {
                    if (problem.equals("changed") && at == JailVanillaCustody.RestoreBoundary.BEFORE_MUTATION) player.getInventory().setItem(2, new ItemStack(Items.DIRT));
                }), "");
                require(Arrays.equals(captured, journal.read(id).snapshot()), "failed restore discarded source");
                if (problem.equals("occupied") || problem.equals("full") || problem.equals("offline") || problem.equals("overflow")) {
                    require(journal.read(id).state() == JailEscrowJournal.State.HELD && port.grants == 0
                            && Arrays.equals(untouched, port.current().inventory()), "refusal changed destination");
                } else {
                    require(journal.read(id).state() == JailEscrowJournal.State.RESTORE_STARTED, "ambiguous outcome completed");
                    if (problem.equals("partial")) port.base.persistAndVerify();
                    int grants = port.grants; byte[] changed = port.current().inventory();
                    expectFailure(() -> JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}), "review");
                    require(port.grants == grants && Arrays.equals(changed, port.current().inventory()), "failed outcome regranted");
                    if (!problem.equals("saveAfter")) expectFailure(() -> JailVanillaCustody.confirmRestoredForDisposableTest(journal, id, port), "No exact");
                }
                require(port.grants <= 1, "duplicate restore");
            }
        }
    }
    private static final class TracePort implements JailVanillaCustody.InventoryPort {
        final VanillaCustodySmoke.PlayerPort base;
        int grants, saves; boolean offline, saveBefore, saveAfter, partial;
        TracePort(ServerPlayer player) { base = new VanillaCustodySmoke.PlayerPort(player); }
        public UUID owner() { return base.owner(); }
        public boolean online() { return !offline && base.online(); }
        public JailVanillaCustody.View current() throws IOException { return base.current(); }
        public JailVanillaCustody.View durable() throws IOException { return base.durable(); }
        public void clearAndMark(UUID id) throws IOException { base.clearAndMark(id); }
        public JailVanillaCustody.View planRestore(byte[] snapshot, UUID id) throws IOException { return base.planRestore(snapshot, id); }
        public void restoreExact(byte[] snapshot, UUID id) throws IOException {
            grants++;
            if (partial) {
                var root = NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(snapshot)), NbtAccounter.create(16L*1024*1024));
                var staged = new net.minecraft.world.entity.player.Inventory(base.player()); staged.load(root.getList("items", 10));
                base.player().getInventory().setItem(2, staged.getItem(2).copy());
                throw new IOException("partial setter failure");
            }
            base.restoreExact(snapshot, id);
        }
        public void persistAndVerify() throws IOException {
            saves++; if (saveBefore) throw new IOException("injected before save"); base.persistAndVerify();
            if (saveAfter) throw new IOException("injected after save");
        }
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void expectFailure(Action action, String message) throws Exception {
        try { action.run(); } catch (IOException expected) {
            require(expected.getMessage().contains(message), "unexpected refusal: " + expected); return;
        }
        throw new AssertionError("Expected failure: " + message);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
