package dev.unyielding.jail.party.live;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Bounded test protocol. A snapshot is data, never a client-side custody authorization. */
@EventBusSubscriber(modid="uq_bundle_smoke",bus=EventBusSubscriber.Bus.MOD)
public final class SessionWire {
    public record Request(String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("uq_bundle_smoke","jail_session_request"));
        public static final StreamCodec<FriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,8192),b->new Request(b.readUtf(8192)));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Reply(String json) implements CustomPacketPayload {
        public static final Type<Reply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("uq_bundle_smoke","jail_session_reply"));
        public static final StreamCodec<FriendlyByteBuf,Reply> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,131072),b->new Reply(b.readUtf(131072)));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
        if(!Boolean.getBoolean("uq.partySession"))return;
        var registrar=event.registrar("jail-session-test-1");
        registrar.playToServer(Request.TYPE,Request.CODEC,(payload,context)->SessionBoundary.request((net.minecraft.server.level.ServerPlayer)context.player(),payload.json()));
        registrar.playToClient(Reply.TYPE,Reply.CODEC,(payload,context)->SessionClient.receive(payload.json()));
    }
}
