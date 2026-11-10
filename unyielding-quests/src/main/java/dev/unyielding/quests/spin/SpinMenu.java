package dev.unyielding.quests.spin;

import dev.unyielding.quests.Config;
import dev.unyielding.quests.net.Net;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Server-only, one-use request tokens; clients never select prizes or weights. */
public final class SpinMenu {
    private static final Map<UUID,String> tokens=new HashMap<>();
    public static void clear(){tokens.clear();}
    public static void logout(ServerPlayer p){tokens.remove(p.getUUID());}
    public static void handle(ServerPlayer p,CompoundTag request){
        String action=request.getString("action");
        if (!action.equals("status") && !action.equals("spin")) return;
        boolean ready=LuckySpin.starterReady(p);
        if(action.equals("spin")){
            String token=tokens.remove(p.getUUID());
            if(token!=null && token.equals(request.getString("token")) && ready){
                LuckySpin.Spin result=DailyLogin.redeem(p);
                if(result!=null) Net.sendTo(p,new Net.SpinResult(List.of(result)));
            }
        }
        CompoundTag state=new CompoundTag();
        String next=UUID.randomUUID().toString(); tokens.put(p.getUUID(),next);
        state.putString("token",next);
        state.putLong("count",DailyLogin.available(p));
        state.putBoolean("ready",ready);
        state.putBoolean("enabled",Config.DAILY_ENABLED.get());
        Net.sendTo(p,new Net.SpinMenuState(state));
    }
}
