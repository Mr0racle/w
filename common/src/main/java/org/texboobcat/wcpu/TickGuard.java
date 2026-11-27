package org.texboobcat.wcpu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ConcurrentModificationException;

// Keeps the server alive when another mod's code on the server thread trips over a list that was
// changed under it.
//
// Region workers run beside the server thread, so mods that keep unsynchronised global state (a
// network map, a registry of links, a list of contraptions) can see it change in the middle of a
// loop. In a plain server that never happens and such mods do not guard against it. The exception
// then ends the whole tick loop and takes the server down, although the effect of one skipped tick
// is small. A raced tick is dropped and logged instead. Only ConcurrentModificationException is
// tolerated, and only a few per minute: a failure that repeats every tick is a real defect and
// still stops the server, so a broken state cannot go unnoticed.
public final class TickGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(TickGuard.class);
    static final int MAX_PER_WINDOW = 20;
    private static final long WINDOW_NANOS = 60_000_000_000L;

    private static long windowStart;
    private static int inWindow;
    private static long total;

    private TickGuard() {
    }

    /** True when the exception was absorbed and the server should carry on with the next tick. */
    public static synchronized boolean tolerate(ConcurrentModificationException raced) {
        long now = System.nanoTime();
        if (inWindow == 0 || now - windowStart > WINDOW_NANOS) {
            windowStart = now;
            inWindow = 0;
        }
        if (inWindow >= MAX_PER_WINDOW) {
            LOGGER.error("wcpu: too many raced ticks in a minute; stopping instead of hiding it", raced);
            return false;
        }
        inWindow++;
        total++;
        if (total == 1 || total % 25 == 0) {
            LOGGER.warn("wcpu: a mod changed a list while another loop was reading it during the"
                + " server tick; the rest of that tick was skipped ({} so far). Stack of the first"
                + " frames follows.", total, raced);
        }
        return true;
    }

    static synchronized void reset() {
        windowStart = 0L;
        inWindow = 0;
        total = 0L;
    }
}
