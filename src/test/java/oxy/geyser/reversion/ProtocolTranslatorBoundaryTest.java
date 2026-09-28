package oxy.geyser.reversion;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import oxy.geyser.reversion.ouranos.ProtocolInfo;
import oxy.geyser.reversion.ouranos.base.ProtocolToProtocol;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v340to332.Protocol340to332;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v766to748.Protocol766to748;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v776to766.Protocol776to766;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v818to800.Protocol818to800;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v844to827.Protocol844to827;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v1001to944.Protocol1001to944;
import oxy.geyser.reversion.ouranos.translators.old_to_new.v582to589.Protocol582to589;
import oxy.geyser.reversion.ouranos.translators.old_to_new.v671to685.Protocol671to685;
import oxy.geyser.reversion.ouranos.translators.old_to_new.v712to729.Protocol712to729;
import oxy.geyser.reversion.util.BridgeCodecSelector;
import oxy.geyser.reversion.util.GeyserApiCompat;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTranslatorBoundaryTest {
    /**
     * Protocol the descending chain starts at: the bridge the runtime actually selects, i.e. the
     * newest Bedrock protocol the pinned Geyser and the checked-in Ouranos mapping data share
     * (1001 on both Geyser 2.11.2 and 2.11.3). Derived from both sides' own data rather than from
     * {@link BridgeCodecSelector#select}.
     */
    static final int BRIDGE = derivedBridgeProtocol();

    /** First registered protocol below the bridge; a client here needs only the bridge step. */
    static final int NEXT_BELOW_BRIDGE = nextBelowBridge();

    private static int derivedBridgeProtocol() {
        int[] geyserProtocols = GeyserApiCompat.supportedBedrockProtocols();
        return ProtocolInfo.getPacketCodecs().stream()
                .map(BedrockCodec::getProtocolVersion)
                .filter(protocol -> Arrays.stream(geyserProtocols).anyMatch(supported -> supported == protocol))
                .filter(BridgeCodecSelector::hasMappingData)
                .max(Integer::compare)
                .orElseThrow(() -> new AssertionError("no Bedrock protocol is shared between the pinned Geyser ("
                        + Arrays.toString(geyserProtocols) + ") and the checked-in Ouranos mapping data"));
    }

    private static int nextBelowBridge() {
        return ProtocolInfo.getPacketCodecs().stream()
                .map(BedrockCodec::getProtocolVersion)
                .filter(protocol -> protocol < BRIDGE)
                .max(Integer::compare)
                .orElseThrow(() -> new AssertionError("no protocol is registered below the bridge " + BRIDGE));
    }

    static List<Class<?>> classes(List<ProtocolToProtocol> translators) {
        return translators.stream().<Class<?>>map(translator -> translator.getClass()).toList();
    }

    @Test void identicalProtocolsNeedNoTranslators() {
        assertTrue(ProtocolInfo.getTranslators(BRIDGE, BRIDGE).isEmpty());
    }

    @Test void clientAtTranslatorBoundaryIsNotTranslatedDown() {
        // 844 is the key of Protocol844to827, so a client that already speaks 844 must not be
        // downgraded by the translator registered at 844. Reaching it from the bridge still costs
        // the single bridge step (1001 -> 944), which is what the runtime runs.
        var chain = classes(ProtocolInfo.getTranslators(BRIDGE, 844));
        assertFalse(chain.contains(Protocol844to827.class),
                "the translator keyed at the client protocol must never run for a client on that protocol");
        assertEquals(List.of(Protocol1001to944.class), chain,
                "only the bridge step may run between the bridge and a client that already speaks 844");
    }

    @Test void clientBelowTranslatorBoundaryGetsExactlyThatTranslator() {
        // Top of the chain is the bridge, so the 1001 -> 944 step precedes the boundary step.
        assertEquals(List.of(Protocol1001to944.class, Protocol844to827.class),
                classes(ProtocolInfo.getTranslators(BRIDGE, 827)),
                "the chain must run the bridge step first and then exactly the translator the client sits below");
    }

    @Test void clientAtTheFirstProtocolBelowTheBridgeOnlyNeedsTheBridgeStep() {
        assertEquals(List.of(Protocol1001to944.class),
                classes(ProtocolInfo.getTranslators(BRIDGE, NEXT_BELOW_BRIDGE)),
                "the first protocol below the bridge is a passthrough codec, so only the bridge step may run");
    }

    @Test void descendingChainKeepsOrderAndExcludesClientKeyedTranslator() {
        assertEquals(List.of(Protocol844to827.class, Protocol818to800.class,
                Protocol776to766.class, Protocol766to748.class), classes(ProtocolInfo.getTranslators(898, 748)));
    }

    @Test void bridgeIsTheTopStepOfTheFullDescendingChain() {
        var chain = classes(ProtocolInfo.getTranslators(BRIDGE, 332));
        assertEquals(Protocol1001to944.class, chain.getFirst(),
                "the derived bridge must be the newest protocol the descending chain starts from");
        assertEquals(Protocol340to332.class, chain.getLast(),
                "the descending chain must end at the oldest registered translator step");
    }

    @Test void v1001DowngradeTo944UsesExactlyTheBridgeStep() {
        assertEquals(List.of(Protocol1001to944.class), classes(ProtocolInfo.getTranslators(1001, 944)),
                "1001 -> 944 is the single downgrade step; nothing else may be inserted between them");
    }

    @Test void v1001ChainToTheOldestCodecIsBookendedByTheBridgeAndOldestSteps() {
        var chain = classes(ProtocolInfo.getTranslators(1001, 332));
        assertEquals(Protocol1001to944.class, chain.getFirst(),
                "a 1001 client must enter the chain through Protocol1001to944");
        assertEquals(Protocol340to332.class, chain.getLast(),
                "the chain must still reach the oldest registered translator step");
        assertEquals(24, chain.size(),
                "1001 -> 332 crosses 24 registered downgrade steps; re-check this count when a translator is added "
                        + "or removed, and update it deliberately");
    }

    @Test void ascendingAtTranslatorBoundaryIsNotTranslatedUp() {
        assertTrue(ProtocolInfo.getTranslators(712, 766).isEmpty());
    }

    @Test void ascendingExcludesTranslatorKeyedAtClient() {
        var translators = classes(ProtocolInfo.getTranslators(545, 712));
        assertEquals(List.of(Protocol582to589.class, Protocol671to685.class), translators);
        assertFalse(translators.contains(Protocol712to729.class));
    }
}
