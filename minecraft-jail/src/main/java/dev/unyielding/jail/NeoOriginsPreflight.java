package dev.unyielding.jail;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;

/**
 * Read-only compatibility inspection for the specifically inspected NeoOrigins
 * version. A successful inspection is NOT permission to jail or change an
 * origin: escrow, cross-store durability, lifecycle callbacks and recovery are
 * not implemented by this class. It never changes origins, claims or inventory.
 */
public final class NeoOriginsPreflight {
    private static final String SUPPORTED_VERSION = "2.2.27";
    private static final String CLAIMS_DATA = "neoorigins_origin_claims";
    private static final ResourceLocation LAYER = ResourceLocation.parse("neoorigins:origin");
    private static final ResourceLocation HUMAN = ResourceLocation.parse("neoorigins:human");

    public enum Status {
        INSPECTED, MOD_ABSENT, UNSUPPORTED_VERSION, MISSING_ATTACHMENT,
        MISSING_LAYER, MISSING_HUMAN, MISSING_SELECTION, HUMAN_UNAVAILABLE,
        MISSING_SELECTED_ORIGIN, CLAIM_DATA_UNAVAILABLE, ORIGINAL_CLAIM_NOT_OWNED,
        HUMAN_CLAIMED_BY_OTHER, API_UNAVAILABLE
    }
    /** This result is informational; INSPECTED does not authorize admission. */
    public record Result(Status status, ResourceLocation original, boolean unique,
                         UUID originalOwner, UUID humanOwner) { }

    private NeoOriginsPreflight() { }

    public static Result inspect(ServerPlayer player) {
        if (player == null || player.server == null || !player.server.isSameThread()) return result(Status.API_UNAVAILABLE);
        try {
            var container = ModList.get().getModContainerById("neoorigins");
            if (container.isEmpty()) return result(Status.MOD_ABSENT);
            if (!SUPPORTED_VERSION.equals(container.get().getModInfo().getVersion().toString()))
                return result(Status.UNSUPPORTED_VERSION);

            Class<?> layersClass = Class.forName("com.cyberday1.neoorigins.data.LayerDataManager");
            Object layers = layersClass.getField("INSTANCE").get(null);
            Object layer = layersClass.getMethod("getLayer", ResourceLocation.class).invoke(layers, LAYER);
            if (layer == null) return result(Status.MISSING_LAYER);
            Class<?> originsClass = Class.forName("com.cyberday1.neoorigins.data.OriginDataManager");
            Object origins = originsClass.getField("INSTANCE").get(null);
            Method hasOrigin = originsClass.getMethod("hasOrigin", ResourceLocation.class);
            if (!Boolean.TRUE.equals(hasOrigin.invoke(origins, HUMAN))) return result(Status.MISSING_HUMAN);

            Class<?> attachments = Class.forName("com.cyberday1.neoorigins.attachment.OriginAttachments");
            Object attachment = attachments.getMethod("originData").invoke(null);
            if (!(attachment instanceof AttachmentType<?> type)) return result(Status.API_UNAVAILABLE);
            // Unlike getData, this never creates a missing player attachment.
            Object data = player.getExistingDataOrNull(type);
            if (data == null) return result(Status.MISSING_ATTACHMENT);
            Class<?> dataClass = Class.forName("com.cyberday1.neoorigins.attachment.PlayerOriginData");
            Object selection = dataClass.getMethod("getOrigin", ResourceLocation.class).invoke(data, LAYER);
            if (!(selection instanceof ResourceLocation original)) return result(Status.MISSING_SELECTION);
            if (!Boolean.TRUE.equals(hasOrigin.invoke(origins, original)))
                return new Result(Status.MISSING_SELECTED_ORIGIN, original, false, null, null);
            Object current = dataClass.getMethod("getOrigins").invoke(data);
            if (!(current instanceof Map<?, ?> map)) return result(Status.API_UNAVAILABLE);
            // Copy the unmodifiable view; preserve every unrelated layer.
            Map<?, ?> snapshot = Map.copyOf(map);
            Object available = layer.getClass().getMethod("getAvailableOriginIds", Map.class).invoke(layer, snapshot);
            if (!(available instanceof List<?> choices)) return result(Status.API_UNAVAILABLE);
            if (!choices.contains(HUMAN)) return new Result(Status.HUMAN_UNAVAILABLE, original, false, null, null);

            Class<?> configClass = Class.forName("com.cyberday1.neoorigins.config.AdminConfig");
            boolean unique = Boolean.TRUE.equals(configClass.getMethod("isUniqueLayer", ResourceLocation.class).invoke(null, LAYER));
            if (!unique) return new Result(Status.INSPECTED, original, false, null, null);

            Class<?> claimsClass = Class.forName("com.cyberday1.neoorigins.data.OriginClaimsData");
            Method load = claimsClass.getMethod("load", CompoundTag.class, HolderLookup.Provider.class);
            // get() reads existing SavedData and does not create a new claims record.
            // Missing/unreadable data is ambiguous, so report unavailable, not free.
            SavedData.Factory<SavedData> factory = new SavedData.Factory<>(
                    () -> { throw new IllegalStateException("preflight cannot create claims"); },
                    (tag, registries) -> {
                        try { return (SavedData) load.invoke(null, tag, registries); }
                        catch (ReflectiveOperationException | RuntimeException failure) { throw new IllegalStateException(failure); }
                    });
            SavedData claims = player.server.overworld().getDataStorage().get(factory, CLAIMS_DATA);
            if (claims == null || !claimsClass.isInstance(claims))
                return new Result(Status.CLAIM_DATA_UNAVAILABLE, original, true, null, null);
            Method owner = claimsClass.getMethod("getOwner", ResourceLocation.class, ResourceLocation.class);
            UUID currentOwner = (UUID) owner.invoke(claims, LAYER, original);
            UUID humanOwner = (UUID) owner.invoke(claims, LAYER, HUMAN);
            Status status = switch (NeoOriginsClaimPolicy.classify(player.getUUID(), currentOwner, humanOwner)) {
                case ORIGINAL_NOT_OWNED -> Status.ORIGINAL_CLAIM_NOT_OWNED;
                case HUMAN_OWNED_BY_OTHER -> Status.HUMAN_CLAIMED_BY_OTHER;
                case OWNED -> Status.INSPECTED;
            };
            return new Result(status, original, true, currentOwner, humanOwner);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return result(Status.API_UNAVAILABLE);
        }
    }

    private static Result result(Status status) { return new Result(status, null, false, null, null); }
}
