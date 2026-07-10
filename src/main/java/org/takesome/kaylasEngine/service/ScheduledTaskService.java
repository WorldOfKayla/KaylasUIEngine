package org.takesome.kaylasEngine.service;

import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Engine-owned scheduler shared by periodic application services.
 *
 * <p>Tasks are isolated so an exception does not silently cancel all future executions. Threads are
 * daemon threads and are terminated together with the engine lifecycle.</p>
 */
public final class ScheduledTaskService implements AutoCloseable {
    private final ScheduledThreadPoolExecutor scheduler;
    private final Logger logger;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ScheduledTaskService(int threadCount, String threadNamePrefix, Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
        int safeThreadCount = Math.max(1, threadCount);
        String safePrefix = threadNamePrefix == null || threadNamePrefix.isBlank()
                ? "engine-scheduler"
                : threadNamePrefix.trim();
        this.scheduler = new ScheduledThreadPoolExecutor(
                safeThreadCount,
                daemonThreadFactory(safePrefix)
        );
        this.scheduler.setRemoveOnCancelPolicy(true);
        this.scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        this.scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    }

    public ScheduledFuture<?> schedule(
            String taskName,
            Runnable task,
            Duration delay
    ) {
        ensureOpen();
        return scheduler.schedule(
                guarded(taskName, task),
                toMillis(delay, true),
                TimeUnit.MILLISECONDS
        );
    }

    public ScheduledFuture<?> scheduleAtFixedRate(
            String taskName,
            Runnable task,
            Duration initialDelay,
            Duration period
    ) {
        ensureOpen();
        return scheduler.scheduleAtFixedRate(
                guarded(taskName, task),
                toMillis(initialDelay, true),
                toMillis(period, false),
                TimeUnit.MILLISECONDS
        );
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(
            String taskName,
            Runnable task,
            Duration initialDelay,
            Duration delay
    ) {
        ensureOpen();
        return scheduler.scheduleWithFixedDelay(
                guarded(taskName, task),
                toMillis(initialDelay, true),
                toMillis(delay, false),
                TimeUnit.MILLISECONDS
        );
    }

    public int queuedTaskCount() {
        return scheduler.getQueue().size();
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        scheduler.shutdownNow();
        logger.debug("ScheduledTaskService stopped; discardedTasks={}", scheduler.getQueue().size());
    }

    private Runnable guarded(String taskName, Runnable task) {
        Objects.requireNonNull(task, "task");
        String safeTaskName = taskName == null || taskName.isBlank() ? "scheduled-task" : taskName.trim();
        return () -> {
            try {
                task.run();
            } catch (Throwable error) {
                logger.error("Scheduled task '{}' failed", safeTaskName, error);
            }
        };
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("ScheduledTaskService is closed.");
        }
    }

    private static long toMillis(Duration duration, boolean allowZero) {
        Duration safeDuration = duration == null ? Duration.ZERO : duration;
        long millis = safeDuration.toMillis();
        return allowZero ? Math.max(0L, millis) : Math.max(1L, millis);
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + '-' + sequence.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignored, error) ->
                    org.takesome.kaylasEngine.Engine.LOGGER.error("Uncaught scheduled task failure", error)
            );
            return thread;
        };
    }
}
