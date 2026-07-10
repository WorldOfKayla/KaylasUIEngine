package org.takesome.kaylasEngine.gui.diagnostics;

import org.apache.logging.log4j.Logger;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Lightweight Swing EDT heartbeat and queue-delay diagnostic. */
public final class EdtLagWatchdog {
    private static final int DEFAULT_SAMPLE_MS = 250;
    private static final long DEFAULT_LAG_WARN_NANOS = 350_000_000L;
    private static final long DEFAULT_STATUS_INTERVAL_NANOS = 5_000_000_000L;

    private final String name;
    private final Logger logger;
    private final int sampleMs;
    private final long lagWarnNanos;
    private final long statusIntervalNanos;
    private final Timer timer;

    private long lastTickNanos;
    private long lastStatusNanos;
    private long maxLagNanos;
    private boolean running;

    public EdtLagWatchdog(String name, Logger logger) {
        this(name, logger, DEFAULT_SAMPLE_MS, DEFAULT_LAG_WARN_NANOS, DEFAULT_STATUS_INTERVAL_NANOS);
    }

    public EdtLagWatchdog(
            String name,
            Logger logger,
            int sampleMs,
            long lagWarnNanos,
            long statusIntervalNanos
    ) {
        this.name = name == null || name.isBlank() ? "SwingEDT" : name.trim();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.sampleMs = Math.max(16, sampleMs);
        this.lagWarnNanos = Math.max(1L, lagWarnNanos);
        this.statusIntervalNanos = Math.max(this.lagWarnNanos, statusIntervalNanos);
        this.timer = new Timer(this.sampleMs, event -> tick());
        this.timer.setCoalesce(true);
    }

    public void start() {
        runOnEdt(this::startOnEdt);
    }

    public void stop() {
        runOnEdt(this::stopOnEdt);
    }

    public void logQueueDelay(String operation, long queuedAtNanos, long warningThresholdNanos) {
        long delay = System.nanoTime() - queuedAtNanos;
        if (delay >= Math.max(1L, warningThresholdNanos)) {
            logger.warn("[EDT-QUEUE] {} waited {} ms before execution", operation, nanosToMillis(delay));
        }
    }

    public long maxLagMillis() {
        return nanosToMillis(maxLagNanos);
    }

    public static long nanosToMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, nanos));
    }

    private void startOnEdt() {
        if (running) {
            return;
        }
        running = true;
        lastTickNanos = System.nanoTime();
        lastStatusNanos = lastTickNanos;
        maxLagNanos = 0L;
        timer.start();
        logger.info("[EDT-WATCHDOG] {} started", name);
    }

    private void stopOnEdt() {
        if (!running) {
            return;
        }
        timer.stop();
        running = false;
        logger.info("[EDT-WATCHDOG] {} stopped; maxLag={} ms", name, nanosToMillis(maxLagNanos));
    }

    private void tick() {
        long now = System.nanoTime();
        long expected = TimeUnit.MILLISECONDS.toNanos(sampleMs);
        long lag = Math.max(0L, now - lastTickNanos - expected);
        maxLagNanos = Math.max(maxLagNanos, lag);

        if (lag >= lagWarnNanos) {
            logger.warn("[EDT-LAG] {} delayed by {} ms", name, nanosToMillis(lag));
        } else if (now - lastStatusNanos >= statusIntervalNanos) {
            logger.debug("[EDT-WATCHDOG] {} alive; maxLag={} ms", name, nanosToMillis(maxLagNanos));
            lastStatusNanos = now;
        }
        lastTickNanos = now;
    }

    private static void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
