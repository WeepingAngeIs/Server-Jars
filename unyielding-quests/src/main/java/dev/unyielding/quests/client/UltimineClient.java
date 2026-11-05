package dev.unyielding.quests.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.ultimine.Shapes;
import dev.unyielding.quests.ultimine.UltimineConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Client side of vein mining: key, shape cycling, outline preview. */
public final class UltimineClient {
    public static final KeyMapping KEY = new KeyMapping("key.unyieldingquests.ultimine", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, ClientInit.CATEGORY);

    private static boolean held;
    private static int shape;
    private static List<BlockPos> preview = List.of();

    private UltimineClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(KEY));
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(UnyieldingQuests.MODID, "ultimine"), UltimineClient::drawOverlay));
        NeoForge.EVENT_BUS.register(new Events());
    }

    private static void drawOverlay(GuiGraphics g, net.minecraft.client.DeltaTracker dt) {
        if (!held) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.options.hideGui) return;
        int y = 4;
        g.drawString(mc.font, "Ultimine - scroll to change shape", 4, y, 0xFFFFD23C, true);
        y += 11;
        for (Shapes.Shape s : Shapes.Shape.values()) {
            boolean sel = s.ordinal() == shape;
            g.drawString(mc.font, (sel ? "> " : "  ") + s.label, 4, y, sel ? 0xFFFFFFFF : 0xFF8A94A8, true);
            y += 10;
        }
    }

    public static final class Events {
        @SubscribeEvent
        public void onTick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            boolean now = mc.player != null && mc.screen == null && KEY.isDown() && UltimineConfig.ENABLED.get();
            if (now != held) {
                held = now;
                Net.sendToServer(new Net.UltimineState(held, shape));
            }
            preview = List.of();
            if (held && mc.level != null && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                preview = Shapes.compute(mc.level, hit.getBlockPos(), hit.getDirection(), Shapes.Shape.of(shape), UltimineConfig.MAX_BLOCKS.get());
            }
        }

        @SubscribeEvent
        public void onScroll(InputEvent.MouseScrollingEvent e) {
            if (!held) return;
            double dy = e.getScrollDeltaY();
            if (dy == 0) return;
            int n = Shapes.Shape.values().length;
            shape = Math.floorMod(shape + (dy > 0 ? -1 : 1), n);
            Net.sendToServer(new Net.UltimineState(true, shape));
            e.setCanceled(true);
        }

        @SubscribeEvent
        public void onRender(RenderLevelStageEvent e) {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || preview.isEmpty()) return;
            Minecraft mc = Minecraft.getInstance();
            Vec3 cam = e.getCamera().getPosition();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buffers.getBuffer(RenderType.lines());
            PoseStack ps = e.getPoseStack();
            for (BlockPos p : preview) {
                AABB box = new AABB(p).inflate(0.002).move(-cam.x, -cam.y, -cam.z);
                LevelRenderer.renderLineBox(ps, vc, box, 1f, 0.85f, 0.2f, 0.9f);
            }
            buffers.endBatch(RenderType.lines());
        }

        @SubscribeEvent
        public void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
            held = false;
            preview = List.of();
        }
    }
}
