package dev.unyielding.quests.ultimine;

import dev.unyielding.quests.net.Net;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server side of vein mining. */
public final class Ultimine {
    private record State(boolean held, Shapes.Shape shape) {
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Set<UUID> ACTIVE = new HashSet<>();

    private Ultimine() {
    }

    public static void onState(ServerPlayer p, Net.UltimineState s) {
        if (s.held()) STATES.put(p.getUUID(), new State(true, Shapes.Shape.of(s.shape())));
        else STATES.remove(p.getUUID());
    }

    private static State active(ServerPlayer p) {
        if (!UltimineConfig.ENABLED.get()) return null;
        State s = STATES.get(p.getUUID());
        return s != null && s.held ? s : null;
    }

    public static final class Events {
        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
            STATES.remove(e.getEntity().getUUID());
            ACTIVE.remove(e.getEntity().getUUID());
        }

        @SubscribeEvent
        public void onBreak(BlockEvent.BreakEvent e) {
            if (!(e.getPlayer() instanceof ServerPlayer p) || ACTIVE.contains(p.getUUID())) return;
            State st = active(p);
            if (st == null || p.isSpectator()) return;
            if (UltimineConfig.REQUIRE_TOOL.get() && p.getMainHandItem().isEmpty()) return;
            if (!(e.getLevel() instanceof ServerLevel level)) return;
            BlockPos origin = e.getPos();
            Direction face = faceOf(p, origin);
            List<BlockPos> list = Shapes.compute(level, origin, face, st.shape, UltimineConfig.MAX_BLOCKS.get());
            ACTIVE.add(p.getUUID());
            try {
                float exhaustion = (float) (0.005 * UltimineConfig.EXHAUSTION.get());
                for (BlockPos pos : list) {
                    if (pos.equals(origin)) continue;
                    ItemStack tool = p.getMainHandItem();
                    int keep = UltimineConfig.PREVENT_TOOL_BREAK.get();
                    if (tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= Math.max(1, keep)) break;
                    if (p.getFoodData().getFoodLevel() <= 0 && !p.isCreative() && exhaustion > 0) break;
                    if (p.gameMode.destroyBlock(pos)) p.causeFoodExhaustion(exhaustion);
                }
            } finally {
                ACTIVE.remove(p.getUUID());
            }
        }

        @SubscribeEvent
        public void onUse(PlayerInteractEvent.RightClickBlock e) {
            if (!(e.getEntity() instanceof ServerPlayer p) || e.getHand() != InteractionHand.MAIN_HAND || ACTIVE.contains(p.getUUID())) return;
            State st = active(p);
            if (st == null || !(p.level() instanceof ServerLevel level)) return;
            ItemStack stack = p.getMainHandItem();
            boolean sameLayer;
            if (stack.getItem() instanceof AxeItem && UltimineConfig.RIGHT_CLICK_AXE.get()) sameLayer = false;
            else if (stack.getItem() instanceof HoeItem && UltimineConfig.RIGHT_CLICK_HOE.get()) sameLayer = true;
            else if (stack.getItem() instanceof ShovelItem && UltimineConfig.RIGHT_CLICK_SHOVEL.get()) sameLayer = true;
            else return;
            BlockPos origin = e.getPos();
            List<BlockPos> list = Shapes.compute(level, origin, e.getFace() == null ? Direction.UP : e.getFace(), Shapes.Shape.SHAPELESS, UltimineConfig.MAX_BLOCKS.get());
            ACTIVE.add(p.getUUID());
            try {
                for (BlockPos pos : list) {
                    if (pos.equals(origin) || sameLayer && pos.getY() != origin.getY()) continue;
                    if (stack.isEmpty()) break;
                    Direction f = e.getFace() == null ? Direction.UP : e.getFace();
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(f, 0.5), f, pos, false);
                    stack.useOn(new UseOnContext(level, p, InteractionHand.MAIN_HAND, stack, hit));
                }
            } finally {
                ACTIVE.remove(p.getUUID());
            }
        }

        private static Direction faceOf(ServerPlayer p, BlockPos origin) {
            HitResult hit = p.pick(p.blockInteractionRange() + 1, 1f, false);
            if (hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK && b.getBlockPos().equals(origin)) return b.getDirection();
            return Direction.orderedByNearest(p)[0].getOpposite();
        }
    }
}
