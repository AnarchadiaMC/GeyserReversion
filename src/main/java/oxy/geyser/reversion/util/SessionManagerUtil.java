package oxy.geyser.reversion.util;

import org.geysermc.geyser.session.GeyserSession;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class SessionManagerUtil {
    private static final Method REACHED_MAX_CONNECTIONS_PER_ADDRESS = findReachedMaxConnectionsPerAddress();

    public static boolean reachedMaxConnectionsPerAddress(Object sessionManager, GeyserSession session) {
        if (REACHED_MAX_CONNECTIONS_PER_ADDRESS == null) {
            return false;
        }

        try {
            return (boolean) REACHED_MAX_CONNECTIONS_PER_ADDRESS.invoke(sessionManager, session);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to check max connections per address", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException("Unable to check max connections per address", cause);
        }
    }

    private static Method findReachedMaxConnectionsPerAddress() {
        try {
            return Class.forName("org.geysermc.geyser.session.SessionManager")
                    .getMethod("reachedMaxConnectionsPerAddress", GeyserSession.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        }
    }

    private SessionManagerUtil() {
    }
}
