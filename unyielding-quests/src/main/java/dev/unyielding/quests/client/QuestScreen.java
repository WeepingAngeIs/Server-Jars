package dev.unyielding.quests.client;

import dev.unyielding.quests.client.ui.Theme;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.client.ui.UiConfig;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.ItemFilter;
import dev.unyielding.quests.quest.QuestFile;
import dev.unyielding.quests.quest.QuestFile.Chapter;
import dev.unyielding.quests.quest.QuestFile.Quest;
import dev.unyielding.quests.quest.QuestFile.Reward;
import dev.unyielding.quests.quest.QuestFile.Task;
import dev.unyielding.quests.quest.QuestManager;
import dev.unyielding.quests.quest.TeamProgress;
import dev.unyielding.quests.spin.LuckySpin;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The quest book. */
public class QuestScreen extends Screen {
    private static final int TOP = 28;
    private static final float UNIT = 28f;
    private static final int ROW = 20;

    private static long lastChapter;
    private static final Map<Long, float[]> VIEWS = new HashMap<>();

    private record Hit(int x0, int y0, int x1, int y1, Runnable action) {
        boolean in(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private record SlotHit(int x, int y, ItemStack stack, List<Component> extra) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + 18 && my >= y && my < y + 18;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final List<SlotHit> slots = new ArrayList<>();
    private final Map<Long, Float> hoverAnim = new HashMap<>();

    private int side = 160;
    private Chapter chapter;
    private float viewX, viewY, zoom = 1f;
    private Quest selected, shown;
    private float detailAnim;
    private int detailScroll;
    private int sideScroll;
    private boolean dragging;
    private double dragMoved;
    private int sideContentHeight;
    private int detailContentHeight;
    private int detailViewHeight;
    private Quest hovered;
    private long lastFrame;
    private float dt;

    public QuestScreen() {
        super(Component.literal("Quests"));
    }

    @Override
    protected void init() {
        Theme.ensureLoaded();
        side = Mth.clamp(width / 4, 130, 190);
        QuestFile f = ClientData.file;
        if (f == null || f.chapters.isEmpty()) return;
        if (chapter == null) {
            Chapter pick = f.chapterMap.get(lastChapter);
            selectChapter(pick != null ? pick : f.chapters.get(0));
            Ui.play(SoundEvents.BOOK_PAGE_TURN, 1f, 0.8f);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void selectChapter(Chapter c) {
        if (chapter != null) VIEWS.put(chapter.id, new float[]{viewX, viewY, zoom});
        if (chapter != null && chapter != c) Ui.play(SoundEvents.BOOK_PAGE_TURN, 1.1f, 0.6f);
        chapter = c;
        lastChapter = c.id;
        selected = null;
        float[] v = VIEWS.get(c.id);
        if (v != null) {
            viewX = v[0];
            viewY = v[1];
            zoom = v[2];
            return;
        }
        zoom = 1f;
        QuestFile f = ClientData.file;
        Quest focus = f.quests.get(c.autofocus);
        if (focus != null) {
            viewX = (float) focus.x;
            viewY = (float) focus.y;
        } else if (!c.quests.isEmpty()) {
            double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
            for (Quest q : c.quests) {
                minX = Math.min(minX, q.x);
                maxX = Math.max(maxX, q.x);
                minY = Math.min(minY, q.y);
                maxY = Math.max(maxY, q.y);
            }
            viewX = (float) ((minX + maxX) / 2);
            viewY = (float) ((minY + maxY) / 2);
            double spanX = (maxX - minX + 2) * UNIT, spanY = (maxY - minY + 2) * UNIT;
            double availX = Math.max(100, width - side), availY = Math.max(100, height - TOP);
            zoom = (float) Math.max(0.3, Math.min(1.0, Math.min(availX / spanX, availY / spanY)));
        } else {
            viewX = viewY = 0;
        }
    }

    private void open(Quest q) {
        selected = q;
        shown = q;
        detailScroll = 0;
        Ui.play(SoundEvents.BOOK_PAGE_TURN, 1.25f, 0.7f);
    }

    // ---- helpers ----------------------------------------------------------

    private UUID me() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getUUID() : new UUID(0, 0);
    }

    private float sx(double qx) {
        return side + (width - side) / 2f + (float) (qx - viewX) * UNIT * zoom;
    }

    private float sy(double qy) {
        return TOP + (height - TOP) / 2f + (float) (qy - viewY) * UNIT * zoom;
    }

    private boolean visible(Quest q, TeamProgress tp) {
        if (!q.hideUntilDeps) return true;
        return tp.questDone(q.id) || QuestManager.depsDone(ClientData.file, q, tp);
    }

    private enum Status {LOCKED, AVAILABLE, STARTED, DONE, CLAIM}

    private Status status(Quest q, TeamProgress tp, QuestFile f) {
        if (tp.questDone(q.id)) return hasUnclaimed(q, tp) ? Status.CLAIM : Status.DONE;
        if (!QuestManager.available(f, q, tp)) return Status.LOCKED;
        for (Task t : q.tasks) if (tp.taskDone(t.id) || tp.progress(t.id) > 0) return Status.STARTED;
        return Status.AVAILABLE;
    }

    private static int color(Status s) {
        return switch (s) {
            case LOCKED -> Theme.locked;
            case AVAILABLE -> Theme.available;
            case STARTED -> Theme.inProgress;
            case DONE -> Theme.done;
            case CLAIM -> Theme.claim;
        };
    }

    private boolean hasUnclaimed(Quest q, TeamProgress tp) {
        if (!tp.questDone(q.id)) return false;
        UUID me = me();
        for (Reward r : q.rewards) if (!tp.isClaimed(me, r.id)) return true;
        return ClientData.spinAvailable(q, me);
    }

    private float taskFraction(Quest q, TeamProgress tp) {
        if (q.tasks.isEmpty()) return 0;
        double sum = 0;
        for (Task t : q.tasks) {
            long max = t.maxProgress();
            sum += tp.taskDone(t.id) ? 1 : Math.min(1.0, tp.progress(t.id) / (double) max);
        }
        return (float) (sum / q.tasks.size());
    }

    private int[] chapterCounts(Chapter c, TeamProgress tp) {
        int done = 0;
        for (Quest q : c.quests) if (tp.questDone(q.id)) done++;
        return new int[]{done, c.quests.size()};
    }

    private boolean chapterHasClaim(Chapter c, TeamProgress tp) {
        for (Quest q : c.quests) if (hasUnclaimed(q, tp)) return true;
        return false;
    }

    private int unclaimedTotal(QuestFile f, TeamProgress tp) {
        UUID me = me();
        int n = 0;
        for (Quest q : f.quests.values()) {
            if (!tp.questDone(q.id)) continue;
            for (Reward r : q.rewards) if (!tp.isClaimed(me, r.id) && !r.excludeFromClaimAll) n++;
            if (ClientData.spinAvailable(q, me)) n++;
        }
        return n;
    }

    static String speciesName(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String key = "cobblemon.species." + path + ".name";
        String s = Component.translatable(key).getString();
        if (s.equals(key)) s = path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1);
        return s;
    }

    private static String typeName(String t) {
        return t.isEmpty() ? "" : Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase();
    }

    private static String plural(long n, String word) {
        if (n == 1) return word;
        if (word.endsWith("s") || word.endsWith("Pokémon")) return word;
        return word + "s";
    }

    /** One clear sentence saying what the task wants. */
    static String goal(Task t) {
        String title = Gfx.strip(Gfx.codes(t.title)).trim();
        switch (t.type) {
            case ITEM: {
                if (!title.isEmpty()) return (t.consume ? "Hand in: " : "Get: ") + title;
                String n = t.item == null ? "item" : t.item.isFilter() ? "any matching item"
                        : t.item.exists() ? t.item.stack().getHoverName().getString() : pretty(t.item.id) + " (not in this modpack)";
                return (t.consume ? "Hand in " : "Get ") + t.amount + " × " + n;
            }
            case KILL:
                if (!title.isEmpty()) return title;
                String n = t.target.isEmpty() ? "mobs" : Component.translatable("entity." + t.target.replace(':', '.')).getString();
                return "Kill " + t.amount + " " + plural(t.amount, n);
            case COBBLEMON:
                if (!title.isEmpty()) return title;
                return cobblemonGoal(t);
            default:
                if (!title.isEmpty()) return title;
                return switch (t.type) {
                    case CHECKMARK -> "Read this quest, then press Check";
                    case ADVANCEMENT -> "Earn the advancement " + t.target;
                    case STRUCTURE -> "Find " + pretty(t.target);
                    case BIOME -> "Visit " + pretty(t.target);
                    case DIMENSION -> "Travel to " + pretty(t.target);
                    case KEY_VALUE -> "Reach " + t.amount + " " + t.target;
                    default -> "Complete this task";
                };
        }
    }

    private static String pretty(String id) {
        String p = id.replace("#", "");
        p = p.contains(":") ? p.substring(p.indexOf(':') + 1) : p;
        p = p.substring(p.lastIndexOf('/') + 1).replace('_', ' ');
        return p.isEmpty() ? id : Character.toUpperCase(p.charAt(0)) + p.substring(1);
    }

    private static String cobblemonGoal(Task t) {
        String who;
        List<String> species = new ArrayList<>();
        for (String s : t.extra.split(",")) if (!s.isBlank()) species.add(speciesName(s.trim()));
        String type = typeName(t.pokemonType);
        String shiny = t.shiny ? "shiny " : "";
        if (!species.isEmpty()) {
            if (species.size() == 1) who = shiny + species.get(0);
            else who = String.join(", ", species.subList(0, species.size() - 1)) + " or " + species.get(species.size() - 1);
        } else if (!type.isEmpty()) {
            who = shiny + type + "-type Pokémon";
        } else {
            who = shiny + "Pokémon";
        }
        long n = t.amount;
        return switch (t.target) {
            case "catch" -> species.size() > 1 ? (n == 1 ? "Catch one of: " + who : "Catch " + n + " of: " + who)
                    : n == 1 ? (species.isEmpty() ? "Catch a " + who : "Catch " + who) : "Catch " + n + " " + who;
            case "have_registered" -> t.dexProgress.toLowerCase().endsWith("percent")
                    ? "Have " + n + "% of all " + (type.isEmpty() ? "" : type + "-type ") + "Pokémon registered as " + (t.dexProgress.toLowerCase().startsWith("caught") ? "caught" : "seen") + " in your Pokédex"
                    : "Register " + n + " " + who + " as " + (t.dexProgress.equalsIgnoreCase("caught") ? "caught" : "seen") + " in your Pokédex";
            case "defeat" -> n == 1 ? "Win a Pokémon battle" : "Win " + n + " Pokémon battles";
            case "scan" -> "Scan " + n + " " + who + " with your Pokédex";
            case "evolve" -> n == 1 ? "Evolve a Pokémon" : "Evolve " + n + " Pokémon";
            case "evolve_into" -> "Evolve a Pokémon into " + who;
            case "fishing" -> "Fish up " + (n == 1 ? who : n + " " + who) + " with a Poké Rod";
            case "revive_fossil" -> n == 1 ? "Revive a fossil Pokémon" : "Revive " + n + " fossil Pokémon";
            case "select_starter" -> "Choose your first Pokémon";
            case "level_up_to" -> "Raise a Pokémon to level " + n;
            default -> pretty(t.target) + " " + n + " " + who;
        };
    }

    /** Small line under a task explaining how it completes. */
    private static String completesHow(Task t) {
        return switch (t.type) {
            case ITEM -> t.consume ? "Press Submit to hand them in (they are taken from your inventory)."
                    : "Keep it in your inventory and it counts by itself.";
            case CHECKMARK -> "Press Check when you have done it.";
            case ADVANCEMENT -> "Completes by itself when you earn this advancement.";
            case STRUCTURE -> "Completes by itself when you are inside it.";
            case BIOME, DIMENSION -> "Completes by itself when you get there.";
            case COBBLEMON -> "scan".equals(t.target) ? "Point your Pokédex at wild Pokémon to scan them." : "Counts by itself.";
            default -> "Counts by itself.";
        };
    }

    /** Icon for a task row: its own icon/item, otherwise something that fits the quest (never a plain book). */
    private static ItemStack taskIcon(Task t) {
        if (t.icon != null && t.icon.exists()) return t.icon.stack();
        if (t.item != null && t.item.exists()) return t.item.stack();
        QuestFile f = ClientData.file;
        Quest q = f == null ? null : f.quests.get(t.quest);
        switch (t.type) {
            case KILL -> {
                var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.ResourceLocation.tryParse(t.target));
                if (type.isPresent()) {
                    var egg = net.minecraft.world.item.SpawnEggItem.byId(type.get());
                    if (egg != null) return new ItemStack(egg);
                }
            }
            case COBBLEMON -> {
                String sp = t.extra.split(",")[0].trim();
                if (!sp.isEmpty()) return pokemonIcon(sp);
            }
            default -> {
            }
        }
        return q != null ? questIcon(q) : pokeBall();
    }

