package dev.unyielding.quests.devtest;

import dev.unyielding.quests.client.ClientData;
import dev.unyielding.quests.client.QuestScreen;
import dev.unyielding.quests.client.QuestToast;
import dev.unyielding.quests.client.TeamScreen;
import dev.unyielding.quests.quest.QuestFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Dev-only scripted screenshots (not shipped in the jar).
 * Write commands (one per line) to /tmp/uq-cmd.txt: quests | chapter N | select QUESTID | close | team | inv | toast QUESTID | wait N | shot NAME
 */
public final class AutoTest {
    private static final Deque<String> queue = new ArrayDeque<>();
    private static int wait;
    public static double hoverX = -1, hoverY = -1;

    private AutoTest() {
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(new File("/tmp/uqshots"), name + ".png", mc.getMainRenderTarget(), c -> {
        });
    }

    public static void tick() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientData.file == null) return;
        Path cmd = Path.of("/tmp/uq-cmd.txt");
        if (Files.exists(cmd)) {
            for (String l : Files.readAllLines(cmd)) if (!l.isBlank()) queue.add(l.trim());
            Files.delete(cmd);
        }
        if (wait > 0) {
            wait--;
            return;
        }
        String c = queue.poll();
        if (c == null) return;
        String[] a = c.split(" ", 2);
        QuestFile f = ClientData.file;
        switch (a[0]) {
            case "quests" -> mc.setScreen(new QuestScreen());
            case "chapter" -> {
                if (mc.screen instanceof QuestScreen s) s.debugChapter(Integer.parseInt(a[1]));
            }
            case "select" -> {
                if (mc.screen instanceof QuestScreen s) s.debugSelect(QuestFile.parseId(a[1]));
            }
            case "focus" -> {
                if (mc.screen instanceof QuestScreen s) s.debugFocus(QuestFile.parseId(a[1]));
            }
            case "view" -> {
                String[] v = a[1].trim().split("\\s+");
                if (mc.screen instanceof QuestScreen s) s.debugView(Double.parseDouble(v[0]), Double.parseDouble(v[1]), Float.parseFloat(v[2]));
            }
            case "close" -> mc.setScreen(null);
            case "team" -> mc.setScreen(new TeamScreen());
            case "inv" -> mc.setScreen(new InventoryScreen(mc.player));
            case "creative" -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), false));
            case "complete" -> {
                var srv = mc.getSingleplayerServer();
                if (srv != null) {
                    var sp = srv.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp != null) dev.unyielding.quests.quest.QuestManager.adminComplete(sp, QuestFile.parseId(a[1]));
                }
            }
            case "spin" -> dev.unyielding.quests.net.Net.sendToServer(new dev.unyielding.quests.net.Net.Claim(dev.unyielding.quests.spin.LuckySpin.spinId(QuestFile.parseId(a[1])), 0));
            case "claimquest" -> dev.unyielding.quests.net.Net.sendToServer(new dev.unyielding.quests.net.Net.Claim(QuestFile.parseId(a[1]), 1));
            case "claimall" -> dev.unyielding.quests.net.Net.sendToServer(new dev.unyielding.quests.net.Net.Claim(0, 2));
            case "skipall" -> {
                if (mc.screen instanceof QuestScreen s) s.debugSkipAll();
            }
            case "key" -> {
                if (mc.screen != null) mc.screen.keyPressed(Integer.parseInt(a[1]), 0, 0);
            }
            case "edit" -> {
                var srv = mc.getSingleplayerServer();
                if (srv != null && mc.player != null) srv.getPlayerList().op(mc.player.getGameProfile());
                QuestScreen.editMode = !QuestScreen.editMode;
            }
            case "click" -> {
                String[] v = a[1].trim().split("\\s+");
                double x = Double.parseDouble(v[0]), y = Double.parseDouble(v[1]);
                int b = v.length > 2 ? Integer.parseInt(v[2]) : 0;
                if (mc.screen != null) {
                    mc.screen.mouseMoved(x, y);
                    mc.screen.mouseClicked(x, y, b);
                    mc.screen.mouseReleased(x, y, b);
                }
            }
            case "press" -> {
                String[] v = a[1].trim().split("\\s+");
                if (mc.screen != null) mc.screen.mouseClicked(Double.parseDouble(v[0]), Double.parseDouble(v[1]), 0);
            }
            case "dragto" -> {
                String[] v = a[1].trim().split("\\s+");
                if (mc.screen != null) mc.screen.mouseDragged(Double.parseDouble(v[0]), Double.parseDouble(v[1]), 0, Double.parseDouble(v[2]), Double.parseDouble(v[3]));
            }
            case "release" -> {
                String[] v = a[1].trim().split("\\s+");
                if (mc.screen != null) mc.screen.mouseReleased(Double.parseDouble(v[0]), Double.parseDouble(v[1]), 0);
            }
            case "type" -> {
                if (mc.screen != null) for (char ch : a[1].toCharArray()) mc.screen.charTyped(ch, 0);
            }
            case "hover" -> {
                String[] v = a[1].trim().split("\\s+");
                hoverX = Double.parseDouble(v[0]);
                hoverY = Double.parseDouble(v[1]);
                double sc = mc.getWindow().getWidth() / (double) mc.getWindow().getGuiScaledWidth();
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), hoverX * sc, hoverY * sc);
                if (mc.screen != null) mc.screen.mouseMoved(hoverX, hoverY);
            }
            case "scroll" -> {
                if (mc.screen != null) mc.screen.mouseScrolled(mc.getWindow().getGuiScaledWidth() / 2.0, mc.getWindow().getGuiScaledHeight() / 2.0, 0, Double.parseDouble(a[1]));
            }
            case "gamemode" -> {
                var srv = mc.getSingleplayerServer();
                if (srv != null) {
                    var sp = srv.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp != null) sp.setGameMode(a[1].equals("creative") ? net.minecraft.world.level.GameType.CREATIVE : net.minecraft.world.level.GameType.SURVIVAL);
                }
            }
            case "toast" -> mc.getToasts().addToast(new QuestToast(f.quests.get(QuestFile.parseId(a[1]))));
            case "wait" -> wait = Integer.parseInt(a[1]);
            case "shot" -> {
                new File("/tmp/uqshots").mkdirs();
                shot(a[1]);
                Files.writeString(Path.of("/tmp/uq-shot-done"), a[1]);
            }
            default -> {
            }
        }
        wait = Math.max(wait, 2);
        // vein mine test hooks
        if (Files.exists(Path.of("/tmp/uq-ult.txt"))) {
            String[] u = Files.readString(Path.of("/tmp/uq-ult.txt")).trim().split(" ");
            Files.delete(Path.of("/tmp/uq-ult.txt"));
            if (u[0].equals("hold")) dev.unyielding.quests.net.Net.sendToServer(new dev.unyielding.quests.net.Net.UltimineState(true, Integer.parseInt(u[1])));
            else if (u[0].equals("release")) dev.unyielding.quests.net.Net.sendToServer(new dev.unyielding.quests.net.Net.UltimineState(false, 0));
        }
    }
}
