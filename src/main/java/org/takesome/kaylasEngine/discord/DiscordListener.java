package org.takesome.kaylasEngine.discord;

import club.minnced.discord.rpc.DiscordRPC;

public interface DiscordListener {
    DiscordRPC getDiscordLib();

    void discordRpcStart(String state, String details, String icon);

    void updatePresence(String state,
                        String details,
                        String largeImageKey,
                        String largeImageText,
                        String smallImageKey,
                        String smallImageText,
                        boolean resetTimestamp);

    boolean isAvailable();

    void clearPresence();

    void shutdown();
}
