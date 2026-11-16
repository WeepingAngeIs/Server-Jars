package dev.unyielding.jail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class LegacyBundleGuardTest {
    @TempDir Path directory;
    private Path jar(String name) throws Exception {
        Path file = directory.resolve("companion.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(name)); zip.write(new byte[]{1}); zip.closeEntry();
        }
        return file;
    }
    @Test void rejectsIntegratedBundle() throws Exception {
        assertThrows(IllegalStateException.class, () -> LegacyBundleGuard.verify(jar("dev/unyielding/quests/jail/JailData.class")));
    }
    @Test void rejectsResidualMixin() throws Exception {
        assertThrows(IllegalStateException.class, () -> LegacyBundleGuard.verify(jar("dev/unyielding/quests/mixin/CustodyPacketMixin.class")));
    }
    @Test void acceptsJailFreeBundle() throws Exception {
        assertDoesNotThrow(() -> LegacyBundleGuard.verify(jar("dev/unyielding/quests/quest/QuestManager.class")));
    }
    @Test void rejectsUnreadableBundle() {
        assertThrows(IllegalStateException.class, () -> LegacyBundleGuard.verify(directory.resolve("missing.jar")));
    }
    @Test void checksDevelopmentDirectory() throws Exception {
        Files.createDirectories(directory.resolve("dev/unyielding/quests/jail"));
        assertThrows(IllegalStateException.class, () -> LegacyBundleGuard.verify(directory));
    }
}
