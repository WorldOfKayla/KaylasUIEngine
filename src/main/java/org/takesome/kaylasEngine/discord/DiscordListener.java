package org.takesome.kaylasEngine.discord;

import club.minnced.discord.rpc.DiscordRPC;

public interface DiscordListener {
    DiscordRPC getDiscordLib();
    void discordRpcStart(String state, String details, String icon);
}
