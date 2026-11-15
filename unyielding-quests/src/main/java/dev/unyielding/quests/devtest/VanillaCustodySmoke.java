package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.jail.JailEscrowJournal;
import dev.unyielding.quests.jail.JailVanillaCustody;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Destructive fixtures ONLY in a separate disposable CI world; never shipped in the release. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID)
public final class VanillaCustodySmoke {
    private static final String MARKER = "uq_disposable_custody";
    private static final UUID HELD = UUID.fromString("73409e80-1541-428a-8aba-dd3695ff2f01");
    private static final UUID INTERRUPTED = UUID.fromString("73409e80-1541-428a-8aba-dd3695ff2f02");
    private static boolean finished;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (finished || !Boolean.getBoolean("uq.vanillaCustody")) return;
        var server = event.getServer();
        var admin = server.getPlayerList().getPlayerByName("Dev");
        var visitor = server.getPlayerList().getPlayerByName("Visitor");
        if (admin == null || visitor == null || server.getTickCount() < 100) return;
        finished = true;
        Path result = Path.of("/tmp/uq-vanilla-custody-result");
        try {
            require("true".equals(System.getenv("GITHUB_ACTIONS")) && "github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")), "CI fixtures forbidden here");
            require(server.getWorldPath(LevelResource.ROOT).toRealPath().getFileName().toString().equals("jail_custody_smoke"), "not disposable custody world");
            require(Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath().equals(
                    net.neoforged.fml.ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()), "not assembled JAR");
            require(!System.getProperty("java.class.path", "").contains("unyielding-quests/build/classes"), "development classes leaked");
            Path directory = server.getWorldPath(LevelResource.ROOT).resolve("custody-tests");
            int phase = Integer.getInteger("uq.custodyPhase", 1);
            if (phase == 1) {
                Files.createDirectory(directory);
                for (var boundary : JailVanillaCustody.Boundary.values()) {
                    seed(admin);
                    Path path = directory.resolve(boundary.name()); JailEscrowJournal.provisionNew(path);
                    UUID id = UUID.randomUUID();
                    try (var journal = new JailEscrowJournal(path)) {
                        try {
                            JailVanillaCustody.removeForDisposableTest(journal, id, new PlayerPort(admin), at -> {
                                if (at == boundary) throw new IOException("injected " + at);
                            });
                            throw new AssertionError("checkpoint did not interrupt");
                        } catch (IOException expected) { require(expected.getMessage().equals("injected " + boundary), "unexpected failure " + expected); }
                    }
                    try (var journal = new JailEscrowJournal(path)) {
                        var inspection = JailVanillaCustody.inspect(journal, id, new PlayerPort(admin));
                        var expected = switch (boundary) {
                            case BEFORE_PREPARE -> JailVanillaCustody.Inspection.NO_RECORD;
                            case PREPARED -> JailVanillaCustody.Inspection.PREPARED_UNCHANGED;
                            case REMOVAL_STARTED -> JailVanillaCustody.Inspection.REMOVAL_NOT_PERSISTED_REVIEW;
                            case MUTATED -> JailVanillaCustody.Inspection.REVIEW_REQUIRED;
                            case PLAYER_DURABLE -> JailVanillaCustody.Inspection.REMOVAL_PERSISTED_REVIEW;
                            case HELD -> JailVanillaCustody.Inspection.HELD_VERIFIED;
                        };
                        require(inspection == expected, boundary + " wrong reconciliation: " + inspection);
                        if (boundary != JailVanillaCustody.Boundary.BEFORE_PREPARE) {
                            byte[] unchanged = new PlayerPort(admin).current().inventory();
                            try { JailVanillaCustody.removeForDisposableTest(journal, id, new PlayerPort(admin), at -> {}); throw new AssertionError("duplicate accepted"); }
                            catch (IOException expectedDuplicate) { require(expectedDuplicate.getMessage().contains("Existing custody"), "unexpected duplicate refusal"); }
                            require(Arrays.equals(unchanged, new PlayerPort(admin).current().inventory()), "duplicate changed player");
                        }
                    }
                }
                seed(admin); seed(visitor);
                Path held = directory.resolve("held"), interrupted = directory.resolve("interrupted");
                JailEscrowJournal.provisionNew(held); JailEscrowJournal.provisionNew(interrupted);
                try (var journal = new JailEscrowJournal(held)) {
                    JailVanillaCustody.removeForDisposableTest(journal, HELD, new PlayerPort(admin), at -> {});
                    // Mismatched live state and wrong transaction marker must not be accepted.
                    admin.getInventory().setItem(0, new ItemStack(Items.DIRT));
                    require(JailVanillaCustody.inspect(journal, HELD, new PlayerPort(admin)) == JailVanillaCustody.Inspection.REVIEW_REQUIRED, "mismatch accepted");
                    admin.getInventory().setItem(0, ItemStack.EMPTY);
                    admin.getPersistentData().putUUID(MARKER, UUID.randomUUID());
                    new PlayerPort(admin).persistAndVerify();
                    require(JailVanillaCustody.inspect(journal, HELD, new PlayerPort(admin)) == JailVanillaCustody.Inspection.REVIEW_REQUIRED, "wrong marker accepted");
                    admin.getPersistentData().putUUID(MARKER, HELD);
                    new PlayerPort(admin).persistAndVerify();
                }
                try (var journal = new JailEscrowJournal(interrupted)) {
                    try { JailVanillaCustody.removeForDisposableTest(journal, INTERRUPTED, new PlayerPort(visitor), at -> {
                        if (at == JailVanillaCustody.Boundary.REMOVAL_STARTED) throw new IOException("restart before mutation");
                    }); throw new AssertionError("missing interruption"); }
                    catch (IOException expected) { require(expected.getMessage().equals("restart before mutation"), "unexpected interruption failure"); }
                }
                server.saveEverything(true, true, true);
            } else if (phase == 2) {
                try (var journal = new JailEscrowJournal(directory.resolve("held"))) {
                    require(JailVanillaCustody.inspect(journal, HELD, new PlayerPort(admin)) == JailVanillaCustody.Inspection.HELD_VERIFIED, "held did not survive actual server restart");
                    require(journal.read(HELD).snapshot().length > 100, "full item snapshot lost");
                }
                try (var journal = new JailEscrowJournal(directory.resolve("interrupted"))) {
                    require(JailVanillaCustody.inspect(journal, INTERRUPTED, new PlayerPort(visitor)) == JailVanillaCustody.Inspection.REMOVAL_NOT_PERSISTED_REVIEW, "interrupted original changed on restart");
                    require(Arrays.equals(journal.read(INTERRUPTED).snapshot(), new PlayerPort(visitor).current().inventory()), "components/source slots changed on restart");
                }
            } else throw new IOException("bad phase");
            Files.writeString(result, "passed: assembled vanilla custody phase " + phase + "; forced player files, component/slot preservation, interruption boundaries, duplicate refusal, read-only reconciliation\n");
        } catch (Throwable failure) {
            UnyieldingQuests.LOGGER.error("Vanilla custody smoke failed", failure);
            try { Files.writeString(result, "failed: " + failure + "\n"); } catch (Exception ignored) {}
        }
    }

    private static void seed(ServerPlayer player) throws IOException {
        player.getInventory().clearContent(); player.getPersistentData().remove(MARKER);
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Escrow component fixture")); sword.setDamageValue(17);
        CompoundTag custom = new CompoundTag(); custom.putString("unique", UUID.randomUUID().toString());
        sword.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        player.getInventory().setItem(2, sword);
        player.getInventory().setItem(36, new ItemStack(Items.DIAMOND_BOOTS));
        player.getInventory().setItem(40, new ItemStack(Items.SHIELD));
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(java.util.List.of(new ItemStack(Items.DIAMOND, 23))));
        player.getInventory().setItem(8, box);
        new PlayerPort(player).persistAndVerify();
    }

    /** Same-thread exclusive test path; this does not claim to capture Accessories or arbitrary mod stores. */
    private record PlayerPort(ServerPlayer player) implements JailVanillaCustody.InventoryPort {
        private void guard() throws IOException {
            if (!player.server.isSameThread() || player.containerMenu != player.inventoryMenu
                    || !player.containerMenu.getCarried().isEmpty()) throw new IOException("transient inventory");
            for (int i = 0; i < 5; i++) if (player.inventoryMenu.getSlot(i).hasItem()) throw new IOException("crafting items present");
        }
        @Override public UUID owner() { return player.getUUID(); }
        @Override public JailVanillaCustody.View current() throws IOException {
            guard(); return view(player.getInventory().save(new ListTag()), player.getPersistentData());
        }
        private JailVanillaCustody.View view(ListTag items, CompoundTag persistent) throws IOException {
            CompoundTag root = new CompoundTag(); root.putInt("version", 1);
            root.putInt("vanillaSlots", player.getInventory().getContainerSize());
            // Vanilla Inventory's Slot tags are the canonical original source slots,
            // including armor 100..103 and offhand 150; nested stacks stay opaque.
            root.put("items", items);
            var output = new ByteArrayOutputStream(); NbtIo.write(root, new DataOutputStream(output));
            return new JailVanillaCustody.View(output.toByteArray(), items.isEmpty(), persistent.hasUUID(MARKER) ? persistent.getUUID(MARKER) : null);
        }
        private Path file() { return player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(owner() + ".dat"); }
        @Override public JailVanillaCustody.View durable() throws IOException {
            guard(); CompoundTag saved;
            try (var input = Files.newInputStream(file())) { saved = NbtIo.readCompressed(input, NbtAccounter.create(16L * 1024 * 1024)); }
            if (!saved.hasUUID("UUID") || !owner().equals(saved.getUUID("UUID")) || !saved.contains("Inventory", 9)
                    || !(saved.get("Inventory") instanceof ListTag inventory)
                    || (!inventory.isEmpty() && inventory.getElementType() != 10)) throw new IOException("malformed or wrong player save");
            return view(saved.getList("Inventory", 10), saved.getCompound("NeoForgeData"));
        }
        @Override public void persistAndVerify() throws IOException {
            guard(); var before = current();
            player.server.getPlayerList().saveAll();
            // PlayerList.save catches write failures internally: force the actual
            // replaced file and directory, then read back, rather than trusting it.
            try (var channel = FileChannel.open(file(), StandardOpenOption.WRITE)) { channel.force(true); }
            try (var directory = FileChannel.open(file().getParent(), StandardOpenOption.READ)) { directory.force(true); }
            var disk = durable();
            if (!Arrays.equals(before.inventory(), disk.inventory()) || before.empty() != disk.empty()
                    || !java.util.Objects.equals(before.removedBy(), disk.removedBy())) throw new IOException("player save readback mismatch");
        }
        @Override public void clearAndMark(UUID transaction) throws IOException {
            guard(); player.getInventory().clearContent(); player.getPersistentData().putUUID(MARKER, transaction);
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
