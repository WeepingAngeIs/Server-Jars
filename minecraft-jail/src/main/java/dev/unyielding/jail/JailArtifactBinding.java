package dev.unyielding.jail;

/** Each implementation artifact has a distinct custody runtime identity, not a version-name exemption. */
final class JailArtifactBinding {
    static final String POLICY = "player-only-autorespawn-v1";
    private JailArtifactBinding() {}
    static String row(String sha512) {
        if(sha512==null||!sha512.matches("[0-9a-f]{128}"))throw new IllegalArgumentException("Exact Jail SHA-512 required");
        return "minecraftjail-implementation="+POLICY+":"+sha512;
    }
}
