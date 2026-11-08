package dev.unyielding.bundlesmoke;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.unyielding.quests.settings.MusicSettings;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Hold a real server edit long enough for equipped armor and client state to update. */
@EventBusSubscriber(modid=BundleSmoke.MOD_ID)
public final class BalanceLiveSmoke {
    private static int phase, ticks;
    private static String original;
    private static CompoundTag originalMusic;
    private static boolean wasOp;
    private static ItemStack[] equipment;
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(phase==3 || !Files.exists(Path.of("/tmp/uq-loot-editor-client-passed.json")) || !Files.exists(Path.of("/tmp/uq-config-editor-passed.json"))) return;
        ServerPlayer p=event.getServer().getPlayerList().getPlayerByName("Dev"); if(p==null) return;
        try {
            if(phase==0) {
                wasOp=p.server.getPlayerList().isOp(p.getGameProfile());
                original=BalanceEditorSmoke.armor(); equipment=new ItemStack[SLOTS.length];
                originalMusic=MusicSettings.get(p).snapshot();
                for(int i=0;i<SLOTS.length;i++) { equipment[i]=p.getItemBySlot(SLOTS[i]).copy(); p.setItemSlot(SLOTS[i],ItemStack.EMPTY); }
                p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.NETHERITE_CHESTPLATE));
                phase=1; ticks=0; return;
            }
            if(phase==1) {
                if(++ticks<20) return;
                p.server.getPlayerList().op(p.getGameProfile());
                BalanceEditorSmoke.edit(p,"__armor",BalanceEditorSmoke.armor(),"[75,25,50]");
                MusicEditorSmoke.edit(p,MusicSettings.MASTER,75);
                MusicEditorSmoke.edit(p,"lavender",50);
                require(((Number)BalanceEditorSmoke.field("ARMOR_PERCENT").get()).doubleValue()==75,"Live armor edit denied");
                Files.writeString(Path.of("/tmp/uq-balance-live-ready"),"75,25,50");
                phase=2; ticks=0; return;
            }
            if(++ticks<40) return;
            require(Math.abs(p.getAttributeValue(Attributes.ARMOR)-6d)<0.0001,"Equipped armor did not update live: "+p.getAttributeValue(Attributes.ARMOR));
            require(Math.abs(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS)-.75d)<0.0001,"Equipped toughness did not update live");
            require(Math.abs(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)-.05d)<0.0001,"Equipped knockback did not update live");
            if(!Files.exists(Path.of("/tmp/uq-balance-client-passed.json"))) {
                require(ticks<600,"Client did not receive live armor settings"); return;
            }
            var report=new LinkedHashMap<>(BundleSmoke.verifyProductionJar());
            report.put("already_equipped_armor_toughness_knockback_refresh","passed");
            restore(p);
            BundleSmoke.write("/tmp/uq-balance-live-passed.json",report); phase=3;
        } catch(Throwable failure) {
            try { restore(p); } catch(Throwable ignored) { }
            phase=3; BundleSmoke.fail("/tmp/uq-balance-live-failed.txt",failure);
        }
    }
    private static void restore(ServerPlayer p) throws Exception {
        if(original==null) return;
        p.server.getPlayerList().op(p.getGameProfile());
        BalanceEditorSmoke.edit(p,"__armor",BalanceEditorSmoke.armor(),original);
        if(originalMusic!=null) {
            MusicEditorSmoke.edit(p,MusicSettings.MASTER,originalMusic.getDouble("master"));
            CompoundTag tracks=originalMusic.getCompound("volumes");
            MusicEditorSmoke.edit(p,"lavender",tracks.contains("lavender")?tracks.getDouble("lavender"):100);
        }
        if(equipment!=null) for(int i=0;i<SLOTS.length;i++) p.setItemSlot(SLOTS[i],equipment[i]);
        if(!wasOp) p.server.getPlayerList().deop(p.getGameProfile());
        original=null;
    }
}
