package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@Mod("uq_bundle_smoke")
public final class BundleSmoke {
    private final List<String> evidence = new ArrayList<>();
    private final HashSet<UUID> all = new HashSet<>();
    private boolean ran;
    private int position;

    public BundleSmoke() {
        NeoForge.EVENT_BUS.addListener(this::verify);
    }

    private void verify(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (ran || server.getTickCount() < 20) return;
        ran = true;
        try {
            var admin = connect(server, "SpawnAdmin", 2);
            var guest = connect(server, "SpawnGuest", 0);
            for (int count : new int[]{1, 5, 10, 50, 99})
                check(admin, "pokespawn lopunny " + count, count, "lopunny", 0);
            check(admin, "spawnpokemon lopunny 20", 20, "lopunny", 0);
            check(admin, "pokespawn pikachu 5", 5, "pikachu", 0);
            check(admin, "pokespawn cobblemon:lopunny 5", 5, "lopunny", 0);
            check(admin, "pokespawn lopunny", 1, "lopunny", 0);
            check(admin, "spawnpokemon lopunny level=5", 1, "lopunny", 5);
            check(admin, "pokespawn lopunny level=5 5", 5, "lopunny", 5);
            check(admin, "spawnpokemonat ~ ~ ~ lopunny level=5", 1, "lopunny", 5);
            for (String count : List.of("0", "100", "-1", "2.5", "1e2", "2147483648", "99 99"))
                check(admin, "pokespawn lopunny " + count, 0, null, 0);
            check(admin, "pokespawn no_such_species 5", 0, null, 0);
            for (String alias : List.of("pokespawn", "spawnpokemon")) {
                check(guest, alias + " lopunny", 0, null, 0);
                check(guest, alias + " lopunny 5", 0, null, 0);
                check(admin, alias + " lopunny 2", 2, "lopunny", 0);
                var dispatcher = server.getCommands().getDispatcher();
                var suggestions = dispatcher.getCompletionSuggestions(
                        dispatcher.parse(alias + " lop", admin.createCommandSourceStack().withPermission(2))).join();
                require(suggestions.getList().stream().anyMatch(s -> s.getText().contains("lopunny")), suggestions.toString());
                evidence.add(alias + " retains Lopunny tab completion");
            }
            evidence.add("Distinct UUIDs across all successful cases: " + all.size());
            evidence.add("SPAWN QUANTITY PASS");
        } catch (Throwable error) {
            error.printStackTrace();
            evidence.add("FAIL: " + error);
        } finally {
            try {
                Files.write(Path.of("../../spawn-evidence/counts.txt"), evidence);
            } catch (Exception error) {
                error.printStackTrace();
            }
            server.halt(false);
        }
    }

    private void check(ServerPlayer player, String command, int expected, String species, int level) {
        ServerLevel world = player.serverLevel();
        // Stay in the connected player's ticking chunk. getChunk alone loads terrain,
        // but does not make newly inserted entities visible to the world lookup.
        var origin = player.blockPosition();
        Vec3 spot = new Vec3(origin.getX() + 0.5, world.getSeaLevel() + 5 + ++position * 3, origin.getZ() + 0.5);
        var before = pokemon(world);
        var source = player.createCommandSourceStack().withPosition(spot).withPermission(player.hasPermissions(2) ? 2 : 0);
        int result = 0;
        try {
            result = player.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException failure) {
            if (expected != 0) throw new AssertionError(command, failure);
        }
        var after = pokemon(world);
        after.keySet().removeAll(before.keySet());
        require(after.size() == expected && result == expected, command + " returned " + result + " and created " + after.size());
        for (var entity : after.values()) {
            var pokemon = entity.getPokemon();
            require(all.add(entity.getUUID()), "Duplicate UUID for " + command);
            require(pokemon.getSpecies().getResourceIdentifier().getPath().equals(species), "Wrong species for " + command);
            require(level == 0 || pokemon.getLevel() == level, "Wrong level for " + command);
        }
        evidence.add(player.getGameProfile().getName() + " /" + command + " returned " + result
                + ", added " + after.size() + " UUIDs " + after.keySet());
    }

    private static Map<UUID, PokemonEntity> pokemon(ServerLevel world) {
        Map<UUID, PokemonEntity> found = new HashMap<>();
        for (var entity : world.getAllEntities()) {
            if (entity instanceof PokemonEntity pokemon) found.put(entity.getUUID(), pokemon);
        }
        return found;
    }

    private static ServerPlayer connect(MinecraftServer server, String name, int permission) {
        var profile = new GameProfile(UUID.randomUUID(), name);
        var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault()) {
            @Override public boolean hasPermissions(int level) { return level <= permission; }
        };
        var transport = new Connection(PacketFlow.SERVERBOUND) {
            @Override public boolean isMemoryConnection() { return true; }
            @Override public void send(Packet<?> packet, PacketSendListener listener, boolean flush) {}
        };
        new EmbeddedChannel(transport);
        NetworkRegistry.configureMockConnection(transport);
        server.getPlayerList().placeNewPlayer(transport, player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
