package dev.unyielding.jail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HexFormat;

/** A server installation is a set of loaded mod identities. Optional client JARs never install a permission. */
final class JailRuntimeProfile {
    static final String PACK_KERNEL = "21.1.249";

    private static final String BOUNDED_249 = "268c942530f240efea4b34bfce591d07da5c23a3f49360bf6d15a63965d696c8";
    // The bounded runtime plus the pack's exact Sophisticated Backpacks, Core, SOL Onion and CreativeCore.
    private static final String FOCUSED_ITEMS_249 = "681fb2244140c28863053b222ae32aeedd0c1b4379d8023b4a0e867a57fdf871";
    // Stock server roster plus the pinned roommedia.1 Storage Plus artifact.
    private static final String STOCK_WITH_STORAGE_249 = "50985dea6855dfe10b828b90bf7b41d2caca1221611b844e1f6706c0b6c3905c";
    // The same published roster, with the exact inspected spawn-quantity companion
    // and the exact roommedia.1 Storage Plus artifact present from startup.
    private static final String SPAWN_WITH_STORAGE_249 = "56172e09426f12aa24a50db27bc03755ce6d7186370febe8ba745acc597dbbee";

    private JailRuntimeProfile() {}

    static String seal(Collection<String> files) {
        String canonical = String.join("\n", files.stream().sorted().toList());
        return HexFormat.of().formatHex(JailOriginTransaction.hash(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    static boolean focusedItems(String kernel, Collection<String> files, boolean hostedItemsFixture) {
        return hostedItemsFixture && PACK_KERNEL.equals(kernel) && matches(seal(files), FOCUSED_ITEMS_249);
    }

    static boolean spawnWithStorage(String kernel, Collection<String> files) {
        return PACK_KERNEL.equals(kernel) && matches(seal(files), SPAWN_WITH_STORAGE_249);
    }

    static boolean check(String kernel, String originalKernel, Collection<String> files, String unsupported) throws IOException {
        if (!originalKernel.equals(kernel) && !PACK_KERNEL.equals(kernel))
            throw new IOException("Uncertified NeoForge kernel " + kernel);
        if (originalKernel.equals(kernel)) {
            if (unsupported == null) return false;
            throw new IOException("Uncertified mod " + unsupported);
        }
        String observed = seal(files);
        if (unsupported == null && matches(observed, BOUNDED_249)) return false;
        if (matches(observed, STOCK_WITH_STORAGE_249)) return true;
        if (matches(observed, SPAWN_WITH_STORAGE_249)) return true;
        try (InputStream input = JailRuntimeProfile.class.getResourceAsStream("/assets/minecraftjail/unyielding-249-server.sha256")) {
            if (input != null && matches(observed, new String(input.readAllBytes(), StandardCharsets.US_ASCII))) return true;
        }
        throw new IOException("Uncertified NeoForge 21.1.249 server file-set SHA-256 " + observed);
    }

    static boolean matches(String observed, String approved) {
        String pin = approved.strip();
        return pin.matches("[0-9a-f]{64}") && pin.equals(observed);
    }
}
