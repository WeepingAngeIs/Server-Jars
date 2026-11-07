package dev.unyielding.bundlesmoke;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = BundleSmoke.MOD_ID, value = Dist.CLIENT)
public final class BundleClientSmoke {
    private static boolean checked;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (checked || mc.player == null || mc.getOverlay() != null) return;
        checked = true;
        try {
            Map<String, Object> report = BundleSmoke.verifyProductionJar();
            BundleSmoke.require(mc.getResourcePackRepository().getSelectedIds().contains("file/Mawonna.zip"), "Pinned Mawonna resource pack was not loaded");
            Class<?> repoClass = Class.forName("com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository");
            Object repo = repoClass.getField("INSTANCE").get(null);
            var modelsMethod = repoClass.getDeclaredMethod("getTexturedModels");
            modelsMethod.setAccessible(true);
            Map<?, ?> models = (Map<?, ?>) modelsMethod.invoke(repo);
            for (String key : List.of("mawonna:inkyeon.geo", "mawonna:fairy_sliggoo.geo", "mawonna:pink_corvisquire.geo", "mawile_custom:mawile_third_evolution.geo")) {
                BundleSmoke.require(models.containsKey(ResourceLocation.parse(key)), "Model not baked: " + key);
            }
            Class<?> stateClass = Class.forName("com.cobblemon.mod.common.client.render.models.blockbench.PosableState");
            Class<?> floating = Class.forName("com.cobblemon.mod.common.client.render.models.blockbench.FloatingState");
            var getPoser = repoClass.getMethod("getPoser", ResourceLocation.class, stateClass);
            var getTexture = repoClass.getMethod("getTextureNoSubstitute", ResourceLocation.class, stateClass);
            var setAspects = stateClass.getMethod("setCurrentAspects", Set.class);
            Map<?, ?> variations = (Map<?, ?>) repoClass.getMethod("getVariations").invoke(repo);
            Map<String, List<String>> textures = new LinkedHashMap<>();
            for (String id : List.of("fairy_sliggoo", "inkyeon", "covislight", "mawonna", "mawonna_flower")) {
                var location = ResourceLocation.parse("cobblemon:" + id);
                BundleSmoke.require(variations.containsKey(location), "Missing species resolver: " + id);
                Object state = floating.getConstructor().newInstance();
                BundleSmoke.require(getPoser.invoke(repo, location, state) != null, "Missing poser: " + id);
                Object normal = getTexture.invoke(repo, location, state);
                setAspects.invoke(state, Set.of("shiny"));
                Object shiny = getTexture.invoke(repo, location, state);
                BundleSmoke.require(normal != null && shiny != null && !normal.equals(shiny), "Missing distinct normal/shiny texture: " + id);
                textures.put(id, List.of(normal.toString(), shiny.toString()));
            }
            var ringId = ResourceLocation.parse("mawonna:mawilite_vow_ring");
            BundleSmoke.require(BuiltInRegistries.ITEM.containsKey(ringId), "Vow ring is not registered");
            var model = mc.getItemRenderer().getItemModelShaper().getItemModel(new ItemStack(BuiltInRegistries.ITEM.get(ringId)));
            BundleSmoke.require(model != null && model != mc.getModelManager().getMissingModel(), "Vow ring model is missing");
            BundleSmoke.require(mc.getResourceManager().getResource(ResourceLocation.parse("mawonna:textures/item/mawilite_vow_ring.png")).isPresent(), "Vow ring texture is missing");
            report.put("mawonna_pack_retained", true);
            report.put("custom_models_and_posers", "passed");
            report.put("vow_ring_model_and_texture", "passed");
            report.put("species_textures", textures);
            BundleSmoke.write("/tmp/uq-bundle-client-passed.json", report);
        } catch (Throwable failure) {
            BundleSmoke.fail("/tmp/uq-bundle-client-failed.txt", failure);
        }
    }
}
