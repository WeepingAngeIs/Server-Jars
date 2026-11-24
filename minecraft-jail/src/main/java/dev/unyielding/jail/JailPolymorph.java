package dev.unyielding.jail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;

/** Inspection of the pinned Polymorph provider. It does not create an attachment. */
final class JailPolymorph {
    private static final String CAPABILITIES = "com.illusivesoulworks.polymorph.common.PolymorphNeoForgeCapabilities";
    private static final String ATTACHMENT = CAPABILITIES + "$RecipeDataAttachment";
    private static final String PLAYER_DATA = "com.illusivesoulworks.polymorph.common.capability.PlayerRecipeData";
    private static final String BASE_DATA = "com.illusivesoulworks.polymorph.common.capability.AbstractRecipeData";
    private static final String CACHE = "com.illusivesoulworks.polymorph.common.capability.RecipeCache";

    private JailPolymorph() {}

    static boolean neutralTag(Tag tag) {
        return tag instanceof CompoundTag compound && compound.isEmpty();
    }

    private static Object field(String className, Object instance, String name) throws ReflectiveOperationException {
        Class<?> owner = Class.forName(className);
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    static void verify(ServerPlayer player, Tag tag) throws IOException {
        if (!neutralTag(tag)) throw new IOException("Active or unknown Polymorph recipe data");
        try {
            Object holder = Class.forName(CAPABILITIES).getField("RECIPE_DATA").get(null);
            if (!(holder instanceof Supplier<?> supplier) || !(supplier.get() instanceof AttachmentType<?> type))
                throw new IOException("Unknown Polymorph attachment registration");
            Object attachment = player.getExistingDataOrNull(type);
            if (attachment == null || !attachment.getClass().getName().equals(ATTACHMENT))
                throw new IOException("Polymorph attachment is missing or changed");
            Object recipeData = JailHumanAudit.call(ATTACHMENT,attachment,"getRecipeData");
            if (recipeData == null || !recipeData.getClass().getName().equals(PLAYER_DATA)
                    || JailHumanAudit.call(BASE_DATA,recipeData,"getOwner") != player)
                throw new IOException("Unexpected Polymorph recipe owner or provider");
            Object menu = JailHumanAudit.call(PLAYER_DATA,recipeData,"getContainerMenu");
            Object selected = JailHumanAudit.call(BASE_DATA,recipeData,"getSelectedRecipe");
            Object recipes = JailHumanAudit.call(BASE_DATA,recipeData,"getRecipesList");
            Object listeners = JailHumanAudit.call(BASE_DATA,recipeData,"getListeners");
            Object loaded = field(BASE_DATA,recipeData,"loadedRecipe");
            Object cached = field(PLAYER_DATA,recipeData,"cachedSelection");
            Object cache = field(BASE_DATA,recipeData,"recipeCache");
            if (cache == null || !cache.getClass().getName().equals(CACHE))
                throw new IOException("Unexpected Polymorph recipe cache");
            Object entries = field(CACHE,cache,"entries");
            if (!(recipes instanceof Collection<?> list) || !(listeners instanceof Collection<?> viewers)
                    || !(entries instanceof Object[] cacheEntries)
                    || !neutralLive(menu,selected,loaded,cached,list,viewers,cacheEntries))
                throw new IOException("Active Polymorph recipe data requires review");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError bad) {
            throw new IOException("Cannot inspect the pinned Polymorph provider",bad);
        }
    }

    static boolean neutralLive(Object menu, Object selected, Object loaded, Object cached,
                               Collection<?> recipes, Collection<?> listeners, Object[] entries) {
        if (menu != null || selected != null || loaded != null || cached != null
                || !recipes.isEmpty() || !listeners.isEmpty()) return false;
        for (Object entry : entries) if (entry != null) return false;
        return true;
    }
}
