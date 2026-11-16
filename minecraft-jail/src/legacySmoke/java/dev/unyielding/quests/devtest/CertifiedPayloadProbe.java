package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Real registered payload in the separate disposable mod, never in the release JAR. */
@EventBusSubscriber(modid=UnyieldingQuests.MODID, bus=EventBusSubscriber.Bus.MOD)
public final class CertifiedPayloadProbe {
    static int accepted;
    public record Probe() implements CustomPacketPayload {
        public static final Type<Probe> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("uq_bundle_smoke","custody_guard_probe"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Probe> CODEC=StreamCodec.unit(new Probe());
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(Probe.TYPE,Probe.CODEC,(payload,context)->accepted++);}
}