    /** A Cobblemon "Pokémon model" item showing the given species (falls back to a Poké Ball). */
    static ItemStack pokemonIcon(String species) {
        QuestFile.ItemRef ref = POKEMON_ICONS.computeIfAbsent(species, sp -> {
            QuestFile.ItemRef r = QuestFile.ItemRef.of("cobblemon:pokemon_model");
            net.minecraft.nbt.CompoundTag c = new net.minecraft.nbt.CompoundTag(), pi = new net.minecraft.nbt.CompoundTag();
            pi.putString("species", sp.contains(":") ? sp : "cobblemon:" + sp);
            pi.put("aspects", new net.minecraft.nbt.ListTag());
            c.put("cobblemon:pokemon_item", pi);
            r.components = c;
            return r;
        });
        return ref.exists() ? ref.stack() : pokeBall();
    }

    private static final Map<String, QuestFile.ItemRef> POKEMON_ICONS = new HashMap<>();

    static ItemStack pokeBall() {
        Item i = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("cobblemon:poke_ball"));
        return new ItemStack(i == Items.AIR ? Items.ENDER_EYE : i);
    }

    private static ItemStack rewardIcon(Reward r) {
        if (r.icon != null && r.icon.exists()) return r.icon.stack();
        if (r.item != null && r.item.exists()) return r.item.stack();
        if (r.type == QuestFile.RewardType.LOOT && ClientData.file != null) {
            QuestFile.LootTable lt = ClientData.file.lootTables.get(r.amount);
            if (lt != null && lt.icon != null && lt.icon.exists()) return lt.icon.stack();
        }
        return switch (r.type) {
            case XP, XP_LEVELS -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            case COMMAND -> new ItemStack(Items.COMMAND_BLOCK);
            default -> new ItemStack(Items.CHEST);
        };
    }

    private static String rewardLabel(Reward r) {
        if (!r.title.isEmpty()) return Gfx.strip(r.title.replace('&', '§'));
        switch (r.type) {
            case ITEM: {
                if (r.item == null) return "Item";
                return r.amount + " × " + r.item.stack().getHoverName().getString();
            }
            case XP:
                return r.amount + " experience";
            case XP_LEVELS:
                return r.amount + (r.amount == 1 ? " experience level" : " experience levels");
            case COMMAND: {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("cobbledollars give \\S+ (\\d+)").matcher(r.command);
                if (m.find()) return m.group(1) + " CobbleDollars";
                return "Special reward";
            }
            case LOOT: {
                QuestFile.LootTable lt = ClientData.file == null ? null : ClientData.file.lootTables.get(r.amount);
                return "Random: " + (lt == null || lt.title.isEmpty() ? "one of several items" : Gfx.strip(lt.title.replace('&', '§')));
            }
            case KEY_VALUE:
                return "legendary".equals(r.key) ? "+" + r.amount + " to your Legendary record" : "+" + r.amount + " " + r.key;
            default:
                return "Reward";
        }
    }

    static ItemStack questIcon(Quest q) {
        if (q.icon != null && q.icon.exists()) return q.icon.stack();
        for (Task t : q.tasks) {
            if (t.item != null && t.item.exists()) return t.item.stack();
        }
        for (Task t : q.tasks) {
            if (t.type == QuestFile.TaskType.COBBLEMON && !t.extra.isBlank()) return pokemonIcon(t.extra.split(",")[0].trim());
        }
        return pokeBall();
    }

    private static final Map<String, java.util.Optional<net.minecraft.resources.ResourceLocation>> CHAPTER_ART = new HashMap<>();

    /** Optional hand-drawn chapter icon: assets/unyieldingquests/textures/chapter_icon/<chapter file name>.png */
    static net.minecraft.resources.ResourceLocation chapterArt(Chapter c) {
        return CHAPTER_ART.computeIfAbsent(c.filename, k -> {
            var rl = net.minecraft.resources.ResourceLocation.tryParse(dev.unyielding.quests.UnyieldingQuests.MODID + ":textures/chapter_icon/" + k.toLowerCase() + ".png");
            return rl != null && exists(rl) ? java.util.Optional.of(rl) : java.util.Optional.empty();
        }).orElse(null);
    }

    private static void drawChapterIcon(GuiGraphics g, Chapter c, int x, int y, boolean active) {
        net.minecraft.resources.ResourceLocation art = chapterArt(c);
        if (art != null) {
            ArtAnim.draw(g, art, x, y, 16, "chapter:" + c.filename, active);
        } else {
            g.renderItem(chapterIcon(c), x, y);
        }
    }

