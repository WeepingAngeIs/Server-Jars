package dev.unyielding.testui;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Opens the actual server menu and operates its widgets. Reflection only observes snapshots and layout. */
@Mod(value = "configtopia_ui_check", dist = Dist.CLIENT)
public final class NativeWeaponMenu {
    private static final Path OUT = Path.of(System.getProperty("configtopia.ui.evidence"));
    private record Example(String group, String path, double original, String seconds) {}
    private static final List<Example> CASES = List.of(
            new Example("Cupid", "cupidBowCharmSeconds", 40, "91"),
            new Example("Returnbloom Dirk", "weapons.returnbloom_dirk.throwCooldownSeconds", 5, "2.5"));
    private enum Step { WORLD, ADMIN, VALUES, FILTER_MOD, ADD, CHOOSE, GROUP, FILTER_ROW, OPEN_ROW,
        SLIDER, TYPE, SAVE, ACKNOWLEDGEMENT, OWNER, FINISHED }
    private final JsonObject results = new JsonObject();
    private int caseIndex;
    private Step step = Step.WORLD;
    private boolean opening, failed;
    private String capture;
    private CompletableFuture<Double> ownerValue;
    private long since = System.nanoTime();
    private int pause;

    public NativeWeaponMenu() throws Exception {
        Path mods = Path.of(System.getProperty("configtopia.ui.mods"));
        for (var pair : List.of(List.of("uwudimension", "uwu.jar"), List.of("unyieldingquests", "quests.jar"))) {
            Path actual = ModList.get().getModFileById(pair.getFirst()).getFile().getFilePath().toRealPath();
            require(actual.equals(mods.resolve(pair.getLast()).toRealPath()), "Unexpected loaded mod " + actual);
        }
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::frame);
    }

    private void advance(Step next) {
        System.out.println("CONFIGTOPIA UI " + step + " -> " + next);
        step = next;
        pause = 3;
        since = System.nanoTime();
    }

    private void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (failed || step == Step.FINISHED || capture != null) return;
        if (pause > 0) { pause--; return; }
        try {
            require(System.nanoTime() - since < 180_000_000_000L, "Timed out at " + step + " screen=" + mc.screen);
            drive(mc);
        } catch (Throwable failure) {
            stop(mc, failure);
        }
    }

    private void drive(Minecraft mc) throws Exception {
        Screen screen = mc.screen;
        Example example = CASES.get(caseIndex);
        switch (step) {
            case WORLD -> {
                if (!opening && screen instanceof TitleScreen title) {
                    opening = true;
                    GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 720);
                    mc.createWorldOpenFlows().createFreshLevel("ConfigtopiaWeaponUI", new LevelSettings(
                            "Disposable weapon settings", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT),
                            new WorldOptions(735212L, false, false), WorldPresets::createNormalWorldDimensions, title);
                } else if (opening && mc.player == null && screen != null) {
                    for (var child : List.copyOf(screen.children())) {
                        if (child instanceof Button b && List.of("Proceed", "I know what I'm doing!").contains(b.getMessage().getString())) {
                            b.onPress();
                            break;
                        }
                    }
                } else if (mc.player != null && mc.player.tickCount > 70 && screen == null) {
                    mc.player.connection.sendCommand("config");
                    advance(Step.ADMIN);
                }
            }
            case ADMIN -> {
                if (is(screen, "AdminSettingsScreen") && click(screen, button(screen, "Config values"))) advance(Step.VALUES);
            }
            case VALUES -> {
                if (is(screen, "ConfigValuesScreen") && click(screen, button(screen, "Add Mod Settings"))) advance(Step.FILTER_MOD);
            }
            case FILTER_MOD -> {
                if (is(screen, "ModSettingsScreen") && type(screen, field(screen, "Search mods by name or ID"), "uwudimension")) advance(Step.ADD);
            }
            case ADD -> {
                AbstractWidget b = button(screen, "Add to my settings");
                if (b == null) b = button(screen, "Open settings");
                if (click(screen, b)) advance(Step.CHOOSE);
            }
            case CHOOSE -> {
                if (click(screen, button(screen, "Choose weapon"))) advance(Step.GROUP);
            }
            case GROUP -> {
                AbstractWidget group = button(screen, example.group);
                if (group == null || !reveal(screen, group)) return;
                if (caseIndex == 0 && !Files.exists(OUT.resolve("choose-weapon.png"))) {
                    capture = "choose-weapon";
                    return;
                }
                if (click(screen, group)) advance(Step.FILTER_ROW);
            }
            case FILTER_ROW -> {
                if (!ready(screen, example.group)) return;
                if (type(screen, field(screen, "Search settings"), example.path) && click(screen, button(screen, "Search")))
                    advance(Step.OPEN_ROW);
            }
            case OPEN_ROW -> {
                if (!ready(screen, example.path)) return;
                var row = row(screen, example.path);
                require(row.getBoolean("available") && row.getString("group").equals(example.group), "Wrong weapon row: " + row);
                require(Double.parseDouble(row.getString("current")) == example.original && row.getString("unit").equals("seconds"), "Wrong initial seconds");
                if (click(screen, button(screen, "Edit value"))) advance(caseIndex == 0 ? Step.TYPE : Step.SLIDER);
            }
            case SLIDER -> {
                EditBox box = field(screen, "Seconds");
                var slider = screen.children().stream().filter(AbstractSliderButton.class::isInstance)
                        .map(AbstractSliderButton.class::cast).findFirst().orElseThrow();
                if (!reveal(screen, slider)) return;
                require(box.getY() + box.getHeight() < slider.getY(), "Seconds slider overlaps typing field");
                click(screen, slider);
                require(Double.parseDouble(box.getValue()) != example.original, "Slider did not adjust typed seconds");
                advance(Step.TYPE);
            }
            case TYPE -> {
                if (!type(screen, field(screen, "Seconds"), example.seconds)) return;
                if (caseIndex == 1) {
                    var slider = screen.children().stream().filter(AbstractSliderButton.class::isInstance)
                            .map(AbstractSliderButton.class::cast).findFirst().orElseThrow();
                    require(slider.getMessage().getString().equals("2.5 seconds"), "Slider did not follow decimal field");
                }
                capture = caseIndex == 0 ? "cupid-seconds" : "dirk-slider-and-seconds";
                advance(Step.SAVE);
            }
            case SAVE -> {
                require(field(screen, "Seconds").getValue().equals(example.seconds), "Lost unsaved seconds");
                if (click(screen, button(screen, "Save"))) advance(Step.ACKNOWLEDGEMENT);
            }
            case ACKNOWLEDGEMENT -> {
                if (!ready(screen, example.path) || read(screen, "editing") != null) return;
                require(Double.parseDouble(row(screen, example.path).getString("current")) == Double.parseDouble(example.seconds),
                        "Save failed to return the updated server snapshot");
                ownerValue = mc.getSingleplayerServer().submit(() -> {
                    try {
                        var spec = (ModConfigSpec) Class.forName("dev.uwu.dimension.AcquisitionPolicy").getField("SPEC").get(null);
                        ModConfigSpec.ConfigValue<?> v = spec.getValues().get(List.of(example.path.split("\\.")));
                        return ((Number) v.get()).doubleValue();
                    } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                });
                advance(Step.OWNER);
            }
            case OWNER -> {
                if (!ownerValue.isDone()) return;
                require(ownerValue.join() == Double.parseDouble(example.seconds), "The provider did not receive the native edit");
                results.addProperty(example.path.substring(example.path.lastIndexOf('.') + 1), ownerValue.join());
                if (++caseIndex < CASES.size()) advance(Step.CHOOSE);
                else {
                    Files.writeString(OUT.resolve("results.json"), results.toString());
                    advance(Step.FINISHED);
                    mc.stop();
                }
            }
            case FINISHED -> { }
        }
    }

    private static CompoundTag row(Screen screen, String aliases) throws Exception {
        var data = (CompoundTag) read(screen, "data");
        for (Tag m : data.getList("mods", Tag.TAG_COMPOUND)) {
            var mod = (CompoundTag) m;
            if (!"uwudimension".equals(mod.getString("id"))) continue;
            for (Tag r : mod.getList("rows", Tag.TAG_COMPOUND)) {
                var row = (CompoundTag) r;
                if (row.getString("aliases").equals(aliases)) return row;
            }
        }
        throw new IllegalStateException("Missing row " + aliases);
    }

    private static boolean ready(Screen screen, String query) throws Exception {
        return is(screen, "ModSettingsScreen") && ((String) read(screen, "pending")).isEmpty()
                && query.equals(((CompoundTag) read(screen, "data")).getString("query"));
    }

    private static boolean is(Screen s, String simple) {
        return s != null && s.getClass().getSimpleName().equals(simple);
    }

    private static AbstractWidget button(Screen s, String label) {
        if (s == null) return null;
        return s.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(b -> !(b instanceof EditBox) && b.active && b.getMessage().getString().equals(label)).findFirst().orElse(null);
    }

    private static EditBox field(Screen s, String name) {
        return s.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(b -> b.getMessage().getString().equals(name)).findFirst().orElseThrow();
    }

    private static boolean reveal(Screen screen, AbstractWidget w) throws Exception {
        if (w == null) return false;
        int top = 0, bottom = screen.height;
        if (is(screen, "ModSettingsScreen") || is(screen, "ConfigValuesScreen")) {
            top = (Integer) read(screen, "top");
            bottom = (Integer) read(screen, "bottom");
        }
        if (w.getY() >= top && w.getY() + w.getHeight() <= bottom) return true;
        screen.mouseScrolled(screen.width * .5, (top + bottom) * .5, 0, w.getY() < top ? 3 : -3);
        return false;
    }

    private static boolean click(Screen screen, AbstractWidget w) throws Exception {
        if (w == null || !reveal(screen, w)) return false;
        double x = w.getX() + w.getWidth() * .5, y = w.getY() + w.getHeight() * .5;
        require(screen.mouseClicked(x, y, 0), "Native click rejected: " + w.getMessage().getString());
        screen.mouseReleased(x, y, 0);
        return true;
    }

    private static boolean type(Screen screen, EditBox box, String value) throws Exception {
        if (!click(screen, box)) return false;
        screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
        for (int i = box.getValue().length(); i > 0; i--) screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        for (char ch : value.toCharArray()) screen.charTyped(ch, 0);
        require(box.getValue().equals(value), "Native text input did not produce " + value);
        return true;
    }

    private static Object read(Object target, String key) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(key);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(key);
    }

    private void frame(RenderFrameEvent.Post event) {
        if (capture == null || failed) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            require(image.getWidth() >= 800 && image.getHeight() >= 450, "Screenshot resolution too small");
            image.writeToFile(OUT.resolve(capture + ".png"));
            capture = null;
        } catch (Throwable failure) {
            stop(mc, failure);
        }
    }

    private void stop(Minecraft mc, Throwable failure) {
        failed = true;
        System.err.println("CONFIGTOPIA NATIVE UI FAILED at " + step);
        failure.printStackTrace();
        mc.stop();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
