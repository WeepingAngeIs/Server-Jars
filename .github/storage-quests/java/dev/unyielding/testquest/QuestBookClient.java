package dev.unyielding.testquest;

import dev.unyielding.quests.client.ClientData;
import dev.unyielding.quests.client.QuestScreen;
import dev.unyielding.quests.quest.QuestFile;
import dev.unyielding.quests.quest.QuestManager;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.lwjgl.glfw.GLFW;

@Mod(value="uq_bundle_smoke", dist=Dist.CLIENT)
public final class QuestBookClient {
    private enum Step { WORLD, PREPARED, INVENTORY, SIDEBAR, ROW, GRAPH, DETAIL, PLAY, DONE }
    private Step step = Step.WORLD;
    private int pause;
    private boolean opening;
    private String capture;
    private volatile Throwable serverFailure;
    private CompletableFuture<Void> serverWork;
    private double targetX, targetY;
    private long since = System.nanoTime();

    public QuestBookClient() {
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, this::started);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::frame);
    }

    private void started(ServerStartedEvent event) {
        try { QuestChecks.started(event.getServer()); }
        catch (Throwable error) { serverFailure = error; }
    }

    private void advance(Step to) {
        System.out.println("STORAGE QUEST " + QuestChecks.PHASE + " " + step + " -> " + to);
        step = to;
        pause = 5;
        since = System.nanoTime();
    }

    private void tick(ClientTickEvent.Post event) {
        if (step == Step.DONE || capture != null) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (serverFailure != null) throw new IllegalStateException("world load", serverFailure);
            if (pause-- > 0) return;
            QuestChecks.check(System.nanoTime()-since < 180_000_000_000L, "Timed out in " + step + " on " + mc.screen);
            switch (step) {
                case WORLD -> {
                    if (mc.player == null) {
                        if (mc.screen instanceof TitleScreen title && !opening &&
                                List.of("before","without").contains(QuestChecks.PHASE)) {
                            opening = true;
                            String name = QuestChecks.PHASE.equals("before") ? "StorageQuestCheck" : "QuestWithoutStorage";
                            mc.createWorldOpenFlows().createFreshLevel(name, new LevelSettings(name, GameType.SURVIVAL,
                                    false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                                    new WorldOptions(22817L,false,false), WorldPresets::createNormalWorldDimensions, title);
                        } else if (mc.screen != null) {
                            for (var child : List.copyOf(mc.screen.children())) {
                                if (child instanceof Button b && List.of("Proceed","I know what I'm doing!").contains(b.getMessage().getString())) {
                                    b.onPress(); break;
                                }
                            }
                        }
                    } else if (mc.player.tickCount > 50 && ClientData.file != null) {
                        serverWork = onServer(mc, () -> {
                            var server = mc.getSingleplayerServer();
                            var p = server.getPlayerList().getPlayers().getFirst();
                            switch (QuestChecks.PHASE) {
                                case "before" -> QuestChecks.before(p);
                                case "after" -> QuestChecks.check(QuestManager.file.chapterMap.containsKey(QuestChecks.CHAPTER), "new synced content");
                                case "restart" -> QuestChecks.check(QuestManager.file.chapterMap.get(QuestChecks.CHAPTER).quests.stream()
                                        .allMatch(q -> QuestManager.progressOf(p).questDone(q.id)), "all chapter completions survived restart");
                                case "without" -> QuestChecks.check(!QuestManager.file.chapterMap.containsKey(QuestChecks.CHAPTER)
                                        && server.getAdvancements().get(net.minecraft.resources.ResourceLocation.parse(
                                                "unyieldingquests:anya_storage_plus/iron")) == null, "optional mod absent");
                                default -> throw new IllegalArgumentException(QuestChecks.PHASE);
                            }
                        });
                        advance(Step.PREPARED);
                    }
                }
                case PREPARED -> {
                    if (!serverWork.isDone()) return;
                    serverWork.join();
                    if (!QuestChecks.PHASE.equals("after")) { pass(mc); return; }
                    mc.setScreen(new InventoryScreen(mc.player));
                    advance(Step.INVENTORY);
                }
                case INVENTORY -> {
                    var s = (InventoryScreen)mc.screen;
                    var b = s.children().stream().filter(c -> c instanceof Button button &&
                            button.getMessage().getString().startsWith("Quest Book")).map(c -> (Button)c).findFirst().orElseThrow();
                    click(s,b.getX()+b.getWidth()/2.0,b.getY()+b.getHeight()/2.0);
                    advance(Step.SIDEBAR);
                }
                case SIDEBAR -> {
                    var s = (QuestScreen)mc.screen;
                    point(mc,24,70);
                    s.mouseScrolled(24,70,0,-200);
                    advance(Step.ROW);
                }
                case ROW -> {
                    var s = (QuestScreen)mc.screen;
                    for (Object hit : (List<?>)field(s,"chapterHits")) {
                        var chapter = (QuestFile.Chapter)field(hit,"chapter");
                        int y0=(int)field(hit,"y0"), y1=(int)field(hit,"y1");
                        if (chapter.id == QuestChecks.CHAPTER && y1>y0+3) {
                            click(s,24,(y0+y1)/2.0);
                            advance(Step.GRAPH);
                            return;
                        }
                    }
                }
                case GRAPH -> {
                    var s = (QuestScreen)mc.screen;
                    QuestChecks.check(((QuestFile.Chapter)field(s,"chapter")).id == QuestChecks.CHAPTER, "sidebar selected Anya Storage Plus");
                    var ch = ClientData.file.chapterMap.get(QuestChecks.CHAPTER);
                    var quest = ch.quests.stream().filter(q -> q.title.equals("Your Storage in Hand")).findFirst().orElseThrow();
                    targetX=coordinate(s,"sx",quest.x); targetY=coordinate(s,"sy",quest.y);
                    QuestChecks.check(targetX>0 && targetX<s.width && targetY>0 && targetY<s.height, "tablet node in view");
                    point(mc,targetX,targetY);
                    capture="chapter";
                    advance(Step.DETAIL);
                }
                case DETAIL -> {
                    var s = (QuestScreen)mc.screen;
                    click(s,targetX,targetY);
                    var selected = (QuestFile.Quest)field(s,"selected");
                    QuestChecks.check(selected != null && selected.title.equals("Your Storage in Hand")
                            && selected.desc.getFirst().contains("Sneak"), "opened real tablet objective");
                    capture="tablet-detail";
                    advance(Step.PLAY);
                }
                case PLAY -> {
                    if (mc.screen != null) {
                        mc.setScreen(null);
                        serverWork=onServer(mc, () -> {
                            var server=mc.getSingleplayerServer(); var player=server.getPlayerList().getPlayers().getFirst();
                            QuestChecks.play(player);
                            QuestChecks.otherBookMode();
                            QuestManager.sendProgress(player);
                            QuestChecks.record("after-progress", QuestChecks.data(server).save(new net.minecraft.nbt.CompoundTag(),server.registryAccess()));
                        });
                    } else if (serverWork.isDone()) {
                        serverWork.join();
                        pass(mc);
                    }
                }
            }
        } catch (Throwable failure) { fail(mc, failure); }
    }

    private interface Check { void run() throws Exception; }

    private static CompletableFuture<Void> onServer(Minecraft mc, Check check) {
        return mc.getSingleplayerServer().submit(() -> {
            try { check.run(); } catch (Exception e) { throw new IllegalStateException(e); }
        });
    }

    private static Object field(Object value, String name) throws Exception {
        var f=value.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(value);
    }

    private static float coordinate(QuestScreen screen,String method,double coordinate) throws Exception {
        Method fn=QuestScreen.class.getDeclaredMethod(method,double.class); fn.setAccessible(true);
        return (float)fn.invoke(screen,coordinate);
    }

    private static void point(Minecraft mc, double x, double y) {
        var window=mc.getWindow();
        GLFW.glfwSetCursorPos(window.getWindow(), x*window.getScreenWidth()/window.getGuiScaledWidth(),
                y*window.getScreenHeight()/window.getGuiScaledHeight());
    }

    private static void click(Screen s,double x,double y) {
        QuestChecks.check(s.mouseClicked(x,y,0), "click at "+x+","+y);
        s.mouseReleased(x,y,0);
    }

    private void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        try (var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(QuestChecks.OUT.resolve(capture+".png"));
            capture=null;
        } catch (Throwable error) { fail(Minecraft.getInstance(),error); }
    }

    private void pass(Minecraft mc) throws Exception {
        Files.writeString(QuestChecks.OUT.resolve(QuestChecks.PHASE+".pass"), "PASS\n");
        advance(Step.DONE);
        mc.stop();
    }

    private void fail(Minecraft mc, Throwable failure) {
        step=Step.DONE; capture=null; failure.printStackTrace();
        try { Files.writeString(QuestChecks.OUT.resolve(QuestChecks.PHASE+".failure"),failure.toString()); }
        catch (Exception ignored) { }
        mc.stop();
    }
}
