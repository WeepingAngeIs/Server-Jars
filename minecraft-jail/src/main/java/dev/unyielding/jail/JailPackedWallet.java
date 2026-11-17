package dev.unyielding.jail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentType;

/** Data-only admission audit. The existing exact-slot journal owns the complete wallet stack.
 * Never calls WalletDataWrapper, moves coins separately, equips a wallet, or rewrites receipts. */
final class JailPackedWallet {
    private static final String ITEM = "io.github.lightman314.lightmanscurrency.common.items.WalletItem";
    private static final String DATA = "io.github.lightman314.lightmanscurrency.common.items.data.WalletData";
    private static final String HANDLER = "io.github.lightman314.lightmanscurrency.common.attachments.WalletHandler";
    private static final String WALLET = "lightmanscurrency:wallet_leather";
    private static final Set<String> DECORATION = Set.of("minecraft:custom_name", "minecraft:lore");
    private JailPackedWallet() {}

    static boolean present(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (BuiltInRegistries.ITEM.getKey(player.getInventory().getItem(i).getItem()).toString().equals(WALLET)) return true;
        return false;
    }
    static boolean accepts(ServerPlayer player, int slot, ItemStack stack) throws IOException {
        if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(WALLET)) return false;
        if (!JailConfig.JAIL_PACKED_WALLETS.get())
            throw new IOException("Packed-wallet custody is disabled; wallet was not moved");
        if (!stack.getItem().getClass().getName().equals(ITEM))
            throw new IOException("Unreviewed wallet provider");
        // Caller has already checked exact dependency/companion hashes. Recheck here too;
        // this helper must never become a filename/version-only trust route.
        JailHumanAudit.runtime(player);
        if (JailXp.nonzero(player)) throw new IOException("Packed-wallet plus retained-XP combination is not reviewed");
        nativeWalletEmpty(player);
        CompoundTag encoded = exact(player, stack, Set.of("minecraft:custom_name", "minecraft:lore", "lightmanscurrency:wallet_data"));
        Object data = stack.get(component("lightmanscurrency:wallet_data"));
        if (data == null || !data.getClass().getName().equals(DATA)) throw new IOException("Wallet data is missing or unreviewed");
        Object raw = JailHumanAudit.call(DATA, data, "items");
        if (!(raw instanceof List<?> contents)) throw new IOException("Unknown wallet slot store");
        if (contents.size() != 6) throw new IOException("Unexpected wallet slot count; no truncation is permitted");
        int occupied = 0;
        for (Object value : contents) {
            if (!(value instanceof ItemStack coin)) throw new IOException("Unknown wallet entry");
            if (coin.isEmpty()) continue;
            occupied++;
            if (!BuiltInRegistries.ITEM.getKey(coin.getItem()).toString().equals("lightmanscurrency:coin_gold")
                    || !coin.getItem().getClass().getName().equals("io.github.lightman314.lightmanscurrency.common.items.CoinItem")
                    || coin.getCount() < 1 || coin.getCount() > coin.getMaxStackSize())
                throw new IOException("Only ordinary physical gold coins are reviewed inside this wallet");
            exact(player, coin, DECORATION);
        }
        int wallets = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (player.getInventory().getItem(i).getItem().getClass().getName().equals(ITEM)) wallets++;
        Object capacity = stack.get(component("lightmanscurrency:wallet_capacity"));
        var shape = new PackedWalletPolicy.Shape(slot, player.getInventory().selected, stack.getCount(), wallets,
                capacity instanceof Integer n ? n : -1, contents.size(),
                (int) JailHumanAudit.call(DATA, data, "bonusSlots"),
                (boolean) JailHumanAudit.call(DATA, data, "autoExchange"), occupied > 0);
        if (!PackedWalletPolicy.accepts(shape))
            throw new IOException("Wallet requires one non-upgraded six-slot leather wallet in main inventory, physical coins and auto-exchange off");
        if (Boolean.TRUE.equals(JailHumanAudit.call(ITEM, null, "HasBankAccess",
                new Class<?>[]{stack.getItem().getClass()}, stack.getItem())))
            throw new IOException("Bank-enabled wallet configuration is not reviewed");
        // Detection must itself be read-only, including custom native component data.
        if (!encoded.equals(stack.save(player.registryAccess()))) throw new IOException("Wallet changed during audit");
        return true;
    }

    private static DataComponentType<?> component(String id) throws IOException {
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse(id));
        if (type == null) throw new IOException("Required wallet component is absent: " + id);
        return type;
    }
    private static CompoundTag exact(ServerPlayer player, ItemStack stack, Set<String> mutable) throws IOException {
        CompoundTag encoded = (CompoundTag) stack.save(player.registryAccess());
        if (!Set.of("id", "count", "components").containsAll(encoded.getAllKeys())
                || !mutable.containsAll(encoded.getCompound("components").getAllKeys()))
            throw new IOException("Unreviewed wallet/coin components, attachments or modified defaults");
        ItemStack decoded = ItemStack.parseOptional(player.registryAccess(), encoded.copy());
        if (decoded.isEmpty() || !encoded.equals(decoded.save(player.registryAccess())) || !ItemStack.matches(stack, decoded))
            throw new IOException("Complete wallet/coin state does not round-trip");
        return encoded.copy();
    }
    private static void nativeWalletEmpty(ServerPlayer player) throws IOException {
        try {
            Object supplier = Class.forName("io.github.lightman314.lightmanscurrency.common.core.ModAttachmentTypes")
                    .getField("WALLET_HANDLER").get(null);
            var type = (AttachmentType<?>) ((Supplier<?>) supplier).get();
            Object handler = player.getExistingDataOrNull(type);
            if (handler == null || !handler.getClass().getName().equals(HANDLER))
                throw new IOException("Native wallet attachment absent; inspection will not create it");
            // Inspect both routed and hidden backing state. No setWallet/unequip callback.
            for (String field : List.of("walletItem", "backupWallet", "moneyCacheWallet")) {
                Field f = Class.forName(HANDLER).getDeclaredField(field); f.setAccessible(true);
                if (!(f.get(handler) instanceof ItemStack value) || !value.isEmpty())
                    throw new IOException("Native wallet backing/cache is occupied or unsettled");
            }
            Object active = JailHumanAudit.call(HANDLER, handler, "getWallet");
            Object visible = JailHumanAudit.call(HANDLER, handler, "getVisibleWallet");
            if (!(active instanceof ItemStack a) || !a.isEmpty() || !(visible instanceof ItemStack v) || !v.isEmpty())
                throw new IOException("Equipped or cosmetic wallet requires its own custody adapter");
            CompoundTag stored = player.saveWithoutId(new CompoundTag()).getCompound("neoforge:attachments").getCompound("lightmanscurrency:wallet");
            if (!Set.of("Wallet", "Visible").containsAll(stored.getAllKeys())
                    || !stored.contains("Wallet", 10) || !stored.getCompound("Wallet").isEmpty())
                throw new IOException("Unknown or occupied native wallet storage");
        } catch (ReflectiveOperationException | ClassCastException error) {
            throw new IOException("Pinned native wallet API unavailable; no items moved", error);
        }
    }
}
