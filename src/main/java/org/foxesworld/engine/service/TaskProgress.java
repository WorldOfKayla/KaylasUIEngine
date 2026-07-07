package org.foxesworld.engine.service;

/**
 * Mutable progress snapshot for a background task.
 *
 * <p>The fields are intentionally volatile because worker threads update the snapshot while the
 * Swing task manager reads it on the EDT.</p>
 */
public class TaskProgress {
    private final String taskName;
    private volatile int progress;
    private volatile long memoryUsage;
    private volatile boolean completed;

    public TaskProgress(String taskName) {
        this.taskName = taskName;
        this.progress = 0;
        this.memoryUsage = 0;
        this.completed = false;
    }

    public String getTaskName() {
        return taskName;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
    }

    public long getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(long memoryUsage) {
        this.memoryUsage = Math.max(0L, memoryUsage);
    }

    public boolean isCompleted() {
        return completed;
    }

    public void complete() {
        this.progress = 100;
        this.completed = true;
    }
}
