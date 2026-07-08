package org.takesome.kaylasEngine.discord;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;
import org.takesome.kaylasEngine.Engine;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Discord implements DiscordListener {

    private final String iconKey;
    private  String smallImageText, largeImageText;
    private final DiscordRPC lib;
    private final DiscordRichPresence presence;
    private final Engine engine;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();


    public Discord(Engine engine, String iconKey) {
        this.engine = engine;
        this.iconKey = iconKey;
        String applicationId = engine.getEngineData().getAppId();
        lib = DiscordRPC.INSTANCE;
        String steamId = "";
        DiscordEventHandlers handlers = new DiscordEventHandlers();
        handlers.ready = (user) -> {
            //Instance.setUserLogin(User.username);
        };
        lib.Discord_Initialize(applicationId, handlers, true, steamId);
        presence = new DiscordRichPresence();
        lib.Discord_UpdatePresence(presence);
    }
    @Override
    public void discordRpcStart(String state, String details, String detailsIcon) {
        presence.startTimestamp = System.currentTimeMillis() / 1000;
        presence.details = details;
        presence.state = state;
        presence.largeImageKey = iconKey;
        presence.largeImageText = largeImageText;
        presence.smallImageKey = detailsIcon;
        presence.smallImageText = this.smallImageText;
        lib.Discord_UpdatePresence(presence);

        // Запуск периодической задачи с задержкой
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            if (!shutdownRequested.get()) {
                lib.Discord_RunCallbacks();
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }


    @Override
    public DiscordRPC getDiscordLib() {
        return lib;
    }

    public void setSmallImageText(String smallImageText) {
        this.smallImageText = smallImageText;
    }

    public void setLargeImageText(String largeImageText) {
        this.largeImageText = largeImageText;
    }

    public void shutdown() {
        shutdownRequested.set(true);
        scheduledExecutorService.shutdown();
    }
}
