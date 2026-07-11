package org.takesome.kaylasEngine.crash;

/** Removable engine crash-report listener subscription. */
@FunctionalInterface
public interface CrashReportSubscription extends AutoCloseable {
    @Override
    void close();
}
