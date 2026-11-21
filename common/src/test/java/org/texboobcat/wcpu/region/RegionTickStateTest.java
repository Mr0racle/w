package org.texboobcat.wcpu.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegionTickStateTest {

    @Test
    void entityPhaseRunsOncePerClaim() {
        RegionTickState state = new RegionTickState(1);
        state.markClaimed();
        assertTrue(state.beginEntityPhase());
        assertFalse(state.beginEntityPhase(), "a second start in the same claim must be refused");
        state.markClaimed();
        assertTrue(state.beginEntityPhase(), "the next claim may run the phase again");
    }

    @Test
    void snapshotIsTakenOnceUntilCleared() {
        RegionTickState state = new RegionTickState(1);
        java.util.List<?> first = state.snapshotIfAbsent();
        assertSame(first, state.snapshotIfAbsent());
        assertEquals(0, first.size());
        state.clearSnapshot();
        assertSame(first, state.snapshotIfAbsent(), "the buffer is reused, not reallocated");
    }

    @Test
    void concurrentDoubleStartAdmitsOnlyOne() throws Exception {
        RegionTickState state = new RegionTickState(1);
        state.markClaimed();
        java.util.concurrent.atomic.AtomicInteger admitted = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (state.beginEntityPhase()) {
                    admitted.incrementAndGet();
                }
            });
            threads[i].start();
        }
        go.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals(1, admitted.get());
    }
}
