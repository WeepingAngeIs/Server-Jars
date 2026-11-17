package dev.unyielding.jail.party.live;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Actual graphical loopback client, custom detached snapshot cache only. No vanilla party UI claim. */
@EventBusSubscriber(modid="uq_bundle_smoke",value=Dist.CLIENT)
public final class SessionClient {
    static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    static JsonObject latest,beforeReconnect,stale,oldNetwork;static CompoundTag party;static byte[] pc;
    static boolean hello,done,reconnect,connecting;static int disconnectedTicks,connectAttempts,ticks;static String stage="start";
    static final Map<String,Object> proof=new LinkedHashMap<>();
    static int phase(){return Integer.getInteger("uq.sessionPhase");}
    static String name(){return Minecraft.getInstance().getUser().getName();}
    static UUID action(String n){return UUID.nameUUIDFromBytes(("jail-session-action:"+n).getBytes(StandardCharsets.UTF_8));}
    static void require(boolean b,String why)throws IOException{if(!b)throw new IOException(why);}
    static void flag(String key){proof.put(key,true);System.out.println("JAIL_SESSION_CLIENT "+key);}
    static void send(JsonObject request){PacketDistributor.sendToServer(new SessionWire.Request(GSON.toJson(request)));}
    static JsonObject base(String op,String q){
        JsonObject r=new JsonObject();r.addProperty("op",op);r.addProperty("q",q);
        if(latest!=null)for(String k:List.of("owner","nonce","epoch","receipt","pokemon"))r.add(k,latest.get(k).deepCopy());return r;
    }
    static JsonObject edit(String q,String action,String nickname){var r=base("RENAME",q);r.addProperty("action",action(action).toString());r.addProperty("name",nickname);return r;}
    static CompoundTag decode(byte[] bytes)throws IOException{try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){var n=NbtIo.read(in,NbtAccounter.create(1048576));require(n!=null&&in.available()==0,"Bad client NBT");return n;}}
    static void snapshot(JsonObject r)throws Exception {
        require(r.get("owner").getAsString().equals(Minecraft.getInstance().player.getUUID().toString()),"Snapshot leaked another owner");
        byte[] p=Base64.getDecoder().decode(r.get("party").getAsString()),c=Base64.getDecoder().decode(r.get("pc").getAsString());
        MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(java.nio.ByteBuffer.allocate(8).putInt(p.length).putInt(c.length).array());
        digest.update(r.get("owner").getAsString().getBytes(StandardCharsets.UTF_8));digest.update(r.get("provider").getAsString().getBytes(StandardCharsets.UTF_8));digest.update(p);digest.update(c);
        require(HexFormat.of().formatHex(digest.digest()).equals(r.get("hash").getAsString()),"Snapshot full-state hash mismatch");
        CompoundTag next=decode(p);decode(c);
        if(latest!=null){
            require(Arrays.equals(pc,c),"PC state changed during network nickname edit");
            if(r.get("q").getAsString().startsWith("good-")){
                require(r.get("epoch").getAsLong()==latest.get("epoch").getAsLong()+1&&!r.get("receipt").equals(latest.get("receipt")),"Mutation receipt/epoch not advanced exactly");
                require(next.getAllKeys().equals(party.getAllKeys()),"Party topology changed");int changes=0;
                for(String key:next.getAllKeys())if(!Objects.equals(next.get(key),party.get(key))){
                    require(next.get(key) instanceof CompoundTag&&party.get(key) instanceof CompoundTag,"Non-Pokemon field changed");
                    CompoundTag a=((CompoundTag)party.get(key)).copy(),b=((CompoundTag)next.get(key)).copy();
                    a.remove("Nickname");b.remove("Nickname");require(a.equals(b),"Identity or non-nickname state changed");changes++;
                }
                require(changes==1,"Expected one exact nickname-field difference");flag("client_full_state_nickname_only_delta");
            }else require(next.equals(party),"Reconnect did not preserve complete party");
        }
        latest=r.deepCopy();party=next;pc=c;flag("client_verified_exact_snapshot_hash");
    }
    static void expect(JsonObject r,String code)throws IOException{require(r.get("code").getAsString().equals(code),"Expected "+code+" for "+r);if(code.equals("DENIED"))require(!r.has("party")&&!r.has("pc"),"Denial supplied fallback state");}
    public static void receive(String wire){
        if(!Boolean.getBoolean("uq.partySession")||done)return;
        try{
            JsonObject r=JsonParser.parseString(wire).getAsJsonObject();String q=r.get("q").getAsString(),code=r.get("code").getAsString();
            if(code.equals("WAIT")){hello=false;return;}
            if(name().equals("Dev")){expect(r,"DENIED");flag("unrelated_client_received_no_target_snapshot");complete();return;}
            if(code.equals("NATIVE_DENIED")){require(stage.equals("native"),"Unexpected native denial");flag("real_native_packet_rejected_without_disconnect");stage="queue";send(base("QUEUE","queue"));return;}
            switch(q){
                case "hello" -> {
                    expect(r,"SNAPSHOT");snapshot(r);
                    if(phase()==2){require(r.get("name").getAsString().equals("Network Two"),"Normal restart lost final state");flag("client_restart_preserves_prior_network_edit");send(edit("restart-dup-a","a","No Replay"));}
                    else if(r.get("joins").getAsInt()==1){
                        require(r.get("name").getAsString().equals("Network Original"),"Initial state wrong");stage="malformed";PacketDistributor.sendToServer(new SessionWire.Request("{"));
                    }else{
                        require(beforeReconnect!=null&&!latest.get("nonce").equals(beforeReconnect.get("nonce"))&&latest.get("epoch").getAsLong()>beforeReconnect.get("epoch").getAsLong(),"Reconnection reused session authority");
                        reconnect=false;flag("actual_client_reconnected_with_new_nonce_and_epoch");oldNetwork.addProperty("q","old-network");send(oldNetwork);
                    }
                }
                case "malformed" -> {expect(r,"DENIED");flag("malformed_request_denied_without_disconnect");var bad=edit("spoof","spoof","No");bad.addProperty("owner",UUID.nameUUIDFromBytes("OfflinePlayer:Dev".getBytes(StandardCharsets.UTF_8)).toString());send(bad);}
                case "spoof" -> {expect(r,"DENIED");flag("forged_owner_refused");var bad=edit("bad-receipt","bad-receipt","No");bad.addProperty("receipt","wrong-generation");send(bad);}
                case "bad-receipt" -> {expect(r,"DENIED");flag("wrong_exact_receipt_refused");var bad=edit("foreign-target","foreign","No");bad.addProperty("pokemon",UUID.randomUUID().toString());send(bad);}
                case "foreign-target" -> {expect(r,"DENIED");flag("foreign_pokemon_refused");stale=edit("stale-generation","stale","No");send(edit("good-a","a","Network One"));}
                case "good-a" -> {expect(r,"SNAPSHOT");snapshot(r);require(r.get("name").getAsString().equals("Network One"),"First edit missing");send(stale);}
                case "stale-generation" -> {expect(r,"DENIED");flag("stale_generation_network_request_refused");send(edit("duplicate-a","a","No Replay"));}
                case "duplicate-a" -> {expect(r,"DENIED");flag("duplicate_action_refused_same_connection");stage="native";nativeSwap();}
                case "queue" -> {expect(r,"QUEUED");oldNetwork=edit("old-network","old-network","No");send(base("RECONNECT","reconnect"));}
                case "reconnect" -> {
                    expect(r,"RECONNECT");beforeReconnect=latest.deepCopy();reconnect=true;hello=false;connecting=false;disconnectedTicks=0;
                    Minecraft.getInstance().getConnection().getConnection().disconnect(Component.literal("planned Jail session reconnect"));flag("client_initiated_real_disconnect");
                }
                case "old-network" -> {expect(r,"DENIED");flag("old_connection_token_refused_on_reconnect");send(edit("reconnect-duplicate","a","No Replay"));}
                case "reconnect-duplicate" -> {expect(r,"DENIED");flag("persistent_action_refused_on_reconnect");send(edit("good-b","b","Network Two"));}
                case "restart-dup-a" -> {expect(r,"DENIED");send(edit("restart-dup-b","b","No Replay"));}
                case "restart-dup-b" -> {expect(r,"DENIED");flag("both_persistent_actions_refused_after_server_restart");send(edit("good-c","c","Network Three"));}
                case "good-b","good-c" -> {expect(r,"SNAPSHOT");snapshot(r);var ack=base("ACK","ack");ack.add("hash",latest.get("hash"));send(ack);}
                case "ack" -> {expect(r,"ACK");flag("exact_state_acknowledged_over_real_connection");send(base("DONE","done"));}
                case "done" -> {expect(r,"DONE");complete();}
                default -> throw new IOException("Unexpected reply: "+r);
            }
        }catch(Throwable failure){failure(failure);}
    }
    static void nativeSwap()throws Exception {
        UUID first=UUID.fromString(latest.get("pokemon").getAsString());
        UUID second=UUID.fromString(latest.get("swap_other").getAsString());
        require(!first.equals(second),"Native swap fixture needs two distinct existing Pokemon");
        var packet=new com.cobblemon.mod.common.net.messages.server.storage.party.SwapPartyPokemonPacket(
            first,new com.cobblemon.mod.common.api.storage.party.PartyPosition(0),
            second,new com.cobblemon.mod.common.api.storage.party.PartyPosition(5));
        require(first.equals(packet.getPokemon1ID())&&second.equals(packet.getPokemon2ID()),"Native packet identity mismatch");
        packet.sendToServer();flag("actual_native_swap_packet_sent");
    }
    static void connect()throws Exception {
        Minecraft mc=Minecraft.getInstance();ServerData data=new ServerData("Jail disposable","127.0.0.1:25565",ServerData.Type.OTHER);
        var methods=Arrays.stream(ConnectScreen.class.getDeclaredMethods()).filter(m->m.getName().equals("startConnecting")&&java.lang.reflect.Modifier.isStatic(m.getModifiers())&&m.getParameterCount()==6).toList();
        require(methods.size()==1,"Unreviewed reconnect signature");methods.getFirst().invoke(null,new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25565"),data,false,null);
        connectAttempts++;connecting=true;require(connectAttempts==1,"No repeated reconnect retry masking failure");
    }
    static void complete()throws Exception {
        proof.put("commit",System.getenv("GITHUB_SHA"));proof.put("phase",phase());proof.put("client",name());proof.put("reconnect_attempts",connectAttempts);proof.put("passed",true);proof.put("ordinary_party_ui_verified",false);
        if(latest!=null){proof.put("final_hash",latest.get("hash").getAsString());proof.put("final_name",latest.get("name").getAsString());proof.put("final_receipt",latest.get("receipt").getAsString());}
        Path p=Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/session-client-"+name()+"-phase"+phase()+".json");Files.writeString(p,GSON.toJson(proof));done=true;
    }
    static void failure(Throwable t){t.printStackTrace();done=true;try{Files.writeString(Path.of("/tmp/uq-session-result"),"failed: client "+name()+": "+t+"\n");}catch(IOException ignored){}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("uq.partySession")||done)return;
        try{
            Minecraft mc=Minecraft.getInstance();if(++ticks>10000)throw new IOException("Client protocol timeout");
            if(reconnect&&!connecting){
                if((mc.getConnection()==null||mc.player==null)&&++disconnectedTicks>40)connect();return;
            }
            if(reconnect&&(mc.getConnection()==null||mc.player==null))return;
            if(mc.getConnection()!=null&&mc.player!=null&&mc.player.tickCount>110&&!hello){
                hello=true;JsonObject r=new JsonObject();r.addProperty("op","HELLO");r.addProperty("q",name().equals("Dev")?"dev":"hello");send(r);
            }
        }catch(Throwable failure){failure(failure);}
    }
}
