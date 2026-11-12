package dev.unyielding.quests.client;

import com.google.gson.*;
import dev.unyielding.quests.client.edit.FormScreen;
import dev.unyielding.quests.client.ui.SpriteButton;
import dev.unyielding.quests.client.ui.Theme;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.settings.PokemonEditor;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** Administrator browser for the server's loaded species and spawn data. */
public final class PokemonEditorScreen extends FormScreen {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private CompoundTag data;
    private String search, message;
    private long searchAt;
    private boolean queuedSearch;

    public PokemonEditorScreen(Screen parent, CompoundTag data) {
        super(parent, "Pokemon editor");
        this.data = data.copy(); search = data.getString("query"); message = data.getString("message");
    }
    public static void requestOpen() {
        if (!AdminSettingsScreen.canManage()) return;
        Minecraft mc = Minecraft.getInstance(); CompoundTag loading = new CompoundTag();
        loading.putString("view", "search"); loading.putString("message", "Loading Pokemon from server...");
        mc.setScreen(new PokemonEditorScreen(mc.screen, loading)); send("search", "", 0, "");
    }
    private static void send(String operation, String id, long revision, String json) {
        if (AdminSettingsScreen.canManage()) PokemonEditor.send(new PokemonEditor.Action(operation, id, revision, json));
    }
    public static void receive(PokemonEditor.Sync sync) {
        sync = PokemonTransfer.accept(sync); if (sync == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (!AdminSettingsScreen.canManage()) return;
        CompoundTag data = sync.data();
        if (mc.screen instanceof DraftScreen draft) { draft.receive(data); return; }
        if (mc.screen instanceof JsonScreen json) { json.receive(data); return; }
        if (data.getBoolean("error")) {
            if (mc.screen instanceof PokemonEditorScreen list) { list.message = data.getString("message"); list.relayout(); }
            else if (mc.screen instanceof SpeciesScreen species) species.receive(data);
            else if (mc.screen instanceof FieldListScreen fields) fields.owner.receive(data);
            else if (mc.screen instanceof SpawnsScreen spawns) spawns.owner.receive(data);
            return;
        }
        if (data.getString("view").equals("species")) {
            if (mc.screen instanceof PokemonEditorScreen list) mc.setScreen(new SpeciesScreen(list, data));
            else if (mc.screen instanceof SpeciesScreen species && species.id().equals(data.getString("id"))) species.receive(data);
            else if (mc.screen instanceof FieldListScreen fields) fields.owner.receive(data);
            else if (mc.screen instanceof SpawnsScreen spawns) spawns.owner.receive(data);
        } else if (data.getString("view").equals("search")) {
            if (mc.screen instanceof PokemonEditorScreen list) {
                if (data.getString("query").equals(list.search.trim().toLowerCase(Locale.ROOT))) {
                    list.data = data.copy(); list.message = data.getString("message"); list.relayout();
                }
            } else if (sync.open()) mc.setScreen(new PokemonEditorScreen(mc.screen, data));
        }
    }
    @Override protected int panelWidth() { return Math.min(520, width - 16); }
    @Override protected int panelHeight() { return 450; }
    @Override protected void build() {
        wrap("Search all species loaded by this server, including other installed addons.");
        gap(5);
        field("pokemon-search", left(), cy, innerWidth(), search, "Search Pokemon name or identifier", 120, text -> {
            search = text; searchAt = Util.getMillis(); queuedSearch = true;
        });
        gap(27);
        int half = (innerWidth() - 4) / 2;
        int page = data.getInt("page"), count = data.getList("results", Tag.TAG_COMPOUND).size();
        int pageSize = data.contains("page_size") ? data.getInt("page_size") : 30;
        button(left(), half, "Previous page", null, Ui.Style.NORMAL, () -> search(Math.max(0, page - 1))).active = page > 0;
        button(left() + half + 4, half, "Next page", null, Ui.Style.NORMAL, () -> search(page + 1)).active = (page + 1) * pageSize < data.getInt("total") && count > 0;
        gap(27);
        note(data.getInt("total") + " matching species · page " + (page + 1));
        gap(5);
        for (Tag tag : data.getList("results", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag)tag;
            String name = row.getString("name"), id = row.getString("id");
            SpriteButton b = button(left(), innerWidth(), font.plainSubstrByWidth(name + "  (" + id + ")", innerWidth() - 16), null, Ui.Style.NORMAL,
                    () -> send("get", id, data.getLong("revision"), row.contains("formIndex") ? Integer.toString(row.getInt("formIndex")) : ""));
            tip(b, name + " · " + id); gap(24);
        }
        if (count == 0) wrap("No matching Pokemon. Try part of its name or its namespace.");
        gap(8); wrap(message);
    }
    private void wrap(String text) { while (!text.isEmpty()) { String s = font.plainSubstrByWidth(text, innerWidth()); if (s.isEmpty()) break; note(s); text = text.substring(s.length()); } }
    private void search(int page) { queuedSearch = false; scroll = 0; send("search", search, page, ""); }
    @Override public void tick() { if (queuedSearch && Util.getMillis() - searchAt >= 300) search(0); }
    @Override protected String saveLabel() { return "Done"; }
    @Override protected void save() { cancel(); }
    @Override public void onClose() { cancel(); }

    private abstract static class BaseForm extends FormScreen {
        BaseForm(Screen parent, String title) { super(parent, title); }
        @Override protected int panelWidth() { return Math.min(520, width - 16); }
        @Override protected int panelHeight() { return 450; }
        protected void paragraph(String text) {
            while (text != null && !text.isEmpty()) { String s = font.plainSubstrByWidth(text, innerWidth()); if (s.isEmpty()) break; note(s); text = text.substring(s.length()); }
        }
        protected void row(String text, Runnable action) { button(left(), innerWidth(), text, null, Ui.Style.NORMAL, action); gap(25); }
    }

    public static final class SpeciesScreen extends BaseForm {
        private final PokemonEditorScreen list;
        private final int formIndex;
        private CompoundTag data;
        public SpeciesScreen(PokemonEditorScreen list, CompoundTag data) { super(list, "Edit " + data.getString("name")); this.list = list; this.data = data.copy(); this.formIndex = data.contains("formIndex") ? data.getInt("formIndex") : -1; }
        String id() { return data.getString("id"); }
        JsonObject species() { return JsonParser.parseString(data.getString("species_json")).getAsJsonObject(); }
        JsonArray spawns() { return JsonParser.parseString(data.getString("spawns_json")).getAsJsonArray(); }
        long revision() { return data.getLong("revision"); }
        JsonObject statTarget(JsonObject document) {
            return formIndex < 0 ? document : document.getAsJsonArray("forms").get(formIndex).getAsJsonObject();
        }
        void receive(CompoundTag next) {
            if (next.getBoolean("error")) { data.putString("message", next.getString("message")); data.putBoolean("error", true); }
            else data = next.copy();
            if (minecraft.screen != this) minecraft.setScreen(this); else relayout();
        }
        @Override protected void build() {
            paragraph(id());
            if (formIndex >= 0) {
                JsonObject form = statTarget(species());
                paragraph(form.get("name").getAsString() + " form");
                row("Form stats", () -> minecraft.setScreen(new StatsScreen(this)));
                return;
            }
            paragraph("Changes belong to this server. Save explicitly to apply an override."); gap(6);
            row("Base stats", () -> minecraft.setScreen(new StatsScreen(this)));
            row("Abilities and types", () -> minecraft.setScreen(new AbilitiesScreen(this)));
            row("Evolution conditions", () -> editField("evolutions", "Evolution conditions", "Edit the evolution rules and their requirements. An empty array removes evolutions."));
            row("Spawns and weights", () -> minecraft.setScreen(new SpawnsScreen(this)));
            row("Search all species fields", () -> minecraft.setScreen(new FieldListScreen(this)));
            row("Full species JSON", () -> minecraft.setScreen(new JsonScreen(this, "Full species data", data.getString("species_json"),
                    "All data-driven species properties. Models, textures, sounds and programmed ability mechanics require their normal assets or code.", false, value -> value)));
            gap(4);
            paragraph(data.getBoolean("overridden") ? "Species override: saved" : "Species: using loaded pack defaults");
            paragraph(data.getBoolean("spawn_overridden") ? "Spawn override: saved" : "Spawns: using loaded pack defaults");
            row("Reset species to pack defaults", () -> reset("reset_species", "Reset this species override?"));
            row("Reset spawns to pack defaults", () -> reset("reset_spawns", "Reset spawn overrides?"));
            gap(5); paragraph(data.getString("message"));
        }
        private void editField(String field, String title, String help) {
            JsonObject original = species(); JsonElement value = original.get(field);
            if (value == null) value = new JsonArray();
            minecraft.setScreen(new JsonScreen(this, title, JSON.toJson(value), help, false, edited -> {
                JsonObject next = original.deepCopy(); next.add(field, JsonParser.parseString(edited)); return JSON.toJson(next);
            }));
        }
        private void reset(String operation, String title) {
            String description = operation.equals("reset_spawns")
                    ? "Restores this species' spawn rules from pack defaults. Other species' edits and shared file settings are retained."
                    : "This removes the saved override and restores the currently loaded pack data.";
            minecraft.setScreen(new ConfirmScreen(confirmed -> { minecraft.setScreen(this); if (confirmed) send(operation, id(), revision(), ""); },
                    Component.literal(title), Component.literal(description)));
        }
        @Override protected String saveLabel() { return "Done"; }
        @Override protected void save() { cancel(); }
        @Override public void onClose() { cancel(); }
    }

    private abstract static class DraftScreen extends BaseForm {
        final SpeciesScreen owner;
        final JsonObject original;
        final long revision;
        boolean pending;
        long pendingAt;
        String message = "Save applies this draft on the server.";
        DraftScreen(SpeciesScreen owner, String title) { super(owner, title); this.owner = owner; original = owner.species(); revision = owner.revision(); }
        abstract JsonObject draft();
        @Override protected String saveLabel() { return pending ? "Saving..." : "Save"; }
        @Override protected void save() {
            if (pending) return;
            if (!AdminSettingsScreen.canManage()) { message = "Administrator permission is required."; relayout(); return; }
            try { JsonObject next = draft(); pending = true; pendingAt = Util.getMillis(); message = "Saving on server..."; relayout(); send("save_species", owner.id(), revision, JSON.toJson(next)); }
            catch (RuntimeException e) { message = Objects.toString(e.getMessage(), "Check the draft values."); relayout(); }
        }
        void receive(CompoundTag data) {
            if (!data.getString("id").equals(owner.id())) return;
            pending = false;
            if (!data.getBoolean("error") && data.getString("message").startsWith("Saved")) owner.receive(data);
            else { message = data.getString("message"); relayout(); }
        }
        @Override public void tick() { if (pending && Util.getMillis() - pendingAt > 15000) { pending = false; message = "No reply yet. Reopen this Pokemon to check server values before retrying."; relayout(); } }
        @Override protected void cancel() {
            if (pending) return;
            boolean changed;
            try { changed = !draft().equals(original); } catch (RuntimeException e) { changed = true; }
            if (changed) minecraft.setScreen(new ConfirmScreen(discard -> minecraft.setScreen(discard ? owner : this),
                    Component.literal("Discard draft?"), Component.literal("Your unsaved changes will be lost.")));
            else minecraft.setScreen(owner);
        }
        @Override public void onClose() { cancel(); }
    }

    public static final class StatsScreen extends DraftScreen {
        private static final String[] KEYS = {"hp", "attack", "defence", "special_attack", "special_defence", "speed"};
        private static final String[] LABELS = {"HP", "Attack", "Defence", "Sp. attack", "Sp. defence", "Speed"};
        private final String[] values = new String[6];
        public StatsScreen(SpeciesScreen owner) {
            super(owner, "Base stats"); JsonElement element = owner.statTarget(original).get("baseStats");
            JsonObject stats = element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
            for (int i = 0; i < KEYS.length; i++) values[i] = stats != null && stats.has(KEYS[i]) && stats.get(KEYS[i]).isJsonPrimitive() ? stats.get(KEYS[i]).getAsString() : original.getAsJsonObject("baseStats").get(KEYS[i]).getAsString();
        }
        @Override protected void build() {
            paragraph("Species base stats, before a Pokemon's level, IVs, EVs and nature are applied."); gap(8);
            for (int i = 0; i < KEYS.length; i++) {
                int index = i; label(LABELS[i], left(), cy + 6);
                field("stat-" + KEYS[i], left() + 82, cy, innerWidth() - 82, values[i], "Whole number", 8, text -> values[index] = text).active = !pending; gap(26);
            }
            gap(6); paragraph(message);
        }
        @Override JsonObject draft() {
            JsonObject next = original.deepCopy(), target = owner.statTarget(next), stats = target.has("baseStats") && target.get("baseStats").isJsonObject() ? target.getAsJsonObject("baseStats").deepCopy() : new JsonObject();
            for (int i = 0; i < KEYS.length; i++) {
                int value; try { value = Integer.parseInt(values[i].trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException(LABELS[i] + " must be a whole number."); }
                if (value < 1 || value > 10000) throw new IllegalArgumentException(LABELS[i] + " must be between 1 and 10000."); stats.addProperty(KEYS[i], value);
            }
            target.add("baseStats", stats); return next;
        }
    }

    public static final class AbilitiesScreen extends DraftScreen {
        private String abilities, primary, secondary;
        private final boolean simpleAbilities;
        public AbilitiesScreen(SpeciesScreen owner) {
            super(owner, "Abilities and types");
            JsonElement pool = original.get("abilities");
            simpleAbilities = pool != null && pool.isJsonArray() && java.util.stream.StreamSupport.stream(pool.getAsJsonArray().spliterator(), false)
                    .allMatch(entry -> entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString());
            List<String> entries = new ArrayList<>(); if (simpleAbilities) for (JsonElement a : pool.getAsJsonArray()) entries.add(a.getAsString());
            abilities = String.join(", ", entries); primary = string(original, "primaryType"); secondary = string(original, "secondaryType");
        }
        @Override protected void build() {
            if (simpleAbilities) {
                paragraph("Ability identifiers separated by commas. Prefix a hidden ability with h:."); gap(4);
                field("abilities", left(), cy, innerWidth(), abilities, "ability, h:hiddenability", 4096, text -> abilities = text).active = !pending; gap(28);
            } else paragraph("This species uses structured ability rules. The JSON editor preserves their priorities and other options.");
            row("Edit abilities JSON", this::editAbilitiesJson);
            paragraph("Primary type"); field("primary-type", left(), innerWidth(), primary, "Type identifier", text -> primary = text).active = !pending; gap(28);
            paragraph("Secondary type (leave empty for none)"); field("secondary-type", left(), innerWidth(), secondary, "Optional type identifier", text -> secondary = text).active = !pending; gap(28);
            paragraph("This changes the species' ability choices. Individual Pokemon keep their saved ability until changed through the game's Pokemon controls."); gap(6); paragraph(message);
        }
        @Override JsonObject draft() {
            JsonObject next = original.deepCopy();
            if (simpleAbilities) {
                JsonArray entries = new JsonArray();
                for (String value : abilities.split(",")) { value = value.trim().toLowerCase(Locale.ROOT); if (!value.isEmpty()) entries.add(value); }
                if (entries.isEmpty()) throw new IllegalArgumentException("Enter at least one ability.");
                next.add("abilities", entries);
            }
            if (primary.trim().isEmpty()) throw new IllegalArgumentException("Enter a primary type.");
            next.addProperty("primaryType", primary.trim().toLowerCase(Locale.ROOT));
            if (secondary.trim().isEmpty()) next.remove("secondaryType"); else next.addProperty("secondaryType", secondary.trim().toLowerCase(Locale.ROOT)); return next;
        }
        private void editAbilitiesJson() {
            if (pending) return;
            try {
                JsonObject next = draft(); JsonElement pool = next.get("abilities");
                minecraft.setScreen(new JsonScreen(owner, "Ability rules", JSON.toJson(pool == null ? new JsonArray() : pool),
                        "Edit the full ability array. This supports ordinary and hidden ability identifiers plus structured rules supplied by installed addons. Existing type edits stay in this draft.", false, edited -> {
                            JsonElement value = JsonParser.parseString(edited);
                            if (!value.isJsonArray()) throw new IllegalArgumentException("Abilities must be a JSON array.");
                            JsonObject saved = next.deepCopy(); saved.add("abilities", value); return JSON.toJson(saved);
                        }));
            } catch (RuntimeException error) { message = Objects.toString(error.getMessage(), "Check your values."); relayout(); }
        }
    }

    public static final class FieldListScreen extends BaseForm {
        final SpeciesScreen owner;
        private String search = "";
        FieldListScreen(SpeciesScreen owner) { super(owner, "All species fields"); this.owner = owner; }
        @Override protected void build() {
            paragraph("Search data fields such as moves, forms, behaviour, drops, catchRate or experienceGroup."); gap(5);
            field("field-search", left(), innerWidth(), search, "Search fields", text -> { search = text; scroll = 0; relayout(); }); gap(28);
            for (String field : new TreeSet<>(owner.species().keySet())) if (field.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) {
                row(field, () -> owner.editField(field, field, "Edit this species data field as JSON. Strings require quotes. Objects and arrays keep their normal format."));
            }
            row("Add or edit any field in full JSON", () -> minecraft.setScreen(new JsonScreen(owner, "Full species data", owner.data.getString("species_json"),
                    "All species fields, including fields absent from the current file. Preserve valid Cobblemon data structure.", false, value -> value)));
        }
        @Override protected String saveLabel() { return "Done"; }
        @Override protected void save() { cancel(); }
        @Override public void onClose() { cancel(); }
    }

    public static final class SpawnsScreen extends BaseForm {
        final SpeciesScreen owner;
        SpawnsScreen(SpeciesScreen owner) { super(owner, "Spawns and weights"); this.owner = owner; }
        @Override protected void build() {
            paragraph("Weights are relative to eligible spawns in the same bucket. A weight is not a guaranteed percentage.");
            paragraph("Edit complete conditions and spawn settings in JSON, or choose a rule for its weight, level and bucket."); gap(8);
            JsonArray files = owner.spawns(); int count = 0;
            for (int f = 0; f < files.size(); f++) {
                JsonObject file = files.get(f).getAsJsonObject(), body = file.getAsJsonObject("data");
                if (body == null || !body.has("spawns")) continue;
                JsonArray rules = body.getAsJsonArray("spawns");
                for (int r = 0; r < rules.size(); r++) {
                    JsonObject rule = rules.get(r).getAsJsonObject(); String pokemon = string(rule, "pokemon");
                    if (!matchesSpecies(pokemon, owner.id())) continue;
                    int fi = f, ri = r; count++;
                    row(font.plainSubstrByWidth(string(rule, "id") + " · " + string(rule, "weight"), innerWidth() - 12),
                            () -> minecraft.setScreen(new SpawnRuleScreen(owner, fi, ri)));
                }
            }
            if (count == 0) paragraph("No spawn rules currently target this species. It may be command-only or evolve from another Pokemon.");
            row("Edit all spawn files and conditions", () -> minecraft.setScreen(new JsonScreen(owner, "Spawn files and conditions", JSON.toJson(files),
                    "Array of {id, data} files. Each data object contains enabled, neededInstalledMods, neededUninstalledMods, and spawns. Changes to a shared file affect all its rules.", true, value -> value)));
            row("Create a spawn rule", () -> {
                JsonArray next = files.deepCopy(); JsonObject file = new JsonObject(), body = new JsonObject(), rule = new JsonObject();
                String key = owner.id().substring(owner.id().indexOf(':') + 1);
                String fileId = "unyieldingquests:editor/" + owner.id().replace(':', '/');
                file.addProperty("id", fileId); body.addProperty("enabled", true);
                body.add("neededInstalledMods", new JsonArray()); body.add("neededUninstalledMods", new JsonArray());
                rule.addProperty("id", "unyielding-" + key + "-1"); rule.addProperty("pokemon", owner.id());
                rule.addProperty("type", "pokemon"); rule.addProperty("context", "grounded"); rule.addProperty("bucket", "rare"); rule.addProperty("level", "5-20"); rule.addProperty("weight", 1.0);
                JsonArray presets = new JsonArray(); presets.add("natural"); rule.add("presets", presets);
                JsonObject condition = new JsonObject(); JsonArray biomes = new JsonArray(); biomes.add("minecraft:plains"); condition.add("biomes", biomes); rule.add("condition", condition);
                for (int i = 0; i < next.size(); i++) if (string(next.get(i).getAsJsonObject(), "id").equals(fileId)) {
                    JsonArray existing = next.get(i).getAsJsonObject().getAsJsonObject("data").getAsJsonArray("spawns");
                    rule.addProperty("id", "unyielding-" + key + "-" + (existing.size() + 1)); existing.add(rule);
                    minecraft.setScreen(new SpawnRuleScreen(owner, next, i, existing.size() - 1)); return;
                }
                JsonArray rules = new JsonArray(); rules.add(rule); body.add("spawns", rules); file.add("data", body); next.add(file);
                minecraft.setScreen(new SpawnRuleScreen(owner, next, next.size() - 1, 0));
            });
            gap(5); paragraph(owner.data.getString("message"));
        }
        @Override protected String saveLabel() { return "Done"; }
        @Override protected void save() { cancel(); }
        @Override public void onClose() { cancel(); }
    }

    public static final class SpawnRuleScreen extends BaseForm {
        private final SpeciesScreen owner;
        private final JsonArray original;
        private final int fileIndex, ruleIndex;
        private String weight, bucket, level, biomes, dimensions;
        private static final String[] BOUNDS = {"minX", "maxX", "minY", "maxY", "minZ", "maxZ"};
        private final String[] bounds = new String[6];
        private String message = "Edit conditions and other rule fields in the full rule editor.";
        SpawnRuleScreen(SpeciesScreen owner, int fileIndex, int ruleIndex) {
            this(owner, owner.spawns(), fileIndex, ruleIndex);
        }
        SpawnRuleScreen(SpeciesScreen owner, JsonArray files, int fileIndex, int ruleIndex) {
            super(owner, "Spawn rule"); this.owner = owner; this.fileIndex = fileIndex; this.ruleIndex = ruleIndex; original = files.deepCopy();
            JsonObject rule = rule(original); weight = string(rule, "weight"); bucket = string(rule, "bucket"); level = string(rule, "level");
            JsonObject condition = condition(rule); biomes = listText(condition.get("biomes")); dimensions = listText(condition.get("dimensions"));
            for (int i = 0; i < BOUNDS.length; i++) bounds[i] = string(condition, BOUNDS[i]);
        }
        private JsonObject rule(JsonArray array) { return array.get(fileIndex).getAsJsonObject().getAsJsonObject("data").getAsJsonArray("spawns").get(ruleIndex).getAsJsonObject(); }
        private JsonObject condition(JsonObject rule) { return rule.has("condition") && rule.get("condition").isJsonObject() ? rule.getAsJsonObject("condition") : new JsonObject(); }
        @Override protected void build() {
            paragraph(string(rule(original), "id"));
            paragraph("Relative weight"); field("spawn-weight", left(), innerWidth(), weight, "Non-negative weight", text -> weight = text); gap(28);
            paragraph("Bucket"); field("spawn-bucket", left(), innerWidth(), bucket, "common, uncommon, rare, ultra-rare", text -> bucket = text); gap(28);
            paragraph("Level range"); field("spawn-level", left(), innerWidth(), level, "5-20", text -> level = text); gap(28);
            paragraph("Biomes or biome tags, separated by commas. Empty allows any biome.");
            field("spawn-biomes", left(), cy, innerWidth(), biomes, "minecraft:plains, #cobblemon:is_forest", 12000, text -> biomes = text); gap(28);
            paragraph("Dimensions, separated by commas. Empty allows any dimension.");
            field("spawn-dimensions", left(), cy, innerWidth(), dimensions, "minecraft:overworld", 12000, text -> dimensions = text); gap(28);
            row("Use my current dimension", () -> { if (minecraft.level != null) { dimensions = minecraft.level.dimension().location().toString(); relayout(); } });
            paragraph("Optional area limits: minimum and maximum coordinates. Empty means unlimited.");
            int half = (innerWidth() - 24) / 2;
            for (int axis = 0; axis < 3; axis++) {
                int index = axis * 2; label("XYZ".substring(axis, axis + 1), left(), cy + 6);
                field("spawn-" + BOUNDS[index], left() + 20, cy, half, bounds[index], "Minimum", 20, text -> bounds[index] = text);
                field("spawn-" + BOUNDS[index + 1], left() + 24 + half, cy, half, bounds[index + 1], "Maximum", 20, text -> bounds[index + 1] = text); gap(26);
            }
            row("Use a 32-block area around me", () -> {
                if (minecraft.player != null) {
                    double[] pos = {minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ()};
                    for (int i = 0; i < 3; i++) { bounds[2 * i] = Long.toString((long)Math.floor(pos[i] - 16)); bounds[2 * i + 1] = Long.toString((long)Math.floor(pos[i] + 16)); }
                    if (minecraft.level != null) dimensions = minecraft.level.dimension().location().toString(); relayout();
                }
            });
            row("Conditions and complete rule", () -> {
                try {
                    JsonArray next = draft();
                    minecraft.setScreen(new JsonScreen(owner, "Complete spawn rule", JSON.toJson(rule(next)),
                            "Edit conditions, anticonditions, context, presets, weight multipliers and all other spawn fields.", true, value -> {
                                next.get(fileIndex).getAsJsonObject().getAsJsonObject("data").getAsJsonArray("spawns").set(ruleIndex, JsonParser.parseString(value)); return JSON.toJson(next);
                            }));
                } catch (RuntimeException e) { message = Objects.toString(e.getMessage(), "Check your values."); relayout(); }
            });
            paragraph(message);
        }
        private JsonArray draft() {
            JsonArray next = original.deepCopy(); double value;
            try { value = Double.parseDouble(weight.trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException("Weight must be a number."); }
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Weight must be finite and non-negative.");
            if (bucket.trim().isEmpty() || level.trim().isEmpty()) throw new IllegalArgumentException("Enter a bucket and level range.");
            rule(next).addProperty("weight", value); rule(next).addProperty("bucket", bucket.trim()); rule(next).addProperty("level", level.trim());
            JsonObject condition = condition(rule(next)).deepCopy(); setList(condition, "biomes", biomes); setList(condition, "dimensions", dimensions);
            for (int i = 0; i < BOUNDS.length; i++) {
                if (bounds[i].trim().isEmpty()) condition.remove(BOUNDS[i]);
                else { double coordinate; try { coordinate = Double.parseDouble(bounds[i].trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException("Area limits must be numbers."); }
                    if (!Double.isFinite(coordinate)) throw new IllegalArgumentException("Area limits must be finite."); condition.addProperty(BOUNDS[i], coordinate); }
            }
            for (int i = 0; i < BOUNDS.length; i += 2) if (condition.has(BOUNDS[i]) && condition.has(BOUNDS[i + 1]) && condition.get(BOUNDS[i]).getAsDouble() > condition.get(BOUNDS[i + 1]).getAsDouble())
                throw new IllegalArgumentException("Minimum area limit must not exceed maximum.");
            if (!condition.isEmpty()) rule(next).add("condition", condition); else rule(next).remove("condition"); return next;
        }
        @Override protected String saveLabel() { return "Review"; }
        @Override protected void save() {
            try { minecraft.setScreen(new JsonScreen(owner, "Review spawn changes", JSON.toJson(draft()), "Save applies these spawn files to the server.", true, value -> value)); }
            catch (RuntimeException e) { message = Objects.toString(e.getMessage(), "Check your values."); relayout(); }
        }
        @Override protected void cancel() {
            boolean changed; try { changed = !draft().equals(owner.spawns()); } catch (RuntimeException error) { changed = true; }
            if (changed) minecraft.setScreen(new ConfirmScreen(discard -> minecraft.setScreen(discard ? owner : this),
                    Component.literal("Discard spawn draft?"), Component.literal("Your unsaved spawn changes will be lost.")));
            else minecraft.setScreen(owner);
        }
        @Override public void onClose() { cancel(); }
    }

    /** Scrollable, clipboard-friendly editor for structured Cobblemon settings. */
    public static final class JsonScreen extends Screen {
        private final SpeciesScreen owner;
        private final String help;
        private final boolean spawns;
        private final java.util.function.Function<String, String> serializer;
        private final long revision;
        private String draft, message = "";
        private boolean pending;
        private long pendingAt;
        private MultiLineEditBox box;
        private int px, py, pw, ph, editorTop;
        JsonScreen(SpeciesScreen owner, String title, String draft, String help, boolean spawns, java.util.function.Function<String, String> serializer) {
            super(Component.literal(title)); this.owner = owner; this.draft = draft; this.help = help; this.spawns = spawns; this.serializer = serializer; revision = owner.revision();
        }
        @Override protected void init() {
            Theme.ensureLoaded(); pw = Math.min(660, width - 16); ph = Math.min(500, height - 16); px = (width - pw) / 2; py = (height - ph) / 2;
            editorTop = py + 32 + 10 * font.split(Component.literal(help), pw - 24).size();
            box = addRenderableWidget(new MultiLineEditBox(font, px + 12, editorTop, pw - 30, Math.max(30, py + ph - 63 - editorTop), Component.literal("JSON settings"), getTitle()) {
                @Override public boolean keyPressed(int key, int scan, int mods) { return active && super.keyPressed(key, scan, mods); }
                @Override public boolean charTyped(char value, int mods) { return active && super.charTyped(value, mods); }
                @Override public boolean mouseClicked(double x, double y, int button) { return active && super.mouseClicked(x, y, button); }
            });
            box.setCharacterLimit(Math.max(900000, draft.length())); box.setValue(draft); box.setValueListener(value -> draft = value); box.active = !pending;
            addRenderableWidget(new SpriteButton(px + pw - 82, py + ph - 27, 70, 20, Component.literal(pending ? "Saving..." : "Save"), Ui.ICON_CHECK, Ui.Style.ACCENT, b -> save()));
            addRenderableWidget(new SpriteButton(px + pw - 148, py + ph - 27, 62, 20, Component.literal("Cancel"), null, Ui.Style.NORMAL, b -> cancel()));
            addRenderableWidget(new SpriteButton(px + 12, py + ph - 27, Math.min(100, pw - 174), 20, Component.literal("Format JSON"), null, Ui.Style.NORMAL, b -> {
                if (pending) return; try { draft = JSON.toJson(JsonParser.parseString(draft)); box.setCharacterLimit(Math.max(900000, draft.length())); box.setValue(draft); message = "Formatted. Review and Save to apply."; }
                catch (RuntimeException e) { message = "Invalid JSON: " + Objects.toString(e.getMessage(), "check punctuation"); }
            }));
        }
        private void save() {
            if (pending || !AdminSettingsScreen.canManage()) return;
            try {
                JsonParser.parseString(draft); String value = serializer.apply(draft);
                if (value.length() > 900000) throw new IllegalArgumentException("Data exceeds the editor size limit.");
                JsonElement parsed = JsonParser.parseString(value);
                if (spawns && !parsed.isJsonArray() || !spawns && !parsed.isJsonObject()) throw new IllegalArgumentException(spawns ? "Spawn files must be an array." : "Species data must be an object.");
                pending = true; pendingAt = Util.getMillis(); message = "Saving on server..."; clearWidgets(); init();
                send(spawns ? "save_spawns" : "save_species", owner.id(), revision, value);
            } catch (RuntimeException e) { message = Objects.toString(e.getMessage(), "Invalid JSON."); }
        }
        void receive(CompoundTag data) {
            if (!data.getString("id").equals(owner.id())) return;
            pending = false;
            if (!data.getBoolean("error") && data.getString("message").startsWith("Saved")) owner.receive(data);
            else { message = data.getString("message"); clearWidgets(); init(); }
        }
        @Override public void tick() { if (pending && Util.getMillis() - pendingAt > 15000) { pending = false; message = "No reply yet. Reopen this Pokemon to check the server state."; clearWidgets(); init(); } }
        private void cancel() {
            if (pending) return;
            boolean changed;
            try { changed = !JsonParser.parseString(serializer.apply(draft)).equals(spawns ? owner.spawns() : owner.species()); }
            catch (RuntimeException error) { changed = true; }
            if (changed) minecraft.setScreen(new ConfirmScreen(discard -> minecraft.setScreen(discard ? owner : this),
                    Component.literal("Discard draft?"), Component.literal("Your unsaved changes will be lost.")));
            else minecraft.setScreen(owner);
        }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() { cancel(); }
        @Override public boolean keyPressed(int key, int scan, int mods) { if (key == GLFW.GLFW_KEY_ESCAPE) { cancel(); return true; } return super.keyPressed(key, scan, mods); }
        @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { g.fill(0, 0, width, height, Ui.alpha(Theme.backdrop, .75f)); }
        @Override public void render(GuiGraphics g, int mx, int my, float pt) {
            renderBackground(g, mx, my, pt); Ui.panel(g, px, py, pw, ph); Ui.panel(g, Ui.HEADER, px, py, pw, 28);
            g.drawString(font, font.plainSubstrByWidth(getTitle().getString(), pw - 24), px + 12, py + 10, Theme.text, true);
            int y = py + 32; for (var line : font.split(Component.literal(help), pw - 24)) { g.drawString(font, line, px + 12, y, Theme.textMuted, false); y += 10; }
            g.drawString(font, font.plainSubstrByWidth(message, pw - 24), px + 12, py + ph - 41, Theme.textDim, false);
            super.render(g, mx, my, pt);
        }
    }
    private static String string(JsonObject object, String key) { JsonElement value = object.get(key); return value == null || value.isJsonNull() ? "" : value.getAsString(); }
    private static String listText(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        if (!value.isJsonArray()) return value.getAsString();
        List<String> entries = new ArrayList<>(); for (JsonElement item : value.getAsJsonArray()) entries.add(item.getAsString()); return String.join(", ", entries);
    }
    private static void setList(JsonObject object, String key, String text) {
        JsonArray array = new JsonArray(); for (String value : text.split(",")) if (!value.trim().isEmpty()) array.add(value.trim());
        if (array.isEmpty()) object.remove(key); else object.add(key, array);
    }
    private static boolean matchesSpecies(String properties, String id) {
        String species = dev.unyielding.quests.settings.PokemonEdits.speciesToken(properties);
        return species.equals(id.toLowerCase(Locale.ROOT)) || id.startsWith("cobblemon:") && species.equals(id.substring(id.indexOf(':') + 1).toLowerCase(Locale.ROOT));
    }
}
