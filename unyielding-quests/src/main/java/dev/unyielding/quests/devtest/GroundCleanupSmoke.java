package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;

/** Dedicated throwaway-server assertions; excluded from released jars. */
@EventBusSubscriber(modid=UnyieldingQuests.MODID)
public final class GroundCleanupSmoke {
    private static int stage, wait;
    private static ItemEntity oldFood, freshFood, gear, other, addedAfterPreview, merged;
    private static BlockPos origin;
    private GroundCleanupSmoke() {}
    @SubscribeEvent public static void onTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("uq.groundSmoke") || stage<0 || event.getServer().getTickCount()<100 || wait-- > 0) return;
        MinecraftServer server=event.getServer();
        try {
            ServerLevel level=server.overworld(); Scoreboard board=server.getScoreboard(); Objective objective=board.getObjective("cui_items");
            require(objective!=null && board.getObjective("cui_candidate")!=null && board.getObjective("cui_itemcount")!=null,"cleanup objectives missing");
            if(stage==0) {
                set(board,objective,"enabled",0); set(board,objective,"clear_gear",0); set(board,objective,"clear_food",1); set(board,objective,"clear_other",0); set(board,objective,"grace",1200);
                set(board,objective,"dim_overworld",1); set(board,objective,"dim_nether",0); set(board,objective,"dim_end",0); set(board,objective,"dim_custom",0);
                origin=level.getSharedSpawnPos().above(8); level.getChunkAt(origin);
                oldFood=item(level,origin,0,new ItemStack(Items.COOKED_BEEF,2)); freshFood=item(level,origin,3,new ItemStack(Items.COOKED_CHICKEN));
                gear=item(level,origin,6,new ItemStack(Items.IRON_SWORD)); other=item(level,origin,9,new ItemStack(Items.DIAMOND));
                stage=1; wait=5; return;
            }
            if(stage==1) {
                age(oldFood,1200); age(freshFood,1199); age(gear,1300); age(other,1300);
                Objective candidates=board.getObjective("cui_candidate"); set(board,candidates,"preview_epoch",42);
                function(server,"preview");
                require(get(board,objective,"preview_done")==1,"preview did not finish");
                require(get(board,objective,"preview_stacks")==1 && get(board,objective,"preview_items")==2,"age/category preview counts incorrect");
                addedAfterPreview=item(level,origin,12,new ItemStack(Items.COOKED_PORKCHOP)); age(addedAfterPreview,1300);
                stage=2; wait=5; return;
            }
            if(stage==2) {
                function(server,"manual_clear");
                require(get(board,objective,"manual_done")==1,"manual clear did not finish");
                require(oldFood.isRemoved(),"previewed eligible food was not cleared");
                require(!freshFood.isRemoved() && !gear.isRemoved() && !other.isRemoved() && !addedAfterPreview.isRemoved(),"manual clear removed protected or newly added item");
                ItemEntity mergeOld=item(level,origin,15,new ItemStack(Items.COOKED_MUTTON,5));
                ItemEntity mergeFresh=item(level,origin,15,new ItemStack(Items.COOKED_MUTTON,1));
                age(mergeOld,1300); age(mergeFresh,0);
                var method=ItemEntity.class.getDeclaredMethod("tryToMerge",ItemEntity.class); method.setAccessible(true); method.invoke(mergeOld,mergeFresh);
                merged=mergeOld.isRemoved()?mergeFresh:mergeOld;
                require(!merged.isRemoved() && merged.getAge()==0 && merged.getItem().getCount()==6,"vanilla merge did not retain youngest age");
                stage=3; wait=5; return;
            }
            if(stage==3) {
                function(server,"clear");
                require(!merged.isRemoved(),"fresh merge was removed");
                require(!gear.isRemoved() && !other.isRemoved(),"disabled categories were removed");
                require(addedAfterPreview.isRemoved(),"automatic clear did not remove eligible food");
                Files.writeString(Path.of("ground-smoke-result.txt"),"passed: server preview, exact age boundary, categories, candidate identity, merge grace, automatic clear\n");
                stage=-1; server.halt(false);
            }
        } catch(Throwable failure) {
            UnyieldingQuests.LOGGER.error("Ground cleanup smoke failed at stage {}",stage,failure);
            try { Files.writeString(Path.of("ground-smoke-result.txt"),"failed stage "+stage+": "+failure+"\n"); } catch(Exception ignored) {}
            stage=-1; server.halt(false);
        }
    }
    private static ItemEntity item(ServerLevel level,BlockPos p,int dx,ItemStack stack) {
        ItemEntity e=new ItemEntity(level,p.getX()+0.5+dx,p.getY(),p.getZ()+0.5,stack); e.setNoGravity(true);
        require(level.addFreshEntity(e),"could not add test item"); return e;
    }
    private static void age(ItemEntity e,int age) {
        CompoundTag nbt=e.saveWithoutId(new CompoundTag()); nbt.putShort("Age",(short)age); e.load(nbt);
    }
    private static void function(MinecraftServer server,String name) {
        var f=server.getFunctions().get(ResourceLocation.fromNamespaceAndPath("cui_cleanup",name));
        require(f.isPresent(),"function missing: "+name); server.getFunctions().execute(f.get(),server.getFunctions().getGameLoopSender());
    }
    private static void set(Scoreboard board,Objective objective,String key,int value) { board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#"+key),objective).set(value); }
    private static int get(Scoreboard board,Objective objective,String key) { var v=board.getPlayerScoreInfo(ScoreHolder.forNameOnly("#"+key),objective); return v==null?0:v.value(); }
    private static void require(boolean b,String message) { if(!b)throw new IllegalStateException(message); }
}
