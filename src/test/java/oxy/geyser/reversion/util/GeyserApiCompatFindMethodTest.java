package oxy.geyser.reversion.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the reflective method matcher used to bind Geyser internals that move
 * between versions. A Class literal passed as an expected argument used to be compared with
 * {@code Class.isInstance}, which never matches, so the lookup for Geyser's
 * {@code Bootstraps.setupBootstrap(AbstractBootstrap, TransportType)} failed at runtime even
 * though the method was present.
 */
class GeyserApiCompatFindMethodTest {

    @SuppressWarnings("unused")
    static final class Fixture {
        void target(CharSequence text, Runnable task) { }

        void ambiguous(Object value) { }

        void ambiguous(CharSequence value) { }
    }

    @Test
    void classLiteralsMatchAssignableParameters() {
        assertNotNull(GeyserApiCompat.findMethod(Fixture.class, "target", CharSequence.class, Runnable.class));
        assertNotNull(GeyserApiCompat.findMethod(Fixture.class, "target", String.class, Runnable.class));
    }

    @Test
    void instancesMatchTheirParameterTypes() {
        assertNotNull(GeyserApiCompat.findMethod(Fixture.class, "target", "text", (Runnable) () -> { }));
    }

    @Test
    void unrelatedClassLiteralDoesNotMatch() {
        assertThrows(IllegalStateException.class,
                () -> GeyserApiCompat.findMethod(Fixture.class, "target", Thread.class, Runnable.class));
    }

    @Test
    void ambiguousOverloadsAreRejected() {
        assertThrows(IllegalStateException.class,
                () -> GeyserApiCompat.findMethod(Fixture.class, "ambiguous", CharSequence.class));
    }

    @Test
    void parametersAcceptRejectsNullForPrimitives() {
        assertFalse(GeyserApiCompat.parametersAccept(new Class<?>[]{int.class}, new Object[]{null}));
        assertTrue(GeyserApiCompat.parametersAccept(new Class<?>[]{Integer.class}, new Object[]{null}));
    }
}
