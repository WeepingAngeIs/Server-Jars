package dev.unyielding.jail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HexFormat;

/** A server installation is a set of loaded mod identities. Optional client JARs never install a permission. */
final class JailRuntimeProfile {
    static final String PACK_KERNEL = "21.1.249";

    private JailRuntimeProfile() {}

    static String seal(Collection<String> files) {
        String canonical = String.join("\n", files.stream().sorted().toList());
        return HexFormat.of().formatHex(JailOriginTransaction.hash(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    static boolean check(String kernel, String originalKernel, Collection<String> files, String unsupported) throws IOException {
        if (!originalKernel.equals(kernel) && !PACK_KERNEL.equals(kernel))
            throw new IOException("Uncertified NeoForge kernel " + kernel);
        if (unsupported == null) return false;
        String observed = seal(files);
        if (PACK_KERNEL.equals(kernel)) {
            try (InputStream input = JailRuntimeProfile.class.getResourceAsStream("/assets/minecraftjail/unyielding-249-server.sha256")) {
                if (input != null && matches(observed, new String(input.readAllBytes(), StandardCharsets.US_ASCII))) return true;
            }
        }
        throw new IOException("Uncertified mod " + unsupported + " (server file-set SHA-256 " + observed + ")");
    }

    static boolean matches(String observed, String approved) {
        String pin = approved.strip();
        return pin.matches("[0-9a-f]{64}") && pin.equals(observed);
    }
}
