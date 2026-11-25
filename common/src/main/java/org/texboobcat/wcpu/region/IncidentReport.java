package org.texboobcat.wcpu.region;

import dev.architectury.platform.Platform;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.texboobcat.wcpu.Wcpu;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Writes one text file with everything known at the moment the mod falls back to serial ticking.
//
// The compatibility report names the suspected mod and the failing frame, but a race has two sides
// and the stack only shows the victim. The file adds what the other side could be: what every
// wcpu thread was doing at that moment, which regions were running and on which thread, and the
// counters from /wcpu status. It is written once per fallback, never more than a few per session,
// and can never fail the server: any error while writing is only logged.
final class IncidentReport {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncidentReport.class);
    private static final int MAX_FILES = 10;
    private static final long MIN_GAP_NANOS = 5_000_000_000L;
    private static final int MAX_REGIONS = 12;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static int written;
    private static long last;

    private IncidentReport() {
    }

    static void write(String reasonCode, String reason, @Nullable Throwable failure) {
        try {
            if (!permitted()) {
                return;
            }
            Path dir = Path.of("logs");
            Files.createDirectories(dir);
            Path file = dir.resolve("wcpu-incident-" + LocalDateTime.now().format(STAMP) + ".txt");
            Files.writeString(file, compose(reasonCode, reason, failure), StandardCharsets.UTF_8);
            LOGGER.error("wcpu: incident details written to {} (attach it to a bug report)", file);
        } catch (Exception | LinkageError unexpected) {
            LOGGER.warn("wcpu: could not write the incident file: {}", unexpected.toString());
        }
    }

    private static synchronized boolean permitted() {
        long now = System.nanoTime();
        if (written >= MAX_FILES || (last != 0L && now - last < MIN_GAP_NANOS)) {
            return false;
        }
        written++;
        last = now;
        return true;
    }

    private static String compose(String reasonCode, String reason, @Nullable Throwable failure) {
        StringBuilder text = new StringBuilder(8192);
        text.append("wcpu incident ").append(LocalDateTime.now()).append('\n');
        text.append("wcpu ").append(Platform.getMod(Wcpu.MODID).getVersion())
            .append(", Minecraft ").append(Platform.getMinecraftVersion()).append('\n');
        text.append("reason code: ").append(reasonCode).append('\n');
        text.append("reason: ").append(reason).append("\n\n");
        text.append("workers: ").append(RegionWorkers.poolSize()).append('\n');
        text.append("violations: ").append(RegionTracker.violations().total()).append('\n');
        text.append("refused accesses across running regions: ")
            .append(RegionTracker.foreignBlockEntityRefusals()).append('\n');
        text.append("entity reads cut short by a changing list: ")
            .append(ForeignAccessStats.raceSkips()).append('\n');
        text.append("most refused types: ").append(ForeignAccessStats.top(5)).append("\n\n");
        appendRegions(text);
        if (failure != null) {
            text.append("\nfailure:\n").append(stackOf(failure)).append('\n');
        }
        appendThreads(text);
        return text.toString();
    }

    private static void appendRegions(StringBuilder text) {
        for (LevelRegionIndex index : RegionTracker.indexes()) {
            List<Region> regions = new ArrayList<>(index.regionizer().regions());
            regions.sort(Comparator.comparingLong(Region::lastTickNanos).reversed());
            text.append("regions in ").append(index.levelKey()).append(": ")
                .append(regions.size()).append(" (heaviest first)\n");
            for (Region region : regions.subList(0, Math.min(MAX_REGIONS, regions.size()))) {
                RegionTickState state = index.stateFor(region);
                text.append(String.format(Locale.ROOT,
                    "  #%d  %.1f ms  up to %d chunk(s)  divisor %d  last thread %s%s%n",
                    region.id(), region.lastTickNanos() / 1_000_000.0, region.chunkSpan(),
                    region.tickDivisor(), region.lastThreadName(),
                    state != null && state.isInFlight() ? "  RUNNING NOW" : ""));
            }
        }
    }

    private static void appendThreads(StringBuilder text) {
        text.append("\nthreads:\n");
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            if (StuckWatchdog.relevant(thread, entry.getValue())) {
                text.append("--- ").append(thread.getName()).append(" (")
                    .append(thread.getState()).append(")\n");
                for (StackTraceElement frame : entry.getValue()) {
                    text.append("    at ").append(frame).append('\n');
                }
            }
        }
    }

    private static String stackOf(Throwable failure) {
        StringWriter out = new StringWriter();
        failure.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}
