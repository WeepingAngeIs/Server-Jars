package dev.unyielding.jail;

import java.io.IOException;
import java.util.Set;

/** The finite data envelope for the first physical-wallet custody contract. */
final class PackedWalletPolicy {
    static final String WALLET = "lightmanscurrency:wallet_leather";
    static final String DATA = "lightmanscurrency:wallet_data";
    static final Set<String> DECORATION = Set.of("minecraft:custom_name", "minecraft:lore", "minecraft:custom_data");
    static final Set<String> WALLET_COMPONENTS = Set.of(DATA, "minecraft:custom_name", "minecraft:lore", "minecraft:custom_data");
    static final Set<String> DATA_KEYS = Set.of("Items", "AutoExchange", "BonusSlots");
    private PackedWalletPolicy() {}

    static void envelope(int slot, int selected, int count, int capacity, int bonusSlots,
                         Set<String> components, Set<String> dataKeys) throws IOException {
        if (slot < 0 || slot >= 36 || selected < 0 || selected > 8 || slot == selected || count != 1)
            throw new IOException("Wallet must be a single item in a non-selected main inventory slot");
        if (capacity < 1 || capacity > 16 || bonusSlots != 0)
            throw new IOException("Wallet capacity/upgrades are outside the bounded contract");
        if (!WALLET_COMPONENTS.containsAll(components) || !components.contains(DATA) || !DATA_KEYS.equals(dataKeys))
            throw new IOException("Wallet has unknown/removed components or an unsupported data schema");
    }
    static void coin(String id, int count, int maximum, Set<String> components) throws IOException {
        if (!id.equals("lightmanscurrency:coin_gold") || count < 1 || maximum < 1 || maximum > 64 || count > maximum
                || !DECORATION.containsAll(components))
            throw new IOException("Wallet contains an unsupported coin provider, count, or component");
    }
    static void single(int count) throws IOException {
        if (count != 1) throw new IOException("Exactly one packed wallet is required");
    }
}
