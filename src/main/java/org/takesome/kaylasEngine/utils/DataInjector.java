package org.takesome.kaylasEngine.utils;

import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asynchronous one-shot data container with support for:
 * <ul>
 *   <li>Asynchronous listener notification through an {@link Executor}.</li>
 *   <li>Error propagation through registered error listeners.</li>
 *   <li>Listener removal.</li>
 *   <li>Data transformation through {@link #map(Function)}.</li>
 * </ul>
 *
 * @param <T> transported data type
 */
public class DataInjector<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataInjector.class);

    private final CompletableFuture<T> futureData = new CompletableFuture<>();
    private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<Throwable>> errorListeners = new CopyOnWriteArrayList<>();
    private final Executor executor;

    /**
     * Creates an injector backed by {@link ForkJoinPool#commonPool()}.
     */
    public DataInjector() {
        this.executor = ForkJoinPool.commonPool();
    }

    /**
     * Creates an injector using the supplied executor.
     *
     * @param executor executor used for asynchronous listener notification
     */
    public DataInjector(Executor executor) {
        this.executor = executor;
    }

    /**
     * Registers a listener that is invoked when data becomes available.
     * If data is already available, the listener is scheduled immediately.
     *
     * @param listener data consumer
     */
    public void addListener(Consumer<T> listener) {
        if (futureData.isDone()) {
            executor.execute(() -> {
                try {
                    T data = futureData.get();
                    listener.accept(data);
                } catch (Exception e) {
                    LOGGER.error("Error invoking listener", e);
                    notifyError(e);
                }
            });
        } else {
            LOGGER.debug("Adding listener {} to the queue", listener);
            listeners.add(listener);
        }
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener listener to remove
     * @return {@code true} if the listener was removed; otherwise {@code false}
     */
    public boolean removeListener(Consumer<T> listener) {
        return listeners.remove(listener);
    }

    /**
     * Registers a listener for errors raised during asynchronous notification.
     *
     * @param errorListener error consumer
     */
    public void addErrorListener(Consumer<Throwable> errorListener) {
        errorListeners.add(errorListener);
    }

    /**
     * Notifies all registered error listeners.
     *
     * @param t error to deliver
     */
    private void notifyError(Throwable t) {
        for (Consumer<Throwable> errorListener : errorListeners) {
            try {
                errorListener.accept(t);
            } catch (Exception e) {
                LOGGER.error("Error notifying error listener", e);
            }
        }
    }

    /**
     * Completes the injector and asynchronously notifies every registered listener.
     * Data can be supplied only once.
     *
     * @param data value to store
     */
    public void setContent(T data) {
        if (!futureData.isDone()) {
            LOGGER.debug("Setting data: {}", data);
            futureData.complete(data);
            LOGGER.debug("Notifying {} listeners.", listeners.size());
            for (Consumer<T> listener : listeners) {
                executor.execute(() -> {
                    try {
                        listener.accept(data);
                    } catch (Exception e) {
                        LOGGER.error("Error notifying listener", e);
                        notifyError(e);
                    }
                });
            }
            listeners.clear();
            LOGGER.debug("All listeners have been notified and cleared.");
        } else {
            LOGGER.warn("Data is already set. Ignoring duplicate setContent call.");
        }
    }

    /**
     * Returns the stored data, blocking until it becomes available.
     * The method waits when the injector has not yet been completed.
     *
     * @return stored data
     * @throws Exception if waiting is interrupted or completion fails
     */
    public T getContent() throws Exception {
        LOGGER.debug("getContent() called, waiting for data if necessary.");
        T data = futureData.get();
        LOGGER.debug("Data retrieved: {}", data);
        return data;
    }

    /**
     * Returns the stored data, waiting up to the specified timeout.
     *
     * @param timeout maximum wait duration
     * @param unit    timeout unit
     * @return stored data
     * @throws TimeoutException if data does not become available before the timeout
     * @throws Exception        if waiting is interrupted or completion fails
     */
    public T getContent(long timeout, TimeUnit unit) throws Exception {
        LOGGER.debug("getContent() called with timeout: {} {}", timeout, unit);
        try {
            T data = futureData.get(timeout, unit);
            LOGGER.debug("Data retrieved: {}", data);
            return data;
        } catch (TimeoutException e) {
            LOGGER.warn("Timeout while waiting for data.");
            throw e;
        }
    }

    /**
     * Reports whether data is available.
     *
     * @return {@code true} when data is available; otherwise {@code false}
     */
    public boolean isDataAvailable() {
        boolean available = futureData.isDone();
        LOGGER.debug("isDataAvailable() -> {}", available);
        return available;
    }

    /**
     * Maps the stored value and returns a new injector for the transformed result.
     *
     * @param <R>    transformed data type
     * @param mapper transformation function
     * @return a new injector containing the transformed data
     */
    public <R> DataInjector<R> map(Function<T, R> mapper) {
        DataInjector<R> resultInjector = new DataInjector<>(executor);
        addListener(data -> {
            try {
                R result = mapper.apply(data);
                resultInjector.setContent(result);
            } catch (Exception e) {
                LOGGER.error("Error in map transformation", e);
                resultInjector.notifyError(e);
            }
        });
        addErrorListener(resultInjector::notifyError);
        return resultInjector;
    }
}
