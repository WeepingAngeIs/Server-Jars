package dev.unyielding.quests.settings;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforgespi.language.IModInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class NeoForgeSettingsTest {
    private enum Difficulty { EASY, MEDIUM, HARD }

    private static final class MemoryConfig {
        private int saves;
        private boolean failNextSave;

        MemoryConfig(ModConfigSpec spec) {
            this(spec, null);
        }

        MemoryConfig(ModConfigSpec spec, Path path) {
            // FML seals ILoadedConfig. Construct its real in-memory implementation in the fixture.
            var info = (IModInfo) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IModInfo.class},
                    (proxy, method, args) -> method.getName().equals("getModId") ? "example" : null);
            ModContainer owner = new ModContainer(info) {
                public IEventBus getEventBus() {
                    saves++;
                    if (failNextSave) {
                        failNextSave = false;
                        throw new IllegalStateException("internal server file location");
                    }
                    return null;
                }
            };
            try {
                var reg = ModConfig.class.getDeclaredConstructor(ModConfig.Type.class, IConfigSpec.class,
                        ModContainer.class, String.class, ReentrantLock.class);
                reg.setAccessible(true);
                var mod = reg.newInstance(ModConfig.Type.SERVER, spec, owner, "example.toml", new ReentrantLock());
                var load = Class.forName("net.neoforged.fml.config.LoadedConfig")
                        .getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
                load.setAccessible(true);
                CommentedConfig data = CommentedConfig.inMemory();
                spec.correct(data);
                spec.acceptConfig((IConfigSpec.ILoadedConfig) load.newInstance(data, path, mod));
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Unsupported test FML fixture version", failure);
            }
            saves = 0;
        }
    }

    private static NeoForgeSettings.Registration registration(String name, ModConfig.Type type, String file, ModConfigSpec spec) {
        return new NeoForgeSettings.Registration(name, type, file, spec);
    }

    private static NeoForgeSettings.Catalog catalog(ModConfigSpec spec) {
        return new NeoForgeSettings.Catalog(List.of(registration("example", ModConfig.Type.SERVER, "private/location.toml", spec)));
    }

    private static CompoundTag row(NeoForgeSettings.Catalog catalog, String query) {
        var matches = catalog.rows(query, 0, 10);
        assertEquals(1, matches.total());
        return matches.rows().getCompound(0);
    }

    @Test
    void discoversTypedNestedValuesAndDoesNotExposeSecretsOrRawFiles() {
        var builder = new ModConfigSpec.Builder();
        builder.push("creatures");
        builder.define("spawnAtNight", true);
        builder.define("message", "secret value that must stay on the server");
        builder.define("apiKey", 123456);
        builder.define("allowList", List.of("private-list"));
        builder.define("number", 14);
        builder.worldRestart().defineInRange("density", 0.3, 0.0, 1.0);
        builder.pop();
        var spec = builder.build();
        var storage = new MemoryConfig(spec);
        var settings = catalog(spec);
        assertEquals(3, settings.rows("", 0, 25).total());
        var toggle = row(settings, "spawn at night");
        assertEquals("creatures.spawnAtNight", toggle.getString("aliases"));
        assertTrue(toggle.getBoolean("value"));
        assertEquals("example", NeoForgeSettings.modId(toggle.getString("key")));
        assertEquals("", NeoForgeSettings.modId("registered.example.private/location.toml"));
        assertEquals("WORLD_RESTART", row(settings, "creatures.density").getString("activation"));
        assertFalse(settings.rows("", 0, 25).rows().toString().contains("private"));
        assertEquals(0, storage.saves);
    }

    @Test
    void validatesTypesRangesAndConflictsBeforeWritingAndCanUndo() {
        var builder = new ModConfigSpec.Builder();
        var number = builder.defineInRange("number", 4L, 0L, 8L);
        builder.defineInRange("decimal", 0.5, -2.0, 2.0);
        builder.define("toggle", false);
        var spec = builder.build();
        var storage = new MemoryConfig(spec);
        var settings = catalog(spec);
        String key = row(settings, "number").getString("key");
        for (String invalid : List.of("9", "3.5", "1e0", "99999999999999999999")) {
            assertThrows(IllegalArgumentException.class, () -> settings.change(key, "4", invalid));
        }
        for (String invalid : List.of("Infinity", "NaN", "1e4000", "0x1.0p1", "1f")) {
            assertThrows(IllegalArgumentException.class,
                    () -> settings.change(row(settings, "decimal").getString("key"), "0.5", invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> settings.change(row(settings, "toggle").getString("key"), "false", "1"));
        assertThrows(IllegalStateException.class, () -> settings.change(key, "3", "6"));
        assertEquals(0, storage.saves);
        assertEquals(new NeoForgeSettings.Edit(key, 4L, 6L), settings.change(key, "4", "6"));
        assertEquals(6L, number.getRaw());
        assertThrows(IllegalStateException.class, () -> settings.change(key, "4", "5"));
        assertEquals(new NeoForgeSettings.Edit(key, 6L, 4L), settings.change(key, "6", "4"));
        assertEquals(4L, number.getRaw());
        assertEquals(2, storage.saves);
    }

    @Test
    void usesTheRegisteredEnumSubsetAndPersistedRestartValues() {
        var builder = new ModConfigSpec.Builder();
        var value = builder.worldRestart().defineEnum("difficulty", Difficulty.EASY, Difficulty.EASY, Difficulty.HARD);
        var spec = builder.build();
        var storage = new MemoryConfig(spec);
        var settings = catalog(spec);
        assertEquals(Difficulty.EASY, value.get());
        var row = row(settings, "difficulty");
        String key = row.getString("key");
        assertEquals(List.of("EASY", "HARD"), row.getList("opt", Tag.TAG_STRING).stream().map(Tag::getAsString).toList());
        assertThrows(IllegalArgumentException.class, () -> settings.change(key, "EASY", "MEDIUM"));
        assertThrows(IllegalArgumentException.class, () -> settings.change(key, "EASY", "hard"));
        assertEquals(new NeoForgeSettings.Edit(key, Difficulty.EASY, Difficulty.HARD), settings.change(key, "EASY", "HARD"));
        assertEquals("HARD", row(settings, "difficulty").getString("current"));
        assertEquals(Difficulty.EASY, value.get());
        settings.change(key, "HARD", "EASY");
        assertEquals(2, storage.saves);
    }

    @Test
    void rollsBackFailedSavesAndRejectsUnloadedSpecs() {
        var builder = new ModConfigSpec.Builder();
        var toggle = builder.define("enabled", false);
        var spec = builder.build();
        var storage = new MemoryConfig(spec);
        var settings = catalog(spec);
        String key = row(settings, "enabled").getString("key");
        storage.failNextSave = true;
        var failure = assertThrows(IllegalStateException.class, () -> settings.change(key, "false", "true"));
        assertFalse(failure.getMessage().contains("internal server"));
        assertFalse(toggle.getRaw());
        assertEquals(2, storage.saves);
        spec.acceptConfig(null);
        assertThrows(IllegalStateException.class, () -> settings.change(key, "false", "true"));
        assertFalse(row(settings, "enabled").getBoolean("available"));
    }

    @Test
    void savesTheRequestedValueAndPreservesUneditedValuesInTheActualToml(@TempDir Path directory) throws IOException {
        if (FMLPaths.GAMEDIR.get() == null) FMLPaths.loadAbsolutePaths(directory);
        var builder = new ModConfigSpec.Builder();
        builder.define("enabled", false);
        builder.defineInRange("cap", 12, 0, 20);
        var spec = builder.build();
        Path file = directory.resolve("sample.toml");
        new MemoryConfig(spec, file);
        var settings = catalog(spec);
        var key = row(settings, "enabled").getString("key");
        var failure = assertThrows(IllegalArgumentException.class, () -> settings.change(key, "false", "invalid"));
        assertEquals("Enter a valid on/off value.", NeoForgeSettings.feedback(failure));
        assertFalse(NeoForgeSettings.feedback(new IllegalStateException("secret")).contains("secret"));
        assertFalse(Files.exists(file));
        settings.change(key, "false", "true");
        String text = Files.readString(file);
        assertTrue(text.contains("enabled = true"));
        assertTrue(text.contains("cap = 12"));
        settings.change(key, "true", "false");
        assertTrue(Files.readString(file).contains("enabled = false"));
    }

    @Test
    void excludesManagedClientAndAliasTargetsAndDisambiguatesRegistrations() {
        var builder = new ModConfigSpec.Builder();
        builder.define("terrainExcavation", false);
        var spec = builder.build();
        new MemoryConfig(spec);
        var entries = new java.util.ArrayList<NeoForgeSettings.Registration>();
        entries.add(registration("uwudimension", ModConfig.Type.COMMON, "a.toml", spec));
        entries.add(registration("cubalance", ModConfig.Type.COMMON, "a.toml", spec));
        entries.add(registration("example", ModConfig.Type.CLIENT, "a.toml", spec));
        var a = registration("example", ModConfig.Type.SERVER, "a.toml", spec);
        var b = registration("example", ModConfig.Type.SERVER, "b.toml", spec);
        entries.add(a);
        entries.add(b);
        var combined = new NeoForgeSettings.Catalog(entries);
        var first = combined.rows("", -10, 1);
        var last = combined.rows("", Integer.MAX_VALUE, 1);
        assertEquals(2, first.total());
        assertEquals(0, first.pageIndex());
        assertEquals(1, last.pageIndex());
        assertNotEquals(first.rows().getCompound(0).getString("key"), last.rows().getCompound(0).getString("key"));
        assertEquals(0, new NeoForgeSettings.Catalog(List.of(a, a)).rows("", 0, 1).total());
    }
}
