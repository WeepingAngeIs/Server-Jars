package dev.unyielding.jail;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Identities of equivalent Jar-in-Jar candidates in the pinned NeoForge 21.1.249 pack. */
public final class RuntimeFileIdentity {
    private RuntimeFileIdentity() {}
    // Exact embedded bytes extracted from the published, hash-pinned parent mods. A new or
    // repacked candidate never inherits the equivalence, even when it claims the same mod ID.
    private static final Map<String, Set<String>> CANDIDATES = Map.ofEntries(
        Map.entry("fabric_biome_api_v1", Set.of(
                "59a24fe2ccea9a2005c8261197c095cfc81f2df3afca2865b9acb6555acd7d2d2c6857783ad5e1c5aa509edda12e35916082dace63f223efaef81e34bb034b45",
                "deaa14916d290b56704fc4190c1f6a489fa5916fc122ac7e810fa621de3c2e5f3eb13e81a3aa833502a72e6d65a5c42eed293164eeab56eabafee30f397e12e8")),
        Map.entry("fabric_block_view_api_v2", Set.of(
                "b411493e2e50c1cb86d687f4dc74323363febee42a746d9a5dd9fe343e197cf64fff2ba09b509fd458423237e48083984649a5964e55239a41896708c8abdc50",
                "f43bc9f91ca109109bd84a09c48e2b41fb12f730715a9738564ed1a1588faaec36b0d7df43495eadce699c83678edede1fcc94fa013db6d5c1797a4178bbe147")),
        Map.entry("fabric_command_api_v2", Set.of(
                "c2c0c84fe67fe1dc7d0e2a67d69ca773776e1e150f9e605c93f45efca624476d31cd81bcd0c5ac3c84375501d83e3a7b1cb01bee31ee58b82037c430872c055b",
                "d60c01cf2fb25e3c0e7c3e6bf638f704c3b57f07e69535733ef45b8494531942712403cafa1dae640c1acc6744452541dbeb11eb60540e65c59971df0168e73b")),
        Map.entry("fabric_game_rule_api_v1", Set.of(
                "4f069255a81e8f1eaa06d8032db55c6f348ca439f0df92b330511a0f1afdecc708ae6718dc9bf52abdfff550ef5da60112da2afe80d5a4b3887212239c49b7f1",
                "ea61e100a7347ae4f921c023abf769c9f5180b76b182a173c133f3ffdca0e42f0c35d202c161f89835e592b3e08b2b0a24a0f2e96ab44db301fa1b20a03b801b")),
        Map.entry("fabric_gametest_api_v1", Set.of(
                "c224b2e8df0952a5a016f70f20135512610fc7e7f68b51f31d4130119740487b2920d625846554818e648514fbd5370ad700673539709dbf6b7d02e1febddab0",
                "ef4ffd01dcdd39e7120c269df3cfc64f16241bb45f983c4c994280bfb875eaf99b7fdd6590745c23af6ebdbb9c1611837aec1777bf2c0c935c1b8361e0eb040c")),
        Map.entry("fabric_item_group_api_v1", Set.of(
                "1ddda5003869a69f8d49dfe687fb6f4f22418252344966ff353303ed2cab3383ce09e20b13fec7313cf5b31628404ed591bbc27a65436af9e3923d5ba6d62d5a",
                "8ffbe1fd32ce36c98eccac17e571d0955013fbb2efa510bcf69326d2153866cd1c90fee72cef3d50faf9f525612fbd93e4d1ff4ddc21307106dcb094f54e0081")),
        Map.entry("fabric_key_binding_api_v1", Set.of(
                "24645ad000ab6e8bdc86f8221d9777d0e12cdcecea917268712803a7d63b07b15b48247b7039228810786f1081ebea72eac912ed517df0f9f0a6718a6e5116a4",
                "695a0c1cbe12615e110f8980b7f9771ba21383b0baf8ee6c3ecc74dda823ab8f577d4be1a370337aedc60679b062fd3fc3c1b858422d92f98125b93df0f5ab41")),
        Map.entry("fabric_loot_api_v2", Set.of(
                "270192c741517893cd2adcbbc620582278a1cbc89fc81b1e97db2deee9f0d56e4e2f3cb91b720e4c9ed8db7fde5f6001e09835a0f2a6a0d5d3ed76b71de063de",
                "2d4747ab687ad2b3ab081f405c9379776e16a5dea2ef6a2c4637bcede67e79a0e665aeaf5422bc891f9311d3ce85531258680e8b03c9b0190e45c5b6b614bfd8")),
        Map.entry("fabric_loot_api_v3", Set.of(
                "a65d5dbde96f64bfaa5112b5c6367adf22f33d1b4b81f424f822cdf16f0bb301b81f8cb53de957aa2f1b151c54e66dd922f36cec11f92d81b38cc7cb9f355da7",
                "da5d36cfa250437ca4c5565a636964ac807b52af863eba0cc2c34b2c5fa38d721610baff4044b15745750bce1a6501077a31f5307eefa80237c1e7195fabf5fd")),
        Map.entry("fabric_object_builder_api_v1", Set.of(
                "8490dd2f52751b17281b7826afd2b7c14b67082c62dc3f46da6751be65e2ccf296c015e5dfa510b7bd8009690f1306835b251ad2ce67096fb12816f3744b6e84",
                "d4e85070c69b79d6ab9c139cd8cd0a9cbe0b9c8d46b457596e4eadde1e5f3a28fdff2bb6887a9ea71d7795b0947b01407ce9068bd84341807d2541344d90a733")),
        Map.entry("fabric_particles_v1", Set.of(
                "05eb6fb8afa85eb0ced65c547770e7e0ebc0ada588ea5eb0972e48da2f07486a8e35a1e822c533f7661ea43ac9fab7f8d100277e8f0aabe493c60a89babca2ae",
                "6e9bc8e6491be351489052fe3ef55e71989c5f00aa51356462428d9c1ccb9e83a51e85869c31b9da53c2692c0e9dea2fc03e831ba3c6c3a3a333e8064056ce13")),
        Map.entry("fabric_rendering_data_attachment_v1", Set.of(
                "43feb05f64062cd9dc7909c35e42219f7213089ba6f5c008b7808dca0dd32a3e99523b9587fc6d92bdfe832091fda5e042d67c3d23926e9e119bc928664783e2",
                "973e2caba71561c5d7211a90fba6f0d642ed2e7157df79d9631480b720328e6208e8329f47d0db7d203be97eb45c249e92e081c0cc56b39f13db9ebefbf7cd13")),
        Map.entry("fabric_resource_conditions_api_v1", Set.of(
                "3a84aa2930179052a9b31e6960a1afe186e439821e54097ad5ebf3c51f41de8b62b04d011529f9dbbd7a5995d3bcfbf4d08d5d88bbf829c4d6c5b63b2a562314",
                "b9902cba60e946ad52b4f9dacd99a2243b2460c3b53bc0514285b2e1ae79e517615a46f86b3fdc24b624c6eb531458a48ee7d823e6aa487a8f70ec87a9efc6f0")),
        Map.entry("fabric_resource_loader_v0", Set.of(
                "6d8e97ae18ea924a6fa63855286017b94833f0e178ca093200283515a9e6267e8b4ee54e15f2a1118ce00ae7427276600184d922ef57dcbbeec3a003c69ac434",
                "8985dc18abd9a29212ca69829bdf72983867d006481571903e17aaec20df411f2817d2639eaea76759ad5e58ee4e563f70ba58e74a0f77452434b9f8720df84d")),
        Map.entry("fabric_sound_api_v1", Set.of(
                "c1a1d79ad7ae59b252ca469306fe934550a1c8fa07706660a1cb673c8e6a6f7524de286c0f5f171b451253aacc2b22d0df04641fe20292c1ebc2a9e8fc781411",
                "f5d950d6c5e509a36084dbd17a1ced5877382bb6700aa0febca83c81d25e9a37e6c524e55c1d28a78192bed5b9134dfc37468aef6c2d3eaf7df77cbf090536ef")),
        Map.entry("fabric_transitive_access_wideners_v1", Set.of(
                "321b89f4be14b436e6055382f8b23f0b103d6fb6bb3c671029eaa1f86261810b227f2f3b03bed71e8c62dbf67572b98f5a1a914fc040062395a184ffe325d17d",
                "948f74126d1704b40d85881875731e7166334e3796048d1c9763110396af89d1247cd9b4d5c11fd6286954fa4eaf2d8e004f5bf1c7ebf9392064de90c5a56c81")),
        Map.entry("tiny_config", Set.of(
                "2aff5a1aa61cfb5e7e56f7b648701becaada2a832e890c48dc3e04c092ab23cab1c105bda988d0fb67eec7924a5979f5a97007505778ced204e3e7240d5eea44",
                "2b0c595f1d04d2de1b68a8d5389e2c72ff4c0001cd5bb28e178f54b56f8552cbd94bd2d4effa781ea2ce15368b94668a8c1df81145412db7d6083367a1b8bebf",
                "918716a0e658a9cc26fc00a0bf2cf1869ee104507a3e32ddc11b8570d7343c2edec193558f16e1ae5b8d9636209534fad4a4f49879cbb82bcf5491a71b29d2ed",
                "c741fb87814bdf3970fe2d4aba029a87c2ac509a9630a07ec704af18b08baeb214c758ddc34c21361fc4aa8ffd76cb32855b881e98bcda3f0401bdb92aa9f6fd"))
    );

