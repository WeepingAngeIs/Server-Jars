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

    private JailRuntimeProfile() {}

    static String seal(Collection<String> files) {
        String canonical = String.join("\n", files.stream().sorted().toList());
        return HexFormat.of().formatHex(JailOriginTransaction.hash(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    static boolean focusedItems(String kernel, Collection<String> files, boolean hostedItemsFixture) {
        return hostedItemsFixture && PACK_KERNEL.equals(kernel) && matches(seal(files), FOCUSED_ITEMS_249);
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
