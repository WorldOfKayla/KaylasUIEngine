package org.takesome.kaylasEngine.fileLoader.fileGuard;

/**
 * Immutable result of a file-guard pass.
 */
public record FileGuardReport(
        boolean successful,
        int manifestFiles,
        int rootsChecked,
        int filesChecked,
        int filesKept,
        int filesDeleted,
        int symbolicLinksDeleted,
        int directoriesDeleted,
        long durationMillis
) {
    public FileGuardReport {
        if (manifestFiles < 0
                || rootsChecked < 0
                || filesChecked < 0
                || filesKept < 0
                || filesDeleted < 0
                || symbolicLinksDeleted < 0
                || directoriesDeleted < 0
                || durationMillis < 0) {
            throw new IllegalArgumentException("File guard report counters cannot be negative");
        }
    }

    static FileGuardReport failed(long durationMillis) {
        return new FileGuardReport(false, 0, 0, 0, 0, 0, 0, 0, Math.max(0, durationMillis));
    }
}
