package dev.unyielding.quests.client.edit;

import dev.unyielding.quests.client.ClientData;
import dev.unyielding.quests.client.ui.Theme;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.QuestEdit;
import dev.unyielding.quests.quest.QuestFile;
import dev.unyielding.quests.quest.QuestFile.ItemRef;
import dev.unyielding.quests.quest.QuestFile.Quest;
import dev.unyielding.quests.quest.QuestFile.Reward;
import dev.unyielding.quests.quest.QuestFile.RewardType;
import dev.unyielding.quests.quest.QuestFile.Task;
import dev.unyielding.quests.quest.QuestFile.TaskType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The "Edit quest" form: title, text, look, tasks and rewards. */
public class EditQuestScreen extends FormScreen {
    private final Quest draft;
    private String descText;
    private String iconId;
    private final List<String[]> taskKinds = new ArrayList<>();

    private static final String[] SHAPES = {"circle", "square", "rsquare", "diamond", "pentagon", "hexagon", "octagon", "gear", "star", "heart"};
    private static final String[] SIZES = {"0.75", "1", "1.25", "1.5", "2", "2.5"};
    private static final String[] TASK_TYPES = {"Check box", "Item", "Pokémon", "Kill mobs", "Advancement", "Structure", "Biome", "Dimension"};
    private static final String[] ACTIONS = {"catch", "defeat", "evolve", "level_up_to", "scan", "fishing", "revive_fossil", "have_registered", "select_starter"};
    private static final String[] DEX = {"-", "caught", "seen", "caught_percent", "seen_percent"};
    private static final String[] REWARD_TYPES = {"Item", "CobbleDollars", "XP", "XP levels", "Command", "Loot table"};
    private static final Pattern CD = Pattern.compile("^/?cobbledollars give \\{p} (\\d+)$");

    public EditQuestScreen(Screen parent, Quest q) {
        super(parent, "Edit quest");
        draft = QuestEdit.copy(q);
        descText = String.join("\n", draft.desc);
        iconId = draft.icon == null ? "" : draft.icon.id;
    }

    @Override
    protected int panelWidth() {
        return Math.min(420, width - 16);
    }

    // ---- model helpers --------------------------------------------------------

    private static String taskKind(Task t) {
        return switch (t.type) {
            case ITEM -> "Item";
            case COBBLEMON -> "Pokémon";
            case KILL -> "Kill mobs";
            case ADVANCEMENT -> "Advancement";
            case STRUCTURE -> "Structure";
            case BIOME -> "Biome";
            case DIMENSION -> "Dimension";
            default -> "Check box";
        };
    }

    private static void setTaskKind(Task t, String kind) {
        t.item = null;
        t.target = "";
        t.extra = "";
        t.pokemonType = "";
        t.dexProgress = "";
        t.amount = 1;
        t.consume = false;
        t.shiny = false;
        switch (kind) {
            case "Item" -> {
                t.type = TaskType.ITEM;
                t.rawType = "item";
                t.item = ItemRef.of(heldItemId().isEmpty() ? "minecraft:apple" : heldItemId());
            }
            case "Pokémon" -> {
                t.type = TaskType.COBBLEMON;
                t.rawType = "unyieldingquests:cobblemon";
                t.target = "catch";
            }
            case "Kill mobs" -> {
                t.type = TaskType.KILL;
                t.rawType = "kill";
                t.target = "minecraft:zombie";
                t.amount = 10;
            }
            case "Advancement" -> {
                t.type = TaskType.ADVANCEMENT;
                t.rawType = "advancement";
            }
            case "Structure" -> {
                t.type = TaskType.STRUCTURE;
                t.rawType = "structure";
            }
            case "Biome" -> {
                t.type = TaskType.BIOME;
                t.rawType = "biome";
            }
            case "Dimension" -> {
                t.type = TaskType.DIMENSION;
                t.rawType = "dimension";
                t.target = "minecraft:the_nether";
            }
            default -> {
                t.type = TaskType.CHECKMARK;
                t.rawType = "checkmark";
            }
        }
    }

    private static String rewardKind(Reward r) {
        return switch (r.type) {
            case ITEM -> "Item";
            case XP -> "XP";
            case XP_LEVELS -> "XP levels";
            case COMMAND -> CD.matcher(r.command.trim()).matches() ? "CobbleDollars" : "Command";
            case LOOT -> "Loot table";
            default -> "Command";
        };
    }

