package org.texboobcat.wcpu;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * One global lock for mod-owned redstone-link network state (plain unsynchronized sets that a worker
 * and the main thread can touch at once). Bounded wait: on timeout the action still runs, so a stuck
 * holder can never freeze the server.
 */
public final class LinkNetworkLock {
    static final long WAIT_MILLIS = 50;
    private static final ReentrantLock LOCK = new ReentrantLock();

    private LinkNetworkLock() {}

    public static void run(Runnable action) {
        boolean held = acquire();
        try {
            action.run();
        } finally {
            if (held) {
                LOCK.unlock();
            }
        }
    }

    static <T> T call(Supplier<T> action) {
        boolean held = acquire();
        try {
            return action.get();
        } finally {
            if (held) {
                LOCK.unlock();
            }
        }
    }

    private static boolean acquire() {
        try {
            return LOCK.tryLock(WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    static boolean isHeldByCurrentThread() {
        return LOCK.isHeldByCurrentThread();
    }
}
