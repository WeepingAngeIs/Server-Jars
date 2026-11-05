package dev.unyielding.quests.essentials;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.function.Consumer;

import static dev.unyielding.quests.essentials.Essentials.*;

/** Operator tools: heal, feed, fly, god, speed, mute, invsee, kits. */
final class AdminCmds {
    private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath("unyieldingquests", "speed");

    private AdminCmds() {
    }

    private interface PlayerAction {
        String run(ServerPlayer p);
    }

    /** /name [player] for operators; targets yourself when no player is given. */
    private static LiteralArgumentBuilder<CommandSourceStack> simple(String name, PlayerAction a) {
        return Commands.literal(name).requires(s -> s.hasPermission(2))
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String msg = a.run(p);
                    if (msg != null) ok(c.getSource(), msg);
                    return 1;
                })
                .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                    String msg = a.run(p);
                    if (msg != null) ok(c.getSource(), msg + " (" + p.getGameProfile().getName() + ")");
                    return 1;
                }));
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        reg(d, "heal", simple("heal", p -> {
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(20f);
            p.removeAllEffects();
            return "Healed.";
        }));
        reg(d, "feed", simple("feed", p -> {
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(20f);
            return "Fed.";
        }));
        reg(d, "extinguish", simple("extinguish", p -> {
            p.clearFire();
            return "Extinguished.";
        }));
        reg(d, "fly", simple("fly", p -> {
            boolean on = !p.getAbilities().mayfly;
            p.getAbilities().mayfly = on;
            if (!on) p.getAbilities().flying = false;
            p.onUpdateAbilities();
            return "Flying " + (on ? "enabled." : "disabled.");
        }));
        reg(d, "god", simple("god", p -> {
            boolean on = !p.getAbilities().invulnerable;
            p.getAbilities().invulnerable = on;
            p.onUpdateAbilities();
            return "God mode " + (on ? "enabled." : "disabled.");
        }));
        reg(d, "speed", Commands.literal("speed").requires(s -> s.hasPermission(2))
                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.1, 10))
                        .executes(c -> setSpeed(c.getSource(), c.getSource().getPlayerOrException(), DoubleArgumentType.getDouble(c, "multiplier")))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> setSpeed(c.getSource(), EntityArgument.getPlayer(c, "player"), DoubleArgumentType.getDouble(c, "multiplier"))))));
        reg(d, "mute", Commands.literal("mute").requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer t = EntityArgument.getPlayer(c, "player");
                    boolean now = data().muted.add(t.getUUID());
                    if (!now) data().muted.remove(t.getUUID());
                    data().setDirty();
                    ok(c.getSource(), t.getGameProfile().getName() + (now ? " is now muted." : " is no longer muted."));
                    return 1;
                })));
        reg(d, "invsee", Commands.literal("invsee").requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer me = c.getSource().getPlayerOrException();
                    ServerPlayer t = EntityArgument.getPlayer(c, "player");
                    me.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x6, id, inv, new InvView(t), 6),
                            Component.literal(t.getGameProfile().getName() + "'s inventory")));
                    return 1;
                })));
        registerKits(d);
    }

    private static int setSpeed(CommandSourceStack s, ServerPlayer p, double mul) {
        var attr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(SPEED_ID);
            if (Math.abs(mul - 1.0) > 0.001) attr.addPermanentModifier(new AttributeModifier(SPEED_ID, mul - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        p.getAbilities().setFlyingSpeed((float) (0.05 * mul));
        p.onUpdateAbilities();
        ok(s, "Speed of " + p.getGameProfile().getName() + " set to x" + mul + ".");
        return 1;
    }

    /** A chest-sized window onto a player's inventory (36 main + 4 armour + offhand). */
    private static final class InvView implements Container {
        private final ServerPlayer t;

        InvView(ServerPlayer t) {
            this.t = t;
        }

        @Override
        public int getContainerSize() {
            return 54;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < 41; i++) if (!getItem(i).isEmpty()) return false;
            return true;
        }

        @Override
        public ItemStack getItem(int i) {
            var inv = t.getInventory();
            if (i < 36) return inv.getItem(i);
            if (i < 40) return inv.armor.get(i - 36);
            if (i == 40) return inv.offhand.get(0);
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int i, int n) {
            ItemStack s = getItem(i);
            return s.isEmpty() ? ItemStack.EMPTY : s.split(n);
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            ItemStack s = getItem(i);
            setItem(i, ItemStack.EMPTY);
            return s;
        }

        @Override
        public void setItem(int i, ItemStack s) {
            var inv = t.getInventory();
            if (i < 36) inv.setItem(i, s);
            else if (i < 40) inv.armor.set(i - 36, s);
            else if (i == 40) inv.offhand.set(0, s);
        }

        @Override
        public void setChanged() {
            t.getInventory().setChanged();
        }

        @Override
        public boolean stillValid(Player p) {
            return !t.hasDisconnected();
        }

        @Override
        public void clearContent() {
        }
    }

    // ---- kits -------------------------------------------------------------------

    private static void registerKits(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> kit = Commands.literal("kit");
        kit.then(Commands.literal("list").executes(c -> {
            if (data().kits.isEmpty()) return fail(c.getSource(), "There are no kits.");
            c.getSource().sendSuccess(() -> Component.literal("Kits: " + String.join(", ", data().kits.keySet())), false);
            return 1;
        }));
        kit.then(Commands.literal("create").requires(s -> s.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> create(c.getSource(), StringArgumentType.getString(c, "name"), 0))
                        .then(Commands.argument("cooldown_seconds", IntegerArgumentType.integer(0))
                                .executes(c -> create(c.getSource(), StringArgumentType.getString(c, "name"), IntegerArgumentType.getInteger(c, "cooldown_seconds"))))));
        kit.then(Commands.literal("delete").requires(s -> s.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(data().kits.keySet(), b))
                        .executes(c -> {
                            String n = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
                            if (data().kits.remove(n) == null) return fail(c.getSource(), "No such kit.");
                            data().setDirty();
                            ok(c.getSource(), "Kit deleted.");
                            return 1;
                        })));
        kit.then(Commands.literal("give").requires(s -> s.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(data().kits.keySet(), b))
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                            EssData.Kit k = data().kits.get(StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT));
                            if (k == null) return fail(c.getSource(), "No such kit.");
                            give(EntityArgument.getPlayer(c, "player"), k);
                            ok(c.getSource(), "Kit given.");
                            return 1;
                        }))));
        kit.then(Commands.argument("name", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(data().kits.keySet(), b))
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String n = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
                    EssData.Kit k = data().kits.get(n);
                    if (k == null) return fail(c.getSource(), "No such kit. Try /kit list.");
                    var claims = data().kitClaims.computeIfAbsent(p.getUUID(), x -> new java.util.HashMap<>());
                    Long last = claims.get(n);
                    long now = System.currentTimeMillis();
                    if (!p.hasPermissions(2) && last != null) {
                        if (k.cooldownSeconds <= 0) return fail(c.getSource(), "You already claimed this kit.");
                        long left = (last + k.cooldownSeconds * 1000L - now + 999) / 1000;
                        if (left > 0) return fail(c.getSource(), "You can claim this kit again in " + formatTime(left) + ".");
                    }
                    claims.put(n, now);
                    data().setDirty();
                    give(p, k);
                    ok(c.getSource(), "Kit '" + n + "' claimed.");
                    return 1;
                }));
        reg(d, "kit", kit);
    }

    private static String formatTime(long s) {
        if (s >= 3600) return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
        if (s >= 60) return (s / 60) + "m " + (s % 60) + "s";
        return s + "s";
    }

    private static void give(ServerPlayer p, EssData.Kit k) {
        for (ItemStack st : k.items) {
            ItemStack copy = st.copy();
            if (!p.getInventory().add(copy)) p.drop(copy, false);
        }
    }

    private static int create(CommandSourceStack s, String rawName, int cooldown) throws CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        EssData.Kit k = new EssData.Kit();
        k.cooldownSeconds = cooldown;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (!st.isEmpty()) k.items.add(st.copy());
        }
        if (k.items.isEmpty()) return fail(s, "Your inventory is empty - put the kit items in it first.");
        data().kits.put(rawName.toLowerCase(Locale.ROOT), k);
        data().setDirty();
        ok(s, "Kit '" + rawName.toLowerCase(Locale.ROOT) + "' created from your inventory (" + k.items.size() + " stacks, cooldown " + cooldown + "s; 0 = once only).");
        return 1;
    }

    @SuppressWarnings("unused")
    private static void each(Iterable<ServerPlayer> l, Consumer<ServerPlayer> c) {
        for (ServerPlayer p : l) c.accept(p);
    }
}
