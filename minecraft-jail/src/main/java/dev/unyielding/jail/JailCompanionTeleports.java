package dev.unyielding.jail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Read-only boundary for the exact reviewed companion's ephemeral TPA queue.
 * An outside player can accept an earlier request, so checking only the prisoner's
 * later commands cannot make an already pending request safe. Never clear other
 * players' requests or modify this provider's state to manufacture eligibility. */
final class JailCompanionTeleports {
    private static final String OWNER = "dev.unyielding.quests.essentials.TeleCmds";
    private static final long LIFETIME_MILLIS = 60_000L;
    private static final int MAX_RECIPIENTS = 4_096, MAX_REQUESTS = 16_384;

    private JailCompanionTeleports() {}

    record Pending(UUID recipient, UUID sender, boolean here, long created) {}

    static void verifyNeutral(ServerPlayer player) throws IOException {
        // The reviewed companion is part of this exact roster. Older standalone
        // On contracts may not install TeleCmds and keep their existing checks.
        if (!JailHumanAudit.hasSpawnSwapContract(player)) return;
        verify(player, false);
    }

    static void verify(ServerPlayer player, boolean alreadyInCustody) throws IOException {
        if (player.server == null || !player.server.isSameThread())
            throw new IOException("Teleport-request admission audit requires the server thread");
        try {
            ClassLoader loader = JailCompanionTeleports.class.getClassLoader();
            Class<?> owner = Class.forName(OWNER, false, loader);
            Class<?> request = Class.forName(OWNER + "$Request", false, loader);
            if (!request.isRecord() || request.getRecordComponents().length != 3)
                throw new IOException("Unknown companion teleport request shape");
            var components = request.getRecordComponents();
            if (!components[0].getName().equals("from") || components[0].getType() != UUID.class
                    || !components[1].getName().equals("here") || components[1].getType() != boolean.class
                    || !components[2].getName().equals("time") || components[2].getType() != long.class)
                throw new IOException("Unknown companion teleport request fields");
            Field queue = owner.getDeclaredField("REQUESTS");
            if (!Modifier.isStatic(queue.getModifiers()) || !Map.class.isAssignableFrom(queue.getType())
                    || !queue.trySetAccessible() || !(queue.get(null) instanceof Map<?, ?> requests))
                throw new IOException("Companion teleport requests are unavailable");
            if (requests.size() > MAX_RECIPIENTS)
                throw new IOException("Companion teleport request queue exceeds the bounded admission audit");
            Field from = request.getDeclaredField("from"), here = request.getDeclaredField("here"), time = request.getDeclaredField("time");
            if (!from.trySetAccessible() || !here.trySetAccessible() || !time.trySetAccessible())
                throw new IOException("Companion teleport request fields are inaccessible");
            List<Pending> observed = new ArrayList<>();
            for (var entry : requests.entrySet()) {
                if (!(entry.getKey() instanceof UUID recipient) || !(entry.getValue() instanceof List<?> rows))
                    throw new IOException("Malformed companion teleport request queue");
                if (rows.size() > MAX_REQUESTS - observed.size())
                    throw new IOException("Too many pending companion teleport requests to audit safely");
                for (Object row : rows) {
                    if (row == null || row.getClass() != request || !(from.get(row) instanceof UUID sender))
                        throw new IOException("Malformed companion teleport request");
                    observed.add(new Pending(recipient, sender, here.getBoolean(row), time.getLong(row)));
                }
            }
            verifyRequests(player.getUUID(), System.currentTimeMillis(), observed, alreadyInCustody);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            throw new IOException("Unable to audit companion teleport requests; admission refused", failure);
        }
    }

    static void verifyRequests(UUID target, long now, List<Pending> requests) throws IOException {
        verifyRequests(target, now, requests, false);
    }

    static void verifyRequests(UUID target, long now, List<Pending> requests, boolean alreadyInCustody) throws IOException {
        if (target == null || now < 0 || requests == null)
            throw new IOException("Malformed teleport request audit");
        if (requests.size() > MAX_REQUESTS)
            throw new IOException("Too many pending companion teleport requests to audit safely");
        for (Pending request : requests) {
            if (request == null || request.recipient() == null || request.sender() == null || request.created() < 0)
                throw new IOException("Malformed companion teleport request");
            // Match TeleCmds.answer exactly: age == 60 seconds is still live.
            // A future timestamp remains live after a wall-clock adjustment.
            boolean expired = now >= request.created() && now - request.created() > LIFETIME_MILLIS;
            // An outsider's later incoming request cannot be accepted through the
            // quarantined player's command/payload fences. It must not block
            // escape recovery or release. An outgoing request could still be
            // accepted by an outsider and therefore remains unsafe while held.
            if (!expired && JailUnyieldingSwapState.blocksRequest(target, request.recipient(), request.sender(), alreadyInCustody))
                throw new IOException("Pending incoming or outgoing teleport request. Deny it or wait for expiry before admission");
        }
    }
}
