package dev.unyielding.jail;

import java.io.IOException;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** A normal stack must survive the same native save/load used by the custody chest. */
final class JailOrdinaryItem {
    private static final Set<String> PROVIDERS_WITH_OWN_CUSTODY = Set.of(
            "lightmanscurrency", "sophisticatedbackpacks", "sophisticatedstorage", "sophisticatedcore");

    private JailOrdinaryItem() {}

    static boolean accepts(ServerPlayer player, ItemStack stack) throws IOException {
        var item = stack.getItem();
        String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
        if (namespace.equals("minecraft") || PROVIDERS_WITH_OWN_CUSTODY.contains(namespace)) return false;
        if (!JailHumanAudit.hasItems249Contract(player)) return false;
        if (!(stack.save(player.registryAccess()) instanceof CompoundTag encoded)
                || encoded.contains("neoforge:attachments")) return false;
        // Defaults supplied by the registered item type are identical on both sides of
        // a transfer. A custom per-stack provider can refer to storage outside the item.
        for (var change : stack.getComponentsPatch().entrySet()) {
            var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(change.getKey());
            if (type == null || !type.getNamespace().equals("minecraft")) return false;
        }
        ItemStack restored = ItemStack.parse(player.registryAccess(), encoded).orElse(ItemStack.EMPTY);
        return ItemStack.matches(stack, restored)
                && JailNbt.mismatch(encoded, restored.save(player.registryAccess()), "stack") == null;
    }
}