    private static void setRewardKind(Reward r, String kind) {
        r.item = null;
        r.icon = null;
        r.command = "";
        r.title = "";
        r.key = "";
        r.amount = 1;
        switch (kind) {
            case "Item" -> {
                r.type = RewardType.ITEM;
                r.rawType = "item";
                r.item = ItemRef.of(heldItemId().isEmpty() ? "minecraft:diamond" : heldItemId());
            }
            case "XP" -> {
                r.type = RewardType.XP;
                r.rawType = "xp";
                r.amount = 100;
            }
            case "XP levels" -> {
                r.type = RewardType.XP_LEVELS;
                r.rawType = "xp_levels";
                r.amount = 1;
            }
            case "CobbleDollars" -> {
                r.type = RewardType.COMMAND;
                r.rawType = "command";
                r.console = true;
                setCobbleDollars(r, 25);
            }
            case "Loot table" -> {
                r.type = RewardType.LOOT;
                r.rawType = "loot";
                QuestFile f = ClientData.file;
                r.amount = f != null && !f.lootTables.isEmpty() ? f.lootTables.keySet().iterator().next() : 0;
            }
            default -> {
                r.type = RewardType.COMMAND;
                r.rawType = "command";
                r.console = true;
                r.command = "/give {p} minecraft:apple";
                r.title = "Special reward";
            }
        }
    }

    private static void setCobbleDollars(Reward r, long n) {
        r.amount = n;
        r.command = "/cobbledollars give {p} " + n;
        r.title = n + " CobbleDollars";
        r.icon = ItemRef.of(n >= 500 ? "cobblemon:relic_coin_sack" : n >= 100 ? "cobblemon:relic_coin_pouch" : "cobblemon:relic_coin");
        if (!r.icon.exists()) r.icon = ItemRef.of("minecraft:gold_nugget");
    }

    private static long cobbleDollars(Reward r) {
        Matcher m = CD.matcher(r.command.trim());
        return m.matches() ? Long.parseLong(m.group(1)) : 0;
    }

    // ---- form ------------------------------------------------------------------

    @Override
    protected void build() {
        int w = innerWidth();
        heading("Text");
        int x = labelled("Title");
        field("title", x, right() - x, draft.title, "Quest name (use & for colours, e.g. &6Gold)", s -> draft.title = s);
        gap(24);
        x = labelled("Subtitle");
        field("sub", x, right() - x, draft.subtitle.isEmpty() ? "" : draft.subtitle.get(0), "One short line under the name", s -> {
            draft.subtitle.clear();
            if (!s.isBlank()) draft.subtitle.add(s);
        });
        gap(24);
        x = labelled("Description");
        field("desc", x, cy, right() - x, descText, "Longer text. Use \\n for a new line", 2000, s -> {
            descText = s;
            draft.desc.clear();
            if (!s.isBlank()) for (String line : s.split("\\\\n")) draft.desc.add(line);
        });
        gap(26);

        heading("Look");
        x = labelled("Icon");
        field("icon", x, right() - x - 70, iconId, "Item id, empty = automatic (first task's item)", s -> {
            iconId = s.trim();
            draft.icon = iconId.isEmpty() ? null : ItemRef.of(iconId);
        });
        button(right() - 64, 64, "Held item", null, Ui.Style.NORMAL, () -> {
            iconId = heldItemId();
            draft.icon = iconId.isEmpty() ? null : ItemRef.of(iconId);
            relayout();
        });
        gap(24);
        x = labelled("Shape");
        cycle(x, 110, "Shape: ", SHAPES, draft.shape.isEmpty() ? "circle" : draft.shape, s -> draft.shape = s);
        cycle(x + 116, 80, "Size: ", SIZES, trimNum(draft.size), s -> draft.size = parseDouble(s, 1));
        gap(24);
        toggle(left(), 150, "Optional", draft.optional, b -> draft.optional = b);
        toggle(left() + 156, 180, "Hidden until unlocked", draft.hideUntilDeps, b -> draft.hideUntilDeps = b);
        gap(24);
        toggle(left(), 150, "Hide lines to it", draft.hideDepLines, b -> draft.hideDepLines = b);
        gap(26);

        heading("Tasks  (what the player has to do)");
        for (int i = 0; i < draft.tasks.size(); i++) taskRow(i, w);
        button(left(), 110, "+ Add task", null, Ui.Style.ACCENT, () -> {
            Task t = new Task();
            t.id = QuestEdit.newId();
            t.quest = draft.id;
            setTaskKind(t, "Check box");
            draft.tasks.add(t);
            relayout();
        });
        gap(28);

        heading("Rewards");
        for (int i = 0; i < draft.rewards.size(); i++) rewardRow(i, w);
        button(left(), 110, "+ Add reward", null, Ui.Style.ACCENT, () -> {
            Reward r = new Reward();
            r.id = QuestEdit.newId();
            r.quest = draft.id;
            setRewardKind(r, "Item");
            draft.rewards.add(r);
            relayout();
        });
        gap(28);
        note("Every quest also gives a Lucky Spin when it is claimed (if enabled in the config).");
        gap(4);
    }

