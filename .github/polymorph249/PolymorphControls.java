package dev.unyielding.bundlesmoke;

import java.lang.reflect.InvocationTargetException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import static dev.unyielding.bundlesmoke.PlayerOnlySmoke.cmd;

/** Run only on a fresh disposable player with the exact pinned Polymorph. */
final class PolymorphControls {
    private static final String BASE = "com.illusivesoulworks.polymorph.common.capability.AbstractRecipeData";
    private static final String PLAYER = "com.illusivesoulworks.polymorph.common.capability.PlayerRecipeData";
    private PolymorphControls() {}

    private static void require(boolean value, String reason) {
        if (!value) throw new IllegalStateException(reason);
    }

    private static void check(ServerPlayer player, Tag tag, boolean accepted) throws Exception {
        var method = Class.forName("dev.unyielding.jail.JailPolymorph")
                .getDeclaredMethod("verify",ServerPlayer.class,Tag.class);
        method.setAccessible(true);
        try {
            method.invoke(null,player,tag);
            require(accepted,"Accepted an active or unknown Polymorph state");
        } catch (InvocationTargetException error) {
            if (accepted || !(error.getCause() instanceof java.io.IOException)) throw error;
        }
    }

    static void run(ServerPlayer player) throws Exception {
        require(System.getenv("GITHUB_ACTIONS").equals("true")
                && System.getenv("RUNNER_ENVIRONMENT").equals("github-hosted")
                && player.getGameProfile().getName().equals("Visitor")
                && player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toRealPath().getFileName().toString().equals("jail_certified_smoke"),
                "Polymorph controls require a disposable hosted Visitor");
        var apiClass = Class.forName("com.illusivesoulworks.polymorph.api.PolymorphApi");
        Object api = apiClass.getMethod("getInstance").invoke(null);
        Object data = apiClass.getMethod("getPlayerRecipeData",net.minecraft.world.entity.player.Player.class)
                .invoke(api,player);
        var provider = Class.forName(PLAYER);
        var base = Class.forName(BASE);
        var current = player.saveWithoutId(new CompoundTag()).getCompound("neoforge:attachments")
                .get("polymorph:recipe_data");
        check(player,current,true);
        require(cmd("jailintegrated check Visitor")==1,"Neutral Polymorph data failed the actual admission check");

        var unknown = new CompoundTag();
        unknown.putBoolean("unexpected",true);
        check(player,unknown,false);

        var menuSetter = provider.getMethod("setContainerMenu",AbstractContainerMenu.class);
        try {
            menuSetter.invoke(data,player.containerMenu);
            check(player,current,false);
            require(cmd("jailintegrated check Visitor")==0,"Actual admission check accepted an active Polymorph menu");
        } finally {
            menuSetter.invoke(data,new Object[]{null});
        }

        RecipeHolder<?> recipe = player.server.getRecipeManager()
                .byKey(ResourceLocation.parse("minecraft:stick")).orElseThrow();
        var select = base.getMethod("setSelectedRecipe",RecipeHolder.class);
        try {
            select.invoke(data,recipe);
            Tag selected = player.saveWithoutId(new CompoundTag()).getCompound("neoforge:attachments")
                    .get("polymorph:recipe_data");
            require(selected instanceof CompoundTag c && c.contains("SelectedRecipe"),
                    "Actual selected recipe was not serialized");
            check(player,selected,false);
            check(player,current,false);
            require(cmd("jailintegrated check Visitor")==0,"Actual admission check accepted a selected Polymorph recipe");
            require(base.getMethod("getSelectedRecipe").invoke(data)==recipe,
                    "Refusal changed the selected recipe");
        } finally {
            select.invoke(data,new Object[]{null});
        }

        var pending = new CompoundTag();
        pending.putString("SelectedRecipe","minecraft:stick");
        var loaded = base.getDeclaredField("loadedRecipe");
        loaded.setAccessible(true);
        require(loaded.get(data)==null,"Player already had a pending recipe");
        try {
            base.getMethod("readNBT",net.minecraft.core.HolderLookup.Provider.class,CompoundTag.class)
                    .invoke(data,player.registryAccess(),pending);
            Tag serialized = player.saveWithoutId(new CompoundTag()).getCompound("neoforge:attachments")
                    .get("polymorph:recipe_data");
            require(serialized instanceof CompoundTag c && c.isEmpty(),
                    "Provider changed pending-selection serialization");
            check(player,serialized,false);
            require(cmd("jailintegrated check Visitor")==0,"Actual admission check accepted a pending Polymorph recipe");
            require(loaded.get(data)!=null,"Refusal cleared the pending recipe");
        } finally {
            loaded.set(data,null);
        }
        check(player,current,true);
        require(cmd("jailintegrated check Visitor")==1,"Clearing fixture activity did not restore neutral eligibility");
    }
}
