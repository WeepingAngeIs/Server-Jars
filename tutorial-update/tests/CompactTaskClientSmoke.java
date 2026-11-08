package dev.unyielding.bundlesmoke;

import com.mojang.blaze3d.platform.InputConstants;
import dev.unyielding.quests.client.ClientData;
import dev.unyielding.quests.client.ClientInit;
import dev.unyielding.quests.client.InvButtons;
import dev.unyielding.quests.client.QuestScreen;
import dev.unyielding.quests.client.tutorial.TutorialScreen;
import dev.unyielding.quests.quest.QuestFile;
import dev.unyielding.quests.quest.TeamProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.glfw.GLFW;

import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Measures the real quest renderer; excluded from the distributable JAR. */
@EventBusSubscriber(modid = BundleSmoke.MOD_ID, value = Dist.CLIENT)
public final class CompactTaskClientSmoke {
    private static boolean checked;
    private static final long TRAINING = 0x5A9D2C7E4F100001L;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (checked || mc.player == null || ClientData.file == null || mc.getOverlay() != null
                || !Files.exists(Path.of("/tmp/uq-admin-test-done"))
                || !Files.exists(Path.of("/tmp/uq-currency-client-passed.json"))) return;
        checked = true;
        TeamProgress originalProgress = ClientData.progress;
        try {
            Map<String, Object> report = BundleSmoke.verifyProductionJar();
            checkQuestKeyAndWelcome(report);
            ClientData.progress = new TeamProgress();
            var chapter = ClientData.file.chapterMap.get(TRAINING);
            require(chapter != null, "Catching and Team Training chapter is missing");
            var quests = chapter.quests.stream().filter(q -> q.tasks.size() > 1
                    && q.tasks.stream().allMatch(t -> t.type == QuestFile.TaskType.ITEM
                    && t.consume == q.tasks.getFirst().consume)).toList();
            require(quests.stream().anyMatch(q -> q.id == 0x05AAEB7B168BFD49L), "Pokeball collection fixture is missing");
            require(quests.stream().anyMatch(q -> q.id == 0x49A4AA72407D550FL), "EXP candy collection fixture is missing");
            require(quests.stream().anyMatch(q -> q.id == 0x6BEA397DFCDA7261L), "IV candy collection fixture is missing");
            var height = QuestScreen.class.getDeclaredField("detailContentHeight");
            height.setAccessible(true);
            var animation = QuestScreen.class.getDeclaredField("detailAnim");
            animation.setAccessible(true);
            List<Map<String, Object>> measurements = new ArrayList<>();
            for (int width : new int[]{640, 320}) {
                QuestScreen screen = new QuestScreen();
                screen.init(mc, width, 400);
                for (QuestFile.Quest quest : quests) {
                    screen.debugSelect(quest.id);
                    animation.setFloat(screen, 1f);
                    RecordingGraphics compact = draw(mc, screen);
                    int compactHeight = height.getInt(screen);
                    String prefix = quest.tasks.getFirst().consume ? "Press Submit" : "Keep it in your inventory";
                    require(compact.count(prefix) == 1, "First task must retain the only shared completion explanation");
                    require(compact.count("How to get:") <= 1, "Later task cards repeat acquisition descriptions");
                    long savedChapter = quest.chapter;
                    try {
                        // Compare the same real quest outside the compact chapter: its old full layout is preserved.
                        quest.chapter = 0;
                        RecordingGraphics expanded = draw(mc, screen);
                        int expandedHeight = height.getInt(screen);
                        require(expanded.count(prefix) == quest.tasks.size(), "Another chapter lost its full task help");
                        require(compactHeight < expandedHeight, "Repeated descriptions were hidden without shortening the panel");
                        measurements.add(Map.of("quest", QuestFile.idToString(quest.id), "width", width,
                                "tasks", quest.tasks.size(), "compact_height", compactHeight, "full_height", expandedHeight));
                    } finally {
                        quest.chapter = savedChapter;
                    }
                }
            }
            report.put("first_task_help_retained_later_item_cards_compact", "passed");
            report.put("other_chapters_unchanged", "passed");
            report.put("normal_and_320px_panel_measurements", measurements);
            BundleSmoke.write("/tmp/uq-compact-tasks-passed.json", report);
        } catch (Throwable failure) {
            BundleSmoke.fail("/tmp/uq-compact-tasks-failed.txt", failure);
        } finally {
            ClientData.progress = originalProgress;
        }
    }

    private static void checkQuestKeyAndWelcome(Map<String, Object> report) throws ReflectiveOperationException {
        require(ClientInit.OPEN_QUESTS.getDefaultKey().getValue() == GLFW.GLFW_KEY_F12,
                "Fresh quest key default must be F12");
        var originalKey = ClientInit.OPEN_QUESTS.getKey();
        try {
            ClientInit.OPEN_QUESTS.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F9));
            require(ClientInit.questKeyLabel().getString().equals("F9"), "Quest key label ignores the player's binding");
            require(InvButtons.questButtonLabel().getString().contains("F9"), "Inventory button ignores the player's binding");
            TutorialScreen tutorial = new TutorialScreen(new CompoundTag());
            var pageField = TutorialScreen.class.getDeclaredField("pages");
            pageField.setAccessible(true);
            String welcome = ((List<?>)pageField.get(tutorial)).getFirst().toString();
            require(welcome.toLowerCase(java.util.Locale.ROOT).contains("inventory")
                            && welcome.toLowerCase(java.util.Locale.ROOT).contains("ball"),
                    "Welcome page does not explain opening quests with the inventory Pokeball");
            require(!welcome.contains("F9  ·") && !welcome.contains("J  ·"),
                    "Welcome page still tells players to use a quest hotkey");
            ClientInit.OPEN_QUESTS.setKey(InputConstants.UNKNOWN);
            require(ClientInit.questKeyLabel().getString().equals("Set in Key Binds"),
                    "Unbound quest key lacks a useful instruction");
            require(InvButtons.questButtonLabel().getString().contains("Set in Key Binds"),
                    "Unbound inventory button displays an unusable key name");
            report.put("f12_default_remapped_and_unbound_key_labels", "passed");
            report.put("welcome_uses_inventory_pokeball_instructions", "passed");
        } finally {
            ClientInit.OPEN_QUESTS.setKey(originalKey);
        }
    }

    private static RecordingGraphics draw(Minecraft mc, QuestScreen screen) {
        RecordingGraphics graphics = new RecordingGraphics(mc);
        screen.render(graphics, -1000, -1000, 0f);
        graphics.flush();
        return graphics;
    }

    private static final class RecordingGraphics extends GuiGraphics {
        private final List<String> lines = new ArrayList<>();

        RecordingGraphics(Minecraft mc) {
            super(mc, mc.renderBuffers().bufferSource());
        }

        @Override
        public int drawString(Font font, FormattedCharSequence sequence, int x, int y, int color, boolean shadow) {
            StringBuilder line = new StringBuilder();
            sequence.accept((index, style, codePoint) -> {
                line.appendCodePoint(codePoint);
                return true;
            });
            lines.add(line.toString());
            return super.drawString(font, sequence, x, y, color, shadow);
        }

        long count(String prefix) {
            return lines.stream().filter(line -> line.startsWith(prefix)).count();
        }
    }
}