    private static String trimNum(double v) {
        String s = String.valueOf(v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private void taskRow(int i, int w) {
        Task t = draft.tasks.get(i);
        int rows = switch (t.type) {
            case ITEM, KILL, COBBLEMON -> 2;
            default -> 1;
        };
        if (t.type == TaskType.COBBLEMON) rows = 3;
        card(rows * 24 + 6);
        int x0 = left() + 6;
        cy += 3;
        cycle(x0, 96, "", TASK_TYPES, taskKind(t), k -> setTaskKind(t, k));
        int fx = x0 + 100;
        int fw = right() - 6 - 24 - fx;
        switch (t.type) {
            case CHECKMARK -> field("t" + i + "lbl", fx, fw, t.title, "What to do (shown on the task)", s -> t.title = s);
            case ITEM -> field("t" + i + "item", fx, fw, t.item == null ? "" : t.item.id, "item id, e.g. minecraft:diamond", s -> {
                if (t.item == null) t.item = new ItemRef();
                t.item.id = s.trim();
            });
            case KILL -> field("t" + i + "ent", fx, fw, t.target, "entity id, e.g. minecraft:zombie", s -> t.target = s.trim());
            case COBBLEMON -> cycle(fx, 110, "Do: ", ACTIONS, t.target.isEmpty() ? "catch" : t.target, s -> t.target = s);
            case ADVANCEMENT -> field("t" + i + "adv", fx, fw, t.target, "advancement id, e.g. minecraft:story/enter_the_nether", s -> t.target = s.trim());
            case STRUCTURE -> field("t" + i + "str", fx, fw, t.target, "structure id, e.g. minecraft:village or #minecraft:village", s -> t.target = s.trim());
            case BIOME -> field("t" + i + "bio", fx, fw, t.target, "biome id, e.g. minecraft:cherry_grove", s -> t.target = s.trim());
            case DIMENSION -> field("t" + i + "dim", fx, fw, t.target, "dimension id, e.g. minecraft:the_end", s -> t.target = s.trim());
            default -> note("(this task type cannot be edited here)");
        }
        if (t.type == TaskType.COBBLEMON) {
            field("t" + i + "amt", fx + 114, fw - 114, String.valueOf(t.amount), "amount / level", s -> t.amount = Math.max(1, parseInt(s, 1)));
        }
        button(right() - 6 - 20, 20, "✘", null, Ui.Style.NORMAL, () -> {
            draft.tasks.remove(t);
            relayout();
        });
        if (t.type == TaskType.ITEM) {
            cy += 24;
            label("Count", fx, cy + 6);
            field("t" + i + "cnt", fx + 32, 50, String.valueOf(t.amount), "1", s -> t.amount = Math.max(1, parseInt(s, 1)));
            toggle(fx + 86, 86, "Hand in", t.consume, b -> t.consume = b);
            button(fx + 176, 64, "Held item", null, Ui.Style.NORMAL, () -> {
                if (t.item == null) t.item = new ItemRef();
                String h = heldItemId();
                if (!h.isEmpty()) t.item.id = h;
                relayout();
            });
        } else if (t.type == TaskType.KILL) {
            cy += 24;
            label("Count", fx, cy + 6);
            field("t" + i + "cnt", fx + 32, 50, String.valueOf(t.amount), "10", s -> t.amount = Math.max(1, parseInt(s, 1)));
        } else if (t.type == TaskType.COBBLEMON) {
            cy += 24;
            label("Pokémon", fx, cy + 6);
            field("t" + i + "pk", fx + 44, fw - 44, t.extra, "species, e.g. pikachu (empty = any)", s -> t.extra = s.trim());
            cy += 24;
            label("Type", fx, cy + 6);
            field("t" + i + "ty", fx + 44, 70, t.pokemonType, "fire…", s -> t.pokemonType = s.trim());
            toggle(fx + 118, 58, "Shiny", t.shiny, b -> t.shiny = b);
            cycle(fx + 180, fw - 180, "Dex: ", DEX, t.dexProgress.isEmpty() ? "-" : t.dexProgress, s -> t.dexProgress = s.equals("-") ? "" : s);
        }
        cy += 27;
    }

    private void rewardRow(int i, int w) {
        Reward r = draft.rewards.get(i);
        String kind = rewardKind(r);
        int rows = kind.equals("Command") || kind.equals("Item") ? 2 : 1;
        card(rows * 24 + 6);
        int x0 = left() + 6;
        cy += 3;
        cycle(x0, 96, "", REWARD_TYPES, kind, k -> setRewardKind(r, k));
        int fx = x0 + 100;
        int fw = right() - 6 - 24 - fx;
        switch (kind) {
            case "Item" -> field("r" + i + "item", fx, fw, r.item == null ? "" : r.item.id, "item id", s -> {
                if (r.item == null) r.item = new ItemRef();
                r.item.id = s.trim();
            });
            case "XP" -> field("r" + i + "xp", fx, 80, String.valueOf(r.amount), "points", s -> r.amount = Math.max(1, parseInt(s, 100)));
            case "XP levels" -> field("r" + i + "xpl", fx, 80, String.valueOf(r.amount), "levels", s -> r.amount = Math.max(1, parseInt(s, 1)));
            case "CobbleDollars" -> field("r" + i + "cd", fx, 80, String.valueOf(cobbleDollars(r)), "amount", s -> setCobbleDollars(r, Math.max(1, parseInt(s, 25))));
            case "Loot table" -> {
                QuestFile f = ClientData.file;
                List<String> names = new ArrayList<>();
                List<Long> ids = new ArrayList<>();
                if (f != null) for (QuestFile.LootTable lt : f.lootTables.values()) {
                    ids.add(lt.id);
                    names.add(lt.title.isEmpty() ? QuestFile.idToString(lt.id) : lt.title);
                }
                if (names.isEmpty()) note("(no reward tables in this book)");
                else {
                    int cur = Math.max(0, ids.indexOf(r.amount));
                    cycle(fx, fw, "", names.toArray(new String[0]), names.get(cur), s -> r.amount = ids.get(names.indexOf(s)));
                }
            }
            default -> field("r" + i + "cmd", fx, fw, r.command, "/command, {p} = player name", s -> r.command = s);
        }
        button(right() - 6 - 20, 20, "✘", null, Ui.Style.NORMAL, () -> {
            draft.rewards.remove(r);
            relayout();
        });
        if (kind.equals("Item")) {
            cy += 24;
            label("Count", fx, cy + 6);
            field("r" + i + "cnt", fx + 32, 50, String.valueOf(r.amount), "1", s -> r.amount = Math.max(1, parseInt(s, 1)));
            button(fx + 86, 64, "Held item", null, Ui.Style.NORMAL, () -> {
                if (r.item == null) r.item = new ItemRef();
                String h = heldItemId();
                if (!h.isEmpty()) r.item.id = h;
                relayout();
            });
        } else if (kind.equals("Command")) {
            cy += 24;
            label("Shown as", fx, cy + 6);
            field("r" + i + "ttl", fx + 48, fw - 48 - 90, r.title, "label in the book", s -> r.title = s);
            toggle(fx + fw - 86, 86, "As console", r.console, b -> r.console = b);
        }
        cy += 27;
    }

    @Override
    protected void renderExtra(GuiGraphics g, int mx, int my, float pt) {
        // small item previews next to item fields
        for (var w : renderables) {
            if (!(w instanceof net.minecraft.client.gui.components.EditBox b)) continue;
            String hint = b.getMessage().getString();
            if (!(hint.startsWith("item id") || hint.startsWith("Item id"))) continue;
            if (b.getY() < top || b.getY() > bottom - 12) continue;
            String id = b.getValue().trim();
            if (id.isEmpty()) continue;
            ItemRef ref = ItemRef.of(id);
            int x = b.getX() + b.getWidth() - 14, y = b.getY() - 4;
            if (ref.exists()) {
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale(0.9f, 0.9f, 1f);
                g.renderItem(ref.stack(), 0, 0);
                g.pose().popPose();
            } else {
                g.drawString(font, "?", x + 6, y + 4, Theme.bad, false);
            }
        }
    }

    @Override
    protected void save() {
        if (draft.tasks.isEmpty()) {
            Task t = new Task();
            t.id = QuestEdit.newId();
            t.quest = draft.id;
            setTaskKind(t, "Check box");
            draft.tasks.add(t);
        }
        Net.sendToServer(new Net.EditRequest(QuestEdit.questSet(draft)));
        minecraft.setScreen(parent);
    }
}
