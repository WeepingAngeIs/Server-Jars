package dev.unyielding.quests.devtest;

import dev.unyielding.quests.client.tutorial.TutorialScreen;
import dev.unyielding.quests.client.tutorial.TutorialClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** Cloud-only exercise of the real join packet, menu arbitration and responsive guide. */
public final class TutorialSmoke {
    private static boolean running;
    private static int stage;
    private static int ticks;
    private static int lastPage;
    private static TutorialScreen guide;
    private static InventoryScreen otherMenu;
    private static int allCommands;
    private static final Path MARKER = Path.of("/tmp/uq-tutorial-test-done");

    private TutorialSmoke() {}

    public static void begin() {
        running = true;
        stage = 0;
        ticks = 0;
        guide = null;
    }

    public static boolean tick() throws Exception {
        if (!running) return false;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        try {
            switch (stage) {
                case 0 -> {
                    if (mc.screen instanceof TutorialScreen shown) {
                        if (guide != shown) {
                            guide = shown;
                            ticks = 0;
                        }
                        require(guide.pageIndex() == 0 && guide.pageCount() >= 4, "Initial guide pages missing");
                        if (ticks > 20) {
                            shot("01-welcome");
                            guide.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
                            advance();
                        }
                    } else require(ticks < 500, "Automatic tutorial did not open");
                }
                case 1 -> {
                    require(mc.screen == guide, "Guide lost focus during navigation");
                    if (ticks < 12) break;
                    if (guide.pageIndex() == 1) shot("02-commands");
                    if (guide.directoryPage()) {
                        allCommands = guide.displayedEntries();
                        require(allCommands > 15, "Server command directory incomplete");
                        guide.searchCommands("quests");
                        require(guide.displayedEntries() > 0 && guide.displayedEntries() < allCommands, "Command search failed");
                        advance();
                    } else {
                        guide.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
                        ticks = 0;
                    }
                }
                case 2 -> {
                    if (ticks < 12) break;
                    shot("03-command-search");
                    guide.searchCommands("");
                    require(guide.displayedEntries() == allCommands, "Clearing search lost commands");
                    guide.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
                    require(guide.pageIndex() == guide.pageCount() - 1, "Starter is not the last page");
                    advance();
                }
                case 3 -> {
                    if (ticks < 12) break;
                    shot("04-starter");
                    lastPage = guide.pageIndex();
                    otherMenu = new InventoryScreen(mc.player);
                    mc.setScreen(otherMenu);
                    advance();
                }
                case 4 -> {
                    require(mc.screen == otherMenu, "Tutorial replaced another open menu");
                    if (ticks < 120) break;
                    mc.setScreen(null);
                    advance();
                }
                case 5 -> {
                    if (ticks < 39) require(mc.screen == null, "Tutorial ignored clear-screen delay");
                    if (mc.screen instanceof TutorialScreen shown) {
                        guide = shown;
                        require(guide.pageIndex() == lastPage, "Interrupted tutorial lost its page");
                        guide.onClose();
                        advance();
                    } else require(ticks < 100, "Interrupted tutorial did not resume");
                }
                case 6 -> {
                    require(mc.screen == null, "Dismissed tutorial reopened");
                    if (ticks < 100) break;
                    mc.getConnection().sendCommand("tutorial");
                    advance();
                }
                case 7 -> {
                    if (mc.screen instanceof TutorialScreen shown) {
                        guide = shown;
                        require(guide.pageIndex() == 0, "/tutorial did not reopen at the beginning");
                        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 640, 480);
                        mc.options.guiScale().set(2);
                        mc.resizeDisplay();
                        advance();
                    } else require(ticks < 180, "/tutorial failed to open through the server");
                }
                case 8 -> {
                    if (ticks < 30) break;
                    require(guide.width >= 300 && guide.width <= 340, "Compact GUI size not applied");
                    for (var element : guide.children()) {
                        if (element instanceof AbstractWidget widget) {
                            require(widget.getX() >= 0 && widget.getY() >= 0
                                    && widget.getX() + widget.getWidth() <= guide.width
                                    && widget.getY() + widget.getHeight() <= guide.height,
                                    "Guide control clipped at compact size");
                        }
                    }
                    guide.mouseScrolled(guide.width / 2d, guide.height / 2d, 0, -3);
                    advance();
                }
                case 9 -> {
                    if (ticks < 15) break;
                    shot("05-compact-scroll");
                    guide.onClose();
                    advance();
                }
                case 10 -> {
                    if (ticks < 20) break;
                    mc.getConnection().sendCommand("tutorial");
                    advance();
                }
                case 11 -> {
                    if (mc.screen instanceof TutorialScreen) {
                        TutorialClient.reset();
                        mc.setScreen(null);
                        advance();
                    } else require(ticks < 180, "Tutorial could not reopen for disconnect cleanup test");
                }
                case 12 -> {
                    require(mc.screen == null, "Screen removal requeued tutorial after disconnect cleanup");
                    if (ticks < 120) break;
                    Files.writeString(MARKER,
                            "passed: real join, filtered command directory, search, final starter page, menu priority, resume, dismissal, /tutorial reopen, disconnect cleanup and 320px layout\n");
                    running = false;
                }
                default -> throw new IllegalStateException("Unknown tutorial test step");
            }
        } catch (Throwable failure) {
            Files.writeString(MARKER, "failed: stage " + stage + ": " + failure + "\n");
            running = false;
            throw failure;
        }
        return true;
    }

    private static void advance() {
        stage++;
        ticks = 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(new File("/tmp/uqshots"), name + ".png", mc.getMainRenderTarget(), message -> {});
    }
}
