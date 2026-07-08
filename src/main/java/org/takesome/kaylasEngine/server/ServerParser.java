package org.takesome.kaylasEngine.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.utils.HTTP.HTTPrequest;

public abstract class ServerParser extends HTTPrequest {
    protected final Engine engine;
    protected int serversNum = 0;
    protected final List<ServerAttributes> serverList = new ArrayList<>();

    public ServerParser(Engine engine, String requestMethod) {
        super(engine, requestMethod);
        this.engine = engine;
    }

    public abstract CompletableFuture<List<ServerAttributes>> parseServers(String login);

    public int getServersNum() {
        return this.serversNum;
    }
}
