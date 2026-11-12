package dev.unyielding.quests.client;

import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.settings.PokemonCounterRules;
import dev.unyielding.quests.settings.PokemonProgressSync;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.*;

/** Display goals are independent of the provider's parsing and storage maximum. */
public final class PokemonCounterDisplay {
    private static Map<String,Map<String,Integer>> goals;
    private static Map<String,Map<String,Integer>> pending;
    private static long pendingSequence;
    private static int next,total;
    private PokemonCounterDisplay() {}
    public static void accept(PokemonProgressSync.Sync packet) {
        CompoundTag data=packet.data();int index=data.getInt("index"),parts=data.getInt("total");
        if(index<0||parts<1||parts>4096||index>=parts)return;
        if(index==0){pending=new HashMap<>();pendingSequence=data.getLong("sequence");next=0;total=parts;}
        if(pending==null||pendingSequence!=data.getLong("sequence")||next!=index||total!=parts)return;
        for(var raw:data.getList("rows",10)) {
            CompoundTag row=(CompoundTag)raw;String key=row.getString("key");
            if(key.length()>300)return;
            Map<String,Integer> values=new HashMap<>();CompoundTag input=row.getCompound("goals");
            for(String feature:PokemonProgressSync.FEATURES)if(input.contains(feature)) {
                int value=input.getInt(feature);if(value<1||value>PokemonCounterRules.MAX_PROGRESS)return;values.put(feature,value);
            }
            pending.put(key,Map.copyOf(values));
        }
        if(++next==total){goals=Map.copyOf(pending);pending=null;}
    }
    public static int maximum(List<String> keys,Pokemon pokemon,int original) {
        for(String feature:keys)if(PokemonProgressSync.FEATURES.contains(feature)) {
            if(goals==null)return switch(feature) {
                case PokemonCounterRules.DEFEATS -> 666;
                case PokemonCounterRules.DEFEATS_1000 -> 1000;
                default -> 49;
            };
            String key=PokemonProgressSync.formKey(pokemon.getSpecies().getResourceIdentifier().toString(),pokemon.getForm().getName());
            Integer goal=goals.getOrDefault(key,Map.of()).get(feature);
            return goal==null?Math.max(1,PokemonCounterRules.value(pokemon,feature)):goal;
        }
        return original;
    }
    public static boolean synchronizedGoals(){return goals!=null;}
    public static void clear(){goals=null;pending=null;next=0;total=0;pendingSequence=0;}
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
    /** Keep optional Cobblemon method signatures out of subscriber reflection. */
    @EventBusSubscriber(modid=UnyieldingQuests.MODID,value=Dist.CLIENT)
    public static final class ConnectionEvents {
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){
            if(net.neoforged.fml.ModList.get().isLoaded("cobblemon"))PokemonCounterDisplay.clear();
        }
    }
}
