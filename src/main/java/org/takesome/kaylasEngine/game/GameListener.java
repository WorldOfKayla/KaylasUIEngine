package org.takesome.kaylasEngine.game;

import org.takesome.kaylasEngine.server.ServerAttributes;

@SuppressWarnings("unused")
public interface GameListener {
    void onGameStart(ServerAttributes serverAttributes);
    void onGameExit(ServerAttributes serverAttributes);

    default void onGameFailed(ServerAttributes serverAttributes, Throwable throwable, int exitCode) {
        onGameExit(serverAttributes);
    }
}
