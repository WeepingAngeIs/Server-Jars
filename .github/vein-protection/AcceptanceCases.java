package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.*;

/** Public exact addon builds plus synthetic players; never imports private policy data. */
final class AcceptanceCases {
    static int originalBonus;
    static void natural(boolean creative, int fresh, boolean foreign){
        BundleSmoke.seed(false);
        if(fresh>0)BundleSmoke.origin=new BlockPos(fresh,80,fresh+9);
        var level=BundleSmoke.server.overworld();var pos=BundleSmoke.origin;
        for(int i=0;i<3;i++)level.setBlock(pos.east(i),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(pos.offset(0,0,2),Blocks.BEDROCK.defaultBlockState(),3);
        BundleSmoke.p().teleportTo(level,pos.getX()+.5,81,pos.getZ()+2.5,180,75);
        BundleSmoke.p().setGameMode(creative?GameType.CREATIVE:GameType.SURVIVAL);
        if(foreign)BundleSmoke.claim(BundleSmoke.v().getUUID());
    }
    static Object call(Object target,String name,Object... args)throws Exception{
        var method=Arrays.stream(target.getClass().getMethods()).filter(m->m.getName().equals(name)&&m.getParameterCount()==args.length).findFirst().orElseThrow();method.setAccessible(true);return method.invoke(target,args);
    }
    static int bonus(){try{Object manager=call(BundleSmoke.api(),"getPlayerConfigManager"),option=call(manager,"getOptionForId","claims.bonusChunkClaims"),config=call(manager,"getLoadedConfig",BundleSmoke.p().getUUID());BundleSmoke.require(option!=null,"bonus option unavailable");return ((Number)call(config,"getEffective",option)).intValue();}catch(Exception bad){throw new IllegalStateException(bad);}}
    static int command(String value,boolean console)throws Exception{return BundleSmoke.server.getCommands().getDispatcher().execute(value,console?BundleSmoke.server.createCommandSourceStack():BundleSmoke.p().createCommandSourceStack());}
    static void crowd(){
        BundleSmoke.seed(false);
        var party=Cobblemon.INSTANCE.getStorage().getParty(BundleSmoke.p());
        if(party.size()==0)for(int i=0;i<6;i++){var props=new PokemonProperties();props.setSpecies("eevee");props.setLevel(5);BundleSmoke.require(party.add(props.create(BundleSmoke.p())),"cannot seed disposable party");}
        BundleSmoke.require(party.size()==6,"populated party HUD fixture missing");
        for(int i=0;i<10;i++)BundleSmoke.p().sendSystemMessage(net.minecraft.network.chat.Component.literal("Startup notice "+i+": disposable acceptance fixture"));
    }
    static void verifyDurability(String caseName){
        try{var tool=BundleSmoke.p().getMainHandItem();String expected="durability "+(tool.getMaxDamage()-tool.getDamageValue())+"/"+tool.getMaxDamage();String text=Files.readString(Path.of(System.getenv("UQ_VEIN_EVIDENCE")).resolve(caseName.replace(' ','_')+".txt"));BundleSmoke.require(text.contains(expected),"feedback differs from completed tool state: "+expected+" versus "+text);}catch(Exception bad){throw new IllegalStateException(bad);}
    }
    static void extend(){
        for(String id:List.of("opacbonusclaims","opacpvp","xaerominimap","xaeroworldmap"))BundleSmoke.require(net.neoforged.fml.ModList.get().isLoaded(id),"exact addon missing: "+id);
        // First freshly-visible click, without a server-held-state acknowledgement or
        // refresh packets; the actual keybinding and vanilla attack mapping drive input.
        BundleSmoke.cases.addFirst(new BundleSmoke.Case("cold first-click Survival",()->natural(false,3071,false),"natural_cold","2 extra blocks",()->BundleSmoke.blocks(true,true,true)));
        BundleSmoke.cases.addFirst(new BundleSmoke.Case("cold first-click Creative",()->natural(true,2047,false),"natural_cold","2 extra blocks",()->BundleSmoke.blocks(true,true,true)));
        BundleSmoke.add("normal-hardness Survival hold",()->natural(false,0,false),"natural_warm","2 extra blocks",()->BundleSmoke.blocks(true,true,true));
        BundleSmoke.add("normal-hardness Survival tool result",()->natural(false,0,false),"natural_warm","2 extra blocks",()->{BundleSmoke.blocks(true,true,true);verifyDurability("normal-hardness Survival tool result");});
        BundleSmoke.add("Survival foreign extras protected",()->natural(false,0,true),"natural_warm","0 extra blocks",()->BundleSmoke.blocks(true,false,false));
        BundleSmoke.add("bonus addon unauthorized grant",()->{
            BundleSmoke.seed(false);originalBonus=bonus();boolean denied=false;try{command("opacbonusclaims claims Dev plus 7",false);}catch(Exception expected){denied=true;}
            BundleSmoke.require(denied&&bonus()==originalBonus,"default-role player granted bonus claims");
        },"preview","NONE",()->BundleSmoke.blocks(false,false,false));
        BundleSmoke.add("bonus addon grant preserves protection",()->{
            BundleSmoke.seed(false);try{command("opacbonusclaims claims Dev plus 7",true);}catch(Exception bad){throw new IllegalStateException(bad);}BundleSmoke.require(bonus()==originalBonus+7,"bonus grant quantity incorrect");BundleSmoke.claim(BundleSmoke.v().getUUID());
        },"mine","0 extra blocks",()->BundleSmoke.blocks(true,false,false));
        BundleSmoke.add("bonus addon revoke preserves protection",()->{
            BundleSmoke.seed(false);try{command("opacbonusclaims claims Dev minus 7",true);}catch(Exception bad){throw new IllegalStateException(bad);}BundleSmoke.require(bonus()==originalBonus,"bonus revoke quantity incorrect");BundleSmoke.claim(BundleSmoke.v().getUUID());
        },"mine","0 extra blocks",()->BundleSmoke.blocks(true,false,false));
        BundleSmoke.add("crowded startup populated HUD",AcceptanceCases::crowd,"mine","2 extra blocks",()->BundleSmoke.blocks(true,true,true));
        BundleSmoke.add("populated party shape HUD",AcceptanceCases::crowd,"preview","NONE",()->BundleSmoke.blocks(false,false,false));
        BundleSmoke.add("six shape scroll cycle",AcceptanceCases::crowd,"cycle","NONE",()->BundleSmoke.blocks(false,false,false));
    }
}
