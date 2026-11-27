package org.texboobcat.wcpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class LinkNetworkLockTest {
    @Test
    void releasesAfterExceptionAndIsReentrant() {
        assertThrows(IllegalStateException.class, () -> LinkNetworkLock.run(() -> {
            LinkNetworkLock.run(() -> assertTrue(LinkNetworkLock.isHeldByCurrentThread()));
            throw new IllegalStateException("boom");
        }));
        assertFalse(LinkNetworkLock.isHeldByCurrentThread());
    }

    @Test
    void timesOutAndStillRunsWhenHeldElsewhere() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> LinkNetworkLock.run(() -> {
            locked.countDown();
            try {
                release.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));
        holder.start();
        locked.await();
        AtomicBoolean ran = new AtomicBoolean();
        assertEquals(7, LinkNetworkLock.call(() -> {
            ran.set(true);
            return 7;
        }));
        assertTrue(ran.get());
        assertFalse(LinkNetworkLock.isHeldByCurrentThread());
        release.countDown();
        holder.join();
    }
}
