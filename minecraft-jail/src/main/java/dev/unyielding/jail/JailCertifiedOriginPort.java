package dev.unyielding.jail;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;

/** Separate opt-in contract for the verified native Human + Warrior/Nitwit combination. */
public class JailCertifiedOriginPort extends JailNativeOriginPort {
    static final String POLICY="native-supported-v2";
    record Effect(ResourceLocation power,ResourceLocation attribute,ResourceLocation modifier,double amount){}
    private List<Effect> declared=List.of();
    public JailCertifiedOriginPort(ServerPlayer p,JailVanillaCustody.InventoryPort inventory,UUID tx,UUID retired,byte[] existing)throws IOException {
        super(p,inventory,tx,retired,existing,true);if(existing==null)resolve();
    }
    @Override protected void resolve()throws IOException {
        super.resolve();ResourceLocation main=layer,mainHuman=human;
        var powered=new ArrayList<Map.Entry<ResourceLocation,ResourceLocation>>();for(var e:selections().entrySet())if(!powers(e.getValue()).isEmpty())powered.add(e);
        if(powered.size()>1)throw new IOException("Multiple powered origin/class layers are not certified; no layers were changed");
        if(powered.size()==1&&!powered.getFirst().getKey().equals(main)){
            if(!selections().get(main).equals(mainHuman))throw new IOException("A powered class currently requires native Human in the main layer");
            layer=powered.getFirst().getKey();original=powered.getFirst().getValue();human=id("neoorigins:class_nitwit");
            JsonObject raw=(JsonObject)call(ORIGINS,singleton(ORIGINS),"getRawOriginJson",new Class<?>[]{ResourceLocation.class},human);
            if(raw==null||!raw.has("name")||!raw.get("name").isJsonPrimitive()||!raw.get("name").getAsString().equals("origins.neoorigins.class_nitwit.name"))throw new IOException("Native unpowered Nitwit class is unavailable");
            available();
        }
    }
    private Holder<Attribute> attribute(ResourceLocation raw)throws IOException {
        String path=raw.getPath().replaceFirst("^(generic|player)\\.","");
        for(String candidate:List.of(raw.toString(),raw.getNamespace()+":"+path,raw.getNamespace()+":generic."+path,raw.getNamespace()+":player."+path)){
            var holder=BuiltInRegistries.ATTRIBUTE.getHolder(id(candidate));if(holder.isPresent())return holder.get();
        }
        throw new IOException("Origin attribute is unavailable: "+raw);
    }
    @Override protected String audit()throws IOException {
        versions();available();Object data=data();
        // The mutation machinery can describe more additive powers than the hosted
        // proof covers. Admission must not mistake that capability for certification.
        var selected=selections();ResourceLocation main=id("neoorigins:origin"),classes=id("neoorigins:class");
        if(!layer.equals(classes)||!original.equals(id("neoorigins:class_warrior"))||!human.equals(id("neoorigins:class_nitwit"))
                ||!selected.keySet().equals(Set.of(main,classes))||!id("neoorigins:human").equals(selected.get(main)))
            throw new IOException("Certified admissions currently require exactly native Human + Warrior; other origin/class combinations need their own hosted proof. No layers were changed");
        Set<ResourceLocation> verifiedPowers=Set.of(id("neoorigins:class_warrior_damage"),id("neoorigins:class_warrior_knockback_resist"),id("neoorigins:class_warrior_battle_stance"),id("neoorigins:class_warrior_endurance"),id("neoorigins:class_warrior_iron_will"));
        if(powers(original).size()!=verifiedPowers.size()||!new HashSet<>(powers(original)).equals(verifiedPowers))throw new IOException("Native Warrior power list differs from the verified combination");
        if((int)call(DATA,data,"getEvolutionTier")!=0)throw new IOException("Tier "+call(DATA,data,"getEvolutionTier")+" is not certified: Human gains powers at higher tiers");
        if(!((Set<?>)call(DATA,data,"getDynamicGrantedPowers")).isEmpty()||!((Set<?>)call(DATA,data,"getGlobalGrantedPowers")).isEmpty())throw new IOException("Dynamic/global granted powers have unaudited lifecycle hooks");
        for(String key:List.of("isPendingOrbCommit","isPendingLayerPickerCommit","isPendingAdminReselect"))if(Boolean.TRUE.equals(call(DATA,data,key)))throw new IOException("Finish the pending origin selection before jailing");
        try{var f=type(DATA).getDeclaredField("activeCooldowns");f.setAccessible(true);if(!((Map<?,?>)f.get(data)).isEmpty())throw new IOException("Unpersisted native cooldowns require a separate adapter");}catch(ReflectiveOperationException bad){throw new IOException("Cannot inspect cooldowns",bad);}
        if(!powers(human).isEmpty())throw new IOException("The temporary Human/Nitwit replacement has powers");
        for(var e:selections().entrySet())if(!e.getKey().equals(layer)&&!powers(e.getValue()).isEmpty())throw new IOException("Uncertified additional powered layer "+e.getKey()+": "+e.getValue());
        JsonObject manifest=new JsonObject();manifest.addProperty("adapter",POLICY);manifest.addProperty("runtime",JailRuntimeMigration.receiptRuntime(player,transaction));Set<ResourceLocation> ids=new HashSet<>(selections().values());ids.add(original);ids.add(human);
        for(var key:ids){JsonObject raw=(JsonObject)call(ORIGINS,singleton(ORIGINS),"getRawOriginJson",new Class<?>[]{ResourceLocation.class},key);if(raw==null)throw new IOException("Missing origin definition "+key);manifest.add(key.toString(),canonicalJson(raw));}
        var effects=new ArrayList<Effect>();Set<ResourceLocation> attributes=new HashSet<>();JsonObject definitions=new JsonObject();var manager=singleton("com.cyberday1.neoorigins.data.PowerDataManager");
        if(powers(original).size()>8)throw new IOException("Origin has more than eight powers; lifecycle not certified");
        for(var power:powers(original)){
            JsonObject raw=(JsonObject)call("com.cyberday1.neoorigins.data.PowerDataManager",manager,"getRawPowerJson",new Class<?>[]{ResourceLocation.class},power);
            if(raw==null||!raw.has("type"))throw new IOException("Power definition missing: "+power);String kind=raw.get("type").getAsString();
            Object holder=call("com.cyberday1.neoorigins.data.PowerDataManager",manager,"getPower",new Class<?>[]{ResourceLocation.class},power);Object implementation=call("com.cyberday1.neoorigins.api.power.PowerHolder",holder,"type");
            if(kind.equals("neoorigins:attribute_modifier")){
                if(implementation.getClass()!=type("com.cyberday1.neoorigins.power.builtin.AttributeModifierPower")||!Set.of("type","attribute","amount","operation","hidden").containsAll(raw.keySet())||!raw.get("operation").getAsString().equals("add_value"))throw new IOException("Power "+power+" has conditional/custom attribute hooks; not certified");
                double amount=raw.get("amount").getAsDouble();var attribute=attribute(id(raw.get("attribute").getAsString()));var key=attribute.unwrapKey().orElseThrow().location();String path=key.getPath().replaceFirst("^(generic|player)\\.","");
                if(!key.getNamespace().equals("minecraft")||!Set.of("attack_damage","armor","knockback_resistance","max_health","luck").contains(path)||!Double.isFinite(amount)||amount<=0||amount>10||!attributes.add(key))throw new IOException("Power "+power+" has unsupported/colliding attribute "+key);
                String powerKey=power.getNamespace()+"_"+power.getPath().replace('/','_');var modifier=ResourceLocation.fromNamespaceAndPath("neoorigins","power_"+powerKey+"_"+key.getPath()+"_add_value_"+Integer.toHexString(Double.hashCode(amount)));
                effects.add(new Effect(power,key,modifier,amount));
            }else if(kind.equals("neoorigins:effect_immunity")){
                if(implementation.getClass()!=type("com.cyberday1.neoorigins.compat.EffectImmunityPower")||!Set.of("type","effects","hidden").containsAll(raw.keySet())||!raw.has("effects")||!raw.get("effects").isJsonArray()||raw.getAsJsonArray("effects").size()!=1||!raw.getAsJsonArray("effects").get(0).getAsString().equals("minecraft:weakness"))throw new IOException("Power "+power+" has uncertified immunity configuration");
            }else throw new IOException("Power "+power+" ("+kind+") has callbacks not certified for custody; no origin was changed");
            definitions.add(power.toString(),canonicalJson(raw));
        }
        declared=List.copyOf(effects);manifest.add("powers",definitions);
        Map<String,Double> verifiedAttributes=Map.of("class_warrior_damage",1.0,"class_warrior_knockback_resist",0.3,"class_warrior_battle_stance",2.0,"class_warrior_endurance",2.0);
        Map<String,String> verifiedTargets=Map.of("class_warrior_damage","attack_damage","class_warrior_knockback_resist","knockback_resistance","class_warrior_battle_stance","armor","class_warrior_endurance","max_health");
        if(declared.size()!=4)throw new IOException("Native Warrior effect set differs from the verified combination");
        for(var effect:declared)if(!verifiedAttributes.containsKey(effect.power().getPath())||effect.amount()!=verifiedAttributes.get(effect.power().getPath())||!effect.attribute().getPath().replaceFirst("^(generic|player)\\.","").equals(verifiedTargets.get(effect.power().getPath())))throw new IOException("Native Warrior power changed: "+effect.power()+"; no mutation was authorized");
        String hash=HexFormat.of().formatHex(JailOriginTransaction.hash(canonicalJson(manifest).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if(intent!=null&&(!POLICY.equals(intent.getString("adapter"))||!hash.equals(intent.getString("definitions"))))throw new IOException("Certified origin definitions changed since capture");return hash;
    }
    float healthBonus(){return (float)declared.stream().filter(e->e.attribute().equals(Attributes.MAX_HEALTH.unwrapKey().orElseThrow().location())).mapToDouble(Effect::amount).sum();}
    void neutralBase()throws IOException {
        audit();effects();verifyNeutralAttributes();
    }
    @Override protected void verifyCallbackAttributes() throws IOException {
        if (JailAttributeView.applies(player)) verifyNeutralAttributes();
    }
    private void verifyNeutralAttributes() throws IOException {
        var defaults=DefaultAttributes.getSupplier(net.minecraft.world.entity.EntityType.PLAYER);Set<String> modifiers=new HashSet<>();declared.forEach(e->modifiers.add(e.modifier().toString()));
        CompoundTag neutral=JailNbt.decode(JailEquipmentEffects.neutral(player));
        for(Tag raw:neutral.getList("attributes",10)){
            CompoundTag row=(CompoundTag)raw;
            String attributeName=row.getString("id");
            var holder=attribute(id(attributeName));
            if(!defaults.hasAttribute(holder)||Double.compare(defaults.getBaseValue(holder),row.getDouble("base"))!=0)
                throw new IOException("Nondefault base attribute "+attributeName+" is not certified for respawn");
            for(Tag m:row.getList("modifiers",10)){
                CompoundTag mod=(CompoundTag)m;
                if (!modifiers.contains(mod.getString("id")) && !JailPackState.intrinsic(player,attributeName,mod.getString("id"),mod.getDouble("amount"),mod.getString("operation")))
                    throw new IOException("Additional attribute modifier "+mod.getString("id")+" is not certified for neutral custody");
            }
        }
    }
    @Override protected void effects()throws IOException {
        var selected=selections().get(layer);if(!selected.equals(original)&&!selected.equals(human))throw new IOException("Selected layer changed outside custody");boolean active=selected.equals(original);Set<ResourceLocation> ids=new HashSet<>();
        for(var e:declared){ids.add(e.modifier());var instance=player.getAttribute(attribute(e.attribute()));if(instance==null)throw new IOException("Native attribute missing: "+e.attribute());var m=instance.getModifier(e.modifier());if(active?(m==null||m.amount()!=e.amount()||m.operation()!=AttributeModifier.Operation.ADD_VALUE):m!=null)throw new IOException("Native effect does not match audited power "+e.power());}
        for(Tag raw:player.getAttributes().save()){var row=(CompoundTag)raw;var instance=player.getAttribute(attribute(id(row.getString("id"))));for(var m:instance.getModifiers())if(m.id().getNamespace().equals("neoorigins")&&!ids.contains(m.id()))throw new IOException("Unknown native modifier "+m.id()+"; refusing stale-effect sweeping");}
    }
    @Override void validateAdmission()throws IOException {super.validateAdmission();JailHealthShift.apply(player.getHealth(),healthBonus(),true);}
    @Override public byte[] prepareIntent(UUID tx,byte[] digest)throws IOException {
        super.prepareIntent(tx,digest);intent.putString("adapter",POLICY);ListTag modifiers=new ListTag();
        for(var e:declared){CompoundTag m=new CompoundTag();m.putString("attribute",e.attribute().toString());m.putString("id",e.modifier().toString());m.putDouble("amount",e.amount());m.putString("operation","add_value");modifiers.add(m);}
        intent.put("declared_modifiers",modifiers);intent.putFloat("health_bonus",healthBonus());return encode(intent);
    }
    @Override protected JailOriginTransaction.View plan(byte[] bytes,UUID tx,boolean temporary)throws IOException {
        intent=decode(bytes);audit();var before=current();if(temporary?!isPrepared(bytes,before):!isTemporary(bytes,tx,before))throw new IOException("Native origin/claim phase changed");
        CompoundTag body=decode(before.state());body.getCompound("origin_data").getCompound("origins").putString(layer.toString(),(temporary?human:original).toString());
        body.put("claims",expectedClaims(temporary));body.put("all_claims",expectedAllClaims(temporary));
        for(Tag raw:body.getList("attributes",10)){
            CompoundTag row=(CompoundTag)raw;var matching=declared.stream().filter(e->e.attribute().toString().equals(row.getString("id"))).toList();if(matching.isEmpty())continue;var e=matching.getFirst();ListTag mods=new ListTag();
            for(Tag m:row.getList("modifiers",10))if(!((CompoundTag)m).getString("id").equals(e.modifier().toString()))mods.add(m.copy());
            if(!temporary){CompoundTag m=new CompoundTag();m.putString("id",e.modifier().toString());m.putDouble("amount",e.amount());m.putString("operation","add_value");mods.add(m);}
            if(mods.isEmpty())row.remove("modifiers");else {
                mods.sort(Comparator.comparing(m -> ((CompoundTag)m).getString("id")));
                row.put("modifiers",mods);
            }
        }
        body.putFloat("health",JailHealthShift.apply(player.getHealth(),healthBonus(),temporary));body.put("receipt",receipt(tx,temporary?JailOriginTransaction.Receipt.TEMPORARY:JailOriginTransaction.Receipt.ORIGINAL));return new JailOriginTransaction.View(encode(body),tx,temporary?JailOriginTransaction.Receipt.TEMPORARY:JailOriginTransaction.Receipt.ORIGINAL);
    }
    @Override protected void expectedNativeHealth(CompoundTag expected,float hp,float max){float after=player.getMaxHealth();expected.putFloat("health",after>max?Math.min(hp+after-max,after):Math.min(hp,after));}
    @Override protected void finishDeclaredHealth(CompoundTag expected)throws IOException {float hp=expected.getFloat("health");if(hp<=0||hp>player.getMaxHealth()||!Float.isFinite(hp))throw new IOException("Unsafe normalized origin health");if(player.getHealth()!=hp)player.setHealth(hp);}
}
