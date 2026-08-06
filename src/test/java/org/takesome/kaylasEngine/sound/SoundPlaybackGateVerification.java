package org.takesome.kaylasEngine.sound;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Regression verification for deterministic launcher-audio shutdown. */
public final class SoundPlaybackGateVerification {
    private SoundPlaybackGateVerification() {
    }

    public static void main(String[] args) {
        verifyQueuedPlaybackCannotStartAfterSuspension();
        verifyActivePlaybackBlocksLaunchBarrier();
        verifyIdleCallbackWaitsForPendingPlayback();
        verifyPlaybackCanResumeExplicitly();
        verifyNativeClipClosesBeforeBarrierCompletion();
        System.out.println("Sound playback shutdown-gate verification passed.");
    }

    private static void verifyQueuedPlaybackCannotStartAfterSuspension() {
        SoundPlaybackGate gate = new SoundPlaybackGate();
        SoundPlaybackGate.Ticket queued = gate.reserve();
        require(queued != null, "initial sound reservation was rejected");

        CompletableFuture<Void> drained = gate.stopAndDrain(true);
        require(drained.isDone(), "launch barrier waited for a queued but inaudible sound");
        require(!gate.activate(queued), "queued sound started after launch suspension");
        require(gate.reserve() == null, "new sound reservation was accepted while suspended");
    }

    private static void verifyActivePlaybackBlocksLaunchBarrier() {
        SoundPlaybackGate gate = new SoundPlaybackGate();
        SoundPlaybackGate.Ticket active = gate.reserve();
        require(gate.activate(active), "active sound could not be registered");

        CompletableFuture<Void> drained = gate.stopAndDrain(true);
        require(!drained.isDone(), "launch barrier completed before active sound closed");
        gate.deactivate();
        require(drained.isDone(), "launch barrier did not complete after active sound closed");
    }

    private static void verifyIdleCallbackWaitsForPendingPlayback() {
        SoundPlaybackGate gate = new SoundPlaybackGate();
        SoundPlaybackGate.Ticket pending = gate.reserve();
        CompletableFuture<Void> idle = gate.whenIdle();
        require(!idle.isDone(), "idle callback ignored pending sound startup");
        gate.abandon(pending);
        require(idle.isDone(), "idle callback did not complete after pending sound was abandoned");
    }

    private static void verifyPlaybackCanResumeExplicitly() {
        SoundPlaybackGate gate = new SoundPlaybackGate();
        gate.stopAndDrain(true);
        require(gate.isSuspended(), "sound gate did not enter suspended state");
        gate.resume();
        require(!gate.isSuspended(), "sound gate did not resume");
        SoundPlaybackGate.Ticket resumed = gate.reserve();
        require(resumed != null && gate.activate(resumed), "sound playback did not work after resume");
        gate.deactivate();
    }


    private static void verifyNativeClipClosesBeforeBarrierCompletion() {
        try {
            String source = Files.readString(Path.of(
                    "src/main/java/org/takesome/kaylasEngine/sound/SoundPlayer.java"
            ));
            int cleanup = source.indexOf("private void cleanupClip");
            int close = source.indexOf("closeQuietly(clip);", cleanup);
            int deactivate = source.indexOf("playbackGate.deactivate();", cleanup);
            require(cleanup >= 0 && close > cleanup, "SoundPlayer native clip close is missing");
            require(deactivate > close,
                    "launch barrier completes before the native audio clip is closed");
            require(source.contains("audioWatchdogExecutor.schedule("),
                    "independent hard-stop audio watchdog is missing");
        } catch (Exception error) {
            throw new IllegalStateException("Unable to verify SoundPlayer shutdown ordering", error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
