package org.texboobcat.wcpu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ConcurrentModificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TickGuardTest {

    @BeforeEach
    void clean() {
        TickGuard.reset();
    }

    @Test
    void absorbsAFewRacedTicks() {
        for (int i = 0; i < TickGuard.MAX_PER_WINDOW; i++) {
            assertTrue(TickGuard.tolerate(new ConcurrentModificationException()));
        }
    }

    @Test
    void stopsWhenItRepeatsEveryTick() {
        for (int i = 0; i < TickGuard.MAX_PER_WINDOW; i++) {
            TickGuard.tolerate(new ConcurrentModificationException());
        }
        assertFalse(TickGuard.tolerate(new ConcurrentModificationException()));
    }
}
