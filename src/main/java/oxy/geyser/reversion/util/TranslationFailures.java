package oxy.geyser.reversion.util;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.geyser.session.GeyserSession;
import oxy.geyser.reversion.GeyserReversion;
import java.util.Set;

/** Per-session, per-packet diagnostics without logging player data or inventory contents. */
public final class TranslationFailures {
    private final Set<String> reported = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<String> CRITICAL = Set.of("START_GAME", "CRAFTING_DATA", "INVENTORY_CONTENT",
            "INVENTORY_SLOT", "ITEM_STACK_REQUEST", "ITEM_STACK_RESPONSE", "CONTAINER_OPEN",
            "PLAYER_AUTH_INPUT", "MOVE_PLAYER", "INVENTORY_TRANSACTION", "LEVEL_CHUNK", "SUB_CHUNK");

    public void report(GeyserSession session, BedrockPacket packet, String direction, Exception failure) {
        String type = packet.getPacketType().getName();
        if (reported.add(direction + ":" + type)) {
            GeyserReversion.LOGGER.warning("Bedrock translation failed: " + type + " (" + direction
                    + "). Client protocol " + session.getUpstream().getSession().getCodec().getProtocolVersion()
                    + "; cause " + failure.getClass().getSimpleName() + ".");
            if (GeyserReversion.CONFIG.debugMode()) {
                GeyserReversion.LOGGER.severe("Translation failure details (debug mode)", failure);
            }
        }
        if (CRITICAL.contains(type)) {
            session.disconnect("This Bedrock version encountered an incompatible gameplay packet. "
                    + "Please update Minecraft; the server logged the affected packet type.");
        }
    }
}
