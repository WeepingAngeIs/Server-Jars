package dev.unyielding.jail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeFileIdentityTest {
    @TempDir Path directory;
    private Path jar(String name, long stamp, String... files) throws IOException {
        Path archive = directory.resolve(name);
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (int i = 0; i < files.length; i += 2) {
                var item = new ZipEntry(files[i]); item.setTime(stamp); zip.putNextEntry(item);
                zip.write(files[i+1].getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        return archive;
    }

    @Test void completeFileNamesLengthsAndContentsDetermineArchiveIdentity() throws Exception {
        Path a = jar("a.jar", 12345, "m/A.class", "a", "META-INF/MANIFEST.MF", "m");
        Path b = jar("b.jar", 77777, "META-INF/MANIFEST.MF", "m", "m/A.class", "a");
        assertEquals(RuntimeFileIdentity.exactContents(a), RuntimeFileIdentity.exactContents(b));
        var changes = List.of(
                jar("class.jar", 12345, "m/A.class", "b", "META-INF/MANIFEST.MF", "m"),
                jar("manifest.jar", 12345, "m/A.class", "a", "META-INF/MANIFEST.MF", "n"),
                jar("rename.jar", 12345, "m/B.class", "a", "META-INF/MANIFEST.MF", "m"),
                jar("missing.jar", 12345, "m/A.class", "a"),
                jar("extra.jar", 12345, "m/A.class", "a", "META-INF/MANIFEST.MF", "m", "x.dat", ""));
        for (Path change : changes) assertNotEquals(RuntimeFileIdentity.exactContents(a), RuntimeFileIdentity.exactContents(change));
    }

    @Test void sameModIdCannotGrantEquivalenceToAnUnreviewedArchive() throws Exception {
        Path fake = jar("unverified.jar", 12345, "META-INF/neoforge.mods.toml", "modId = \"fabric_command_api_v2\"");
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(fake)));
        assertTrue(RuntimeFileIdentity.generated("fabric_command_api_v2"));
        assertFalse(RuntimeFileIdentity.generated("fabric_renderer_api_v1"));
        assertEquals(actual, RuntimeFileIdentity.digest("fabric_command_api_v2",fake));
        assertEquals(actual, RuntimeFileIdentity.digest("unknown",fake));
        assertNotEquals(RuntimeFileIdentity.exactContents(fake),actual);
    }

    @Test void ambiguousOrMalformedArchiveEntriesRefuse() throws Exception {
        for (String name : List.of("../file", "/absolute", "a//b", "a/./b", "C:/drive", "a\\b")) {
            Path bad=jar("bad.jar", 12345, name, "a");
            assertThrows(IOException.class, () -> RuntimeFileIdentity.entries(bad));
        }
        Path conflict=jar("conflict.jar",12345,"a","file","a/b","subfile");
        assertThrows(IOException.class,()->RuntimeFileIdentity.entries(conflict));
        Path duplicate=jar("duplicate.jar",12345,"abc","hi","xyz","bye");
        byte[] bytes=Files.readAllBytes(duplicate);
        for(int i=0;i<bytes.length-2;i++) {
            if(bytes[i]=='x' && bytes[i+1]=='y' && bytes[i+2]=='z') {
                bytes[i]='a';bytes[i+1]='b';bytes[i+2]='c';
            }
        }
        Files.write(duplicate,bytes);
        assertThrows(IOException.class,()->RuntimeFileIdentity.entries(duplicate));
    }
}
