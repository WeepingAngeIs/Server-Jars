package dev.unyielding.quests.settings;

import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.UnyieldingQuests;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Server-authoritative editor for the existing cleanup scoreboard. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID)
public final class GroundSettings {
    private GroundSettings() {}
    private record Pending(net.minecraft.server.MinecraftServer server, long expires, java.util.List<Integer> settings, int epoch, int stacks, int items) {}
    private static final java.util.Map<java.util.UUID, Pending> PENDING = new java.util.HashMap<>();
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING.values().removeIf(preview -> preview.server() == event.getServer());
    }
    private static java.util.List<Integer> fingerprint(Scoreboard b, Objective o) {
        return java.util.List.of(get(b,o,"clear_gear",0),get(b,o,"clear_food",1),get(b,o,"clear_other",0),get(b,o,"grace",1200),get(b,o,"dim_overworld",1),get(b,o,"dim_nether",1),get(b,o,"dim_end",1));
    }
    private static boolean runFunction(ServerPlayer p,String name) {
        var manager=p.getServer().getFunctions(); var function=manager.get(ResourceLocation.fromNamespaceAndPath("cui_cleanup",name));
        if(function.isEmpty()) return false;
        manager.execute(function.get(),manager.getGameLoopSender()); return true;
    }
    public record Action(String key, int value) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.parse("unyieldingquests:ground_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Action::key, ByteBufCodecs.VAR_INT, Action::value, Action::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Sync(CompoundTag data, boolean open) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.parse("unyieldingquests:ground_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Sync::data, ByteBufCodecs.BOOL, Sync::open, Sync::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent e) {
        var r=e.registrar("1");
        r.playToServer(Action.TYPE,Action.CODEC,(p,c)->handle((ServerPlayer)c.player(),p));
        r.playToClient(Sync.TYPE,Sync.CODEC,(p,c)->dev.unyielding.quests.client.GroundSettingsScreen.receive(p));
    }
    private static int get(Scoreboard board, Objective obj, String name, int fallback) {
        var v=board.getPlayerScoreInfo(ScoreHolder.forNameOnly("#"+name),obj);
        return v==null ? fallback : v.value();
    }
    private static void set(Scoreboard board,Objective obj,String name,int value) {
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#"+name),obj).set(value);
    }
    private static void audit(ServerPlayer p,String key,int before,int after) {
        if(before==after) return;
        try { ConfigAudit.record(p,"ground."+key,before,after); }
        catch(RuntimeException failure) { dev.unyielding.quests.UnyieldingQuests.LOGGER.error("Could not audit ground setting {}",key,failure); }
    }
    public static void open(ServerPlayer p) { sync(p,true,""); }
    private static void sync(ServerPlayer p,boolean open,String message) {
        if (!p.hasPermissions(2)) return;
        var board=p.getServer().getScoreboard(); var obj=board.getObjective("cui_items");
        if(obj==null) { p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Cleanup is not loaded yet. Try again after the world loads.")); return; }
        CompoundTag t=new CompoundTag();
        t.putInt("interval",get(board,obj,"interval",12000)); t.putInt("timer",get(board,obj,"timer",0));
        t.putInt("grace",get(board,obj,"grace",1200)); t.putInt("enabled",get(board,obj,"enabled",1));
        t.putInt("gear",get(board,obj,"clear_gear",0)); t.putInt("food",get(board,obj,"clear_food",1)); t.putInt("other",get(board,obj,"clear_other",0));
        for(String d:new String[]{"overworld","nether","end","custom"}) t.putInt("dim_"+d,get(board,obj,"dim_"+d,1));
        Pending preview=PENDING.get(p.getUUID());
        if(preview!=null && preview.server()==p.getServer() && preview.expires()>System.nanoTime() && preview.settings().equals(fingerprint(board,obj)) && board.getObjective("cui_candidate")!=null && preview.epoch()==get(board,board.getObjective("cui_candidate"),"preview_epoch",0)) {
            t.putBoolean("preview",true); t.putInt("preview_stacks",preview.stacks()); t.putInt("preview_items",preview.items());
        } else PENDING.remove(p.getUUID());
        t.putString("message",message); Net.sendTo(p,new Sync(t,open));
    }
    private static void handle(ServerPlayer p,Action a) {
        if (!p.hasPermissions(2)) return;
        var b=p.getServer().getScoreboard(); var o=b.getObjective("cui_items"); if(o==null)return;
        String k=a.key(); int v=a.value();
        if (k.equals("refresh")) { sync(p,false,""); return; }
        if (k.equals("cancel_preview")) { PENDING.remove(p.getUUID()); sync(p,false,"Manual clear cancelled."); return; }
        if (k.equals("preview")) {
            PENDING.clear();
            var candidates=b.getObjective("cui_candidate");
            if(candidates==null || b.getObjective("cui_itemcount")==null) { sync(p,false,"Preview tracking is unavailable. Reload the cleanup functions."); return; }
            int epoch=get(b,candidates,"preview_epoch",0);
            if(epoch==Integer.MAX_VALUE) { sync(p,false,"Preview tracking is exhausted; no items were cleared."); return; }
            epoch++;
            set(b,candidates,"preview_epoch",epoch);
            set(b,o,"preview_done",0);
            if (!runFunction(p,"preview") || get(b,o,"preview_done",0)!=1) { set(b,o,"preview_mode",0); sync(p,false,"Preview failed; no items were cleared."); return; }
            PENDING.put(p.getUUID(),new Pending(p.getServer(),System.nanoTime()+30_000_000_000L,fingerprint(b,o),epoch,get(b,o,"preview_stacks",0),get(b,o,"preview_items",0)));
            sync(p,false,"Preview complete. Confirm within 30 seconds."); return;
        }
        if (k.equals("confirm")) {
            Pending preview=PENDING.remove(p.getUUID());
            if(preview==null || preview.server()!=p.getServer() || preview.expires()<=System.nanoTime() || !preview.settings().equals(fingerprint(b,o)) || b.getObjective("cui_candidate")==null || preview.epoch()!=get(b,b.getObjective("cui_candidate"),"preview_epoch",0)) { sync(p,false,"Preview expired or settings changed; preview again."); return; }
            set(b,o,"manual_done",0);
            if (!runFunction(p,"manual_clear")) { sync(p,false,"Manual clear unavailable; no items were cleared."); return; }
            if (get(b,o,"manual_done",0)!=1) { sync(p,false,"Manual clear did not finish. Some items may have been cleared; preview again."); return; }
            sync(p,false,"Manual clear finished. Fresh-drop grace still applies."); return;
        }
        if (k.equals("interval") && v>=5 && v<=86400) { int before=get(b,o,"interval",12000); set(b,o,"interval",v*20); audit(p,k,before,v*20); set(b,o,"timer",0); set(b,o,"last_interval",v*20); sync(p,false,"Interval saved; countdown reset to "+v+" seconds."); return; }
        if (k.equals("grace") && v>=60 && v<=1600) { int before=get(b,o,"grace",1200); set(b,o,"grace",v*20); audit(p,k,before,v*20); sync(p,false,"Fresh-drop grace saved."); return; }
        if ((k.equals("enabled") || k.equals("clear_gear") || k.equals("clear_food") || k.equals("clear_other") || k.equals("dim_overworld") || k.equals("dim_nether") || k.equals("dim_end") || k.equals("dim_custom")) && (v==0 || v==1)) {
            int before=get(b,o,k,k.equals("enabled") || k.equals("clear_food") || k.startsWith("dim_") ? 1 : 0);
            set(b,o,k,v); audit(p,k,before,v); sync(p,false,"Saved."); return;
        }
        sync(p,false,"Invalid setting or value.");
    }
}
