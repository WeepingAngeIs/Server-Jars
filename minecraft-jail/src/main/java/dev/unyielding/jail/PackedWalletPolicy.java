package dev.unyielding.jail;

/** Bounds for the first physical-wallet contract; not permission to transfer an item. */
public final class PackedWalletPolicy {
    private PackedWalletPolicy() {}
    public record Shape(int slot, int selected, int count, int wallets, int capacity,
                        int encodedSlots, int bonusSlots, boolean autoExchange, boolean occupied) {}
    public static boolean accepts(Shape s) {
        return s != null && s.slot() >= 9 && s.slot() < 36 && s.slot() != s.selected()
                && s.count() == 1 && s.wallets() == 1 && s.capacity() == 6
                && s.encodedSlots() == 6 && s.bonusSlots() == 0
                && !s.autoExchange() && s.occupied();
    }
}
