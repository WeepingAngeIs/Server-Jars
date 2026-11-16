package dev.unyielding.jail;

import java.io.IOException;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * One packed, non-equipped physical wallet. No native wallet mutation API is called.
 * The existing exact-slot journal owns the complete stack and its native WalletData.
 * There is no second currency balance, denomination conversion, or reconstructed grant.
 */
final class JailPackedWallet {
    private static final String ITEM = "io.github.lightman314.lightmanscurrency.common.items.WalletItem";
    private static final String HANDLER = "io.github.lightman314.lightmanscurrency.common.attachments.WalletHandler";
    private static final Set<String> STACK_KEYS = Set.of("id", "count", "components");
    private JailPackedWallet() {}

    static boolean candidate(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(PackedWalletPolicy.WALLET);
    }
    static boolean present(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (candidate(player.getInventory().getItem(i))) return true;
        return false;
    }
    private static Object invoke(String api, Object target, String method, Class<?>[] types, Object... args) throws IOException {
        return JailEquipmentPort.call(api, target, method, types, args);
    }
    private static CompoundTag encoded(ServerPlayer p, ItemStack stack) throws IOException {
        if (stack.isEmpty()) throw new IOException("Unexpected empty custody stack");
        CompoundTag tag = (CompoundTag) stack.save(p.registryAccess());
        if (!STACK_KEYS.containsAll(tag.getAllKeys()) || !tag.contains("id", Tag.TAG_STRING) || !tag.contains("count", Tag.TAG_INT))
            throw new IOException("Attached/opaque stack storage is not certified");
        if (tag.contains("components") && !tag.contains("components", Tag.TAG_COMPOUND))
            throw new IOException("Invalid component patch");
        // Decode a detached copy, not ItemStack.copy(): nested component objects must
        // not be shared with the live source while validating them.
        ItemStack decoded = ItemStack.parseOptional(p.registryAccess(), tag.copy());
        if (decoded.isEmpty() || !tag.equals(decoded.save(p.registryAccess())) || !ItemStack.matches(stack, decoded))
            throw new IOException("Stack or nested components do not round-trip exactly");
        return tag;
    }
    private static void noEquippedWallet(ServerPlayer p) throws IOException {
        CompoundTag attachments = p.saveWithoutId(new CompoundTag()).getCompound("neoforge:attachments");
        CompoundTag internal = attachments.getCompound("lightmanscurrency:wallet");
        if (!attachments.contains("lightmanscurrency:wallet", Tag.TAG_COMPOUND)
                || !Set.of("Wallet", "Visible").containsAll(internal.getAllKeys())
                || !internal.contains("Wallet", Tag.TAG_COMPOUND) || !internal.getCompound("Wallet").isEmpty())
            throw new IOException("Native wallet attachment is absent, occupied or unsupported");
        Object handler = invoke(HANDLER, null, "get", new Class<?>[]{LivingEntity.class}, p);
        if (handler == null || handler.getClass() != JailEquipmentPort.type(HANDLER)
                || !((ItemStack) invoke(HANDLER, handler, "getWallet", new Class<?>[0])).isEmpty())
            throw new IOException("Equipped/Curios wallets need a separate custody contract");
        // Calling native inspection must not silently populate or migrate a wallet.
        if (!attachments.equals(p.saveWithoutId(new CompoundTag()).getCompound("neoforge:attachments")))
            throw new IOException("Native wallet inspection changed attachment state");
    }
    static boolean accepts(ServerPlayer p, int slot, ItemStack stack) throws IOException {
        if (!candidate(stack)) return false;
        JailHumanAudit.runtime(p); // Includes the unchanged exact companion and dependency hashes.
        if (stack.getItem().getClass() != JailEquipmentPort.type(ITEM)) throw new IOException("Unknown wallet item implementation");
        CompoundTag whole = encoded(p, stack), components = whole.getCompound("components");
        if (!components.contains(PackedWalletPolicy.DATA, Tag.TAG_COMPOUND)) throw new IOException("Uninitialized wallet data");
        CompoundTag data = components.getCompound(PackedWalletPolicy.DATA);
        if (!data.contains("Items", Tag.TAG_LIST) || !data.contains("AutoExchange", Tag.TAG_BYTE)
                || !data.contains("BonusSlots", Tag.TAG_INT)) throw new IOException("Invalid native WalletData field types");
        ItemStack detached = ItemStack.parseOptional(p.registryAccess(), whole.copy());
        int capacity = (int) invoke(ITEM, null, "InventorySize", new Class<?>[]{ItemStack.class}, detached);
        PackedWalletPolicy.envelope(slot, p.getInventory().selected, stack.getCount(), capacity, data.getInt("BonusSlots"),
                components.getAllKeys(), data.getAllKeys());
        for (String feature : new String[]{"HasBankAccess", "CanExchange", "CanPickup"})
            if ((boolean) invoke(ITEM, null, feature, new Class<?>[]{JailEquipmentPort.type(ITEM)}, stack.getItem()))
                throw new IOException("Wallet runtime enables unreviewed native feature: " + feature);
        ListTag contents = (ListTag) data.get("Items");
        if (contents == null || contents.size() != capacity || contents.getElementType() != Tag.TAG_COMPOUND)
            throw new IOException("Wallet has hidden/overflow or malformed content slots");
        int occupied = 0;
        for (Tag raw : contents) {
            CompoundTag row = (CompoundTag) raw;
            if (row.isEmpty()) continue;
            ItemStack coin = ItemStack.parseOptional(p.registryAccess(), row.copy());
            if (coin.isEmpty() || !row.equals(encoded(p, coin))) throw new IOException("Nested wallet content was lost or normalized");
            PackedWalletPolicy.coin(BuiltInRegistries.ITEM.getKey(coin.getItem()).toString(), coin.getCount(), coin.getMaxStackSize(), row.getCompound("components").getAllKeys());
            if (!coin.getItem().getClass().getName().equals("io.github.lightman314.lightmanscurrency.common.items.CoinItem"))
                throw new IOException("Unknown nested coin implementation");
            occupied++;
        }
        if (occupied == 0) throw new IOException("This contract requires an occupied wallet");
        if (!whole.equals(stack.save(p.registryAccess())) || !whole.equals(detached.save(p.registryAccess())))
            throw new IOException("Wallet changed during read-only audit");
        return true;
    }
    static void validateInventory(ServerPlayer p, Inventory source) throws IOException {
        noEquippedWallet(p);
        int count = 0;
        for (int i = 0; i < source.getContainerSize(); i++) if (candidate(source.getItem(i))) {
            accepts(p, i, source.getItem(i)); count++;
        }
        PackedWalletPolicy.single(count);
    }
    static void validateSnapshot(ServerPlayer p, byte[] vanilla) throws IOException {
        CompoundTag body = JailNbt.decode(vanilla);
        if (body.getInt("version") != 1 || body.getInt("vanillaSlots") != p.getInventory().getContainerSize()
                || !body.contains("items", Tag.TAG_LIST)) throw new IOException("Unknown wallet custody inventory schema");
        Inventory staged = new Inventory(p);
        staged.load(body.getList("items", Tag.TAG_COMPOUND));
        if (!body.getList("items", Tag.TAG_COMPOUND).equals(staged.save(new ListTag())))
            throw new IOException("Wallet source slots/components do not round-trip");
        validateInventory(p, staged);
    }
}
