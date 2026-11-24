package dev.unyielding.bundlesmoke;

import com.mojang.authlib.GameProfile;
import dev.unyielding.jail.JailCustodyService;
import dev.unyielding.jail.JailData;
import dev.unyielding.jail.JailIntegrated;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import static dev.unyielding.bundlesmoke.PlayerOnlySmoke.*;

/** The subject is the real connected Visitor. The peer is a named synthetic server fixture,
 * not a second network client; requests and answers execute the companion's native handlers. */
final class PolicyTeleportProof {
    private static UUID heldTarget;
    private static List<?> heldList;
    private static List<?> heldSnapshot;
    private static long heldTimestamp;
    private PolicyTeleportProof() {}
    @SuppressWarnings("unchecked")
    private static Map<UUID,List<?>> requestMap() throws Exception {
        var field=Class.forName("dev.unyielding.quests.essentials.TeleCmds").getDeclaredField("REQUESTS");
        field.setAccessible(true);
        return (Map<UUID,List<?>>)field.get(null);
    }
    private static void incomingUnchanged(ServerPlayer player) throws Exception {
        var row=requestMap().get(player.getUUID());
        require(player.getUUID().equals(heldTarget) && row==heldList && heldSnapshot.equals(row),
                "Custody consumed or changed the native incoming teleport request");
    }
    static void holdIncoming(ServerPlayer player) throws Exception {
        require(JailCustodyService.quarantined(player) && heldTarget==null,
                "Incoming custody control requires one active admission");
        var requests=requestMap();
        require(!requests.containsKey(player.getUUID()),"Incoming control would overwrite a request");
        var peer=FakePlayerFactory.get(player.serverLevel(),new GameProfile(UUID.randomUUID(),"PolicyIncoming"));
        peer.setPos(player.getX()+40,player.getY(),player.getZ()+40);
        var request=Class.forName("dev.unyielding.quests.essentials.TeleCmds")
                .getDeclaredMethod("request",CommandSourceStack.class,ServerPlayer.class,boolean.class);
        request.setAccessible(true);
        require((int)request.invoke(null,peer.createCommandSourceStack(),player,true)==1,
                "Native incoming tpahere request to the confined player failed");
        heldTarget=player.getUUID();heldList=requests.get(heldTarget);heldSnapshot=List.copyOf(heldList);
        require(heldList.size()==1,"Unexpected incoming request count");
        var time=heldList.getFirst().getClass().getDeclaredMethod("time");time.setAccessible(true);
        heldTimestamp=(long)time.invoke(heldList.getFirst());
        require(System.currentTimeMillis()-heldTimestamp<=60_000,"Incoming control was already expired");
        // Exercise the ordinary command entry point. The prisoner's command fence
        // must stop acceptance before the native handler consumes this request.
        var accept=player.server.getCommands().getDispatcher().getRoot().getChild("tpaccept");
        require(accept!=null && accept.canUse(player.createCommandSourceStack()),
                "Native tpaccept is unavailable independently of the custody command fence");
        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"tpaccept");
        incomingUnchanged(player);
        var audit=Class.forName("dev.unyielding.jail.JailPackState")
                .getDeclaredMethod("verifyAttachments",ServerPlayer.class,net.minecraft.nbt.CompoundTag.class);
        audit.setAccessible(true);
        audit.invoke(null,player,player.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getCompound("neoforge:attachments"));
        incomingUnchanged(player);
        proof.put("held_incoming_peer_scope","synthetic server FakePlayer sending native tpahere; real connected Visitor remains command-fenced");
        flag("held_incoming_native_request_preserved_and_accept_command_blocked");
    }
    static void beforeEscape(ServerPlayer player) throws Exception {
        incomingUnchanged(player);
        require(System.currentTimeMillis()-heldTimestamp<=60_000,"Incoming request expired before actual escape");
        flag("held_incoming_native_request_live_at_actual_escape");
    }
    static void afterEscape(ServerPlayer player) throws Exception {
        incomingUnchanged(player);
        require(player.isAlive() && player.connection.player==player && JailCustodyService.quarantined(player),
                "Incoming request control lost real confined respawn");
        flag("held_incoming_request_kept_through_escape_and_respawn");
    }
    static void beforeRelease(ServerPlayer player) throws Exception {
        incomingUnchanged(player);
        require(System.currentTimeMillis()-heldTimestamp<=60_000,"Incoming request expired before actual release");
        flag("held_incoming_native_request_live_at_actual_release");
    }
    static void afterRelease(ServerPlayer player) throws Exception {
        incomingUnchanged(player);
        require(!JailCustodyService.quarantined(player) && JailData.get(player.server).assignment(player.getUUID())==null
                && JailIntegrated.activeTransaction(player.server,player.getUUID())==null,
                "Incoming request prevented complete release");
        flag("held_incoming_request_did_not_block_actual_release");
        // Cleanup happens only after release evidence. This is an ordinary native
        // deny command; a synthetic peer is not registered as an online client, so
        // the native handler removes the request and reports that peer offline.
        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"tpdeny");
        var remaining=requestMap().get(player.getUUID());
        require(remaining==null || remaining.isEmpty(),"Native deny did not close the synthetic incoming request");
        flag("released_native_deny_closed_synthetic_incoming_request");
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    static void run(ServerPlayer player) throws Exception {
        require(player.connection.player==player && !player.hasDisconnected(),"Teleport subject is not connected");
        Class<?> type=Class.forName("dev.unyielding.quests.essentials.TeleCmds");
        var field=type.getDeclaredField("REQUESTS");field.setAccessible(true);
        Map<UUID,List<Object>> requests=(Map)field.get(null);
        require(requests.isEmpty(),"Fresh teleport fixture already has requests");
        var request=type.getDeclaredMethod("request",CommandSourceStack.class,ServerPlayer.class,boolean.class);
        var answer=type.getDeclaredMethod("answer",CommandSourceStack.class,ServerPlayer.class,boolean.class);
        request.setAccessible(true);answer.setAccessible(true);
        var requestType=Class.forName(type.getName()+"$Request");
        var constructor=requestType.getDeclaredConstructor(UUID.class,boolean.class,long.class);constructor.setAccessible(true);
        var cooldown=(ModConfigSpec.IntValue)Class.forName("dev.unyielding.quests.essentials.EssConfig").getField("TPA_COOLDOWN").get(null);
        int oldCooldown=cooldown.get();
        var peer=FakePlayerFactory.get(player.serverLevel(),new GameProfile(UUID.randomUUID(),"PolicyPeer"));
        peer.setPos(player.getX()+3,player.getY(),player.getZ()+1);
        var prior=dev.unyielding.jail.Loc.of(player);
        UUID unrelatedTarget=UUID.randomUUID(),unrelatedFrom=UUID.randomUUID();
        try {
            // Test pacing only; the original server value is restored without saving.
            cooldown.set(0);
            require((int)request.invoke(null,player.createCommandSourceStack(),peer,false)==1,"Native tpa request failed");
            var row=List.copyOf(requests.get(peer.getUUID()));
            Custody249Proof.refused(player,"active_outgoing_tpa");
            require(row.equals(requests.get(peer.getUUID())),"Admission consumed an outgoing request");
            require((int)answer.invoke(null,peer.createCommandSourceStack(),player,true)==1,"Native accept after refused admission failed");
            require(player.distanceToSqr(peer.getX(),peer.getY(),peer.getZ())<0.0001
                    && dev.unyielding.jail.JailData.get(player.server).assignment(player.getUUID())==null,
                    "Refused admission left partial confinement before native teleport");
            prior.teleport(player);
            flag("native_tpa_accepted_only_after_admission_refusal_left_player_free");

            require((int)request.invoke(null,player.createCommandSourceStack(),peer,true)==1,"Native tpahere request failed");
            row=List.copyOf(requests.get(peer.getUUID()));
            Custody249Proof.refused(player,"active_outgoing_tpahere");
            require(row.equals(requests.get(peer.getUUID())),"Admission consumed a tpahere request");
            require((int)answer.invoke(null,peer.createCommandSourceStack(),player,false)==1,"Native request denial failed");

            require((int)request.invoke(null,peer.createCommandSourceStack(),player,false)==1,"Native incoming request failed");
            row=List.copyOf(requests.get(player.getUUID()));
            Custody249Proof.refused(player,"active_incoming_tpa");
            require(row.equals(requests.get(player.getUUID())),"Admission consumed an incoming request");
            requests.remove(player.getUUID()); // Remove only this synthetic control's request.

            Object expired=constructor.newInstance(player.getUUID(),false,System.currentTimeMillis()-61_000);
            requests.put(peer.getUUID(),new ArrayList<>(List.of(expired)));
            require(cmd("jailintegrated check Visitor")==1,"Native-expired request still blocked neutral admission");
            require(requests.get(peer.getUUID()).equals(List.of(expired)),"Preflight mutated expired requests");
            require((int)answer.invoke(null,peer.createCommandSourceStack(),player,true)==0,"An expired request was accepted");
            flag("expired_request_uses_native_sixty_second_semantics");

            Object unrelated=constructor.newInstance(unrelatedFrom,false,System.currentTimeMillis());
            requests.put(unrelatedTarget,new ArrayList<>(List.of(unrelated)));
            require(cmd("jailintegrated check Visitor")==1,"Unrelated players' request blocked this subject");
            require(requests.get(unrelatedTarget).equals(List.of(unrelated)),"Preflight changed unrelated players' request");
            flag("unrelated_teleport_request_preserved");
            requests.remove(unrelatedTarget);

            ((Map)requests).put(player.getUUID(),"unreviewed-shape");
            Custody249Proof.refused(player,"malformed_teleport_request_state");
            require("unreviewed-shape".equals(((Map)requests).get(player.getUUID())),"Refusal rewrote malformed request state");
            requests.remove(player.getUUID());
        } finally {
            cooldown.set(oldCooldown);
            requests.remove(player.getUUID());requests.remove(peer.getUUID());requests.remove(unrelatedTarget);
            if(dev.unyielding.jail.JailData.get(player.server).assignment(player.getUUID())==null) prior.teleport(player);
        }
        require(requests.isEmpty() && cmd("jailintegrated check Visitor")==1,"Teleport controls did not restore neutral eligibility");
        proof.put("teleport_peer_scope","synthetic server FakePlayer; subject is real connected Visitor");
        flag("pending_teleport_admission_controls_preserved_inventory");
    }
}
