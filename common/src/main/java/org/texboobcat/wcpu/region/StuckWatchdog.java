package org.texboobcat.wcpu.region;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

// Writes thread stacks to the log when the main thread has waited a long time for a region.
//
// A region that never finishes freezes the server with nothing in the log, and the person who
// hits it usually cannot take a thread dump. The wait itself is left alone (forcing a running
// region to stop would be unsafe); this only makes the hang explain itself.
final class StuckWatchdog {

    static final long AFTER_NANOS = 10_000_000_000L;
    private static final long REPEAT_NANOS = 60_000_000_000L;
    private static final Logger LOGGER = LoggerFactory.getLogger(StuckWatchdog.class);
    private static long lastReport;

    private StuckWatchdog() {
    }

    /** Logs the relevant stacks, at most once a minute. {@code waitingFor} says what is awaited. */
    static void report(String waitingFor) {
        long now = System.nanoTime();
        synchronized (StuckWatchdog.class) {
            if (lastReport != 0L && now - lastReport < REPEAT_NANOS) {
                return;
            }
            lastReport = now;
        }
        StringBuilder text = new StringBuilder("wcpu: the main thread has waited over ")
            .append(AFTER_NANOS / 1_000_000_000L).append(" s for ").append(waitingFor)
            .append("; thread stacks follow so the stuck code can be found\n");
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            StackTraceElement[] stack = entry.getValue();
            if (relevant(thread, stack)) {
                text.append("--- ").append(thread.getName()).append(" (")
                    .append(thread.getState()).append(")\n");
                for (StackTraceElement frame : stack) {
                    text.append("    at ").append(frame).append('\n');
                }
            }
        }
        LOGGER.error(text.toString());
    }

    private static boolean relevant(Thread thread, StackTraceElement[] stack) {
        String name = thread.getName();
        if (name.contains("Server thread") || name.toLowerCase(java.util.Locale.ROOT).contains("wcpu")) {
            return true;
        }
        for (StackTraceElement frame : stack) {
            if (frame.getClassName().startsWith("org.texboobcat.wcpu")) {
                return true;
            }
        }
        return false;
    }
}
