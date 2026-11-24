package dev.unyielding.bundlesmoke;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import static dev.unyielding.bundlesmoke.PlayerOnlySmoke.*;

/** Test only: exercise exact native pending-request shapes without modifying real players. */
final class SwapGuardControls {
    private SwapGuardControls() {}

    @SuppressWarnings("unchecked") private static Map<UUID,Object> requests() throws Exception {
        var field=Class.forName("dev.unyielding.quests.essentials.TeleCmds").getDeclaredField("REQUESTS");
        field.setAccessible(true);
        return (Map<UUID,Object>)field.get(null);
    }
    private static Object request(UUID sender) throws Exception {
        var ctor=Class.forName("dev.unyielding.quests.essentials.TeleCmds$Request")
                .getDeclaredConstructor(UUID.class,boolean.class,long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(sender,false,System.currentTimeMillis());
    }
    private static void preflightCase(ServerPlayer p,UUID receiver,Object request,boolean accepted,String label) throws Exception {
        var map=requests();
        require(!map.containsKey(receiver),"A real fixture request would be overwritten");
        var row=List.of(request);
        map.put(receiver,row);
        try {
            require(cmd("jailintegrated check Visitor")== (accepted?1:0),"Wrong teleport preflight: "+label);
            require(map.get(receiver)==row && row.getFirst()==request,"Preflight changed teleport request: "+label);
        } finally { require(map.remove(receiver,row),"Test request changed before cleanup"); }
    }
    static void preflight(ServerPlayer p) throws Exception {
        UUID player=p.getUUID(),other=UUID.randomUUID(),third=UUID.randomUUID();
        require(cmd("jailintegrated check Visitor")==1,"Neutral target failed before teleport controls");
        preflightCase(p,player,request(other),false,"incoming");
        preflightCase(p,other,request(player),false,"outgoing");
        preflightCase(p,other,request(third),true,"unrelated");
        preflightCase(p,other,"unexpected request",false,"unknown request type");
        require(cmd("jailintegrated check Visitor")==1,"Neutral target failed after teleport controls");
        flag("teleport_requests_refused_or_preserved_as_appropriate");
    }

    static void held(ServerPlayer p) throws Exception {
        UUID player=p.getUUID(),other=UUID.randomUUID();
        var map=requests();
        require(!map.containsKey(player) && !map.containsKey(other),"Fixture request collision");
        var method=Class.forName("dev.unyielding.jail.JailPackState")
                .getDeclaredMethod("verifyAttachments",ServerPlayer.class,CompoundTag.class);
        method.setAccessible(true);
        var attachments=p.saveWithoutId(new CompoundTag()).getCompound("neoforge:attachments");
        Object incoming=request(other);var incomingRow=List.of(incoming);map.put(player,incomingRow);
        try {
            method.invoke(null,p,attachments);
            require(map.get(player)==incomingRow && incomingRow.getFirst()==incoming,"Custody altered incoming request");
        } finally { require(map.remove(player,incomingRow),"Incoming test request changed"); }
        Object outgoing=request(player);var outgoingRow=List.of(outgoing);map.put(other,outgoingRow);
        try {
            try { method.invoke(null,p,attachments); throw new IllegalStateException("Outgoing custody request was accepted"); }
            catch (InvocationTargetException expected) {
                require(expected.getTargetException() instanceof IOException &&
                        String.valueOf(expected.getTargetException().getMessage()).contains("teleport request"),
                        "Unexpected outgoing-request refusal");
            }
            require(map.get(other)==outgoingRow && outgoingRow.getFirst()==outgoing,"Custody altered outgoing request");
        } finally { require(map.remove(other,outgoingRow),"Outgoing test request changed"); }
        flag("held_teleport_requests_checked_without_mutation");
    }
}
