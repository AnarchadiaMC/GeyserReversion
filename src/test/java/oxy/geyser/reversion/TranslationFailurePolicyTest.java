package oxy.geyser.reversion;

import org.cloudburstmc.protocol.bedrock.packet.SetTimePacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.geysermc.geyser.session.GeyserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import oxy.geyser.reversion.util.TranslationFailures;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationFailurePolicyTest {

    static class RecordingFailures extends TranslationFailures {
        final List<String> disconnects = new ArrayList<>();

        @Override
        protected void disconnect(GeyserSession session, String reason) {
            disconnects.add(reason);
        }
    }

    @BeforeEach
    void pinFailureLimit() {
        System.setProperty(TranslationFailures.FAILURE_LIMIT_PROPERTY, "3");
    }

    @AfterEach
    void clearFailureLimit() {
        System.clearProperty(TranslationFailures.FAILURE_LIMIT_PROPERTY);
    }

    @Test
    void criticalPacketDisconnectsOnFirstFailureOnly() {
        var failures = new RecordingFailures();
        var packet = new StartGamePacket();

        failures.report(null, packet, "serverbound", new IllegalStateException("boom"));
        assertEquals(1, failures.disconnects.size());

        failures.report(null, packet, "serverbound", new IllegalStateException("boom"));
        assertEquals(1, failures.disconnects.size());
    }

    @Test
    void nonCriticalPacketDisconnectsOnlyAfterTheFailureLimit() {
        var failures = new RecordingFailures();
        var packet = new SetTimePacket();

        for (int i = 0; i < 2; i++) {
            failures.report(null, packet, "clientbound", new IllegalStateException("boom"));
        }
        assertTrue(failures.disconnects.isEmpty());

        failures.report(null, packet, "clientbound", new IllegalStateException("boom"));
        assertEquals(1, failures.disconnects.size());

        failures.report(null, packet, "clientbound", new IllegalStateException("boom"));
        assertEquals(1, failures.disconnects.size());
    }

    @Test
    void directionsAreCountedSeparately() {
        var failures = new RecordingFailures();
        var packet = new SetTimePacket();

        for (int i = 0; i < 2; i++) {
            failures.report(null, packet, "clientbound", new IllegalStateException("boom"));
            failures.report(null, packet, "serverbound", new IllegalStateException("boom"));
        }
        assertTrue(failures.disconnects.isEmpty());

        failures.report(null, packet, "serverbound", new IllegalStateException("boom"));
        assertEquals(1, failures.disconnects.size());
    }

    @Test
    void nullSessionAndUnavailableCodecNeverThrow() {
        var failures = new TranslationFailures();

        assertDoesNotThrow(() -> failures.report(null, new SetTimePacket(), "clientbound", new IllegalStateException("boom")));
        assertDoesNotThrow(() -> failures.report(null, new StartGamePacket(), "serverbound", new IllegalStateException("boom")));
        assertDoesNotThrow(() -> failures.report(null, new SetTimePacket(), "clientbound", null));
    }
}
