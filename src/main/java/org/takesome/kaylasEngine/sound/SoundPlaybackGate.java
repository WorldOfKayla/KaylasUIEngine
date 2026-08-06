package org.takesome.kaylasEngine.sound;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serializes sound playback admission and shutdown barriers independently from the native audio API.
 */
final class SoundPlaybackGate {
    private long generation;
    private boolean suspended;
    private int pendingPlaybacks;
    private int activePlaybacks;
    private final List<CompletableFuture<Void>> activeDrainWaiters = new ArrayList<>();
    private final List<CompletableFuture<Void>> idleWaiters = new ArrayList<>();

    synchronized Ticket reserve() {
        if (suspended) {
            return null;
        }
        pendingPlaybacks++;
        return new Ticket(generation);
    }

    synchronized boolean activate(Ticket ticket) {
        if (!consumePending(ticket)) {
            return false;
        }
        boolean accepted = !suspended && ticket.generation == generation;
        if (accepted) {
            activePlaybacks++;
        }
        completeSatisfiedWaiters();
        return accepted;
    }

    synchronized void abandon(Ticket ticket) {
        if (consumePending(ticket)) {
            completeSatisfiedWaiters();
        }
    }

    synchronized void deactivate() {
        if (activePlaybacks > 0) {
            activePlaybacks--;
        }
        completeSatisfiedWaiters();
    }

    synchronized CompletableFuture<Void> stopAndDrain(boolean suspendFurtherPlayback) {
        generation++;
        if (suspendFurtherPlayback) {
            suspended = true;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        activeDrainWaiters.add(future);
        completeSatisfiedWaiters();
        return future;
    }

    synchronized CompletableFuture<Void> whenIdle() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        idleWaiters.add(future);
        completeSatisfiedWaiters();
        return future;
    }

    synchronized void resume() {
        suspended = false;
        generation++;
    }

    synchronized boolean isSuspended() {
        return suspended;
    }

    synchronized int activePlaybacks() {
        return activePlaybacks;
    }

    synchronized int pendingPlaybacks() {
        return pendingPlaybacks;
    }

    private boolean consumePending(Ticket ticket) {
        if (ticket == null || !ticket.consumed.compareAndSet(false, true)) {
            return false;
        }
        if (pendingPlaybacks > 0) {
            pendingPlaybacks--;
        }
        return true;
    }

    private void completeSatisfiedWaiters() {
        if (activePlaybacks == 0) {
            completeAll(activeDrainWaiters);
        }
        if (activePlaybacks == 0 && pendingPlaybacks == 0) {
            completeAll(idleWaiters);
        }
    }

    private void completeAll(List<CompletableFuture<Void>> waiters) {
        if (waiters.isEmpty()) {
            return;
        }
        List<CompletableFuture<Void>> completed = new ArrayList<>(waiters);
        waiters.clear();
        completed.forEach(future -> future.complete(null));
    }

    static final class Ticket {
        private final long generation;
        private final AtomicBoolean consumed = new AtomicBoolean(false);

        private Ticket(long generation) {
            this.generation = generation;
        }
    }
}
