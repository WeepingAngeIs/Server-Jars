package dev.unyielding.jail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;

/** Refuse two owners of the same jail state before a world can be opened. */
final class LegacyBundleGuard {
    private static final String OLD_PACKAGE = String.join("/", "dev", "unyielding", "quests", "jail") + "/";
    private static final String OLD_MIXIN_PACKAGE = String.join("/", "dev", "unyielding", "quests", "mixin") + "/";
    private static final Set<String> OLD_MIXINS = Set.of(
            OLD_MIXIN_PACKAGE + "JailMovementMixin.class",
            OLD_MIXIN_PACKAGE + "CustodyPacketMixin.class",
            OLD_MIXIN_PACKAGE + "JailEquipmentAccess.class",
            OLD_MIXIN_PACKAGE + "JailXpDropMixin.class");
    private LegacyBundleGuard() {}
    static void verify(Path companion) {
        try {
            boolean integrated;
            if (Files.isDirectory(companion)) {
                integrated = Files.exists(companion.resolve(OLD_PACKAGE))
                        || OLD_MIXINS.stream().anyMatch(name -> Files.exists(companion.resolve(name)));
            } else {
                try (ZipFile jar = new ZipFile(companion.toFile())) {
                    integrated = jar.stream().anyMatch(entry ->
                            entry.getName().startsWith(OLD_PACKAGE) || OLD_MIXINS.contains(entry.getName()));
                }
            }
            if (integrated) throw new IllegalStateException(
                    "Minecraft Jail cannot load beside an Unyielding JAR that still contains jail code. "
                    + "Use the matching jail-free counterpart. No world or custody data was changed.");
        } catch (IOException | UnsupportedOperationException bad) {
            throw new IllegalStateException("Cannot verify the Unyielding companion; refusing unsafe jail ownership", bad);
        }
    }
}
