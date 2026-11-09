package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Checks the combined JAR's client content without selecting an external resource pack. */
final class PokemonEmbeddedClientSmoke {
    private static final List<String> SPECIES = List.of("fairy_sliggoo", "inkyeon", "covislight", "mawonna", "mawonna_flower", "lunavee", "buzzeon", "ghost_jirachi", "creepyeon");
    private static final List<String> DISTINCT_SHINY = SPECIES.subList(0,5);
    private static final List<String> MODELS = List.of("lunavee:lunavee.geo", "buzzeon:buzzeon_male.geo", "buzzeon:buzzeon_female.geo",
            "creepyeon:creepyeon_male.geo", "creepyeon:creepyeon_female.geo", "uwu:ghost_jirachi_male.geo", "uwu:ghost_jirachi_female.geo",
            "mawonna:inkyeon.geo", "mawonna:fairy_sliggoo.geo", "mawonna:pink_corvisquire.geo", "mawile_custom:mawile_third_evolution.geo");

    /** Reloading stays asynchronous so the render thread can finish Minecraft's reload tasks. */
    static CompletableFuture<Void> exercise(Minecraft mc, Map<String,Object> report) {
        try { verify(mc); }
        catch (Throwable failure) { return CompletableFuture.failedFuture(failure); }
        return mc.reloadResourcePacks().thenRunAsync(() -> {
            try {
                int appearances = verify(mc);
                report.put("embedded_pokemon_species_count", SPECIES.size());
                report.put("embedded_appearance_combinations_checked", appearances);
                report.put("embedded_models_textures_posers_and_actual_vertex_render", "passed");
                report.put("embedded_content_survives_resource_reload_without_external_pack", "passed");
            } catch (Throwable failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, mc);
    }

    private static int verify(Minecraft mc) throws Exception {
        require(mc.getResourcePackRepository().getSelectedIds().stream().noneMatch(id -> id.startsWith("file/")),
                "Embedded-content check must run without an external file resource pack");
        VaryingModelRepository repository = VaryingModelRepository.INSTANCE;
        var modelMethod = VaryingModelRepository.class.getDeclaredMethod("getTexturedModels"); modelMethod.setAccessible(true);
        Map<?,?> models = (Map<?,?>)modelMethod.invoke(repository);
        for (String model : MODELS) require(models.containsKey(ResourceLocation.parse(model)), "Embedded model was not baked: " + model);
        Set<ResourceLocation> decoded = new HashSet<>(); Map<String,ResourceLocation> normals = new HashMap<>();
        int appearances = 0;
        for (String species : SPECIES) {
            require(PokemonSpecies.getByName(species) != null, "Embedded species is not registered: " + species);
            ResourceLocation id = ResourceLocation.parse("cobblemon:" + species);
            var resolver = repository.getVariations().get(id); require(resolver != null, "Embedded resolver missing: " + id);
            for (String sex : List.of("male", "female")) for (boolean shiny : new boolean[]{false,true}) {
                FloatingState state = new FloatingState();
                Set<String> aspects = shiny ? Set.of(sex,"shiny") : Set.of(sex); state.setCurrentAspects(aspects);
                // Use the exact resolver, rather than accepting the repository's generic fallback poser.
                PosableModel poser = resolver.getPoser(state); require(poser != null, "Embedded poser did not resolve: " + id);
                ResourceLocation texture = repository.getTextureNoSubstitute(id,state);
                require(texture != null && !texture.getPath().contains("missing"), "Embedded texture did not resolve: " + id);
                if (decoded.add(texture)) {
                    var resource = mc.getResourceManager().getResource(texture).orElseThrow(() -> new IllegalStateException("Missing embedded texture " + texture));
                    require(!resource.sourcePackId().startsWith("file/"), "Texture came from an external resource pack");
                    try (var input = resource.open(); NativeImage image = NativeImage.read(input)) { require(image.getWidth() > 0 && image.getHeight() > 0, "Embedded image did not decode"); }
                }
                String variant = species + "/" + sex;
                if (!shiny) normals.put(variant,texture);
                else if (DISTINCT_SHINY.contains(species)) require(!texture.equals(normals.get(variant)), "Distinct embedded shiny texture was lost: " + id);
                render(mc,repository,poser,state,id,texture,aspects); appearances++;
            }
        }
        ResourceLocation ringId = ResourceLocation.parse("mawonna:mawilite_vow_ring");
        require(BuiltInRegistries.ITEM.containsKey(ringId), "Bundled companion item is not registered");
        var ringModel = mc.getItemRenderer().getItemModelShaper().getItemModel(new ItemStack(BuiltInRegistries.ITEM.get(ringId)));
        require(ringModel != null && ringModel != mc.getModelManager().getMissingModel(), "Bundled companion item model is missing");
        require(mc.getResourceManager().getResource(ResourceLocation.parse("mawonna:textures/item/mawilite_vow_ring.png")).isPresent(), "Bundled companion item texture is missing");
        return appearances;
    }

    /** Exercise real textured geometry buffers, without exporting images or creating battle data. */
    private static void render(Minecraft mc, VaryingModelRepository repository, PosableModel poser, FloatingState state,
                               ResourceLocation id, ResourceLocation texture, Set<String> aspects) {
        GuiGraphics graphics = new GuiGraphics(mc,mc.renderBuffers().bufferSource());
        RenderContext context = new RenderContext();
        context.put(RenderContext.Companion.getSPECIES(),id); context.put(RenderContext.Companion.getTEXTURE(),texture);
        context.put(RenderContext.Companion.getASPECTS(),aspects); context.put(RenderContext.Companion.getSCALE(),1f);
        context.put(RenderContext.Companion.getDO_QUIRKS(),false); context.put(RenderContext.Companion.getPOSABLE_STATE(),state);
        var oldState = poser.getCurrentState(); RenderContext oldContext;
        try { oldContext=poser.getContext(); }
        catch(kotlin.UninitializedPropertyAccessException unused) { oldContext=new RenderContext(); }
        state.setCurrentModel(poser); poser.setCurrentState(state); poser.setContext(context); poser.setDefault();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(-100000,-100000,0); graphics.pose().scale(20,20,20);
            poser.applyAnimations(null,state,0,0,0,0,0);
            poser.setLayerContext(graphics.bufferSource(),state,repository.getLayers(id,state));
            CountedVertices buffer = new CountedVertices(graphics.bufferSource().getBuffer(RenderType.entityCutoutNoCull(texture)));
            poser.render(context,graphics.pose(),buffer,15728880,OverlayTexture.NO_OVERLAY,-1);
            require(buffer.count > 0,"Embedded model emitted no geometry: " + id);
            graphics.flush();
        } finally { poser.resetLayerContext(); poser.setCurrentState(oldState); poser.setContext(oldContext); poser.setDefault(); graphics.pose().popPose(); }
    }

    private static final class CountedVertices implements VertexConsumer {
        private final VertexConsumer delegate; private int count;
        CountedVertices(VertexConsumer delegate) { this.delegate = delegate; }
        @Override public VertexConsumer addVertex(float x,float y,float z) { require(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"Embedded geometry contains non-finite coordinates"); count++;delegate.addVertex(x,y,z);return this; }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { delegate.setColor(r,g,b,a);return this; }
        @Override public VertexConsumer setUv(float u,float v) { delegate.setUv(u,v);return this; }
        @Override public VertexConsumer setUv1(int u,int v) { delegate.setUv1(u,v);return this; }
        @Override public VertexConsumer setUv2(int u,int v) { delegate.setUv2(u,v);return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { delegate.setNormal(x,y,z);return this; }
    }
}
