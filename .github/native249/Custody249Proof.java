package dev.unyielding.bundlesmoke;

import dev.unyielding.jail.JailCustodyService;
import dev.unyielding.jail.JailData;
import dev.unyielding.jail.JailIntegrated;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import com.google.gson.GsonBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Exercises the production commands on two synthetic players, recording the actual loaded runtime. */
public final class Custody249Proof {
    private Custody249Proof() { }

    static void runtime() throws Exception {
        var mods = ModList.get();
        String kernel = mods.getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString();
        PlayerOnlySmoke.require("21.1.249".equals(kernel), "Wrong NeoForge runtime " + kernel);
        var hashes = new java.util.HashMap<Path,String>();
        var rows = new TreeMap<String,Map<String,String>>();
        for (var mod : mods.getMods()) {
            Path file = mod.getOwningFile().getFile().getFilePath();
            String digest = "runtime";
            if (Files.isRegularFile(file)) {
                digest = hashes.get(file);
                if (digest == null) {
                    var sum = MessageDigest.getInstance("SHA-512");
                    try (var input = new DigestInputStream(Files.newInputStream(file), sum)) {
                        input.transferTo(java.io.OutputStream.nullOutputStream());
                    }
                    digest = HexFormat.of().formatHex(sum.digest());
                    hashes.put(file,digest);
                }
            }
            rows.put(mod.getModId(), Map.of("version",mod.getVersion().toString(),"sha512",digest));
        }
        int phase = Integer.getInteger("uq.playerOnlyPhase");
        Files.writeString(Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke","runtime249-phase"+phase+".json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(rows));
        PlayerOnlySmoke.flag("actual_neoforge_21_1_249");
    }

    private static CompoundTag inventory(ServerPlayer player) {
        CompoundTag playerData = player.saveWithoutId(new CompoundTag());
        CompoundTag snapshot = new CompoundTag();
        snapshot.put("Inventory", playerData.getList("Inventory",10).copy());
        snapshot.putInt("SelectedItemSlot", playerData.getInt("SelectedItemSlot"));
        snapshot.put("attachments", playerData.getCompound("neoforge:attachments").copy());
        return snapshot;
    }

    static void refused(ServerPlayer player, String label) throws Exception {
        var before = inventory(player);
        int bindings = JailCustodyService.ledger(player.server).list().size();
        PlayerOnlySmoke.require(PlayerOnlySmoke.cmd("jail Visitor 6")==0, "Accepted unsupported " + label);
        PlayerOnlySmoke.require(inventory(player).equals(before), "Refusal modified inventory or attachment for " + label);
        PlayerOnlySmoke.require(JailData.get(player.server).assignment(player.getUUID())==null &&
                JailIntegrated.activeTransaction(player.server,player.getUUID())==null &&
                JailCustodyService.ledger(player.server).list().size()==bindings, "Refusal created custody for " + label);
        PlayerOnlySmoke.flag("refused_"+label);
    }

    static void refusals(ServerPlayer player) throws Exception {
        var inv = player.getInventory();
        int slot = PackedWalletProof.SLOT;
        var wallet = inv.getItem(slot).copy();
        var invalid = wallet.copy();
        PackedWalletProof.data(invalid,PackedWalletProof.coins(wallet),true,0);
        inv.setItem(slot,invalid);
        refused(player,"auto_exchange_wallet");
        inv.setItem(slot,wallet.copy());
        PlayerOnlySmoke.require(inv.getItem(33).isEmpty(), "Fixture collision in slot 33");
        inv.setItem(33,wallet.copy());
        refused(player,"multiple_wallets");
        inv.setItem(33,ItemStack.EMPTY);
        Object handler = Class.forName("io.github.lightman314.lightmanscurrency.common.attachments.WalletHandler")
                .getMethod("get",net.minecraft.world.entity.LivingEntity.class).invoke(null,player);
        var setter = handler.getClass().getMethod("setWallet",ItemStack.class);
        inv.setItem(slot, ItemStack.EMPTY);
        setter.invoke(handler,wallet.copy());
        CertifiedJailSmoke.flush();
        refused(player,"equipped_wallet");
        setter.invoke(handler,ItemStack.EMPTY);
        CertifiedJailSmoke.flush();
        inv.setItem(slot,wallet.copy());
        CertifiedJailSmoke.saved();
    }
}
