package org.foxesworld.engine.service;

import org.foxesworld.engine.Engine;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class ExecutorServiceProvider {
    private static final String CONFIG_RESOURCE = "executor-config.properties";
    private static final int DEFAULT_QUEUE_CAPACITY = Math.max(64, Runtime.getRuntime().availableProcessors() * 32);
    private static final int DEFAULT_KEEP_ALIVE_SECONDS = 30;
    private static final int DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final ThreadPoolExecutor executorService;
    private final ExecutorProgress executorProgress;
    private final ConcurrentHashMap<UUID, Future<?>> taskMap = new ConcurrentHashMap<>();
    private final String threadNamePrefix;
    private final long shutdownTimeoutMillis;

    /**
     * Creates the engine-wide background executor.
     *
     * @param poolSize         requested worker count.
     * @param threadNamePrefix worker thread name prefix.
     */
    public ExecutorServiceProvider(int poolSize, String threadNamePrefix) {
        this.threadNamePrefix = normalizeThreadNamePrefix(threadNamePrefix);
        this.executorProgress = new ExecutorProgress();
        ExecutorSettings settings = resolveSettings(poolSize, loadProperties());
        this.shutdownTimeoutMillis = TimeUnit.SECONDS.toMillis(settings.shutdownTimeoutSeconds());
        this.executorService = createExecutorService(settings);
    }

    private Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_RESOURCE)) {
            if (input == null) {
                Engine.LOGGER.debug("{} not found; using executor defaults.", CONFIG_RESOURCE);
                return properties;
            }
            properties.load(input);
            return properties;
        } catch (IOException ex) {
            Engine.LOGGER.warn("Could not load {}; using executor defaults.", CONFIG_RESOURCE, ex);
            return properties;
        }
    }

    private ExecutorSettings resolveSettings(int requestedPoolSize, Properties properties) {
        int poolSize = readPositiveInt(properties, "executor.pool.size", requestedPoolSize);
        int queueCapacity = readPositiveInt(properties, "executor.queue.capacity", DEFAULT_QUEUE_CAPACITY);
        int keepAliveSeconds = readPositiveInt(properties, "executor.keepAlive.seconds", DEFAULT_KEEP_ALIVE_SECONDS);
        int shutdownTimeoutSeconds = readPositiveInt(properties, "executor.shutdownTimeout.seconds", DEFAULT_SHUTDOWN_TIMEOUT_SECONDS);
        boolean prestartCoreThreads = Boolean.parseBoolean(properties.getProperty("executor.prestartCoreThreads", "false"));

        ExecutorSettings settings = new ExecutorSettings(
                poolSize,
                queueCapacity,
                keepAliveSeconds,
                shutdownTimeoutSeconds,
                prestartCoreThreads
        );
        Engine.LOGGER.info(
                "Executor configured: poolSize={}, queueCapacity={}, keepAliveSeconds={}, shutdownTimeoutSeconds={}, prestartCoreThreads={}",
                settings.poolSize(),
                settings.queueCapacity(),
                settings.keepAliveSeconds(),
                settings.shutdownTimeoutSeconds(),
                settings.prestartCoreThreads()
        );
        return settings;
    }

    private int readPositiveInt(Properties properties, String key, int fallback) {
        String rawValue = properties.getProperty(key);
        int safeFallback = Math.max(1, fallback);
        if (rawValue == null || rawValue.isBlank()) {
            return safeFallback;
        }
        try {
            return Math.max(1, Integer.parseInt(rawValue.trim()));
        } catch (NumberFormatException ex) {
            Engine.LOGGER.warn("Invalid executor property {}='{}'; using {}", key, rawValue, safeFallback);
            return safeFallback;
        }
    }

    private ThreadPoolExecutor createExecutorService(ExecutorSettings settings) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                settings.poolSize(),
                settings.poolSize(),
                settings.keepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(settings.queueCapacity()),
                new CustomThreadFactory(this.threadNamePrefix),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(false);
        if (settings.prestartCoreThreads()) {
            int started = executor.prestartAllCoreThreads();
            Engine.LOGGER.debug("Prestarted {} executor worker threads", started);
        }
        return executor;
    }

    /**
     * Submits a tracked fire-and-forget task.
     *
     * @param task     task to execute.
     * @param taskName human-readable task name for diagnostics.
     * @return task identifier.
     */
    public UUID submitTask(Runnable task, String taskName) {
        UUID taskId = UUID.randomUUID();
        String safeTaskName = normalizeTaskName(taskName);
        executorProgress.addTask(taskId.toString(), safeTaskName);
        Engine.LOGGER.debug("Submitting task: {} with ID: {}", safeTaskName, taskId);

        Future<?> future = executorService.submit(() -> runTrackedTask(taskId, safeTaskName, task));
        taskMap.put(taskId, future);
        return taskId;
    }

    /**
     * Submits a tracked task and returns a CompletableFuture for composition.
     */
    public CompletableFuture<Void> runAsync(Runnable task, String taskName) {
        return supplyAsync(() -> {
            task.run();
            return null;
        }, taskName);
    }

    /**
     * Submits a tracked task and returns a CompletableFuture for composition.
     */
    public <T> CompletableFuture<T> supplyAsync(Callable<T> task, String taskName) {
        UUID taskId = UUID.randomUUID();
        String safeTaskName = normalizeTaskName(taskName);
        CompletableFuture<T> resultFuture = new CompletableFuture<>();
        executorProgress.addTask(taskId.toString(), safeTaskName);
        Engine.LOGGER.debug("Submitting completable task: {} with ID: {}", safeTaskName, taskId);

        Future<?> workerFuture = executorService.submit(() -> {
            try {
                T result = task.call();
                executorProgress.updateTask(taskId.toString(), 100);
                resultFuture.complete(result);
            } catch (Throwable throwable) {
                resultFuture.completeExceptionally(throwable);
                Engine.LOGGER.error("Error executing task: {} with ID: {}", safeTaskName, taskId, throwable);
                rethrowUnchecked(throwable);
            } finally {
                executorProgress.removeTask(taskId.toString());
                taskMap.remove(taskId);
                Engine.LOGGER.debug("Task removed: {} with ID: {}", safeTaskName, taskId);
            }
        });
        taskMap.put(taskId, workerFuture);
        resultFuture.whenComplete((result, throwable) -> {
            if (resultFuture.isCancelled()) {
                workerFuture.cancel(true);
            }
        });
        return resultFuture;
    }

    private void runTrackedTask(UUID taskId, String taskName, Runnable task) {
        try {
            task.run();
            executorProgress.updateTask(taskId.toString(), 100);
            Engine.LOGGER.debug("Task completed: {} with ID: {}", taskName, taskId);
        } catch (Throwable throwable) {
            Engine.LOGGER.error("Error executing task: {} with ID: {}", taskName, taskId, throwable);
            rethrowUnchecked(throwable);
        } finally {
            executorProgress.removeTask(taskId.toString());
            taskMap.remove(taskId);
            Engine.LOGGER.debug("Task removed: {} with ID: {}", taskName, taskId);
        }
    }

    /**
     * Submits a task with a result callback. The callback is committed on the Swing EDT.
     *
     * @param task     callable task.
     * @param taskName task name.
     * @param callback result callback; may be null.
     * @param <T>      result type.
     * @return task identifier.
     */
    public <T> UUID submitDynamicTaskWithCallback(Callable<T> task, String taskName, Consumer<T> callback) {
        UUID taskId = UUID.randomUUID();
        String safeTaskName = normalizeTaskName(taskName);
        executorProgress.addTask(taskId.toString(), safeTaskName);
        Engine.LOGGER.debug("Submitting dynamic task: {} with ID: {}", safeTaskName, taskId);

        Future<?> future = executorService.submit(() -> {
            try {
                T result = task.call();
                executorProgress.updateTask(taskId.toString(), 100);
                if (callback != null) {
                    SwingUtilities.invokeLater(() -> callback.accept(result));
                }
            } catch (Throwable throwable) {
                Engine.LOGGER.error("Error executing dynamic task: {} with ID: {}", safeTaskName, taskId, throwable);
                rethrowUnchecked(throwable);
            } finally {
                executorProgress.removeTask(taskId.toString());
                taskMap.remove(taskId);
                Engine.LOGGER.debug("Dynamic task removed: {} with ID: {}", safeTaskName, taskId);
            }
        });

        taskMap.put(taskId, future);
        return taskId;
    }

    /**
     * Cancels a task by id.
     *
     * @param taskId task id.
     * @return true when a tracked task existed and cancellation was requested.
     */
    public boolean cancelTask(UUID taskId) {
        Future<?> future = taskMap.remove(taskId);
        if (future == null) {
            Engine.LOGGER.warn("No task found with ID: {}", taskId);
            return false;
        }
        boolean cancelled = future.cancel(true);
        executorProgress.removeTask(taskId.toString());
        Engine.LOGGER.info("Task cancellation requested: {}, cancelled={}", taskId, cancelled);
        return cancelled;
    }

    /**
     * Completes task tracking by cancelling the underlying future when it is still running.
     * Kept as the canonical external lifecycle hook for existing callers.
     */
    public void completeTask(UUID taskId) {
        cancelTask(taskId);
    }

    protected String getExecutorServiceStatus() {
        return String.format(
                "Pool Size: %d, Active Threads: %d, Queued Tasks: %d, Completed Tasks: %d, Total Tasks: %d, Shutdown: %s",
                executorService.getPoolSize(),
                executorService.getActiveCount(),
                executorService.getQueue().size(),
                executorService.getCompletedTaskCount(),
                executorService.getTaskCount(),
                executorService.isShutdown()
        );
    }

    protected void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        executorService.setRejectedExecutionHandler(handler);
        Engine.LOGGER.info("RejectedExecutionHandler set for the ExecutorService");
    }

    public ExecutorService getExecutorService() {
        return this.executorService;
    }

    public void shutdown() {
        Engine.LOGGER.info("Shutting down ExecutorService");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(shutdownTimeoutMillis, TimeUnit.MILLISECONDS)) {
                Engine.LOGGER.warn("ExecutorService did not stop in {} ms; forcing shutdownNow", shutdownTimeoutMillis);
                executorService.shutdownNow();
                if (!executorService.awaitTermination(shutdownTimeoutMillis, TimeUnit.MILLISECONDS)) {
                    Engine.LOGGER.warn("ExecutorService did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public ExecutorProgress getExecutorProgress() {
        return executorProgress;
    }

    private String normalizeThreadNamePrefix(String prefix) {
        return (prefix == null || prefix.isBlank()) ? "engine-worker" : prefix.trim();
    }

    private String normalizeTaskName(String taskName) {
        return (taskName == null || taskName.isBlank()) ? "unnamed-task" : taskName.trim();
    }

    private void rethrowUnchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new CompletionException(throwable);
    }

    private record ExecutorSettings(
            int poolSize,
            int queueCapacity,
            int keepAliveSeconds,
            int shutdownTimeoutSeconds,
            boolean prestartCoreThreads
    ) {
    }
}