    public static boolean generated(String modId) { return CANDIDATES.containsKey(modId); }

    public static String digest(String id, Path archive) throws IOException {
        MessageDigest raw = sha512();
        try (InputStream input = Files.newInputStream(archive)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = input.read(buffer)) != -1;) raw.update(buffer, 0, n);
        }
        return digest(id, archive, HexFormat.of().formatHex(raw.digest()));
    }

    static String digest(String id, Path archive, String raw) throws IOException {
        if (!CANDIDATES.getOrDefault(id, Set.of()).contains(raw)) return raw;
        return canonical(read(archive, id));
    }

    /** Raw, complete extracted entry hashes for independent census diagnostics. */
    public static SortedMap<String,String> entries(Path archive) throws IOException {
        SortedMap<String,String> result = new TreeMap<>();
        read(archive, "").forEach((name, entry) -> result.put(name, entry.hash()));
        return Collections.unmodifiableSortedMap(result);
    }

    private record Entry(int size, String hash) {}
    static String exactContents(Path archive) throws IOException { return canonical(read(archive, "")); }

    private static String canonical(SortedMap<String,Entry> contents) throws IOException {
        MessageDigest result = sha512();
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), result))) {
            out.write("MinecraftJail/21.1.249/JarJar/v1\0".getBytes(StandardCharsets.US_ASCII));
            out.writeInt(contents.size());
            for (var entry : contents.entrySet()) {
                byte[] name = entry.getKey().getBytes(StandardCharsets.UTF_8);
                out.writeInt(name.length);
                out.write(name);
                out.writeLong(entry.getValue().size());
                out.write(HexFormat.of().parseHex(entry.getValue().hash()));
            }
        }
        return HexFormat.of().formatHex(result.digest());
    }

    private static SortedMap<String,Entry> read(Path path, String id) throws IOException {
        SortedMap<String,Entry> entries = new TreeMap<>();
        Set<String> paths = new HashSet<>();
        int total = 0;
        try (var zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                String name = entry.getName();
                String logical = entry.isDirectory() ? name.substring(0, name.length() - 1) : name;
                if (logical.isEmpty() || logical.contains("\\") || logical.contains(":") ||
                        logical.chars().anyMatch(c -> c < 32 || c == 127) ||
                        Arrays.stream(logical.split("/", -1)).anyMatch(p -> p.isEmpty() || p.equals(".") || p.equals("..")) ||
                        !paths.add(logical) || paths.size() > 4096)
                    throw new IOException("Malformed or duplicate embedded mod path");
                if (entry.isDirectory()) {
                    if (zip.read() != -1) throw new IOException("Embedded directory contains bytes");
                    continue;
                }
                int budget = 16 * 1024 * 1024 - total;
                byte[] bytes = zip.readNBytes(Math.min(budget, 2 * 1024 * 1024) + 1);
                if (bytes.length > budget || bytes.length > 2 * 1024 * 1024) throw new IOException("Oversize embedded mod");
                total += bytes.length;
                byte[] effective = effective(id, name, bytes);
                entries.put(name, new Entry(effective.length, HexFormat.of().formatHex(sha512().digest(effective))));
            }
        }
        for (String pathName : paths) {
            for (int slash = pathName.indexOf('/'); slash >= 0; slash = pathName.indexOf('/', slash+1))
                if (entries.containsKey(pathName.substring(0, slash))) throw new IOException("File used as embedded directory");
        }
        if (entries.isEmpty()) throw new IOException("Empty embedded mod");
        return entries;
    }

    private static byte[] effective(String id, String name, byte[] bytes) throws IOException {
        if (id.isEmpty() || id.equals("tiny_config")) return bytes;
        if (name.equals("META-INF/neoforge.mods.toml")) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            String prefix = "[[dependencies." + id + "]]\nmodId = \"neoforge\"\ntype = \"required\"\nversionRange = \"[21.1.";
            int start = text.indexOf(prefix);
            if (start == -1 || text.indexOf(prefix, start+1) != -1) throw new IOException("Unexpected embedded dependency block");
            int min = start + prefix.length();
            if (!text.startsWith("169,)\"\n", min) && !text.startsWith("248,)\"\n", min))
                throw new IOException("Unexpected embedded NeoForge dependency range");
            // Both reviewed requirements admit exactly the active kernel for this profile.
            return (text.substring(0, min) + "249" + text.substring(min+3)).getBytes(StandardCharsets.UTF_8);
        }
        if (id.equals("fabric_transitive_access_wideners_v1") && name.equals("META-INF/accesstransformer.cfg")) {
            // The pinned alternatives contain the same 332 lines. All directives name distinct
            // exact targets with one access setting per target and no wildcards.
            String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n", -1);
            Arrays.sort(lines);
            return String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
        }
        return bytes;
    }

    private static MessageDigest sha512() {
        try { return MessageDigest.getInstance("SHA-512"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
