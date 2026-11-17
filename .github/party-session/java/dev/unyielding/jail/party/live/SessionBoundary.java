package dev.unyielding.jail.party.live;

import com.google.gson.*;
import java.io.IOException;
import net.minecraft.server.level.ServerPlayer;

/** Outer, test-only caller boundary. It cannot grant native object or mutation permission. */
public final class SessionBoundary {
    private SessionBoundary(){}
    public static void request(ServerPlayer player,String wire){
        if(!SessionBridge.enabled())return;
        JsonObject request;
        try{request=JsonParser.parseString(wire).getAsJsonObject();}
        catch(RuntimeException malformed){SessionBridge.request(player,wire);return;}
        if(!request.has("op")||!request.get("op").isJsonPrimitive()||!"HELLO".equals(request.get("op").getAsString())){SessionBridge.request(player,wire);return;}
        try{
            SessionBridge.require(SessionBridge.server!=null&&SessionBridge.server.isSameThread()&&!SessionBridge.done,"Session setup unavailable");
            SessionBridge.hello(player,request);
            var now=SessionBridge.connections.get(player.getUUID());var old=SessionBridge.retired;
            if(old!=null&&now!=null&&old.player!=player){
                var receipt=now.session.receipt();
                try{GlobalProfile.manager().onPlayerDisconnect(old.player);throw new IOException("Stale disconnect was accepted");}
                catch(NativeFence.Denied expected){
                    SessionBridge.require(SessionBridge.connections.get(player.getUUID())==now&&now.valid&&now.session.live()&&receipt.equals(now.session.receipt()),"Stale disconnect damaged newer session");
                    now.session.assertIntegrity();SessionBridge.flag("stale_disconnect_cannot_retire_new_connection");
                }
            }
        }catch(Throwable failure){
            System.err.println("JAIL_SESSION_SETUP_FAILURE "+failure);
            SessionBridge.fail(failure);
        }
    }
    public static Object invoke(Object receiver,String key,Object[] args,NativeFence.Call operation)throws Throwable {
        if(SessionBridge.enabled()&&receiver==GlobalProfile.manager()&&key.startsWith(SessionBridge.MANAGER)&&args.length==1&&args[0] instanceof ServerPlayer p&&SessionBridge.enrolled(p.getUUID())){
            if(SessionBridge.server==null||!SessionBridge.server.isSameThread())throw new NativeFence.Denied("Player-facing native access on wrong thread");
            if(key.contains("#onPlayerDisconnect(")){
                var current=SessionBridge.connections.get(p.getUUID());
                if(current!=null&&(current.player!=p||current.listener!=p.connection))throw new NativeFence.Denied("Stale disconnect cannot remove current lease");
            }
        }
        return SessionBridge.invoke(receiver,key,args,operation);
    }
    public static boolean blockNative(ServerPlayer p,String handler){return SessionBridge.blockNative(p,handler);}
    public static boolean blockMolang(ServerPlayer p){
        if(!SessionBridge.enabled())return false;
        if(blockCosmetic(p)){
            SessionBridge.flag("enrolled_molang_tick_deferred_before_context");return true;
        }
        SessionBridge.flag("unrelated_molang_tick_not_deferred");return false;
    }
    /** Defer an inspected periodic cosmetic consumer BEFORE it reads or changes quarantined state. */
    public static boolean blockCosmetic(ServerPlayer p){
        if(!SessionBridge.enabled())return false;
        try{
            if(!SessionBridge.enrolled(p.getUUID()))return false;
            SessionBridge.flag("quarantined_callback_deferred_before_lookup");return true;
        }catch(Throwable failure){SessionBridge.fail(failure);return true;}
    }
}
