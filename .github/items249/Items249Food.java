package dev.unyielding.bundlesmoke;

import java.nio.file.Files;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import team.creative.solonion.api.SOLOnionAPI;
import team.creative.solonion.common.event.SOLOnionEvent;
import static dev.unyielding.bundlesmoke.PlayerOnlySmoke.*;

final class Items249Food {
    private static final String CONTROL="items249_deaths";
    private static int ordinaryTick;
    private Items249Food() {}

    static int score(ServerPlayer player,String name) {
        var board=player.server.getScoreboard();
        var objective=board.getObjective(name);
        require(objective!=null,"Missing published objective "+name);
        var entry=board.getPlayerScoreInfo(player,objective);
        return entry==null?0:entry.value();
    }
    static CompoundTag state(ServerPlayer p) {
        var nativeData=p.saveWithoutId(new CompoundTag()).getCompound("neoforge:attachments");
        var saved=new CompoundTag();
        saved.put("history",nativeData.getList("solonion:foodlist",10).copy());
        var benefits=nativeData.getCompound("solonion:benefit").copy();
        if (benefits.get("att") instanceof ListTag mappedAttributes) {
            // SOL writes this map in unspecified order. Keep the entire rows and their types.
            mappedAttributes.sort(java.util.Comparator.comparing(row->((CompoundTag)row).getString("att")));
        }
        saved.put("benefits",benefits);
        for (String key:List.of("so_cycle","so_bonus","so_death","so_lock")) saved.putInt(key,score(p,key));
        var modifiers=new ListTag();
        for (var attribute:List.of(Attributes.MAX_HEALTH,Attributes.ARMOR_TOUGHNESS)) {
            var instance=p.getAttribute(attribute);
            for (var modifier:instance.getModifiers().stream().sorted(java.util.Comparator.comparing(m->m.id().toString())).toList()) {
                if (!List.of("solcycle","solonion").contains(modifier.id().getNamespace())) continue;
                var row=new CompoundTag();
                row.putString("attribute",attribute.unwrapKey().orElseThrow().location().toString());
                row.putString("id",modifier.id().toString());
                row.putString("operation",modifier.operation().name());
                row.putDouble("amount",modifier.amount());
                modifiers.add(row);
            }
        }
        saved.put("modifiers",modifiers);
        return saved;
    }
    private static void eat(ServerPlayer player,int number) {
        var eligible=BuiltInRegistries.ITEM.stream().filter(i->BuiltInRegistries.ITEM.getKey(i).getNamespace().equals("minecraft"))
                .map(i->i.getDefaultInstance()).filter(s->s.has(DataComponents.FOOD)).toList();
        require(eligible.size()>=30,"Fewer than 30 native foods");
        var provider=SOLOnionAPI.getFoodCapability(player);
        for (int i=0;i<number;i++) {
            var meal=eligible.get(i%eligible.size()).copy();
            meal.set(DataComponents.CUSTOM_NAME,Component.literal("Fixture meal "+i));
            provider.eat(player,meal);
        }
        new SOLOnionEvent().updatePlayerBenefits(player);
    }
    static void seed(ServerPlayer player) throws Exception {
        var board=player.server.getScoreboard();
        if (board.getObjective(CONTROL)==null) board.addObjective(CONTROL,ObjectiveCriteria.DEATH_COUNT,
                Component.literal("Control deaths"),ObjectiveCriteria.RenderType.INTEGER,false,null);
        require(score(player,"so_cycle")==0 && score(player,"so_death")==0,"Food fixture was already occupied");
        eat(player,64);
        require(state(player).getList("history",10).size()==64,"Native provider did not retain 64 meals");
        runFoodCycle(player);
        require(score(player,"so_cycle")==1 && score(player,"so_bonus")>0,"Real food datapack did not bank the first cycle");
        eat(player,35);
        runFoodCycle(player);
        var progress=state(player);
        require(progress.getList("history",10).size()==35 && !progress.getCompound("benefits").isEmpty(),"Active food history/benefit missing");
        var health=player.getAttribute(Attributes.MAX_HEALTH);
        require(health.getModifier(ResourceLocation.parse("solcycle:cycle_1"))!=null &&
                health.getModifier(ResourceLocation.parse("solonion:add_value"))!=null,"Native earned and banked health modifiers missing");
        player.setHealth(player.getMaxHealth());
        Files.write(evidence(player,"food"),OriginCustodyPort.encode(progress));
        Files.writeString(evidence(player,"max-health"),Float.toString(player.getMaxHealth()));
        Files.writeString(evidence(player,"control-deaths"),Integer.toString(score(player,CONTROL)));
    }
    private static void runFoodCycle(ServerPlayer player) {
        // This native function schedules nested commands in Minecraft's command execution context.
        player.server.getCommands().performPrefixedCommand(player.server.createCommandSourceStack(),"function solcycle:tick");
    }
    static float expectedHealth(ServerPlayer p,float defaultHealth) throws Exception {
        var saved=evidence(p,"max-health");
        return Files.isRegularFile(saved)?Float.parseFloat(Files.readString(saved))-(22-defaultHealth):defaultHealth;
    }
    static void verify(ServerPlayer player,boolean escaped) throws Exception {
        var expected=OriginCustodyPort.decode(Files.readAllBytes(evidence(player,"food")));
        require(expected.equals(state(player)),"Food components, nutrient history, benefits, banked hearts or scores changed");
        int before=Integer.parseInt(Files.readString(evidence(player,"control-deaths")));
        require(score(player,CONTROL)==before+(escaped?1:0),"Unrelated death scoreboard was altered");
    }
    static void normalDeath(ServerPlayer dev) throws Exception {
        seed(dev);
        dev.setInvulnerable(false);
        ordinaryTick=dev.server.getTickCount();
        dev.kill();
        require(!dev.isAlive() && score(dev,"so_death")==1 && score(dev,CONTROL)==1,
                "Noncustodial death did not increment native datapack and control counters");
    }
    static boolean ordinaryComplete(ServerPlayer dev) throws Exception {
        if (ordinaryTick==0) return false;
        require(dev.server.getTickCount()-ordinaryTick<160,"Normal death did not clear earned food");
        var progress=state(dev);
        if (score(dev,"so_cycle")!=0 || !progress.getList("history",10).isEmpty()) return false;
        require(score(dev,"so_bonus")==0 && score(dev,"so_death")==0 &&
                dev.getAttribute(Attributes.MAX_HEALTH).getModifier(ResourceLocation.parse("solcycle:cycle_1"))==null,
                "Ordinary death did not clear banked food bonus");
        flag("ordinary_death_kept_stock_food_reset");
        return true;
    }
}
