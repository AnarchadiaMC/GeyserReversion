package oxy.geyser.reversion.util;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.geyser.session.GeyserSession;
import oxy.geyser.reversion.GeyserReversion;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Per-session, per-direction, per-packet-type diagnostics without logging player data or inventory contents. */
public class TranslationFailures {
    public static final String FAILURE_LIMIT_PROPERTY = "Geyser.Reversion.TranslationFailureLimit";

    private static final int DEFAULT_FAILURE_LIMIT = 3;
    private static final Set<String> CRITICAL = Set.of("START_GAME", "PLAYER_AUTH_INPUT");
    private static final String CRITICAL_DISCONNECT_MESSAGE = "This Bedrock version encountered an incompatible gameplay packet. "
            + "Please update Minecraft; the server logged the affected packet type.";
    private static final String FAILURE_LIMIT_DISCONNECT_MESSAGE = "This Bedrock version repeatedly failed to translate gameplay packets. "
            + "Please update Minecraft; the server logged the affected packet types.";

    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private final Set<String> disconnected = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> failureCounts = new ConcurrentHashMap<>();

    public void report(GeyserSession session, BedrockPacket packet, String direction, Exception failure) {
        try {
            reportFailure(session, packet, direction, failure);
        } catch (RuntimeException ignored) {
            // Diagnostics must never break packet handling.
        }
    }

    private void reportFailure(GeyserSession session, BedrockPacket packet, String direction, Exception failure) {
        String type = packet == null ? "UNKNOWN" : packet.getPacketType().getName();
        String key = direction + ":" + type;
        int failures = failureCounts.merge(key, 1, Integer::sum);

        if (warned.add(key)) {
            logWarning(type, direction, protocolVersion(session), failure);
        }

        boolean sessionBreaking = CRITICAL.contains(type);
        if (sessionBreaking || failures >= failureLimit()) {
            if (disconnected.add(key)) {
                disconnect(session, sessionBreaking ? CRITICAL_DISCONNECT_MESSAGE : FAILURE_LIMIT_DISCONNECT_MESSAGE);
            }
        }
    }

    protected void disconnect(GeyserSession session, String reason) {
        if (session != null) {
            session.disconnect(reason);
        }
    }

    protected int protocolVersion(GeyserSession session) {
        if (session == null) {
            return -1;
        }
        try {
            var upstream = session.getUpstream();
            if (upstream == null) {
                return -1;
            }
            var bedrockSession = upstream.getSession();
            if (bedrockSession == null) {
                return -1;
            }
            var codec = bedrockSession.getCodec();
            return codec == null ? -1 : codec.getProtocolVersion();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static int failureLimit() {
        Integer limit = Integer.getInteger(FAILURE_LIMIT_PROPERTY, DEFAULT_FAILURE_LIMIT);
        return limit == null || limit < 1 ? DEFAULT_FAILURE_LIMIT : limit;
    }

    private static void logWarning(String type, String direction, int protocol, Exception failure) {
        if (GeyserReversion.LOGGER == null) {
            return;
        }
        String cause = failure == null ? "unknown" : failure.getClass().getSimpleName();
        GeyserReversion.LOGGER.warning("Bedrock translation failed: " + type + " (" + direction
                + "). Client protocol " + protocol + "; cause " + cause + ".");
        if (failure != null && GeyserReversion.config().debugMode()) {
            GeyserReversion.LOGGER.severe("Translation failure details (debug mode)", failure);
        }
    }
}
