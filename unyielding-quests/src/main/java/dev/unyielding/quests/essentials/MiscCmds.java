package dev.unyielding.quests.essentials;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static dev.unyielding.quests.essentials.Essentials.*;

/** Everyday helpers: crafting anywhere, ender chest, hat, nick, near, trash can, ... */
final class MiscCmds {
    private MiscCmds() {
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        reg(d, "crafting", Commands.literal("crafting").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new CraftingMenu(id, inv, ContainerLevelAccess.create(p.level(), p.blockPosition())) {
                @Override
                public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                    return true;
                }
            }, Component.translatable("container.crafting")));
            return 1;
        }));
        reg(d, "anvil", Commands.literal("anvil").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new AnvilMenu(id, inv, ContainerLevelAccess.create(p.level(), p.blockPosition())) {
                @Override
                public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                    return true;
                }
            }, Component.translatable("container.repair")));
            return 1;
        }));
        reg(d, "smithing", Commands.literal("smithing").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new SmithingMenu(id, inv, ContainerLevelAccess.create(p.level(), p.blockPosition())) {
                @Override
                public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                    return true;
                }
            }, Component.translatable("container.upgrade")));
            return 1;
        }));
        reg(d, "stonecutter", Commands.literal("stonecutter").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new StonecutterMenu(id, inv, ContainerLevelAccess.create(p.level(), p.blockPosition())) {
                @Override
                public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                    return true;
                }
            }, Component.translatable("container.stonecutter")));
            return 1;
        }));
        reg(d, "enderchest", Commands.literal("enderchest")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    openEnder(p, p);
                    return 1;
                })
                .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2)).executes(c -> {
                    openEnder(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"));
                    return 1;
                })));
        reg(d, "trashcan", Commands.literal("trashcan").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, new SimpleContainer(27)),
                    Component.literal("Trash can (items are destroyed)")));
            return 1;
        }));
        reg(d, "hat", Commands.literal("hat").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            ItemStack hand = p.getMainHandItem();
            if (hand.isEmpty()) return fail(c.getSource(), "Hold an item to wear it.");
            ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
            p.setItemSlot(EquipmentSlot.HEAD, hand.copy());
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, head);
            ok(c.getSource(), "Nice hat!");
            return 1;
        }));
        reg(d, "kickme", Commands.literal("kickme").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            p.connection.disconnect(Component.literal("You kicked yourself."));
            return 1;
        }));
        reg(d, "near", Commands.literal("near").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            double r = EssConfig.NEAR_RADIUS.get();
            List<String> out = new ArrayList<>();
            for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
                if (o == p || o.level() != p.level()) continue;
                double dist = Math.sqrt(o.distanceToSqr(p));
                if (dist <= r) out.add(o.getGameProfile().getName() + " (" + (int) dist + " blocks)");
            }
            if (out.isEmpty()) return fail(c.getSource(), "Nobody is within " + (int) r + " blocks.");
            ok(c.getSource(), "Nearby: " + String.join(", ", out));
            return 1;
        }));
        reg(d, "nick", Commands.literal("nick")
                .executes(c -> setNick(c.getSource(), c.getSource().getPlayerOrException(), null))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(c -> setNick(c.getSource(), c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name")))));
        reg(d, "rec", Commands.literal("rec").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            boolean on = data().rec.add(p.getUUID());
            if (!on) data().rec.remove(p.getUUID());
            data().setDirty();
            p.refreshDisplayName();
            p.refreshTabListName();
            c.getSource().getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal(p.getGameProfile().getName() + (on ? " is now recording." : " stopped recording.")).withStyle(ChatFormatting.RED), false);
            return 1;
        }));
    }

    private static void openEnder(ServerPlayer viewer, ServerPlayer owner) {
        viewer.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, owner.getEnderChestInventory()),
                Component.translatable("container.enderchest")));
    }

    private static int setNick(CommandSourceStack s, ServerPlayer p, String nick) {
        if (nick == null || nick.isBlank()) {
            data().nicks.remove(p.getUUID());
            ok(s, "Nickname removed.");
        } else {
            if (nick.length() > 32) return fail(s, "That nickname is too long (max 32).");
            data().nicks.put(p.getUUID(), nick);
            ok(s, "Your nickname is now " + dev.unyielding.quests.Text.codes(nick) + ".");
        }
        data().setDirty();
        p.refreshDisplayName();
        p.refreshTabListName();
        return 1;
    }
}
