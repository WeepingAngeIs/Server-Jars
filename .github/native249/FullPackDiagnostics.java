package dev.unyielding.bundlesmoke;

import com.google.gson.GsonBuilder;
import dev.unyielding.jail.JailCertifiedOriginPort;
import dev.unyielding.jail.JailEquipmentPort;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Independent read-only preflight observations strictly during the synthetic full-pack setup. */
public final class FullPackDiagnostics {
    private FullPackDiagnostics() {}
    private static boolean collecting;
    @FunctionalInterface private interface Probe { void run() throws Exception; }

    private static void probe(Map<String,String> out,String label,Probe action) {
        try {
            action.run();
            out.put(label,"passed");
        } catch (Exception ex) {
            Throwable cause=ex instanceof InvocationTargetException nested ? nested.getTargetException() : ex;
            String reason=String.valueOf(cause.getMessage());
            String lower=reason.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("pokemon") || lower.contains("cobblemon") || reason.contains("{") || reason.length()>240)
                reason="see the failing contract and type diagnostics";
            out.put(label,cause.getClass().getSimpleName()+": "+reason);
        }
    }

    private static Object call(String owner,String name,Class<?>[] signature,Object... args) throws Exception {
        var method=Class.forName("dev.unyielding.jail."+owner).getDeclaredMethod(name,signature);
        method.setAccessible(true);
        return method.invoke(null,args);
    }

    static void check(ServerPlayer player,JailEquipmentPort port) throws Exception {
        // The caller has just captured a snapshot, which verifies hosted runner,
        // generated world and test identity. No command or transaction runs here.
        var results=new TreeMap<String,String>();
        collecting=true;
        try {
            probe(results,"native_claims",()->FullPackSession.noClaims(player));
            probe(results,"native_accessory_and_vanilla_slots",port::current);
            probe(results,"native_origin_choices_effects_and_claims",()->new JailCertifiedOriginPort(
                    player,port,UUID.randomUUID(),null,null).current());
            probe(results,"death_providers_and_attached_state",()->call("JailPackState","verifyAttachments",
                    new Class<?>[]{ServerPlayer.class,CompoundTag.class},player,
                    player.saveWithoutId(new CompoundTag()).getCompound("neoforge:attachments")));
            probe(results,"packed_wallet_provider",()->{
                int slot=PackedWalletProof.SLOT;
                Object accepted=call("JailPackedWallet","accepts",new Class<?>[]{ServerPlayer.class,int.class,ItemStack.class},
                        player,slot,player.getInventory().getItem(slot));
                if (!Boolean.TRUE.equals(accepted)) throw new IllegalStateException("Packed wallet rejected");
            });
            probe(results,"neutral_equipment_state",()->call("JailEquipmentEffects","neutral",new Class<?>[]{ServerPlayer.class},player));
        } finally {
            collecting=false;
        }
        Files.writeString(PlayerOnlySmoke.results.resolve("full249-preflight.json"),new GsonBuilder().setPrettyPrinting().create().toJson(results));
    }

    public static void observe(CompoundTag root) {
        if (!collecting || !root.contains("abilities") || !root.contains("attributes") || !root.contains("persistent")) return;
        var paths=new ArrayList<String>();
        try {
            var bytes=new ByteArrayOutputStream();
            var out=new DataOutputStream(bytes);
            out.writeByte(Tag.TAG_COMPOUND);
            out.writeUTF("");
            call("JailNbt","payload",new Class<?>[]{Tag.class,DataOutput.class},root,out);
            CompoundTag read=NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),NbtAccounter.create(4L*1024*1024));
            differences(root,read,"root",paths);
            if (paths.isEmpty()) paths.add("equal");
            Files.writeString(PlayerOnlySmoke.results.resolve("full249-neutral-roundtrip.json"),new GsonBuilder().setPrettyPrinting().create().toJson(paths));
        } catch (Exception ex) {
            System.out.println("FULL249_DIAGNOSTIC neutral observer failed: "+ex.getClass().getSimpleName());
        }
    }

    private static void differences(Tag left,Tag right,String path,List<String> out) {
        if (left==right || left!=null && left.equals(right) || out.size()>=60) return;
        int before=out.size();
        if (left instanceof CompoundTag a && right instanceof CompoundTag b) {
            var keys=new TreeSet<>(a.getAllKeys());
            keys.addAll(b.getAllKeys());
            for (String key:keys) {
                String lower=key.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("poke") || lower.contains("cobble") || !key.matches("[A-Za-z0-9_:/.-]{1,64}") || key.matches("(?i)[0-9a-f-]{30,}")) continue;
                differences(a.get(key),b.get(key),path+"."+key,out);
            }
        } else if (left instanceof ListTag a && right instanceof ListTag b) {
            if (a.getElementType()!=b.getElementType() || a.size()!=b.size())
                out.add(path+" list types="+a.getElementType()+"/"+b.getElementType()+" sizes="+a.size()+"/"+b.size());
            for (int i=0;i<Math.min(a.size(),b.size());i++) differences(a.get(i),b.get(i),path+"["+i+"]",out);
        }
        if (before==out.size()) out.add(path+" types="+type(left)+"/"+type(right));
    }

    private static String type(Tag value) { return value==null?"absent":value.getType().getName(); }
}
