package org.takesome.kaylasEngine.fileLoader.fileGuard;

import java.io.File;

/**
 * Receives file-guard progress and terminal lifecycle events.
 */
public interface FileGuardListener {
    /**
     * Legacy successful-completion callback.
     */
    void onFilesChecked(int filesDeleted);

    void onDirCheck(String dir);

    void onFileCheck(File file);

    /**
     * Detailed successful-completion callback. Existing listeners remain source compatible.
     */
    default void onGuardCompleted(FileGuardReport report) {
        onFilesChecked(report.filesDeleted());
    }

    /**
     * Called instead of successful completion when the scan cannot prove a safe state.
     */
    default void onGuardFailed(FileGuardReport partialReport, Throwable error) {
    }
}
