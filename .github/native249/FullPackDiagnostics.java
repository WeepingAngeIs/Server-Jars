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
    private static int corpseSamples;
    static boolean tracingOrigin;
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
            probe(results,"puffish_native_live_and_saved_defaults",()->{
                var id=net.minecraft.resources.ResourceLocation.parse("puffish_attributes:armor_shred");
                var holder=net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(id).orElseThrow();
                if (!holder.value().getClass().getName().equals("net.puffish.attributesmod.api.DynamicEntityAttribute"))
                    throw new IllegalStateException("Wrong dynamic attribute provider");
                var defaults=net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(net.minecraft.world.entity.EntityType.PLAYER);
                var detached=defaults.createInstance(ignored->{},holder);
                if (!Double.isNaN(detached.getBaseValue()) || Double.doubleToRawLongBits(detached.save().getDouble("base"))!=0L)
                    throw new IllegalStateException("Unexpected live or saved dynamic attribute representation");
            });
            probe(results,"native_accessory_and_vanilla_slots",port::current);
            probe(results,"native_origin_choices_effects_and_claims",()->new JailCertifiedOriginPort(
                    player,port,UUID.randomUUID(),null,null).current());
            probe(results,"origin_neutral_base_and_modifiers",()->{
                var origin=new JailCertifiedOriginPort(player,port,UUID.randomUUID(),null,null);
                var method=JailCertifiedOriginPort.class.getDeclaredMethod("neutralBase");
                method.setAccessible(true);
                method.invoke(origin);
            });
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

    public static void originDifference(CompoundTag expected,CompoundTag observed,String stage) {
        if (!tracingOrigin || expected.equals(observed)) return;
        try {
            var report=new java.util.LinkedHashMap<String,Object>();
            report.put("stage",stage);
            var changedFields=new ArrayList<String>();
            var keys=new TreeSet<>(expected.getAllKeys());
            keys.addAll(observed.getAllKeys());
            for (String key:keys)
                if (!java.util.Objects.equals(expected.get(key),observed.get(key)))
                    changedFields.add(key.matches("[a-z_]{1,30}") ? key : "(other field)");
            report.put("changed_top_level_fields",changedFields);
            var paths=new ArrayList<String>();
            differences(expected,observed,"root",paths);
            report.put("paths_and_types",paths);
            var attributes=new ArrayList<String>();
            var nonDefaultAdditions=new ArrayList<String>();
            var defaults=net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(net.minecraft.world.entity.EntityType.PLAYER);
            var expectedIds=new java.util.HashSet<String>();
            for (var tag:expected.getList("attributes",Tag.TAG_COMPOUND)) {
                var before=(CompoundTag)tag;
                String id=before.getString("id");
                expectedIds.add(id);
                if (id.contains("cobble") || id.contains("poke")) continue;
                CompoundTag after=null;
                for (var row:observed.getList("attributes",Tag.TAG_COMPOUND))
                    if (((CompoundTag)row).getString("id").equals(id)) { after=(CompoundTag)row; break; }
                if (before.equals(after)) continue;
                if (after==null) { attributes.add(id+" absent in observed"); continue; }
                if (!java.util.Objects.equals(before.get("base"),after.get("base")))
                    attributes.add(id+" base "+before.get("base")+" -> "+after.get("base"));
                for (var pair:List.of(Map.entry("expected",before),Map.entry("observed",after)))
                    for (var element:pair.getValue().getList("modifiers",Tag.TAG_COMPOUND)) {
                        var m=(CompoundTag)element;
                        String modifier=m.getString("id");
                        if (modifier.contains("poke") || modifier.contains("cobble")) continue;
                        attributes.add(id+" "+pair.getKey()+" "+modifier+" "+m.getString("operation")+" "+m.getDouble("amount"));
                    }
            }
            for (var row:observed.getList("attributes",Tag.TAG_COMPOUND)) {
                var after=(CompoundTag)row;
                String id=after.getString("id");
                if (expectedIds.contains(id) || id.contains("poke") || id.contains("cobble")) continue;
                attributes.add(id+" appeared, base "+after.getDouble("base"));
                var holder=net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(net.minecraft.resources.ResourceLocation.parse(id)).orElseThrow();
                if (!defaults.hasAttribute(holder) || Double.compare(after.getDouble("base"),defaults.getBaseValue(holder))!=0)
                    nonDefaultAdditions.add(id+" with nondefault base "+after.getDouble("base"));
                if (!java.util.Set.of("id","base").equals(after.getAllKeys())) {
                    nonDefaultAdditions.add(id+" extra row fields "+after.getAllKeys());
                    for (Tag entry:after.getList("modifiers",Tag.TAG_COMPOUND)) {
                        var m=(CompoundTag)entry;
                        String modifier=m.getString("id");
                        if (!modifier.contains("cobble") && !modifier.contains("poke"))
                            nonDefaultAdditions.add(id+" "+modifier+" "+m.getDouble("amount")+" "+m.getString("operation"));
                    }
                }
            }
            report.put("changed_attribute_rows",attributes);
            report.put("new_rows_with_nondefault_state",nonDefaultAdditions);
            if (expected.contains("health",Tag.TAG_FLOAT) && observed.contains("health",Tag.TAG_FLOAT))
                report.put("health_expected_observed",List.of(expected.getFloat("health"),observed.getFloat("health")));
            Files.writeString(PlayerOnlySmoke.results.resolve("full249-origin-differences.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception ex) {
            System.out.println("FULL249_DIAGNOSTIC origin observer failed: "+ex.getClass().getSimpleName());
        }
    }

    /** Read the same live and durable projections after the disposable player dies. */
    static void sampleCorpse(ServerPlayer player) {
        int sample=corpseSamples++;
        if (sample!=0 && sample%5!=0) return;
        var report=new java.util.LinkedHashMap<String,Object>();
        report.put("sample",sample);
        report.put("tick",player.server.getTickCount());
        try {
            UUID tx=dev.unyielding.jail.JailIntegrated.activeTransaction(player.server,player.getUUID());
            if (tx==null) throw new IllegalStateException("No active custody");
            Object ports=call("JailIntegrated","ports",new Class<?>[]{net.minecraft.server.MinecraftServer.class,
                    ServerPlayer.class,UUID.class,UUID.class,String.class},
                    player.server,player,tx,null,"native-packed-wallet-v1");
            var accessor=ports.getClass().getDeclaredMethod("origin");
            accessor.setAccessible(true);
            var origin=(dev.unyielding.jail.JailOriginTransaction.Port)accessor.invoke(ports);
            byte[] liveBytes=origin.current().state();
            byte[] savedBytes=origin.durable().state();
            CompoundTag live=OriginCustodyPort.decode(liveBytes);
            CompoundTag saved=OriginCustodyPort.decode(savedBytes);
            report.put("exact_bytes_equal",java.util.Arrays.equals(liveBytes,savedBytes));
            report.put("pack_contract_applies",call("JailAttributeView","applies",new Class<?>[]{ServerPlayer.class},player));
            report.put("production_light_exception",call("JailPackState","onlyCorpseLightingDiffers",
                    new Class<?>[]{byte[].class,byte[].class},liveBytes,savedBytes));
            var paths=new ArrayList<String>();
            differences(live,saved,"player",paths);
            report.put("differences",paths);
            CompoundTag liveLight=(CompoundTag)call("JailPackState","completeLightCache",new Class<?>[]{CompoundTag.class},live);
            CompoundTag savedLight=(CompoundTag)call("JailPackState","completeLightCache",new Class<?>[]{CompoundTag.class},saved);
            report.put("live_light_cache_valid",liveLight!=null);
            report.put("saved_light_cache_valid",savedLight!=null);
            if (liveLight!=null && savedLight!=null) {
                report.put("live_light",liveLight.toString());
                report.put("saved_light",savedLight.toString());
                liveLight.putInt("sky_light",savedLight.getInt("sky_light"));
                liveLight.putInt("block_light",savedLight.getInt("block_light"));
                var residual=new ArrayList<String>();
                differences(live,saved,"player",residual);
                report.put("differences_after_light_normalization",residual);
                byte[] normalized=(byte[])call("JailNbt","encode",new Class<?>[]{CompoundTag.class},live);
                report.put("normalized_bytes_equal",java.util.Arrays.equals(normalized,savedBytes));
            }
        } catch (Exception ex) {
            Throwable cause=ex instanceof InvocationTargetException nested ? nested.getTargetException() : ex;
            report.put("observer_error",cause.getClass().getName()+": "+String.valueOf(cause.getMessage()));
        }
        try {
            String json=new GsonBuilder().setPrettyPrinting().create().toJson(report);
            if (sample==0) Files.writeString(PlayerOnlySmoke.results.resolve("combined-corpse-first.json"),json);
            Files.writeString(PlayerOnlySmoke.results.resolve("combined-corpse-latest.json"),json);
        } catch (Exception ex) {
            System.out.println("FULL249_DIAGNOSTIC corpse observer failed: "+ex.getClass().getSimpleName());
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
