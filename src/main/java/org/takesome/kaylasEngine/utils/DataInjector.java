package org.takesome.kaylasEngine.utils;

import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Расширенный класс DataInjector для асинхронной установки и получения данных с поддержкой:
 * <ul>
 *   <li>Асинхронного уведомления слушателей через Executor.</li>
 *   <li>Обработки ошибок посредством errorListeners.</li>
 *   <li>Возможности удаления слушателей.</li>
 *   <li>Трансформации данных с помощью метода map.</li>
 * </ul>
 *
 * @param <T> Тип передаваемых данных.
 */
public class DataInjector<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataInjector.class);

    private final CompletableFuture<T> futureData = new CompletableFuture<>();
    private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<Throwable>> errorListeners = new CopyOnWriteArrayList<>();
    private final Executor executor;

    /**
     * Конструктор по умолчанию с использованием ForkJoinPool.commonPool() в качестве executor.
     */
    public DataInjector() {
        this.executor = ForkJoinPool.commonPool();
    }

    /**
     * Конструктор с возможностью указания собственного executor.
     *
     * @param executor Executor для асинхронного уведомления слушателей.
     */
    public DataInjector(Executor executor) {
        this.executor = executor;
    }

    /**
     * Регистрирует слушателя, который будет вызван, когда данные станут доступны.
     * Если данные уже установлены, слушатель вызывается асинхронно.
     *
     * @param listener Обработчик данных.
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
     * Удаляет ранее зарегистрированного слушателя.
     *
     * @param listener Слушатель, который требуется удалить.
     * @return true, если слушатель был удалён, иначе false.
     */
    public boolean removeListener(Consumer<T> listener) {
        return listeners.remove(listener);
    }

    /**
     * Регистрирует слушателя для обработки ошибок, возникающих при уведомлении.
     *
     * @param errorListener Обработчик ошибок.
     */
    public void addErrorListener(Consumer<Throwable> errorListener) {
        errorListeners.add(errorListener);
    }

    /**
     * Уведомляет всех зарегистрированных слушателей об ошибке.
     *
     * @param t Ошибка для уведомления.
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
     * Устанавливает данные и уведомляет всех зарегистрированных слушателей асинхронно.
     * Данные можно установить только один раз.
     *
     * @param data Данные для установки.
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
     * Блокирующий метод для получения данных.
     * Если данные ещё не установлены, метод ожидает их установки.
     *
     * @return Установленные данные.
     * @throws Exception Если ожидание прервано.
     */
    public T getContent() throws Exception {
        LOGGER.debug("getContent() called, waiting for data if necessary.");
        T data = futureData.get();
        LOGGER.debug("Data retrieved: {}", data);
        return data;
    }

    /**
     * Получает данные с указанным таймаутом.
     *
     * @param timeout Максимальное время ожидания.
     * @param unit    Единица измерения времени.
     * @return Установленные данные.
     * @throws TimeoutException Если данные не установлены в пределах таймаута.
     * @throws Exception        Если ожидание прервано.
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
     * Проверяет, установлены ли данные.
     *
     * @return true, если данные доступны, иначе false.
     */
    public boolean isDataAvailable() {
        boolean available = futureData.isDone();
        LOGGER.debug("isDataAvailable() -> {}", available);
        return available;
    }

    /**
     * Преобразует данные с помощью указанного маппера и возвращает новый DataInjector для результата.
     *
     * @param <R>    Тип преобразованных данных.
     * @param mapper Функция преобразования.
     * @return Новый DataInjector с преобразованными данными.
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
