package org.takesome.kaylasEngine.service;

import org.apache.logging.log4j.ThreadContext;
import org.takesome.kaylasEngine.Engine;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates named engine worker threads and attaches Log4j2 thread context for diagnostics.
 */
class CustomThreadFactory implements ThreadFactory {
    private final String prefix;
    private final AtomicInteger count = new AtomicInteger(1);

    CustomThreadFactory(String prefix) {
        this.prefix = (prefix == null || prefix.isBlank()) ? "engine-worker" : prefix.trim();
    }

    @Override
    public Thread newThread(Runnable runnable) {
        String threadName = prefix + '-' + count.getAndIncrement();
        Thread thread = new Thread(() -> {
            try {
                ThreadContext.put("workerName", threadName);
                runnable.run();
            } finally {
                ThreadContext.clearAll();
            }
        }, threadName);
        thread.setUncaughtExceptionHandler((failedThread, throwable) -> {
            if (Engine.LOGGER != null) {
                Engine.LOGGER.error("Uncaught exception in {}", failedThread.getName(), throwable);
            }
        });
        return thread;
    }
}
