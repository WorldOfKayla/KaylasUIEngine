package org.takesome.kaylasEngine.game;

import org.apache.logging.log4j.Logger;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.config.Config;
import org.takesome.kaylasEngine.game.argsReader.ArgsReader;
import org.takesome.kaylasEngine.server.ServerAttributes;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class GameLauncher {
    protected GameListener gameListener;
    protected ServerAttributes gameClient;
    protected Engine engine;
    protected Logger logger;
    protected ArgsReader argsReader;
    protected Config config;
    protected PathBuilders pathBuilders;
    protected int intVer;
    private final String[] toTest = {"_JAVA_OPTIONS", "_JAVA_OPTS", "JAVA_OPTS", "JAVA_OPTIONS"};
    protected URLClassLoader classLoader;
    protected final List<String> processArgs = new ArrayList<>();
    public void createClassLoader(List<URL> libraryURLs) {
        URL[] urls = libraryURLs.toArray(new URL[0]);
        this.classLoader = new URLClassLoader(urls, getClass().getClassLoader());
    }
    protected abstract void setJreArgs();
    protected abstract void setGameArgs();
    protected abstract String addTweakClass();
    protected abstract void launchGame();
    public Logger getLogger() {
        return logger;
    }

    protected  void printDebug(){
        this.getLogger().debug("#############################");
        this.logger.debug("GameDir " + getPathBuilders().buildGameDir());
        this.logger.debug("ClientDir " + getPathBuilders().buildClientDir());
        this.logger.debug("VersionRootDir " + getPathBuilders().buildVersionRootDir());
        this.logger.debug("CoreType " + getPathBuilders().coreTypeName());
        this.logger.debug("VersionsDir " + getPathBuilders().buildVersionDir());
        this.logger.debug("JarFile " + getPathBuilders().buildMinecraftJarPath());
        this.logger.debug("Natives " + getPathBuilders().buildNativesPath());
        this.logger.debug("Libraries " + getPathBuilders().buildLibrariesPath());
        this.logger.debug("Assets " + getPathBuilders().buildAssetsPath());
        this.logger.debug("#############################");
    }
    protected void checkDangerousParams() {
        for (String t : toTest) {
            String env = System.getenv(t);
            if (env != null) {
                env = env.toLowerCase(Locale.US);
                if (env.contains("-cp") || env.contains("-classpath") || env.contains("-javaagent")
                        || env.contains("-agentpath") || env.contains("-agentlib")) {
                    throw new SecurityException("JavaAgent in global options not allowed!");
                }
            }
        }
    }
    public void setGameListener(GameListener gameListener) {
        this.gameListener = gameListener;
    }
    protected int getIntVer() {
        return intVer;
    }
    public String getCurrentJre() {
        return this.gameClient.getJreVersion();
    }

    public URLClassLoader getClassLoader() {
        return classLoader;
    }

    public List<String> getProcessArgs() {
        return processArgs;
    }

    public Engine getEngine() {
        return engine;
    }

    protected void addArgsToProcess(List<String> args){
        processArgs.addAll(args);
    }

    protected String getVersion(){
        String version = gameClient.getServerVersion();
        if (gameClient.getServerVersion().contains("-")) {
            version = gameClient.getServerVersion().split("-")[0];
        }
        return  version;
    }

    public static class PathBuilders {
        private static final String DEFAULT_CORE_TYPE = "Vanilla";
        private static final String DEFAULT_CLIENT = "Default";

        private final GameLauncher gameLauncher;
        private final Path homeDir;

        public PathBuilders(GameLauncher gameLauncher, String homeDir) {
            this.gameLauncher = gameLauncher;
            this.homeDir = Paths.get(homeDir).toAbsolutePath();
        }

        public Path buildGameDir() {
            return homeDir;
        }

        public Path buildVersionRootDir() {
            return buildGameDir().resolve("versions").resolve(gameLauncher.gameClient.getServerVersion());
        }

        public Path buildVersionDir() {
            return buildVersionRootDir().resolve(coreTypeName());
        }

        public Path getArgsFile() {
            return buildVersionDir().resolve(String.format("%s.json", gameLauncher.gameClient.getServerVersion()));
        }

        public Path buildLibrariesPath() {
            return buildVersionDir().resolve("libraries");
        }

        public Path buildNativesPath() {
            return buildVersionDir().resolve("natives");
        }

        public Path buildAssetsPath() {
            return buildVersionRootDir().resolve("assets");
        }

        public Path buildMinecraftJarPath() {
            return buildVersionDir().resolve(String.format("%s.jar", gameLauncher.gameClient.getServerVersion()));
        }

        public Path buildClientDir() {
            Path clientDir = buildGameDir().resolve("clients").resolve(clientName());
            ensureDirectoryExists(clientDir, "client");
            return clientDir;
        }

        public Path buildRuntimeDir() {
            Path runtimeDir = buildGameDir().resolve("runtime");
            ensureDirectoryExists(runtimeDir, "runtime");
            return runtimeDir;
        }

        public String coreTypeName() {
            String coreType = gameLauncher.gameClient.getCoreType();
            if (coreType != null && !coreType.isBlank()) {
                return coreType.trim();
            }

            String legacyClient = gameLauncher.gameClient.getClient();
            if (looksLikeCoreType(legacyClient)) {
                return legacyClient.trim();
            }

            return DEFAULT_CORE_TYPE;
        }

        public String clientName() {
            String client = gameLauncher.gameClient.getClient();
            if (client != null && !client.isBlank() && !looksLikeCoreType(client)) {
                return client.trim();
            }

            String serverName = gameLauncher.gameClient.getServerName();
            if (serverName != null && !serverName.isBlank()) {
                return serverName.trim();
            }

            return DEFAULT_CLIENT;
        }

        private boolean looksLikeCoreType(String value) {
            if (value == null || value.isBlank()) {
                return false;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            return normalized.equals("vanilla")
                    || normalized.equals("forge")
                    || normalized.equals("fabric")
                    || normalized.equals("quilt")
                    || normalized.equals("neoforge")
                    || normalized.equals("runtime");
        }

        /**
         * Utility method to ensure a directory exists, creating it if necessary.
         */
        private void ensureDirectoryExists(Path dir, String type) {
            if (!Files.isDirectory(dir)) {
                try {
                    Files.createDirectories(dir);
                    Engine.getLOGGER().debug("Created {} directory at {}", type, dir);
                } catch (Exception e) {
                    Engine.getLOGGER().error("Failed to create {} directory at {}", type, dir, e);
                    throw new RuntimeException("Could not create " + type + " directory at " + dir, e);
                }
            }
        }
    }
    public void setArgsReader(ArgsReader argsReader) {
        this.argsReader = argsReader;
    }

    public ArgsReader getArgsReader() {
        return argsReader;
    }
    public PathBuilders getPathBuilders() {
        return pathBuilders;
    }

    public void setClassLoader(URLClassLoader classLoader) {
        this.classLoader = classLoader;
    }
}
