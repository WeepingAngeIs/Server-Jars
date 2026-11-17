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
    private static String feedbackText = "";
    private static long feedbackUntil;
    private static int[] lastHudBounds;

    public static void acceptFeedback(String text) {
        feedbackText = text == null ? "" : text;
        feedbackUntil = feedbackText.isEmpty() ? 0 : System.currentTimeMillis() + 3000;
    }

    private UltimineClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(KEY));
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(UnyieldingQuests.MODID, "ultimine"), UltimineClient::drawOverlay));
        NeoForge.EVENT_BUS.register(new Events());
    }

    private static void drawOverlay(GuiGraphics g, net.minecraft.client.DeltaTracker dt) {
        lastHudBounds = null;
        boolean feedback = !feedbackText.isEmpty() && System.currentTimeMillis() < feedbackUntil;
        if (!held && !feedback) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) return;
        // Below the top toast, beside the left party column, above recent chat,
        // and clear of the right-hand minimap at the minimum GUI width.
        int width = Math.max(64, Math.min(150, g.guiWidth() - 236));
        List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
        List<Integer> colors = new java.util.ArrayList<>();
        var heading = mc.font.split(net.minecraft.network.chat.Component.literal("Ultimine"), width);
        lines.addAll(heading); for (var ignored : heading) colors.add(0xFFFFD23C);
        if (held) {
            var selected = mc.font.split(net.minecraft.network.chat.Component.literal("> " + Shapes.Shape.of(shape).label), width);
            lines.addAll(selected); for (var ignored : selected) colors.add(0xFFFFFFFF);
        }
        if (feedback) {
            String body = feedbackText.startsWith("Vein mining: ") ? feedbackText.substring("Vein mining: ".length()) : feedbackText;
            for (String part : body.split("; ")) {
                String label = part.startsWith("durability ") ? "Tool " + part.substring("durability ".length()) : part;
                var wrapped = mc.font.split(net.minecraft.network.chat.Component.literal(label), width);
                lines.addAll(wrapped); for (var ignored : wrapped) colors.add(0xFFE8ECF2);
            }
        } else {
            var hint = mc.font.split(net.minecraft.network.chat.Component.literal("Scroll to change"), width);
            lines.addAll(hint); for (var ignored : hint) colors.add(0xFF8A94A8);
        }
        int x = 72, y = 36, bottom = y + lines.size() * 9;
        g.fill(x - 2, y - 2, x + width + 2, bottom + 2, 0xB0000000);
        for (int i = 0; i < lines.size(); i++) g.drawString(mc.font, lines.get(i), x, y + i * 9, colors.get(i), true);
        lastHudBounds = new int[]{x - 2, y - 2, x + width + 2, bottom + 2};
    }

    public static final class Events {
        private static void syncHeld(Minecraft mc) {
            boolean now = mc.player != null && mc.screen == null && KEY.isDown()
                    && UltimineConfig.ENABLED.get() && ClientData.ultimineEnabled
                    && (!ClientData.ultimineRequiresItem || !mc.player.getMainHandItem().isEmpty());
            if (now != held) {
                held = now;
                Net.sendToServer(new Net.UltimineState(held, shape));
            }
        }

        @SubscribeEvent
        public void onInteraction(InputEvent.InteractionKeyMappingTriggered e) {
            // Send a changed held state before vanilla sends a same-frame action.
            if (e.isAttack() || e.isUseItem()) syncHeld(Minecraft.getInstance());
        }

        @SubscribeEvent
        public void onTick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            syncHeld(mc);
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
            acceptFeedback("");
            lastHudBounds = null;
        }
    }
}