    static ItemStack chapterIcon(Chapter c) {
        return c.icon != null && c.icon.exists() ? c.icon.stack() : pokeBall();
    }

    private static final Map<Long, java.util.Optional<net.minecraft.resources.ResourceLocation>> ART = new HashMap<>();
    private static final Map<String, java.util.Optional<net.minecraft.resources.ResourceLocation>> IMAGES = new HashMap<>();

    /** Clears the picture cache (resource reload). */
    public static void clearArt() {
        ART.clear();
        IMAGES.clear();
        CHAPTER_ART.clear();
        ArtAnim.clear();
    }

    private static boolean exists(net.minecraft.resources.ResourceLocation rl) {
        return Minecraft.getInstance().getResourceManager().getResource(rl).isPresent();
    }

    /** Optional hand-drawn quest picture: assets/unyieldingquests/textures/quest_art/<quest id>.png (replaces the item icon). */
    static net.minecraft.resources.ResourceLocation questArt(long id) {
        return ART.computeIfAbsent(id, k -> {
            var rl = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(dev.unyielding.quests.UnyieldingQuests.MODID,
                    "textures/quest_art/" + QuestFile.idToString(k).toLowerCase() + ".png");
            return exists(rl) ? java.util.Optional.of(rl) : java.util.Optional.empty();
        }).orElse(null);
    }

    /** Chapter page picture; accepts "ns:path" (looked up under textures/ with .png) or a full texture path. */
    static net.minecraft.resources.ResourceLocation imageTexture(String image) {
        return IMAGES.computeIfAbsent(image, k -> {
            var rl = net.minecraft.resources.ResourceLocation.tryParse(k);
            if (rl == null) return java.util.Optional.empty();
            if (!rl.getPath().endsWith(".png")) rl = rl.withPath(p -> "textures/" + p + ".png");
            return exists(rl) ? java.util.Optional.of(rl) : java.util.Optional.empty();
        }).orElse(null);
    }

