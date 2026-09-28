package oxy.geyser.reversion.util;

import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.UpstreamSession;
import oxy.geyser.reversion.GeyserReversion;
import oxy.geyser.reversion.handler.TranslatorPacketHandler;
import oxy.geyser.reversion.handler.TranslatorSendListener;

import java.lang.reflect.Field;

public class GeyserUtil {
    private GeyserUtil() {
    }

    public static boolean hook(final GeyserSession session) {
        try {
            return injectCloudburstUpstream(session, findCloudburstSession(session));
        } catch (ReflectiveOperationException | RuntimeException e) {
            GeyserReversion.LOGGER.warning("Failed to hook translated upstream session; disconnecting client. Cause: "
                    + e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
            if (GeyserReversion.config().debugMode()) {
                GeyserReversion.LOGGER.severe("Translated upstream hook failure details", e);
            }
            return false;
        }
    }

    private static boolean injectCloudburstUpstream(final GeyserSession session, final BedrockServerSession downstream) throws ReflectiveOperationException {
        final Field upstream = GeyserSession.class.getDeclaredField("upstream");
        upstream.setAccessible(true);

        final TranslatorPacketHandler handler = (TranslatorPacketHandler) downstream.getPacketHandler();
        if (handler.getUser() == null) {
            return false;
        }
        upstream.set(session, new TranslatorSendListener(handler.getUser(), downstream, (UpstreamSession) upstream.get(session)));
        return true;
    }

    private static BedrockServerSession findCloudburstSession(final GeyserSession connection) throws ReflectiveOperationException {
        final Field upstream = GeyserSession.class.getDeclaredField("upstream");
        upstream.setAccessible(true);
        final Object session = upstream.get(connection);
        final Field field = UpstreamSession.class.getDeclaredField("session");
        field.setAccessible(true);
        return (BedrockServerSession) field.get(session);
    }
}
