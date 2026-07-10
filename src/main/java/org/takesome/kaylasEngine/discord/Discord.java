package org.takesome.kaylasEngine.discord;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;
import org.takesome.kaylasEngine.Engine;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe owner of the native Discord Rich Presence lifecycle.
 *
 * <p>The callback pump is started once, repeated presence updates are coalesced and Discord failures
 * never prevent the host application from continuing to run.</p>
 */
public final class Discord implements DiscordListener, AutoCloseable {
    private static final int MAX_TEXT_LENGTH = 127;
    private static final int MAX_ASSET_KEY_LENGTH = 32;
    private static final long CALLBACK_INTERVAL_MILLIS = 1_000L;

    private final String defaultLargeImageKey;
    private final DiscordRPC lib;
    private final DiscordRichPresence presence = new DiscordRichPresence();
    private final Object nativeLock = new Object();
    private final AtomicBoolean available = new AtomicBoolean(false);
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private final AtomicBoolean callbackPumpStarted = new AtomicBoolean(false);
    private final ScheduledExecutorService callbackExecutor;

    private volatile String smallImageText;
    private volatile String largeImageText;
    private volatile String lastPresenceSignature = "";

    public Discord(Engine engine, String iconKey) {
        Objects.requireNonNull(engine, "engine");
        this.defaultLargeImageKey = normalizeAssetKey(iconKey);
        this.callbackExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "discord-rpc-callbacks");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignored, error) ->
                    Engine.getLOGGER().warn("Discord RPC callback thread failed: {}", error.getMessage(), error));
            return thread;
        });

        DiscordRPC resolvedLib = null;
        try {
            resolvedLib = DiscordRPC.INSTANCE;
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            handlers.ready = user -> Engine.getLOGGER().debug(
                    "Discord RPC connected as {}#{}",
                    user == null ? "unknown" : user.username,
                    user == null ? "0000" : user.discriminator
            );
            resolvedLib.Discord_Initialize(
                    engine.getEngineData().getAppId(),
                    handlers,
                    true,
                    ""
            );
            available.set(true);
            Engine.getLOGGER().info("Discord RPC initialized.");
        } catch (Throwable error) {
            Engine.getLOGGER().warn(
                    "Discord RPC is unavailable; launcher activity will continue without Rich Presence: {}",
                    rootMessage(error)
            );
        }
        this.lib = resolvedLib;
        startCallbackPump();
    }

    /**
     * Compatibility entry point used by existing launcher code.
     */
    @Override
    public void discordRpcStart(String state, String details, String detailsIcon) {
        updatePresence(
                state,
                details,
                defaultLargeImageKey,
                largeImageText,
                detailsIcon,
                smallImageText,
                true
        );
    }

    /**
     * Publishes a complete Rich Presence snapshot.
     *
     * @param state secondary activity line
     * @param details primary activity line
     * @param largeImageKey Discord application asset key used for the primary image
     * @param largeImageText hover text for the primary image
     * @param smallImageKey Discord application asset key used for the secondary image
     * @param smallImageText hover text for the secondary image
     * @param resetTimestamp whether the elapsed timer starts again for this activity phase
     */
    @Override
    public void updatePresence(String state,
                               String details,
                               String largeImageKey,
                               String largeImageText,
                               String smallImageKey,
                               String smallImageText,
                               boolean resetTimestamp) {
        if (!isAvailable()) {
            return;
        }

        String normalizedState = normalizeText(state);
        String normalizedDetails = normalizeText(details);
        String normalizedLargeKey = firstNonBlank(normalizeAssetKey(largeImageKey), defaultLargeImageKey);
        String normalizedLargeText = normalizeText(largeImageText);
        String normalizedSmallKey = normalizeAssetKey(smallImageKey);
        String normalizedSmallText = normalizeText(smallImageText);
        String signature = String.join("\u001f",
                normalizedState,
                normalizedDetails,
                normalizedLargeKey,
                normalizedLargeText,
                normalizedSmallKey,
                normalizedSmallText
        );

        synchronized (nativeLock) {
            if (!isAvailable() || signature.equals(lastPresenceSignature)) {
                return;
            }
            try {
                if (resetTimestamp || presence.startTimestamp <= 0L) {
                    presence.startTimestamp = System.currentTimeMillis() / 1_000L;
                }
                presence.state = emptyToNull(normalizedState);
                presence.details = emptyToNull(normalizedDetails);
                presence.largeImageKey = emptyToNull(normalizedLargeKey);
                presence.largeImageText = emptyToNull(normalizedLargeText);
                presence.smallImageKey = emptyToNull(normalizedSmallKey);
                presence.smallImageText = emptyToNull(normalizedSmallText);
                lib.Discord_UpdatePresence(presence);
                lastPresenceSignature = signature;
            } catch (Throwable error) {
                Engine.getLOGGER().warn("Unable to update Discord Rich Presence: {}", rootMessage(error));
            }
        }
    }

    @Override
    public DiscordRPC getDiscordLib() {
        return lib;
    }

    @Override
    public boolean isAvailable() {
        return lib != null && available.get() && !shutdownRequested.get();
    }

    public void setSmallImageText(String smallImageText) {
        this.smallImageText = normalizeText(smallImageText);
    }

    public void setLargeImageText(String largeImageText) {
        this.largeImageText = normalizeText(largeImageText);
    }

    @Override
    public void clearPresence() {
        synchronized (nativeLock) {
            if (!isAvailable()) {
                return;
            }
            try {
                lib.Discord_ClearPresence();
                lastPresenceSignature = "";
                presence.startTimestamp = 0L;
            } catch (Throwable error) {
                Engine.getLOGGER().debug("Unable to clear Discord Rich Presence: {}", rootMessage(error));
            }
        }
    }

    private void startCallbackPump() {
        if (!available.get() || !callbackPumpStarted.compareAndSet(false, true)) {
            return;
        }
        callbackExecutor.scheduleWithFixedDelay(() -> {
            synchronized (nativeLock) {
                if (!isAvailable()) {
                    return;
                }
                try {
                    lib.Discord_RunCallbacks();
                } catch (Throwable error) {
                    Engine.getLOGGER().debug("Discord RPC callback failed: {}", rootMessage(error));
                }
            }
        }, 0L, CALLBACK_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        if (!shutdownRequested.compareAndSet(false, true)) {
            return;
        }

        callbackExecutor.shutdownNow();
        synchronized (nativeLock) {
            if (lib == null || !available.getAndSet(false)) {
                return;
            }
            try {
                lib.Discord_ClearPresence();
            } catch (Throwable error) {
                Engine.getLOGGER().debug("Unable to clear Discord presence during shutdown: {}", rootMessage(error));
            }
            try {
                lib.Discord_Shutdown();
            } catch (Throwable error) {
                Engine.getLOGGER().debug("Unable to shut down Discord RPC: {}", rootMessage(error));
            }
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= MAX_TEXT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_TEXT_LENGTH - 1).trim() + '…';
    }

    private static String normalizeAssetKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= MAX_ASSET_KEY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_ASSET_KEY_LENGTH);
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        if (current == null) {
            return "unknown error";
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
