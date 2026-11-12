package dev.unyielding.quests.settings;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeature;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.requirements.PokemonPropertiesRequirement;
import dev.unyielding.quests.net.Net;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small display-only goals: native species packets do not include evolution requirements. */
public final class PokemonProgressSync {
    public static final Set<String> FEATURES=Set.of(PokemonCounterRules.DEFEATS,PokemonCounterRules.DEFEATS_1000,PokemonCounterRules.MEALS);
    private static long sequence;
    private PokemonProgressSync() {}

    public record Sync(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Sync> TYPE=new Type<>(ResourceLocation.parse("unyieldingquests:pokemon_progress_goals"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Sync> CODEC=StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG,Sync::data,Sync::new);
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Sync.TYPE,Sync.CODEC,(packet,context)->
                dev.unyielding.quests.client.PokemonCounterDisplay.accept(packet));
    }
    public static String formKey(String species,String form) {
        // Fixed-size form keys also keep unusually long configured names out of StringTags.
        return species+"|"+UUID.nameUUIDFromBytes(form.getBytes(StandardCharsets.UTF_8));
    }
    public static CompoundTag snapshot() {
        Map<String,Map<String,Integer>> incoming=new TreeMap<>(),direct=new TreeMap<>();
        for(Species species:PokemonSpecies.getSpecies()) {
            Set<FormData> forms=Collections.newSetFromMap(new IdentityHashMap<>());
            forms.add(species.getStandardForm());forms.addAll(species.getForms());
            for(FormData form:forms)for(Evolution evolution:form.getEvolutions()) {
                Map<String,Integer> goals=goals(evolution);
                if(goals.isEmpty())continue;
                merge(direct,formKey(species.getResourceIdentifier().toString(),form.getName()),goals);
                PokemonProperties result=evolution.getResult();
                Species target=target(result.getSpecies());
                if(target==null)continue;
                FormData targetForm=result.getForm()==null
                        ?target.getForm(result.getAspects()==null?Set.of():result.getAspects()):target.getFormByName(result.getForm());
                if(targetForm!=null)merge(incoming,formKey(target.getResourceIdentifier().toString(),targetForm.getName()),goals);
            }
        }
        // A species' own pending conditions take priority over inherited progress after evolution.
        direct.forEach((key,goals)->incoming.computeIfAbsent(key,ignored->new TreeMap<>()).putAll(goals));
        CompoundTag snapshot=new CompoundTag();ListTag rows=new ListTag();
        incoming.forEach((key,goals)->{CompoundTag row=new CompoundTag();row.putString("key",key);
            CompoundTag values=new CompoundTag();goals.forEach(values::putInt);row.put("goals",values);rows.add(row);});
        snapshot.put("rows",rows);return snapshot;
    }
    private static Species target(String name) {
        if(name==null)return null;
        try{return PokemonSpecies.getByIdentifier(ResourceLocation.parse(PokemonRuntime.canonical(name)));}
        catch(RuntimeException invalid){return null;}
    }
    private static Map<String,Integer> goals(Evolution evolution) {
        Map<String,Integer> goals=new TreeMap<>();
        for(var requirement:evolution.getRequirements())if(requirement instanceof PokemonPropertiesRequirement properties)
            for(var property:properties.getTarget().getCustomProperties())if(property instanceof IntSpeciesFeature number&&FEATURES.contains(number.getName()))
                goals.merge(number.getName(),Math.max(1,number.getValue()),Math::max);
        return goals;
    }
    private static void merge(Map<String,Map<String,Integer>> catalog,String key,Map<String,Integer> goals) {
        Map<String,Integer> values=catalog.computeIfAbsent(key,ignored->new TreeMap<>());
        // Alternative evolution routes display the first reachable progress threshold.
        goals.forEach((feature,value)->values.merge(feature,value,Math::min));
    }
    public static List<Sync> packets(CompoundTag snapshot,long revision) {
        ListTag rows=snapshot.getList("rows",10);int total=Math.max(1,(rows.size()+47)/48);
        List<Sync> packets=new ArrayList<>();
        for(int index=0;index<total;index++) {
            CompoundTag data=new CompoundTag();data.putLong("sequence",revision);data.putInt("index",index);data.putInt("total",total);
            ListTag part=new ListTag();for(int row=index*48;row<Math.min(rows.size(),(index+1)*48);row++)part.add(rows.get(row).copy());
            data.put("rows",part);packets.add(new Sync(data));
        }
        return packets;
    }
    public static void login(ServerPlayer player) {for(Sync packet:packets(snapshot(),++sequence))Net.sendTo(player,packet);}
    public static void publish(MinecraftServer server) {
        List<Sync> packets=packets(snapshot(),++sequence);
        for(ServerPlayer player:server.getPlayerList().getPlayers())for(Sync packet:packets)Net.sendTo(player,packet);
    }
}
