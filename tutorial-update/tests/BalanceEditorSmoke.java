package dev.unyielding.bundlesmoke;

import com.google.gson.Gson;
import dev.unyielding.quests.settings.ConfigEditor;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.nio.file.Files;
import java.util.*;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Real server authorization, transaction, persistence and IV-roll boundary checks. */
final class BalanceEditorSmoke {
    static final Gson JSON = new Gson();
    static Class<?> owner() throws Exception { return Class.forName("dev.unyielding.balance.BalanceConfig"); }
    static ModConfigSpec.ConfigValue<?> field(String name) throws Exception {
        return (ModConfigSpec.ConfigValue<?>)owner().getField(name).get(null);
    }
    static ModConfigSpec.DoubleValue[][] tiers() throws Exception {
        return (ModConfigSpec.DoubleValue[][])owner().getField("TIER_CHANCES").get(null);
    }
    static String armor() throws Exception {
        return JSON.toJson(new Object[]{field("ARMOR_PERCENT").get(),field("TOUGHNESS_PERCENT").get(),field("KNOCKBACK_PERCENT").get()});
    }
    static String tier(int t) throws Exception { return JSON.toJson(Arrays.stream(tiers()[t-1]).map(ModConfigSpec.ConfigValue::get).toArray()); }
    static String disk() throws Exception { return Files.readString(FMLPaths.CONFIGDIR.get().resolve("cubalance-common.toml")); }
    static void edit(ServerPlayer p,String key,String expected,String value) { ConfigEditor.handle(p,new ConfigEditor.Action(key,expected,value)); }
    @SuppressWarnings({"unchecked","rawtypes"}) static void restore(ModConfigSpec.ConfigValue value,Object original) { value.set(original); }
    static void exercise(ServerPlayer player,Map<String,Object> report) throws Exception {
        var players=player.server.getPlayerList(); var profile=player.getGameProfile(); boolean wasOp=players.isOp(profile);
        Map<ModConfigSpec.ConfigValue<?>,Object> saved=new LinkedHashMap<>();
        for(String name:List.of("ARMOR_PERCENT","TOUGHNESS_PERCENT","KNOCKBACK_PERCENT","MOD_OVERRIDES","ITEM_OVERRIDES","RAID_ENABLED","NORMAL_IV_MAX")) {
            var value=field(name); saved.put(value,value.get() instanceof List<?> l?new ArrayList<>(l):value.get());
        }
        for(var row:tiers()) for(var value:row) saved.put(value,value.get());
        try {
            players.deop(profile);
            reject(player,"__armor",armor(),"[75,25,50]");
            reject(player,"__raid_odds:1",tier(1),"[10,20,30]");
            players.op(profile);
            for(String bad:List.of("[]","[1,2]","[1,2,3,4]","[-1,50,50]","[301,50,50]","[1e400,50,50]","[\"50\",50,50]","null","{}")) reject(player,"__armor",armor(),bad);
            String oldArmor=armor(); edit(player,"__armor",oldArmor,"[75,25,50]");
            require(((Number)field("ARMOR_PERCENT").get()).doubleValue()==75 && ((Number)field("TOUGHNESS_PERCENT").get()).doubleValue()==25 && ((Number)field("KNOCKBACK_PERCENT").get()).doubleValue()==50,"Armor group edit failed");
            require(disk().matches("(?s).*armor_percent\\s*=\\s*75(?:\\.0)?.*") && disk().matches("(?s).*toughness_percent\\s*=\\s*25(?:\\.0)?.*"),"Armor group not saved");
            reject(player,"__armor",oldArmor,"[100,100,100]");
            for(String name:List.of("MOD_OVERRIDES","ITEM_OVERRIDES")) {
                String key=name.equals("MOD_OVERRIDES")?"armor.mod_overrides":"armor.item_overrides";
                for(String bad:List.of("[\"minecraft=NaN\"]","[\"minecraft=Infinity\"]","[\"minecraft=-1\"]","[\"minecraft=301\"]","[\"bad id=50\"]","[3]","null")) reject(player,key,JSON.toJson(field(name).get()),bad);
            }
            edit(player,"armor.mod_overrides",JSON.toJson(field("MOD_OVERRIDES").get()),"[\"minecraft=80\"]");
            edit(player,"armor.item_overrides",JSON.toJson(field("ITEM_OVERRIDES").get()),"[\"minecraft:netherite_chestplate=60\"]");
            require(field("MOD_OVERRIDES").get().equals(List.of("minecraft=80")) && field("ITEM_OVERRIDES").get().equals(List.of("minecraft:netherite_chestplate=60")),"Armor overrides not accepted");
            require(disk().contains("minecraft:netherite_chestplate=60"),"Override not persisted");
            for(int t=1;t<=7;t++) {
                String key="__raid_odds:"+t;
                for(String bad:List.of("[]","[1,2]","[1,2,3,4]","[-1,0,0]","[40,40,21]","[1e400,0,0]","[\"1\",0,0]","null","{}")) reject(player,key,tier(t),bad);
                String previous=tier(t); edit(player,key,previous,"[10,20,30]");
                require(Arrays.equals(Arrays.stream(tiers()[t-1]).mapToDouble(v->v.get()).toArray(),new double[]{10,20,30}),"Raid group failed tier "+t);
                reject(player,key,previous,"[0,0,0]");
                var roll=owner().getMethod("rollPerfectIvs",int.class,double.class);
                for(double[] check:new double[][]{{0,3},{29.999,3},{30,2},{49.999,2},{50,1},{59.999,1},{60,0},{99.999,0}})
                    require((int)roll.invoke(null,t,check[0])==(int)check[1],"Raid boundary mismatch tier "+t+" roll "+check[0]);
                for(int slot=0;slot<3;slot++) {
                    double[] all=new double[3]; all[slot]=100;
                    edit(player,key,tier(t),JSON.toJson(all));
                    require((int)roll.invoke(null,t,0d)==slot+1 && (int)roll.invoke(null,t,99.999d)==slot+1,"100% raid tier selection failed");
                }
                edit(player,key,tier(t),"[0,0,0]");
                require((int)roll.invoke(null,t,0d)==0 && (int)roll.invoke(null,t,99.999d)==0,"Zero raid odds still yield perfect IVs");
                // Generic row editing must not bypass the sum constraint.
                edit(player,key,tier(t),"[60,40,0]");
                reject(player,"raid_ivs.tier_"+t+".three_perfect_ivs_percent","0.0","1");
            }
            reject(player,"__raid_odds:0","[]","[0,0,0]"); reject(player,"__raid_odds:8","[]","[0,0,0]");
            for(String bad:List.of("-1","32","3.5","\"30\"","1e400")) reject(player,"raid_ivs.normal_iv_max",JSON.toJson(field("NORMAL_IV_MAX").get()),bad);
            edit(player,"raid_ivs.normal_iv_max",JSON.toJson(field("NORMAL_IV_MAX").get()),"31"); require(field("NORMAL_IV_MAX").get().equals(31),"IV cap not editable");
            edit(player,"raid_ivs.enabled",JSON.toJson(field("RAID_ENABLED").get()),"false"); require(field("RAID_ENABLED").get().equals(false),"Raid override disable failed");
            require(disk().matches("(?s).*normal_iv_max\\s*=\\s*31.*") && disk().matches("(?s).*enabled\\s*=\\s*false.*"),"Raid flags not persisted");
            extraSettings(player,report);
            players.deop(profile); reject(player,"__armor",armor(),"[100,100,100]");
            report.put("armor_raid_operator_stale_numeric_list_and_sum_validation","passed");
            report.put("raid_all_seven_tiers_exact_boundaries_zero_and_100_percent","passed");
            report.put("armor_overrides_raid_cap_toggle_and_atomic_groups_persist","passed");
        } finally {
            saved.forEach(BalanceEditorSmoke::restore);
            ((ModConfigSpec)owner().getField("SPEC").get(null)).save();
            if(wasOp) players.op(profile); else players.deop(profile);
        }
    }
    @SuppressWarnings("unchecked") private static void extraSettings(ServerPlayer player,Map<String,Object> report)throws Exception {
        var field=ConfigEditor.class.getDeclaredField("VALUES");field.setAccessible(true);
        Map<String,ModConfigSpec.ConfigValue<?>> values=(Map<String,ModConfigSpec.ConfigValue<?>>)field.get(null);
        for(String key:List.of("portal.targetDimension","portal.linkSearchRadius","soul_tome.tome.capacity","soul_tome.allies.max_out_at_once","leaderboard.rotate_seconds","leaderboard.podium_view_distance"))
            require(values.containsKey(key),"Useful server setting is missing: "+key);
        for(String key:List.of("portal.linkSearchRadius","soul_tome.tome.capacity","leaderboard.podium_view_distance")) {
            var value=values.get(key);Object before=value.get();
            try {
                String replacement=JSON.toJson(before).equals("16")?"17":"16";
                edit(player,key,JSON.toJson(before),replacement);
                require(((Number)value.get()).doubleValue()==Double.parseDouble(replacement),"Extra server setting not editable: "+key);
            }finally{restore(value,before);value.save();}
        }
        report.put("portal_soul_tome_and_leaderboard_extra_controls","passed");
    }
    private static void reject(ServerPlayer p,String key,String expected,String value) throws Exception {
        String before=disk(),a=armor(); List<String> odds=new ArrayList<>(); for(int t=1;t<=7;t++) odds.add(tier(t));
        edit(p,key,expected,value);
        require(disk().equals(before)&&armor().equals(a),"Rejected balance edit changed state: "+key+" "+value);
        for(int t=1;t<=7;t++) require(tier(t).equals(odds.get(t-1)),"Rejected edit changed raid tier "+t);
    }
}
