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
    private static ItemEntity oldFood, freshFood, gear, other, changedFood, addedAfterPreview, merged;
    private static BlockPos origin;
    private GroundCleanupSmoke() {}
    @SubscribeEvent public static void onTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("uq.groundSmoke") || stage<0 || event.getServer().getTickCount()<100 || wait-- > 0) return;
        MinecraftServer server=event.getServer();
        try {
            ServerLevel level=server.overworld(); Scoreboard board=server.getScoreboard(); Objective objective=board.getObjective("cui_items");
            require(objective!=null && board.getObjective("cui_candidate")!=null && board.getObjective("cui_itemcount")!=null,"cleanup objectives missing");
            if(stage==0) {
                checkDebitHistory();
                checkMeetingStorageAndClaims(server);
                set(board,objective,"enabled",0); set(board,objective,"clear_gear",0); set(board,objective,"clear_food",1); set(board,objective,"clear_other",0); set(board,objective,"grace",1200);
                set(board,objective,"dim_overworld",1); set(board,objective,"dim_nether",0); set(board,objective,"dim_end",0); set(board,objective,"dim_custom",0);
                origin=level.getSharedSpawnPos().above(8); level.getChunkAt(origin);
                oldFood=item(level,origin,0,new ItemStack(Items.COOKED_BEEF,2)); freshFood=item(level,origin,3,new ItemStack(Items.COOKED_CHICKEN));
                gear=item(level,origin,6,new ItemStack(Items.IRON_SWORD)); other=item(level,origin,9,new ItemStack(Items.DIAMOND));
                changedFood=item(level,origin,18,new ItemStack(Items.COOKIE,2));
                stage=1; wait=5; return;
            }
            if(stage==1) {
                age(oldFood,1200); age(freshFood,1199); age(gear,1300); age(other,1300); age(changedFood,1300);
                Objective candidates=board.getObjective("cui_candidate"); set(board,candidates,"preview_epoch",42);
                set(board,objective,"dim_overworld",0); function(server,"preview");
                require(get(board,objective,"preview_stacks")==0,"disabled overworld was included");
                set(board,objective,"dim_overworld",1); function(server,"preview");
                require(get(board,objective,"preview_done")==1,"preview did not finish");
                require(get(board,objective,"preview_stacks")==2 && get(board,objective,"preview_items")==4,"age/category preview counts incorrect");
                changedFood.getItem().setCount(3);
                addedAfterPreview=item(level,origin,12,new ItemStack(Items.COOKED_PORKCHOP)); age(addedAfterPreview,1300);
                stage=2; wait=5; return;
            }
            if(stage==2) {
                function(server,"manual_clear");
                require(get(board,objective,"manual_done")==1,"manual clear did not finish");
                require(oldFood.isRemoved(),"previewed eligible food was not cleared");
                require(!freshFood.isRemoved() && !gear.isRemoved() && !other.isRemoved() && !changedFood.isRemoved() && !addedAfterPreview.isRemoved(),"manual clear removed protected or newly added item");
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
                Files.writeString(Path.of("ground-smoke-result.txt"),"passed: server preview, exact age boundary, categories, candidate identity, changed stacks, dimensions, merge grace, automatic clear\n");
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
    private static void checkMeetingStorageAndClaims(MinecraftServer server) throws Exception {
        // Pure save/load roundtrip; does not touch any existing party, home or world SavedData.
        var party = java.util.UUID.randomUUID(); var setter = java.util.UUID.randomUUID();
        CompoundTag row = new CompoundTag(); row.putUUID("party", party); row.putUUID("setter", setter);
        row.put("location", new dev.unyielding.quests.essentials.Loc("minecraft:overworld", 0.5, 70, 0.5, 15, 0).toTag());
        var rows = new net.minecraft.nbt.ListTag(); rows.add(row);
        CompoundTag input = new CompoundTag(); input.put("points", rows);
        var clazz = dev.unyielding.quests.essentials.PartyMeetingData.class;
        var load = clazz.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        var restored = (dev.unyielding.quests.essentials.PartyMeetingData) load.invoke(null, input, null);
        require(restored.save(new CompoundTag(), null).equals(input), "meeting persistence lost identity or location");
        require(((dev.unyielding.quests.essentials.PartyMeetingData) load.invoke(null, new CompoundTag(), null))
                .save(new CompoundTag(), null).getList("points", 10).isEmpty(), "legacy meeting storage not empty");
        var claims = Class.forName("dev.unyielding.quests.essentials.MeetingClaims")
                .getDeclaredMethod("hasAccess", MinecraftServer.class, java.util.UUID.class, ResourceLocation.class, int.class, int.class);
        claims.setAccessible(true);
        if (!net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims"))
            require(Boolean.FALSE.equals(claims.invoke(null, server, setter, ResourceLocation.parse("minecraft:overworld"), 100000, 100000)),
                    "missing claim integration must fail closed");
        require(net.neoforged.neoforge.server.permission.PermissionAPI.getRegisteredNodes()
                .contains(dev.unyielding.quests.essentials.PartyMeetings.USE), "meeting permission node was not registered");
    }
    private static void checkDebitHistory() {
        var legacy = dev.unyielding.quests.spin.DailyLogin.Data.load(new CompoundTag(), null);
        require(legacy.hasValidDebitHistory(), "legacy debit history rejected");
        CompoundTag input = new CompoundTag(); input.putString("appliedDebits", "corrupt");
        var bad = dev.unyielding.quests.spin.DailyLogin.Data.load(input, null);
        require(!bad.hasValidDebitHistory(), "malformed debit history accepted");
        require(input.get("appliedDebits").equals(bad.save(new CompoundTag(), null).get("appliedDebits")), "malformed history was not preserved");
        var duplicate = new net.minecraft.nbt.ListTag();
        CompoundTag id = new CompoundTag(); id.putUUID("id", java.util.UUID.randomUUID());
        duplicate.add(id); duplicate.add(id.copy()); input.put("appliedDebits", duplicate);
        require(!dev.unyielding.quests.spin.DailyLogin.Data.load(input, null).hasValidDebitHistory(), "duplicate debit marker accepted");
        CompoundTag malformedCredit = new CompoundTag(); malformedCredit.putString("appliedQuestCredits", "broken");
        var badCredit = dev.unyielding.quests.spin.DailyLogin.Data.load(malformedCredit, null);
        require(!badCredit.hasValidDebitHistory(), "malformed credit history accepted");
        require(badCredit.save(new CompoundTag(), null).get("appliedQuestCredits").equals(malformedCredit.get("appliedQuestCredits")), "credit evidence lost");
        var credits = new dev.unyielding.quests.spin.DailyLogin.Data();
        var player = java.util.UUID.randomUUID(); var credit = java.util.UUID.randomUUID();
        credits.players.put(player, new long[]{0,0,0,3,0}); credits.appliedQuestCredits.add(credit);
        var restoredCredits = dev.unyielding.quests.spin.DailyLogin.Data.load(credits.save(new CompoundTag(), null), null);
        require(restoredCredits.players.get(player)[3] == 3 && restoredCredits.appliedQuestCredits.contains(credit), "credit and balance snapshot split");
        var quests = dev.unyielding.quests.quest.QuestData.load(new CompoundTag(), null);
        require(quests.validRewardEpochs(), "legacy quest epochs rejected");
        quests.get(player).completedQuests.add(42L); quests.markClaimed(player, 42L);
        CompoundTag beforeReset = quests.save(new CompoundTag(), null);
        quests.get(player).completedQuests.remove(42L); quests.forgetClaims(java.util.Set.of(player), java.util.Set.of(42L));
        var epoch = quests.rewardEpochs.generation(player, 42L);
        quests.get(player).completedQuests.add(99L);
        var afterReset = dev.unyielding.quests.quest.QuestData.load(quests.save(new CompoundTag(), null), null);
        require(afterReset.rewardEpochs.generation(player,42L).equals(epoch), "reset epoch lost");
        require(afterReset.get(player).completedQuests.contains(99L) && !afterReset.get(player).completedQuests.contains(42L), "newer progress changed");
        var oldSnapshot = dev.unyielding.quests.quest.QuestData.load(beforeReset, null);
        require(oldSnapshot.rewardEpochs.generation(player,42L).equals(dev.unyielding.quests.quest.QuestRewardEpochs.LEGACY) && oldSnapshot.isClaimed(player,42L), "pre-reset snapshot changed");
        CompoundTag badEpochs = new CompoundTag(); badEpochs.putString("reward_epochs", "broken");
        var invalidEpochs = dev.unyielding.quests.quest.QuestData.load(badEpochs, null);
        require(!invalidEpochs.validRewardEpochs(), "malformed quest epochs accepted");
        require(invalidEpochs.save(new CompoundTag(), null).get("reward_epochs").equals(badEpochs.get("reward_epochs")), "epoch evidence lost");
    }
    private static void function(MinecraftServer server,String name) {
        var f=server.getFunctions().get(ResourceLocation.fromNamespaceAndPath("cui_cleanup",name));
        require(f.isPresent(),"function missing: "+name); server.getFunctions().execute(f.get(),server.getFunctions().getGameLoopSender());
    }
    private static void set(Scoreboard board,Objective objective,String key,int value) { board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#"+key),objective).set(value); }
    private static int get(Scoreboard board,Objective objective,String key) { var v=board.getPlayerScoreInfo(ScoreHolder.forNameOnly("#"+key),objective); return v==null?0:v.value(); }
    private static void require(boolean b,String message) { if(!b)throw new IllegalStateException(message); }
}
