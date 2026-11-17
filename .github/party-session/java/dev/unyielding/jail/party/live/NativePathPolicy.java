package dev.unyielding.jail.party.live;

import java.io.IOException;
import java.nio.file.*;

/** Test-only exact native spelling plus canonical identity. Never a path-prefix permission. */
public final class NativePathPolicy {
    private NativePathPolicy() {}
    public static Path checkedFile(Path emitted) throws IOException {
        Path absolute=emitted.toAbsolutePath();
        // Only harmless dot spelling from the bound provider is tolerated. Never collapse traversal.
        for(Path part:absolute)if(part.toString().equals(".."))throw new IOException("Parent traversal refused");
        Path canonical=absolute.normalize();
        if(!Files.isRegularFile(absolute,LinkOption.NOFOLLOW_LINKS)
                ||!absolute.toRealPath().equals(canonical)
                ||!absolute.getParent().toRealPath().equals(canonical.getParent()))
            throw new IOException("Missing, symbolic or noncanonical native file");
        return canonical;
    }
    public static boolean temporary(Path authorizedCurrent,Path nativeCurrent,Path requested) {
        try {
            Path canonical=checkedFile(nativeCurrent);
            if(!authorizedCurrent.isAbsolute()||!authorizedCurrent.equals(canonical))return false;
            // The pinned native save dispatcher constructs File(getAbsolutePath() + ".temp").
            // Compare the exact emitted spelling BEFORE resolving filesystem identity.
            Path emitted=Path.of(nativeCurrent.toAbsolutePath()+".temp");
            if(!requested.isAbsolute()||!requested.equals(emitted))return false;
            return checkedFile(requested).equals(Path.of(canonical+".temp"));
        }catch(IOException|RuntimeException refusal){return false;}
    }
}