    private boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, net.minecraft.resources.ResourceLocation icon, Ui.Style style,
                        boolean enabled, int mx, int my, Runnable r) {
        boolean ov = enabled && over(mx, my, x, y, w, h);
        Ui.button(g, font, x, y, w, h, label == null ? null : Component.literal(label), icon, style, ov, enabled);
        if (enabled) hits.add(new Hit(x, y, x + w, y + h, r));
    }

    private void slot(GuiGraphics g, int x, int y, ItemStack stack, boolean decorate, List<Component> extra, int mx, int my) {
        Ui.blit(g, Ui.SLOT, x, y, 18, 18);
        g.renderItem(stack, x + 1, y + 1);
        if (decorate) g.renderItemDecorations(font, stack, x + 1, y + 1);
        slots.add(new SlotHit(x, y, stack, extra));
        if (over(mx, my, x, y, 18, 18)) g.fill(x + 1, y + 1, x + 17, y + 17, 0x40FFFFFF);
    }

    // ---- rendering --------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        if (minecraft.level == null) renderPanorama(g, pt);
        if (UiConfig.blur()) renderBlurredBackground(pt);
        g.fill(0, 0, width, height, Ui.alpha(Theme.backdrop, 0.55f + 0.45f * UiConfig.opacity()));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = Util.getMillis();
        dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        hits.clear();
        slots.clear();
        hovered = null;
        renderBackground(g, mx, my, pt);
        QuestFile f = ClientData.file;
        if (f == null || chapter == null) {
            g.drawCenteredString(font, "The quest book has not arrived from the server yet.", width / 2, height / 2, Theme.text);
            return;
        }
        TeamProgress tp = ClientData.progress;
        detailAnim = Ui.approach(detailAnim, selected != null ? 1f : 0f, 18f, dt);
        if (selected == null && detailAnim < 0.02f) shown = null;
        boolean spinning = spinVisible();
        boolean modal = shown != null || spinning;
        int cmx = modal ? -1000 : mx, cmy = modal ? -1000 : my;
        pendingTip = null;

        renderCanvas(g, f, tp, cmx, cmy);
        renderSidebar(g, f, tp, cmx, cmy);
        renderTop(g, f, tp, cmx, cmy);

        if (hovered != null && !modal) renderNodeTooltip(g, f, tp, hovered, mx, my);
        if (shown != null) renderDetail(g, f, tp, spinning ? -1000 : mx, spinning ? -1000 : my);
        if (spinning) renderSpin(g, mx, my);
        if (pendingTip != null) g.renderComponentTooltip(font, pendingTip, mx, my);
        if (!ClientData.notice.isEmpty() && System.currentTimeMillis() - ClientData.noticeTime < 4000) {
            int w = font.width(ClientData.notice) + 16;
            int x = width / 2 - w / 2, y = height - 30;
            Ui.blit(g, Ui.CHIP, x, y, w, 16, 0xE0A02020);
            g.drawCenteredString(font, ClientData.notice, width / 2, y + 4, 0xFFFFFFFF);
        }
        for (SlotHit s : slots) {
            if (!s.in(mx, my)) continue;
            List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, s.stack()));
            if (s.extra() != null) lines.addAll(s.extra());
            g.renderComponentTooltip(font, lines, mx, my);
            break;
        }
    }

    private void renderTop(GuiGraphics g, QuestFile f, TeamProgress tp, int mx, int my) {
        int x0 = side + 2, w = width - side - 4;
        Ui.panel(g, Ui.HEADER, x0, 2, w, TOP - 2);
        drawChapterIcon(g, chapter, x0 + 5, 6, over(mx, my, x0 + 5, 6, 16, 16));
        int[] cnt = chapterCounts(chapter, tp);
        int bx = width - 6;
        // buttons (right to left)
        button(g, bx - 18, 5, 18, 18, null, Ui.ICON_CLOSE, Ui.Style.NORMAL, true, mx, my, this::onClose);
        bx -= 22;
        int tw = font.width("Teams") + 22;
        button(g, bx - tw, 5, tw, 18, "Teams", Ui.ICON_TEAM, Ui.Style.NORMAL, true, mx, my, () -> Minecraft.getInstance().setScreen(new TeamScreen()));
        bx -= tw + 4;
        int unclaimed = unclaimedTotal(f, tp);
        String cl = unclaimed > 0 ? "Claim all (" + unclaimed + ")" : "Claim all";
        int cw = font.width(cl) + 22;
        boolean roomForClaim = bx - cw > x0 + 120;
        if (roomForClaim) {
            button(g, bx - cw, 5, cw, 18, cl, Ui.ICON_GIFT, Ui.Style.GOLD, unclaimed > 0, mx, my, () -> {
                Net.sendToServer(new Net.Claim(0, 2));
                Ui.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.8f);
            });
            bx -= cw + 4;
        }
        // title + chapter progress
        int titleX = x0 + 26;
        String prog = cnt[0] + " / " + cnt[1];
        int chipW = Ui.chipWidth(font, prog);
        int titleRoom = bx - titleX - chipW - 10;
        String title = font.plainSubstrByWidth(Gfx.readable(chapter.title), Math.max(20, titleRoom));
        g.drawString(font, Component.literal(title).withStyle(ChatFormatting.BOLD), titleX, 7, Theme.text, true);
        int barW = Math.max(30, Math.min(160, titleRoom));
        Ui.bar(g, titleX, 18, barW, 4, cnt[1] == 0 ? 0 : cnt[0] / (double) cnt[1], cnt[0] == cnt[1] ? Theme.done : Theme.accent);
        int chipX = titleX + Math.min(font.width(Component.literal(title).withStyle(ChatFormatting.BOLD)), titleRoom) + 6;
        Ui.chip(g, font, chipX, 6, prog, cnt[0] == cnt[1] && cnt[1] > 0 ? Theme.done : 0xFF2E3442, Theme.text);
    }

    private void renderSidebar(GuiGraphics g, QuestFile f, TeamProgress tp, int mx, int my) {
        Ui.panel(g, 2, 2, side - 4, height - 4);
        // header
        Ui.icon(g, Ui.ICON_QUESTS, 9, 9, Theme.accent);
        int done = tp.completedQuests.size(), total = f.quests.size();
        String pt = done + "/" + total;
        String bookTitle = font.width(Component.literal("Quest Book").withStyle(ChatFormatting.BOLD)) + 26 + font.width(pt) < side - 8 ? "Quest Book" : "Quests";
        g.drawString(font, Component.literal(bookTitle).withStyle(ChatFormatting.BOLD), 20, 9, Theme.text, true);
        g.drawString(font, pt, side - 8 - font.width(pt), 9, Theme.textMuted, false);
        Ui.bar(g, 8, 21, side - 16, 4, total == 0 ? 0 : done / (double) total, Theme.accent);
        int listTop = 30;
        g.fill(8, listTop - 2, side - 8, listTop - 1, 0x22FFFFFF);
        g.enableScissor(2, listTop, side - 2, height - 4);
        int y = listTop + 2 - sideScroll;
        long lastGroup = Long.MIN_VALUE;
        int start = y;
        for (Chapter c : f.chapters) {
            if (c.group != lastGroup) {
                lastGroup = c.group;
                String gt = "";
                for (QuestFile.Group gr : f.groups) if (gr.id == c.group) gt = gr.title;
                if (!gt.isEmpty()) {
                    y += 4;
                    String head = font.plainSubstrByWidth(Gfx.codes(gt).toUpperCase(), side - 18);
                    g.drawString(font, head, 9, y, Theme.textDim, false);
                    y += 11;
                }
            }
            int[] cnt = chapterCounts(c, tp);
            boolean sel = c == chapter;
            boolean ov = mx < side && my >= Math.max(y, listTop) && my < y + ROW && my >= listTop && mx >= 4;
            if (sel) Ui.blit(g, Ui.ROW_SELECTED, 5, y, side - 10, ROW);
            else if (ov) Ui.blit(g, Ui.ROW_HOVER, 5, y, side - 10, ROW);
            drawChapterIcon(g, c, 8, y + 2, ov);
            boolean complete = cnt[1] > 0 && cnt[0] == cnt[1];
            int right = side - 9;
            boolean claim = chapterHasClaim(c, tp);
            if (claim) {
                float bob = UiConfig.animations() ? Mth.sin(Ui.time() * 5f) * 1.2f : 0;
                Ui.blit(g, Ui.BADGE_ALERT, right - 9, (int) (y + 6 + bob), 9, 9);
                right -= 12;
            } else if (complete) {
                Ui.blit(g, Ui.BADGE_CHECK, right - 9, y + 6, 9, 9);
                right -= 12;
            }
            String pc = cnt[0] + "/" + cnt[1];
            if (!complete) {
                g.drawString(font, pc, right - font.width(pc), y + 3, Theme.textMuted, false);
                right -= font.width(pc) + 4;
            }
            int textX = 27;
            String title = font.plainSubstrByWidth(Gfx.readable(c.title), Math.max(10, right - textX));
            g.drawString(font, title, textX, y + 3, sel ? 0xFFFFFFFF : Theme.text, sel);
            int bw = side - 10 - textX - 6;
            Ui.bar(g, textX, y + 13, bw, 3, cnt[1] == 0 ? 0 : cnt[0] / (double) cnt[1], complete ? Theme.done : Theme.available);
            if (my >= listTop) {
                final Chapter target = c;
                int yy = y;
                hits.add(new Hit(4, Math.max(yy, listTop), side - 4, yy + ROW, () -> selectChapter(target)));
            }
            y += ROW + 1;
        }
        sideContentHeight = y - start + 6;
        g.disableScissor();
        int view = height - 4 - listTop;
        if (sideContentHeight > view) {
            int th = Math.max(16, view * view / sideContentHeight);
            int ty = listTop + (int) ((view - th) * (sideScroll / (float) Math.max(1, sideContentHeight - view)));
            Ui.blit(g, Ui.SCROLL_THUMB, side - 6, ty, 3, th);
        }
    }

    private void renderCanvas(GuiGraphics g, QuestFile f, TeamProgress tp, int mx, int my) {
        int x0 = side, y0 = TOP + 2;
        g.enableScissor(x0, y0, width, height);
        g.fill(x0, y0, width, height, Theme.canvas);
        float step = UNIT * zoom;
        if (step > 10) {
            int dot = zoom > 1.3f ? 2 : 1;
            float startX = sx(Math.floor(viewX - (width - side) / 2f / step) - 1);
            float startY = sy(Math.floor(viewY - (height - TOP) / 2f / step) - 1);
            for (float x = startX; x < width; x += step)
                for (float y = startY; y < height; y += step)
                    if (x >= x0 && y >= y0) g.fill(Math.round(x), Math.round(y), Math.round(x) + dot, Math.round(y) + dot, Theme.gridDot);
        }
        for (QuestFile.ChapterImage im : chapter.images) {
            if (im.image.isEmpty()) {
                if (im.text.isEmpty()) continue;
                float icx = sx(im.x), icy = sy(im.y);
                if (icx < x0 - 200 || icx > width + 200 || icy < y0 - 40 || icy > height + 40) continue;
                Component txt = Component.literal(Gfx.readable(im.text)).withStyle(ChatFormatting.BOLD);
                int tw = font.width(txt);
                float sc = Math.max(0.8f, Math.min(2.2f, (float) im.height * UNIT * zoom / 11f));
                if (im.width > 0 && tw > 0) sc = Math.min(sc, Math.max(0.45f, (float) im.width * UNIT * zoom / (tw + 6f)));
                g.pose().pushPose();
                g.pose().translate(icx, icy, 0);
                g.pose().scale(sc, sc, 1f);
                g.drawString(font, txt, -tw / 2, -4, Theme.text, true);
                g.pose().popPose();
                continue;
            }
            net.minecraft.resources.ResourceLocation rl = imageTexture(im.image);
            if (rl == null) continue;
            float w = (float) im.width * UNIT * zoom, h = (float) im.height * UNIT * zoom;
            float icx = sx(im.x), icy = sy(im.y);
            if (icx + w < x0 || icx - w > width || icy + h < y0 || icy - h > height) continue;
            g.pose().pushPose();
            g.pose().translate(icx, icy, 0);
            if (im.rotation != 0) g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) im.rotation));
            g.pose().scale(w / 64f, h / 64f, 1f);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.blit(rl, -32, -32, 0, 0, 64, 64, 64, 64);
            g.pose().popPose();
        }
        float time = Ui.time();
        // dependency lines
        for (Quest q : chapter.quests) {
            if (!visible(q, tp) || q.hideDepLines) continue;
            for (long d : q.deps) {
                Quest dq = f.quests.get(d);
                if (dq == null || dq.chapter != q.chapter || dq.hideDependentLines || !visible(dq, tp)) continue;
                boolean depDone = tp.questDone(dq.id), qDone = tp.questDone(q.id);
                int col = qDone && depDone ? Theme.lineDone : depDone ? Theme.lineOpen : Theme.lineLocked;
                float lw = Math.max(1.4f, 2.4f * zoom);
                float ax = sx(dq.x), ay = sy(dq.y), bx = sx(q.x), by = sy(q.y);
                Gfx.line(g, ax, ay + 1, bx, by + 1, lw + 1.2f, 0x50000000);
                Gfx.line(g, ax, ay, bx, by, lw, col);
                if (depDone && !qDone && UiConfig.animations()) {
                    for (int k = 0; k < 3; k++) {
                        float t = (time * 0.45f + k / 3f) % 1f;
                        Gfx.shape(g, "circle", Mth.lerp(t, ax, bx), Mth.lerp(t, ay, by), Math.max(1.1f, 1.5f * zoom), Ui.alpha(Theme.available, 0.9f));
                    }
                }
            }
        }
        // nodes
        for (Quest q : chapter.quests) {
            if (!visible(q, tp)) continue;
            float cx = sx(q.x), cy = sy(q.y);
            float base = (float) q.size * UNIT * zoom * 0.5f * 0.9f;
            if (cx + base * 2 < x0 || cx - base * 2 > width || cy + base * 2 < y0 || cy - base * 2 > height) continue;
            double dx = mx - cx, dy = my - cy;
            boolean ov = mx >= x0 && my >= y0 && dx * dx + dy * dy <= base * base;
            if (ov) hovered = q;
            float ha = Ui.approach(hoverAnim.getOrDefault(q.id, 0f), ov ? 1f : 0f, 16f, dt);
            hoverAnim.put(q.id, ha);
            float r = base * (1f + 0.12f * ha);
            String shape = q.shape.isEmpty() ? f.defaultShape : q.shape;
            Status st = status(q, tp, f);
            int col = color(st);
            if (st == Status.CLAIM || st == Status.AVAILABLE || ha > 0.01f) {
                float p = st == Status.CLAIM ? 0.55f + 0.45f * Ui.pulse(4f) : st == Status.AVAILABLE ? 0.25f + 0.2f * Ui.pulse(2.5f) : 0.3f;
                Gfx.glow(g, cx, cy, r * 0.85f, r * (1.55f + 0.15f * ha), Ui.alpha(col, Math.max(p, 0.45f * ha)));
            }
            Gfx.shape(g, shape, cx, cy + 1.5f, r + 2f, 0x70000000);
            Gfx.shape(g, shape, cx, cy, r + 1.6f, col);
            Gfx.shapeGradient(g, shape, cx, cy, r - 0.4f, Ui.mix(Theme.nodeFill, 0xFFFFFFFF, 0.18f), Theme.nodeFill);
            float s = (r * 2 * 0.56f * (float) q.iconScale) / 16f;
            if (q.icon != null && q.icon.id.equals("cobblemon:pokemon_model")) s *= 1.7f;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().scale(s, s, 1f);
            net.minecraft.resources.ResourceLocation art = questArt(q.id);
            if (art != null) {
                ArtAnim.draw(g, art, -8, -8, 16, q.id, ov);
            } else {
                g.renderItem(questIcon(q), -8, -8);
            }
            g.pose().popPose();
            if (st == Status.LOCKED) Gfx.shape(g, shape, cx, cy, r - 0.4f, 0x99101318);
            if (st == Status.STARTED) Gfx.arc(g, cx, cy, r + 2.4f, r + 4f, taskFraction(q, tp), Theme.inProgress);
            int bsz = 9;
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            if (st == Status.CLAIM) {
                float bob = UiConfig.animations() ? Mth.sin(Ui.time() * 6f) * 1.5f : 0;
                Ui.blit(g, Ui.BADGE_ALERT, Math.round(cx + r * 0.55f), Math.round(cy - r * 0.95f + bob), bsz, bsz);
            } else if (st == Status.DONE) {
                Ui.blit(g, Ui.BADGE_CHECK, Math.round(cx + r * 0.5f), Math.round(cy + r * 0.35f), bsz, bsz);
            } else if (st == Status.LOCKED && zoom > 0.55f) {
                Ui.blit(g, Ui.BADGE_LOCK, Math.round(cx + r * 0.5f), Math.round(cy + r * 0.35f), bsz, bsz);
            }
            g.pose().popPose();
        }
        g.disableScissor();
    }

    private void renderNodeTooltip(GuiGraphics g, QuestFile f, TeamProgress tp, Quest q, int mx, int my) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(Gfx.readable(q.displayTitle())).withStyle(ChatFormatting.BOLD));
        for (String s : q.subtitle) tip.add(Component.literal(Gfx.codes(s)));
        Status st = status(q, tp, f);
        switch (st) {
            case LOCKED -> {
                List<String> need = new ArrayList<>();
                for (long d : q.deps) {
                    Quest dq = f.quests.get(d);
                    if (dq != null && !tp.questDone(dq.id)) need.add(Gfx.strip(Gfx.codes(dq.displayTitle())));
                }
                tip.add(Component.literal("Locked").withStyle(s -> s.withColor(Theme.bad)));
                if (!need.isEmpty())
                    tip.add(Component.literal("Finish first: " + String.join(", ", need.subList(0, Math.min(3, need.size()))) + (need.size() > 3 ? "…" : ""))
                            .withStyle(s -> s.withColor(Theme.textDim)));
            }
            case AVAILABLE -> tip.add(Component.literal("Ready to start").withStyle(s -> s.withColor(Theme.available)));
            case STARTED -> tip.add(Component.literal("In progress: " + Math.round(taskFraction(q, tp) * 100) + "%").withStyle(s -> s.withColor(Theme.inProgress)));
            case CLAIM -> tip.add(Component.literal("Rewards ready to claim!").withStyle(s -> s.withColor(Theme.claim)));
            case DONE -> tip.add(Component.literal("Completed").withStyle(s -> s.withColor(Theme.good)));
        }
        tip.add(Component.literal("Click to open").withStyle(s -> s.withColor(Theme.textMuted).withItalic(true)));
        g.renderComponentTooltip(font, tip, mx, my);
    }

    // ---- detail panel -----------------------------------------------------

    private int panelW() {
        return Math.min(340, width - 24);
    }

    private void renderDetail(GuiGraphics g, QuestFile f, TeamProgress tp, int mx, int my) {
        Quest q = shown;
        float a = Ui.easeOut(detailAnim);
        g.fill(0, 0, width, height, Ui.alpha(0xA0000000, a));
        boolean interactive = detailAnim > 0.85f && selected != null;
        if (!interactive) {
            mx = -1000;
            my = -1000;
        }
        int pw = panelW(), ph = height - 24;
        int px = (width - pw) / 2, py = 12;
        g.pose().pushPose();
        float sc = 0.94f + 0.06f * a;
        g.pose().translate(width / 2f, height / 2f, 300);
        g.pose().scale(sc, sc, 1f);
        g.pose().translate(-width / 2f, -height / 2f + (1 - a) * 10, 0);

        Ui.panel(g, px, py, pw, ph);
        Ui.panel(g, Ui.HEADER, px, py, pw, 38);
        Status st = status(q, tp, f);
        Ui.blit(g, Ui.SLOT, px + 8, py + 9, 22, 22);
        net.minecraft.resources.ResourceLocation hart = questArt(q.id);
        if (hart != null) {
            ArtAnim.draw(g, hart, px + 11, py + 12, 16, "open:" + q.id, true);
        } else if (q.icon != null && q.icon.id.equals("cobblemon:pokemon_model")) {
            g.pose().pushPose();
            g.pose().translate(px + 19, py + 20, 0);
            g.pose().scale(1.6f, 1.6f, 1f);
            g.renderItem(questIcon(q), -8, -8);
            g.pose().popPose();
        } else {
            g.renderItem(questIcon(q), px + 11, py + 12);
        }
        int titleW = pw - 44 - 30;
        g.drawString(font, Component.literal(font.plainSubstrByWidth(Gfx.readable(q.displayTitle()), titleW)).withStyle(ChatFormatting.BOLD), px + 36, py + 9, Theme.text, true);
        String sub = q.subtitle.isEmpty() ? "" : Gfx.codes(q.subtitle.get(0));
        String chipText = switch (st) {
            case LOCKED -> "LOCKED";
            case AVAILABLE -> "TO DO";
            case STARTED -> "IN PROGRESS";
            case DONE -> "DONE";
            case CLAIM -> "CLAIM REWARDS";
        };
        int cw = Ui.chip(g, font, px + 36, py + 21, chipText, color(st), st == Status.CLAIM || st == Status.STARTED ? 0xFF2A1C00 : 0xFFFFFFFF);
        if (!sub.isEmpty()) g.drawString(font, font.plainSubstrByWidth(sub, pw - 36 - cw - 34), px + 40 + cw, py + 23, Theme.textDim, false);
        button(g, px + pw - 24, py + 9, 16, 16, null, Ui.ICON_CLOSE, Ui.Style.NORMAL, interactive, mx, my, () -> selected = null);

        boolean done = tp.questDone(q.id);
        boolean avail = QuestManager.available(f, q, tp);
        UUID me = me();
        boolean anyUnclaimed = false;
        if (done) for (Reward r : q.rewards) if (!tp.isClaimed(me, r.id)) anyUnclaimed = true;
        if (ClientData.spinAvailable(q, me)) anyUnclaimed = true;
        int footer = anyUnclaimed ? 26 : 0;

        int cx0 = px + 10, cx1 = px + pw - 12, cw0 = cx1 - cx0;
        int top = py + 40, bottom = py + ph - 4 - footer;
        g.enableScissor(px + 2, top, px + pw - 2, bottom);
        int y = top + 6 - detailScroll;
        int start = y;
        boolean inView = my >= top && my < bottom;

        // --- goal box
        List<FormattedCharSequence> goalLines = new ArrayList<>();
        for (Task t : q.tasks) {
            boolean tdone = tp.taskDone(t.id) || done;
            String line = (tdone ? "✔ " : "• ") + goal(t);
            for (FormattedCharSequence l : font.split(FormattedText.of(line, Style.EMPTY.withColor(tdone ? Theme.good : Theme.text)), cw0 - 16)) goalLines.add(l);
        }
        List<FormattedCharSequence> lockLines = new ArrayList<>();
        if (!avail && !done) {
            List<String> need = new ArrayList<>();
            for (long d : q.deps) {
                Quest dq = f.quests.get(d);
                if (dq != null && !tp.questDone(dq.id)) need.add(Gfx.strip(Gfx.codes(dq.displayTitle())));
            }
            String msg;
            if (need.isEmpty()) msg = "Locked: finish the quests before this one first.";
            else if (need.size() > 6) {
                int more = need.size() - 4;
                msg = "Locked: finish " + String.join(", ", need.subList(0, 4)) + " and " + more + " more quests on this page first (" + need.size() + " left).";
            } else msg = "Locked: finish " + String.join(", ", need) + " first.";
            lockLines.addAll(font.split(FormattedText.of(msg, Style.EMPTY.withColor(Theme.bad)), cw0 - 16));
        }
        int gh = 18 + goalLines.size() * 10 + (lockLines.isEmpty() ? 0 : 3 + lockLines.size() * 10);
        Ui.blit(g, Ui.GOAL, cx0, y, cw0, gh);
        g.drawString(font, Component.literal(done ? "COMPLETED" : "GOAL").withStyle(ChatFormatting.BOLD), cx0 + 8, y + 6, done ? Theme.good : Theme.accentText, false);
        int ly = y + 17;
        for (FormattedCharSequence l : goalLines) {
            g.drawString(font, l, cx0 + 8, ly, Theme.text, false);
            ly += 10;
        }
        if (!lockLines.isEmpty()) {
            ly += 3;
            for (FormattedCharSequence l : lockLines) {
                g.drawString(font, l, cx0 + 8, ly, Theme.bad, false);
                ly += 10;
            }
        }
        y += gh + 8;

        // --- description
        for (int i = 1; i < q.subtitle.size(); i++) {
            for (FormattedCharSequence l : font.split(FormattedText.of(Gfx.codes(q.subtitle.get(i))), cw0)) {
                g.drawString(font, l, cx0, y, Theme.textDim, false);
                y += 10;
            }
        }
        for (String s : q.desc) {
            if (s.isEmpty()) {
                y += 5;
                continue;
            }
            for (FormattedCharSequence l : font.split(FormattedText.of(Gfx.codes(s)), cw0)) {
                g.drawString(font, l, cx0, y, Theme.textDim, false);
                y += 10;
            }
            y += 1;
        }
        y += 8;

        // --- tasks
        int tdoneCount = 0;
        for (Task t : q.tasks) if (tp.taskDone(t.id) || done) tdoneCount++;
        g.drawString(font, Component.literal("TASKS").withStyle(ChatFormatting.BOLD), cx0, y, Theme.heading, false);
        g.drawString(font, tdoneCount + "/" + q.tasks.size(), cx0 + font.width(Component.literal("TASKS").withStyle(ChatFormatting.BOLD)) + 6, y, Theme.textMuted, false);
        y += 13;
        for (Task t : q.tasks) {
            boolean tdone = tp.taskDone(t.id) || done;
            ItemStack icon = taskIcon(t);
            boolean hasButton = !tdone && avail && (t.type == QuestFile.TaskType.CHECKMARK || (t.type == QuestFile.TaskType.ITEM && t.consume));
            int textW = cw0 - 32 - (hasButton ? 58 : 6);
            List<FormattedCharSequence> hint = new ArrayList<>();
            if (!tdone) {
                hint.addAll(font.split(FormattedText.of(completesHow(t), Style.EMPTY.withColor(Theme.textMuted)), cw0 - 34));
                if (UiConfig.hints() && t.type == QuestFile.TaskType.ITEM && t.item != null) {
                    String how = Hints.howToGet(t.item.isFilter() ? icon : t.item.stack());
                    if (!how.isEmpty()) hint.addAll(font.split(FormattedText.of("How to get: " + how, Style.EMPTY.withColor(Theme.textDim)), cw0 - 34));
                }
            }
            long max = t.maxProgress();
            boolean bar = !(t.type == QuestFile.TaskType.CHECKMARK || max == 1);
            int ch = 26 + (bar ? 4 : 0) + hint.size() * 9 + (hint.isEmpty() ? 0 : 2);
            Ui.blit(g, Ui.CARD, cx0, y, cw0, ch, tdone ? Ui.alpha(0xFFFFFFFF, 0.75f) : 0xFFFFFFFF);
            List<Component> extra = null;
            if (t.item != null && t.item.isFilter()) {
                extra = new ArrayList<>();
                extra.add(Component.literal("Any of these count:").withStyle(s -> s.withColor(Theme.heading)));
                List<Item> items = ItemFilter.get(t.item.filter).displayItems();
                for (int i = 0; i < Math.min(12, items.size()); i++)
                    extra.add(Component.literal(" • ").append(new ItemStack(items.get(i)).getHoverName()).withStyle(s -> s.withColor(Theme.textDim)));
                if (items.size() > 12) extra.add(Component.literal(" …and " + (items.size() - 12) + " more").withStyle(s -> s.withColor(Theme.textMuted)));
            }
            slot(g, cx0 + 5, y + 4, icon, false, extra, inView ? mx : -1000, inView ? my : -1000);
            String main = goal(t);
            g.drawString(font, font.plainSubstrByWidth(main, textW), cx0 + 29, y + 5, tdone ? Theme.good : Theme.text, false);
            long cur = tdone ? max : Math.min(max, tp.progress(t.id));
            if (bar) {
                String pr = cur + " / " + max;
                int bw = textW - font.width(pr) - 6;
                Ui.bar(g, cx0 + 29, y + 17, Math.max(20, bw), 5, cur / (double) max, tdone ? Theme.done : Theme.available);
                g.drawString(font, pr, cx0 + 29 + Math.max(20, bw) + 5, y + 16, Theme.textDim, false);
            } else {
                Ui.chip(g, font, cx0 + 29, y + 15, tdone ? "DONE" : "TO DO", tdone ? Theme.done : 0xFF3A4150, 0xFFFFFFFF);
            }
            int hy = y + 26 + (bar ? 4 : 0);
            for (FormattedCharSequence l : hint) {
                g.drawString(font, l, cx0 + 29, hy, Theme.textMuted, false);
                hy += 9;
            }
            if (hasButton) {
                final long id = t.id;
                boolean check = t.type == QuestFile.TaskType.CHECKMARK;
                button(g, cx1 - 56, y + 5, 52, 16, check ? "Check" : "Submit", check ? Ui.ICON_CHECK : null, Ui.Style.ACCENT, interactive && inView, mx, my,
                        () -> {
                            Net.sendToServer(new Net.TaskAction(id, check ? 0 : 1));
                            Ui.play(SoundEvents.UI_BUTTON_CLICK, 1.3f, 0.5f);
                        });
            }
            y += ch + 4;
        }
        y += 6;

        // --- rewards
        boolean spinRow = ClientData.spinEnabled;
        if (!q.rewards.isEmpty() || spinRow) {
            g.drawString(font, Component.literal("REWARDS").withStyle(ChatFormatting.BOLD), cx0, y, Theme.heading, false);
            y += 13;
            if (spinRow) {
                long sid = LuckySpin.spinId(q.id);
                boolean spun = tp.isClaimed(me, sid);
                Ui.blit(g, Ui.CARD, cx0, y, cw0, 30, spun ? Ui.alpha(0xFFFFFFFF, 0.75f) : 0xFFFFFFFF);
                Ui.blit(g, Ui.SLOT, cx0 + 5, y + 6, 18, 18);
                if (UiConfig.animations() && done && !spun) {
                    g.pose().pushPose();
                    g.pose().translate(cx0 + 14, y + 15, 0);
                    g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(Ui.time() * 90f));
                    Ui.blit(g, Ui.ICON_SPIN, -8, -8, 16, 16);
                    g.pose().popPose();
                } else {
                    Ui.blit(g, Ui.ICON_SPIN, cx0 + 6, y + 7, 16, 16);
                }
                if (inView && over(mx, my, cx0 + 5, y + 6, 18, 18)) {
                    g.fill(cx0 + 6, y + 7, cx0 + 22, y + 23, 0x40FFFFFF);
                    pendingTip = spinTooltip();
                }
                g.drawString(font, Component.literal("Lucky Spin").withStyle(ChatFormatting.BOLD), cx0 + 29, y + 5, Theme.gold, false);
                g.drawString(font, font.plainSubstrByWidth(spun ? "You already spun for this quest." : "Bonus prize roll — maybe a Legendary!", cw0 - 32 - 62), cx0 + 29, y + 16, Theme.textDim, false);
                if (spun) {
                    int w = Ui.chipWidth(font, "SPUN");
                    Ui.chip(g, font, cx1 - 4 - w, y + 10, "SPUN", Theme.done, 0xFFFFFFFF);
                } else if (done) {
                    button(g, cx1 - 56, y + 7, 52, 16, "Spin", Ui.ICON_GIFT, Ui.Style.GOLD, interactive && inView, mx, my, () -> {
                        Net.sendToServer(new Net.Claim(sid, 0));
                        Ui.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 0.6f);
                    });
                } else {
                    int w = Ui.chipWidth(font, "LOCKED");
                    Ui.chip(g, font, cx1 - 4 - w, y + 10, "LOCKED", 0xFF3A4150, Theme.textDim);
                }
                y += 34;
            }
            for (Reward r : q.rewards) {
                boolean claimed = tp.isClaimed(me, r.id);
                Ui.blit(g, Ui.CARD, cx0, y, cw0, 26);
                ItemStack ri = rewardIcon(r);
                if (r.type == QuestFile.RewardType.ITEM) ri = ri.copyWithCount((int) Math.min(99, Math.max(1, r.amount)));
                slot(g, cx0 + 5, y + 4, ri, r.type == QuestFile.RewardType.ITEM, null, inView ? mx : -1000, inView ? my : -1000);
                g.drawString(font, font.plainSubstrByWidth(rewardLabel(r), cw0 - 32 - 62), cx0 + 29, y + 9, Theme.text, false);
                if (claimed) {
                    int w = Ui.chipWidth(font, "CLAIMED");
                    Ui.chip(g, font, cx1 - 4 - w, y + 8, "CLAIMED", Theme.done, 0xFFFFFFFF);
                } else if (done) {
                    final long id = r.id;
                    button(g, cx1 - 56, y + 5, 52, 16, "Claim", Ui.ICON_GIFT, Ui.Style.GOLD, interactive && inView, mx, my, () -> {
                        Net.sendToServer(new Net.Claim(id, 0));
                        Ui.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.8f);
                    });
                } else {
                    int w = Ui.chipWidth(font, "LOCKED");
                    Ui.chip(g, font, cx1 - 4 - w, y + 8, "LOCKED", 0xFF3A4150, Theme.textDim);
                }
                y += 30;
            }
        }
        detailContentHeight = y - start + 6;
        g.disableScissor();
        detailViewHeight = bottom - top;
        if (detailContentHeight > detailViewHeight) {
            int view = detailViewHeight;
            int th = Math.max(16, view * view / detailContentHeight);
            int ty = top + (int) ((view - th) * (detailScroll / (float) Math.max(1, detailContentHeight - view)));
            Ui.blit(g, Ui.SCROLL_THUMB, px + pw - 6, ty, 3, th);
        }
        if (anyUnclaimed) {
            final long id = q.id;
            int bw = Math.min(170, pw - 40);
            button(g, px + pw / 2 - bw / 2, py + ph - 24, bw, 18, "Claim all rewards", Ui.ICON_GIFT, Ui.Style.GOLD, interactive, mx, my, () -> {
                Net.sendToServer(new Net.Claim(id, 1));
                Ui.play(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.5f);
            });
        }
        g.pose().popPose();
    }

    // ---- Lucky Spin ---------------------------------------------------------

    private static final int SPIN_SLOT = 24;
    private static final long SPIN_MS = 3400;
    private final java.util.ArrayDeque<LuckySpin.Spin> spinQueue = new java.util.ArrayDeque<>();
    private final List<LuckySpin.Spin> spinSummary = new ArrayList<>();
    private final List<Hit> spinHits = new ArrayList<>();
    private LuckySpin.Spin spinCur;
    private long spinStart;
    private boolean spinLanded;
    private int spinLastSlot = -1;
    private List<Component> pendingTip;

    /** Called when the server sends finished spins while this screen is open. */
    public void queueSpins(List<LuckySpin.Spin> spins) {
        spinQueue.addAll(spins);
        if (spinCur == null && spinSummary.isEmpty()) nextSpin();
    }

    private boolean spinVisible() {
        return spinCur != null || !spinSummary.isEmpty();
    }

    private void nextSpin() {
        spinCur = spinQueue.poll();
        spinStart = Util.getMillis();
        spinLanded = false;
        spinLastSlot = -1;
        if (spinCur != null) Ui.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 0.5f);
    }

    private void skipAllSpins() {
        if (spinCur != null) spinSummary.add(spinCur);
        spinSummary.addAll(spinQueue);
        spinQueue.clear();
        spinCur = null;
        Ui.play(SoundEvents.PLAYER_LEVELUP, 1.2f, 0.5f);
    }

    private void closeSpins() {
        spinCur = null;
        spinQueue.clear();
        spinSummary.clear();
    }

    private static String pct(int w, int total) {
        if (total <= 0) return "0%";
        double p = w * 100.0 / total;
        return (p == Math.floor(p) ? String.valueOf((int) p) : String.format(java.util.Locale.ROOT, "%.1f", p)) + "%";
    }

    static List<Component> spinTooltip() {
        int[] w = ClientData.spinWeights;
        int total = 0;
        for (int x : w) total += Math.max(0, x);
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal("Lucky Spin").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD));
        tip.add(Component.literal("Every quest gives you one bonus prize roll.").withStyle(s -> s.withColor(Theme.textDim)));
        LuckySpin.Tier[] tiers = LuckySpin.Tier.values();
        for (int i = tiers.length - 1; i >= 0; i--) {
            LuckySpin.Tier t = tiers[i];
            tip.add(Component.literal(" " + pct(w[i], total) + "  ").withStyle(s -> s.withColor(Theme.text))
                    .append(Component.literal(t.label).withStyle(s -> s.withColor(t.color))));
        }
        tip.add(Component.literal("Pokémon prizes have random stats, natures and abilities.").withStyle(s -> s.withColor(Theme.textMuted).withItalic(true)));
        return tip;
    }

    private void spinButton(GuiGraphics g, int x, int y, int w, int h, String label, net.minecraft.resources.ResourceLocation icon, Ui.Style style, int mx, int my, Runnable r) {
        boolean ov = over(mx, my, x, y, w, h);
        Ui.button(g, font, x, y, w, h, Component.literal(label), icon, style, ov, true);
        spinHits.add(new Hit(x, y, x + w, y + h, r));
    }

    private static float easeOutQuint(float t) {
        t = Mth.clamp(t, 0, 1);
        float u = 1 - t;
        return 1 - u * u * u * u * u;
    }

    private void renderSpin(GuiGraphics g, int mx, int my) {
        spinHits.clear();
        g.fill(0, 0, width, height, 0xB0000000);
        g.pose().pushPose();
        g.pose().translate(0, 0, 700);
        if (spinCur != null) renderSpinWheel(g, mx, my);
        else renderSpinSummary(g, mx, my);
        g.pose().popPose();
    }

    private void renderSpinWheel(GuiGraphics g, int mx, int my) {
        LuckySpin.Spin sp = spinCur;
        int pw = Math.min(330, width - 16), ph = 156;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        float t = (Util.getMillis() - spinStart) / (float) SPIN_MS;
        if (!UiConfig.animations()) t = 1f;
        float e = easeOutQuint(t);
        float f = e * sp.index();
        if (t >= 1f && !spinLanded) {
            spinLanded = true;
            switch (sp.tier()) {
                case BASIC -> Ui.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.8f);
                case GOOD -> Ui.play(SoundEvents.PLAYER_LEVELUP, 1.3f, 0.6f);
                default -> {
                    Ui.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.9f);
                    Ui.play(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0f, 0.8f);
                }
            }
        }
        int slot = Math.round(f);
        if (!spinLanded && slot != spinLastSlot) {
            spinLastSlot = slot;
            Ui.play(SoundEvents.UI_BUTTON_CLICK, 1.5f + (slot % 3) * 0.15f, 0.3f);
        }
        int tierCol = sp.tier().color;
        if (spinLanded && sp.tier().isPokemon()) {
            float p = 0.35f + 0.25f * Ui.pulse(3f);
            Gfx.glow(g, width / 2f, height / 2f, pw * 0.14f, pw * 0.7f, Ui.alpha(tierCol, p));
        }
        Ui.blit(g, Ui.PANEL, px, py, pw, ph);
        Ui.blit(g, Ui.HEADER, px, py, pw, 24);
        Ui.blit(g, Ui.ICON_SPIN, px + 8, py + 4, 16, 16);
        g.drawString(font, Component.literal("LUCKY SPIN").withStyle(ChatFormatting.BOLD), px + 28, py + 8, Theme.gold, true);
        int left = spinQueue.size();
        if (left > 0) {
            String more = "+" + left + " more";
            g.drawString(font, more, px + pw - 8 - font.width(more), py + 8, Theme.textMuted, false);
        }
        // the strip
        int sy0 = py + 32, sh = 44;
        Ui.blit(g, Ui.CARD, px + 8, sy0, pw - 16, sh);
        int centerX = px + pw / 2;
        g.enableScissor(px + 9, sy0 + 1, px + pw - 9, sy0 + sh - 1);
        List<LuckySpin.Entry> strip = sp.strip();
        for (int i = 0; i < strip.size(); i++) {
            float cx = centerX + (i - f) * SPIN_SLOT;
            if (cx < px - 20 || cx > px + pw + 20) continue;
            int sx = Math.round(cx) - 9, sy = sy0 + 12;
            LuckySpin.Entry en = strip.get(i);
            boolean isPrize = spinLanded && i == sp.index();
            float dist = Math.abs(i - f);
            int ca = isPrize ? 0xFF : (int) (0x70 + 0x8F * Math.max(0, 1 - dist / 3f));
            g.fill(sx - 1, sy + 20, sx + 19, sy + 23, Ui.alpha(en.tier().color, ca / 255f));
            Ui.blit(g, Ui.SLOT, sx, sy, 18, 18);
            g.renderItem(en.stack(), sx + 1, sy + 1);
            g.renderItemDecorations(font, en.stack(), sx + 1, sy + 1);
            if (isPrize) {
                int c = Ui.alpha(tierCol, 0.6f + 0.4f * Ui.pulse(5f));
                g.fill(sx - 2, sy - 2, sx + 20, sy - 1, c);
                g.fill(sx - 2, sy + 18, sx + 20, sy + 19, c);
                g.fill(sx - 2, sy - 2, sx - 1, sy + 19, c);
                g.fill(sx + 19, sy - 2, sx + 20, sy + 19, c);
            }
        }
        g.disableScissor();
        // pointer
        int pc = spinLanded ? tierCol : Theme.gold;
        for (int k = 0; k < 5; k++) {
            g.fill(centerX - k, sy0 + 1 + k, centerX + k + 1, sy0 + 2 + k, pc);
            g.fill(centerX - k, sy0 + sh - 2 - k, centerX + k + 1, sy0 + sh - 1 - k, pc);
        }
        // result
        int ry = sy0 + sh + 8;
        if (spinLanded) {
            int cw = Ui.chipWidth(font, sp.tier().label.toUpperCase());
            Ui.chip(g, font, centerX - cw / 2, ry, sp.tier().label.toUpperCase(), tierCol, sp.tier() == LuckySpin.Tier.BASIC ? 0xFF1B2029 : 0xFF1B2029);
            String name = font.plainSubstrByWidth(sp.name(), pw - 24);
            g.drawString(font, Component.literal(name).withStyle(ChatFormatting.BOLD), centerX - font.width(Component.literal(name).withStyle(ChatFormatting.BOLD)) / 2, ry + 15, Theme.text, true);
            if (sp.pokemon()) {
                String where = "It is in your party (or your PC if the party was full).";
                g.drawString(font, where, centerX - font.width(where) / 2, ry + 27, Theme.textDim, false);
            }
        } else {
            String s = "Spinning…";
            g.drawString(font, s, centerX - font.width(s) / 2, ry + 6, Theme.textMuted, false);
        }
        // buttons
        int by = py + ph - 24;
        if (!spinLanded) {
            spinButton(g, centerX - 36, by, 72, 18, "Skip", null, Ui.Style.NORMAL, mx, my, () -> spinStart = Util.getMillis() - SPIN_MS);
        } else if (!spinQueue.isEmpty()) {
            String next = "Next (" + spinQueue.size() + ")";
            int nw = font.width(next) + 26;
            spinButton(g, centerX - nw - 2, by, nw, 18, next, Ui.ICON_GIFT, Ui.Style.GOLD, mx, my, this::nextSpin);
            spinButton(g, centerX + 2, by, 72, 18, "Skip all", null, Ui.Style.NORMAL, mx, my, this::skipAllSpins);
        } else {
            spinButton(g, centerX - 40, by, 80, 18, "Nice!", Ui.ICON_CHECK, Ui.Style.GOLD, mx, my, this::closeSpins);
        }
    }

    private void renderSpinSummary(GuiGraphics g, int mx, int my) {
        int shown = Math.min(spinSummary.size(), 12);
        int pw = Math.min(330, width - 16), ph = 62 + shown * 11 + (spinSummary.size() > shown ? 11 : 0);
        int px = (width - pw) / 2, py = (height - ph) / 2;
        Ui.blit(g, Ui.PANEL, px, py, pw, ph);
        Ui.blit(g, Ui.HEADER, px, py, pw, 24);
        Ui.blit(g, Ui.ICON_SPIN, px + 8, py + 4, 16, 16);
        g.drawString(font, Component.literal("LUCKY SPIN — " + spinSummary.size() + " PRIZES").withStyle(ChatFormatting.BOLD), px + 28, py + 8, Theme.gold, true);
        int y = py + 30;
        for (int i = 0; i < shown; i++) {
            LuckySpin.Spin sp = spinSummary.get(i);
            var prize = sp.strip().isEmpty() ? null : sp.strip().get(Math.min(sp.index(), sp.strip().size() - 1));
            if (prize != null) {
                g.pose().pushPose();
                g.pose().translate(px + 10, y - 1, 0);
                g.pose().scale(0.6f, 0.6f, 1f);
                g.renderItem(prize.stack(), 0, 0);
                g.pose().popPose();
            }
            String line = font.plainSubstrByWidth(sp.name(), pw - 24 - 12 - 90);
            g.drawString(font, line, px + 24, y, Theme.text, false);
            String lab = sp.tier().label;
            g.drawString(font, lab, px + pw - 10 - font.width(lab), y, sp.tier().color, false);
            y += 11;
        }
        if (spinSummary.size() > shown) g.drawString(font, "…and " + (spinSummary.size() - shown) + " more (see chat)", px + 24, y, Theme.textMuted, false);
        spinButton(g, px + pw / 2 - 40, py + ph - 24, 80, 18, "Nice!", Ui.ICON_CHECK, Ui.Style.GOLD, mx, my, this::closeSpins);
    }

    // ---- input ------------------------------------------------------------

    /** For EMI: the item under the mouse, if any. */
    public ItemStack stackAt(double mx, double my) {
        for (SlotHit s : slots) if (s.in(mx, my)) return s.stack();
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (spinVisible()) {
            if (button == 0) {
                for (Hit h : spinHits) {
                    if (h.in(mx, my)) {
                        h.action().run();
                        Ui.play(SoundEvents.UI_BUTTON_CLICK, 1f, 0.25f);
                        break;
                    }
                }
            }
            return true;
        }
        if (button == 0 || button == 1) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.in(mx, my)) {
                    h.action().run();
                    Ui.play(SoundEvents.UI_BUTTON_CLICK, 1f, 0.25f);
                    return true;
                }
            }
        }
        if (selected != null) {
            int pw = panelW(), ph = height - 24;
            int px = (width - pw) / 2;
            if (mx < px || mx > px + pw || my < 12 || my > 12 + ph) selected = null;
            return true;
        }
        if (mx >= side && my >= TOP) {
            dragging = true;
            dragMoved = 0;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && selected == null) {
            dragMoved += Math.abs(dx) + Math.abs(dy);
            viewX -= (float) (dx / (UNIT * zoom));
            viewY -= (float) (dy / (UNIT * zoom));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (spinVisible()) return true;
        if (dragging) {
            dragging = false;
            if (dragMoved < 4 && hovered != null) open(hovered);
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (spinVisible()) return true;
        if (selected != null) {
            int max = Math.max(0, detailContentHeight - detailViewHeight);
            detailScroll = (int) Math.max(0, Math.min(max, detailScroll - sy * 16));
            return true;
        }
        if (mx < side) {
            int max = Math.max(0, sideContentHeight - (height - 34));
            sideScroll = (int) Math.max(0, Math.min(max, sideScroll - sy * 18));
            return true;
        }
        float old = zoom;
        zoom = (float) Math.max(0.25, Math.min(2.5, zoom * (sy > 0 ? 1.12 : 1 / 1.12)));
        if (old != zoom) {
            double qx = viewX + (mx - (side + (width - side) / 2.0)) / (UNIT * old);
            double qy = viewY + (my - (TOP + (height - TOP) / 2.0)) / (UNIT * old);
            viewX = (float) (qx - (mx - (side + (width - side) / 2.0)) / (UNIT * zoom));
            viewY = (float) (qy - (my - (TOP + (height - TOP) / 2.0)) / (UNIT * zoom));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (spinVisible()) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
                if (spinCur != null && !spinLanded) spinStart = Util.getMillis() - SPIN_MS;
                else if (spinCur != null && !spinQueue.isEmpty()) nextSpin();
                else closeSpins();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && selected != null) {
            selected = null;
            return true;
        }
        if (ClientInit.OPEN_QUESTS.matches(key, scan) && selected == null) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** For the automated screenshot test. */
    public void debugSkipAll() {
        if (spinVisible()) skipAllSpins();
    }

    public void debugSelect(long questId) {
        QuestFile f = ClientData.file;
        Quest q = f.quests.get(questId);
        if (q == null) return;
        selectChapter(f.chapterMap.get(q.chapter));
        open(q);
    }

    public void debugFocus(long questId) {
        QuestFile f = ClientData.file;
        Quest q = f.quests.get(questId);
        if (q == null) return;
        selectChapter(f.chapterMap.get(q.chapter));
        viewX = (float) q.x;
        viewY = (float) q.y;
        zoom = 1f;
    }

    public void debugView(double x, double y, float z) {
        viewX = (float) x;
        viewY = (float) y;
        zoom = z;
    }

    public void debugChapter(int index) {
        QuestFile f = ClientData.file;
        selectChapter(f.chapters.get(Math.min(index, f.chapters.size() - 1)));
    }
}
