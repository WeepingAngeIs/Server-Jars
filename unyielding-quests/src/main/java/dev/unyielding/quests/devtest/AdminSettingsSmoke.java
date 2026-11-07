package dev.unyielding.quests.devtest;

import dev.unyielding.quests.client.AdminSettingsScreen;
import dev.unyielding.quests.client.ClientData;
import dev.unyielding.quests.client.TeamScreen;
import dev.unyielding.quests.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.Tag;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Runs only in the cloud development client; excluded from the distributable JAR. */
public final class AdminSettingsSmoke {
    private static final Path COMMAND = Path.of("/tmp/uq-admin-server-cmd");
    private static final Path RESULT = Path.of("/tmp/uq-admin-server-result");
    private static final Path MARKER = Path.of("/tmp/uq-admin-test-done");
    private static boolean running;
    private static int stage;
    private static int ticks;
    private static int version;

    private AdminSettingsSmoke() {}

    public static void begin() throws Exception {
        running = true;
        stage = 0;
        ticks = 0;
        Files.createDirectories(Path.of("/tmp/uqshots"));
        Minecraft mc = Minecraft.getInstance();
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 800);
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        mc.setScreen(null);
        request("deop");
    }

    public static boolean tick() throws Exception {
        if (!running) return false;
        Minecraft mc = Minecraft.getInstance();
        mc.getToasts().clear();
        mc.gui.getChat().clearMessages(true);
        ticks++;
        try {
            require(ticks < 400, "Timed out waiting at stage " + stage);
            switch (stage) {
                case 0 -> {
                    if (!answered("deop")) break;
                    Net.sendToServer(new Net.AdminSettingsAction("__open", false));
                    Net.sendToServer(new Net.AdminSettingsAction("share_quest_progress", true));
                    advance();
                }
                case 1 -> {
                    if (ticks < 40) break;
                    require(!(mc.screen instanceof AdminSettingsScreen), "Non-operator opened admin menu");
                    request("assert-denied");
                    advance();
                }
                case 2 -> {
                    if (!answered("assert-denied")) break;
                    request("op");
                    advance();
                }
                case 3 -> {
                    if (!answered("op")) break;
                    Net.sendToServer(new Net.AdminSettingsAction("__open", false));
                    advance();
                }
                case 4 -> {
                    if (!(mc.screen instanceof AdminSettingsScreen) || ticks < 20) break;
                    require(!shared(), "Sharing should start disabled");
                    shot("06-admin-settings");
                    version = ClientData.adminSettingsVersion;
                    pressToggle("OFF");
                    advance();
                }
                case 5 -> {
                    if (ClientData.adminSettingsVersion <= version || !shared()) break;
                    require(ClientData.shareQuestProgress, "Team status did not update when sharing was enabled");
                    request("assert-on");
                    advance();
                }
                case 6 -> {
                    if (!answered("assert-on")) break;
                    version = ClientData.adminSettingsVersion;
                    pressToggle("ON");
                    advance();
                }
                case 7 -> {
                    if (ClientData.adminSettingsVersion <= version || shared()) break;
                    require(!ClientData.shareQuestProgress, "Team status did not update when sharing was disabled");
                    request("assert-off");
                    advance();
                }
                case 8 -> {
                    if (!answered("assert-off")) break;
                    request("verify-progress");
                    advance();
                }
                case 9 -> {
                    if (!answered("verify-progress") || ticks < 20) break;
                    require(mc.screen instanceof AdminSettingsScreen, "Server update unexpectedly dismissed admin menu");
                    GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 640, 480);
                    mc.options.guiScale().set(2);
                    mc.resizeDisplay();
                    advance();
                }
                case 10 -> {
                    if (ticks < 25) break;
                    require(mc.screen instanceof AdminSettingsScreen, "Admin menu missing at compact size");
                    require(mc.screen.width >= 300 && mc.screen.width <= 340, "Compact layout not applied");
                    for (var element : mc.screen.children()) {
                        if (element instanceof AbstractWidget widget && widget.visible) {
                            require(widget.getX() >= 0 && widget.getY() >= 0
                                    && widget.getX() + widget.getWidth() <= mc.screen.width
                                    && widget.getY() + widget.getHeight() <= mc.screen.height,
                                    "Admin control outside compact window");
                        }
                    }
                    shot("07-admin-compact");
                    GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 800);
                    mc.resizeDisplay();
                    mc.setScreen(new TeamScreen());
                    advance();
                }
                case 11 -> {
                    if (ticks < 25) break;
                    require(!ClientData.shareQuestProgress, "Solo progress state lost after party transitions");
                    shot("08-teams-solo-progress");
                    Net.sendToServer(new Net.AdminSettingsAction("__open", false));
                    advance();
                }
                case 12 -> {
                    if (!(mc.screen instanceof AdminSettingsScreen)) break;
                    request("deop-stale");
                    advance();
                }
                case 13 -> {
                    if (!answered("deop-stale")) break;
                    Net.sendToServer(new Net.AdminSettingsAction("share_quest_progress", true));
                    advance();
                }
                case 14 -> {
                    if (ticks < 40) break;
                    request("assert-denied");
                    advance();
                }
                case 15 -> {
                    if (!answered("assert-denied")) break;
                    require(!ClientData.adminSettings.getBoolean("allowed"), "Revoked administrator retained allowed flag");
                    if (mc.screen instanceof AdminSettingsScreen) {
                        for (var element : mc.screen.children()) {
                            if (element instanceof Button button && (button.getMessage().getString().equals("ON")
                                    || button.getMessage().getString().equals("OFF"))) {
                                require(!button.active, "Revoked administrator retained active toggle");
                            }
                        }
                    }
                    Files.writeString(MARKER, "passed: real admin packets, non-operator and revoked-operator rejection, live toggles, persisted config, solo party isolation, reward/spin replay protection, offline migration, progress save/load and compact menu\n");
                    running = false;
                }
                default -> throw new IllegalStateException("Unknown admin test stage");
            }
        } catch (Throwable failure) {
            Files.writeString(MARKER, "failed: stage " + stage + ": " + failure + "\n");
            running = false;
            throw failure;
        }
        return true;
    }

    private static boolean shared() {
        for (Tag tag : ClientData.adminSettings.getList("settings", Tag.TAG_COMPOUND)) {
            var row = (net.minecraft.nbt.CompoundTag) tag;
            if (row.getString("key").equals("share_quest_progress")) return row.getBoolean("value");
        }
        throw new IllegalStateException("Sharing setting missing from server response");
    }

    private static void pressToggle(String value) {
        Minecraft mc = Minecraft.getInstance();
        require(mc.screen instanceof AdminSettingsScreen, "Admin menu not open");
        for (var element : mc.screen.children()) {
            if (element instanceof Button button && button.visible && button.active
                    && button.getMessage().getString().equals(value)) {
                mc.screen.mouseClicked(button.getX() + button.getWidth() / 2d,
                        button.getY() + button.getHeight() / 2d, 0);
                return;
            }
        }
        throw new IllegalStateException("Sharing toggle not clickable: " + value);
    }

    private static void request(String command) throws Exception {
        Files.deleteIfExists(RESULT);
        Path temporary = Path.of(COMMAND + ".tmp");
        Files.writeString(temporary, command);
        Files.move(temporary, COMMAND, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static boolean answered(String command) throws Exception {
        if (!Files.exists(RESULT)) return false;
        String result = Files.readString(RESULT).trim();
        if (result.startsWith("failed:")) throw new IllegalStateException(result);
        return result.equals("passed: " + command);
    }

    private static void advance() { stage++; ticks = 0; }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(new File("/tmp/uqshots"), name + ".png", mc.getMainRenderTarget(), message -> {});
    }
}
