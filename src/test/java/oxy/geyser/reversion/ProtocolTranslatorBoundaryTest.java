package oxy.geyser.reversion;

import oxy.geyser.reversion.ouranos.ProtocolInfo;
import oxy.geyser.reversion.ouranos.base.ProtocolToProtocol;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v766to748.Protocol766to748;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v776to766.Protocol776to766;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v818to800.Protocol818to800;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v844to827.Protocol844to827;
import oxy.geyser.reversion.ouranos.translators.old_to_new.v582to589.Protocol582to589;
import oxy.geyser.reversion.ouranos.translators.old_to_new.v671to685.Protocol671to685;
import oxy.geyser.reversion.ouranos.translators.old_to_new.v712to729.Protocol712to729;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTranslatorBoundaryTest {
    static List<Class<?>> classes(List<ProtocolToProtocol> translators) {
        return translators.stream().<Class<?>>map(translator -> translator.getClass()).toList();
    }

    @Test void identicalProtocolsNeedNoTranslators() {
        assertTrue(ProtocolInfo.getTranslators(944, 944).isEmpty());
    }

    @Test void clientAtTranslatorBoundaryIsNotTranslatedDown() {
        assertTrue(ProtocolInfo.getTranslators(944, 844).isEmpty());
    }

    @Test void clientBelowTranslatorBoundaryGetsExactlyThatTranslator() {
        assertEquals(List.of(Protocol844to827.class), classes(ProtocolInfo.getTranslators(944, 827)));
    }

    @Test void descendingChainKeepsOrderAndExcludesClientKeyedTranslator() {
        assertEquals(List.of(Protocol844to827.class, Protocol818to800.class,
                Protocol776to766.class, Protocol766to748.class), classes(ProtocolInfo.getTranslators(898, 748)));
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
