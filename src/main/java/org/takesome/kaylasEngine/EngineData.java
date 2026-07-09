package org.takesome.kaylasEngine;

import com.google.gson.Gson;
import org.takesome.kaylasEngine.utils.HTTP.HTTPconf;
import org.takesome.kaylasEngine.gui.loadingManager.LoadManagerAttributes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class EngineData {
    private String logLevel,bindUrl,launcherBrand,launcherVersion,launcherBuild,appId,accessToken,programRuntime,groupDomain,vkAPIversion;
    private String[] styles,loadAdapters;
    private DownloadManager downloadManager;
    private LoadManagerAttributes[] loadManager;
    private HTTPconf httpConf;
    private BackendBinding backend;
    private String[] tweakClasses;
    private Map<String, Object> files;
    public String getBindUrl() {
        return bindUrl;
    }
    public String getLauncherBrand() {
        return launcherBrand;
    }
    public String getLauncherVersion() {
        return launcherVersion;
    }

    public String getLauncherBuild() {
        return launcherBuild;
    }

    public String getAppId() {
        return appId;
    }
    public String getAccessToken() {
        return accessToken;
    }
    public String getGroupDomain() {
        return groupDomain;
    }
    public String getVkAPIversion() {
        return vkAPIversion;
    }
    public DownloadManager getDownloadManager() {
        return downloadManager;
    }
    public LoadManagerAttributes[] getLoadManager() { return  loadManager;};
    public HTTPconf getHttPconf() {
        return httpConf;
    }

    public BackendBinding getBackend() {
        return backend == null ? BackendBinding.disabled() : backend;
    }

    public String[] getTweakClasses() {
        return tweakClasses;
    }
    public String getLogLevel() {
        return logLevel;
    }
    public String getProgramRuntime() {
        return programRuntime;
    }
    public Map<String, Object> getFiles() {
        return files;
    }

    public String[] getStyles() {
        return styles;
    }

    public String[] getLoadAdapters() {
        return loadAdapters;
    }

    public static class BackendBinding {
        private boolean enabled = false;
        private String wsUrl = "ws://127.0.0.1:18080/ws/launcher";
        private int heartbeatSeconds = 15;
        private int maxReconnectAttempts = 0;

        private static BackendBinding disabled() {
            BackendBinding binding = new BackendBinding();
            binding.enabled = false;
            return binding;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getWsUrl() {
            return wsUrl == null || wsUrl.isBlank()
                    ? "ws://127.0.0.1:18080/ws/launcher"
                    : wsUrl;
        }

        public int getHeartbeatSeconds() {
            return heartbeatSeconds <= 0 ? 15 : heartbeatSeconds;
        }

        public int getMaxReconnectAttempts() {
            return Math.max(0, maxReconnectAttempts);
        }
    }

    public static class DownloadManager {
        private int downloadThreads;
        private List<ReplaceMask> replaceMasks;

        public int getDownloadThreads() {
            return downloadThreads;
        }

        public List<ReplaceMask> getReplaceMasks() {
            return replaceMasks;
        }
    }

    public static class ReplaceMask {
        private String mask, suffix, prefix,replace;

        public String getMask() {
            return mask;
        }

        public String getReplace() {
            return replace;
        }

        public String getSuffix() {
            return suffix;
        }

        public String getPrefix() {
            return prefix;
        }
    }

    public EngineData initEngineValues(String propertyPath) {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(propertyPath);
        if (inputStream != null) {
            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            return new Gson().fromJson(reader, EngineData.class);
        }
        return null;
    }
}
