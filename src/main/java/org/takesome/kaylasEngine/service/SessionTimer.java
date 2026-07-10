package org.takesome.kaylasEngine.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

/**
 * Restartable, pause-aware session timer backed by the engine scheduler.
 */
public final class SessionTimer implements AutoCloseable {
    private final ScheduledTaskService scheduler;
    private final Duration publishInterval;
    private final Clock clock;
    private final String taskName;

    private Instant startedAt;
    private Duration accumulated = Duration.ZERO;
    private ScheduledFuture<?> publicationTask;
    private Consumer<Duration> updateListener = ignored -> { };
    private Consumer<Throwable> errorListener = ignored -> { };
    private boolean running;
    private boolean paused;

    public SessionTimer(
            ScheduledTaskService scheduler,
            Duration publishInterval,
            String taskName
    ) {
        this(scheduler, publishInterval, taskName, Clock.systemUTC());
    }

    SessionTimer(
            ScheduledTaskService scheduler,
            Duration publishInterval,
            String taskName,
            Clock clock
    ) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.publishInterval = positiveDuration(publishInterval, "publishInterval");
        this.taskName = taskName == null || taskName.isBlank() ? "session-timer" : taskName.trim();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized void setUpdateListener(Consumer<Duration> updateListener) {
        this.updateListener = updateListener == null ? ignored -> { } : updateListener;
    }

    public synchronized void setErrorListener(Consumer<Throwable> errorListener) {
        this.errorListener = errorListener == null ? ignored -> { } : errorListener;
    }

    public synchronized boolean start() {
        if (running) {
            return false;
        }
        running = true;
        paused = false;
        accumulated = Duration.ZERO;
        startedAt = clock.instant();
        schedulePublication();
        return true;
    }

    public synchronized Duration finish() {
        Duration elapsed = elapsedLocked();
        if (!running) {
            return elapsed;
        }
        cancelPublication();
        accumulated = elapsed;
        startedAt = null;
        running = false;
        paused = false;
        return accumulated;
    }

    public synchronized boolean stop() {
        if (!running) {
            return false;
        }
        finish();
        return true;
    }

    public synchronized boolean pause() {
        if (!running || paused) {
            return false;
        }
        accumulated = elapsedLocked();
        startedAt = null;
        paused = true;
        return true;
    }

    public synchronized boolean resume() {
        if (!running || !paused) {
            return false;
        }
        startedAt = clock.instant();
        paused = false;
        return true;
    }

    public synchronized void reset() {
        accumulated = Duration.ZERO;
        startedAt = running && !paused ? clock.instant() : null;
    }

    public synchronized Duration elapsed() {
        return elapsedLocked();
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    @Override
    public synchronized void close() {
        cancelPublication();
        running = false;
        paused = false;
        startedAt = null;
    }

    private void schedulePublication() {
        cancelPublication();
        publicationTask = scheduler.scheduleAtFixedRate(
                taskName,
                this::publishElapsed,
                Duration.ZERO,
                publishInterval
        );
    }

    private void publishElapsed() {
        Consumer<Duration> update;
        Consumer<Throwable> error;
        Duration elapsed;
        synchronized (this) {
            if (!running || paused) {
                return;
            }
            update = updateListener;
            error = errorListener;
            elapsed = elapsedLocked();
        }
        try {
            update.accept(elapsed);
        } catch (Throwable failure) {
            error.accept(failure);
        }
    }

    private Duration elapsedLocked() {
        if (!running || paused || startedAt == null) {
            return accumulated;
        }
        Duration currentPeriod = Duration.between(startedAt, clock.instant());
        return accumulated.plus(currentPeriod.isNegative() ? Duration.ZERO : currentPeriod);
    }

    private void cancelPublication() {
        if (publicationTask != null) {
            publicationTask.cancel(false);
            publicationTask = null;
        }
    }

    private static Duration positiveDuration(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
        return duration;
    }
}
