package org.takesome.kaylasEngine.server;

public class ServerAttributes {

    @SuppressWarnings("unused")
    private int id, port;
    @SuppressWarnings("unused")
    private String serverName, serverVersion, jreVersion,  serverImage, serverDescription, client, host, ignoreDirs, modsInfo;
    private boolean checkLib;
    //mainClass, forgeVersion, forgeGroup, mcpVersion

    @SuppressWarnings("unused")
    public int getId() {
        return id;
    }

    @SuppressWarnings("unused")
    public String getServerName() {
        return serverName;
    }

    @SuppressWarnings("unused")
    public String getServerVersion() {
        return serverVersion;
    }

    @SuppressWarnings("unused")
    public String getJreVersion() {
        return jreVersion;
    }

    @SuppressWarnings("unused")
    public String getClient() {
        return client;
    }

    @SuppressWarnings("unused")
    public String getHost() {
        return host;
    }

    @SuppressWarnings("unused")
    public int getPort() {
        return port;
    }

    @SuppressWarnings("unused")
    public String getServerImage() {
        return serverImage;
    }

    @SuppressWarnings("unused")
    public String getServerDescription() {
        return serverDescription;
    }

    @SuppressWarnings("unused")
    public String getIgnoreDirs() {
        return ignoreDirs;
    }

    public boolean isCheckLib() {
        return checkLib;
    }

    public String getModsInfo() {
        return modsInfo;
    }
}
