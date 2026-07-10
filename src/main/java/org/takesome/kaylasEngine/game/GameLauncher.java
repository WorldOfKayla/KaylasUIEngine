package org.takesome.kaylasEngine.game;

import org.apache.logging.log4j.Logger;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.config.Config;
import org.takesome.kaylasEngine.game.argsReader.ArgsReader;
import org.takesome.kaylasEngine.server.ServerAttributes;
import org.takesome.kaylasEngine.server.ServerIdentity;

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
        URL[] urls = libraryURLs == null ? new URL[0] : libraryURLs.toArray(new URL[0]);
        this.classLoader = new URLClassLoader(urls, getClass().getClassLoader());
    }

    protected abstract void setJreArgs();
    protected abstract void setGameArgs();
    protected abstract String addTweakClass();
    protected abstract void launchGame();

    public Logger getLogger() {
        return logger;
    }

    protected void printDebug() {
        this.getLogger().debug("#############################");
        this.logger.debug("GameDir {}", getPathBuilders().buildGameDir());
        this.logger.debug("ClientDir {}", getPathBuilders().buildClientDir());
        this.logger.debug("VersionRootDir {}", getPathBuilders().buildVersionRootDir());
        this.logger.debug("CoreType {}", getPathBuilders().coreTypeName());
        this.logger.debug("VersionsDir {}", getPathBuilders().buildVersionDir());
        this.logger.debug("JarFile {}", getPathBuilders().buildMinecraftJarPath());
        this.logger.debug("Natives {}", getPathBuilders().buildNativesPath());
        this.logger.debug("Libraries {}", getPathBuilders().buildLibrariesPath());
        this.logger.debug("Assets {}", getPathBuilders().buildAssetsPath());
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

    protected void addArgsToProcess(List<String> args) {
        if (args != null && !args.isEmpty()) {
            processArgs.addAll(args);
        }
    }

    protected void resetProcessArgs() {
        processArgs.clear();
    }

    protected void notifyGameStart() {
        if (gameListener != null) {
            gameListener.onGameStart(gameClient);
        }
    }

    protected void notifyGameExit() {
        if (gameListener != null) {
            gameListener.onGameExit(gameClient);
        }
    }

    protected void notifyGameFailed(Throwable throwable, int exitCode) {
        if (gameListener != null) {
            gameListener.onGameFailed(gameClient, throwable, exitCode);
        }
    }

    protected String getVersion() {
        String version = gameClient == null ? "" : ServerIdentity.safe(gameClient.getServerVersion());
        if (version.contains("-")) {
            version = version.split("-")[0];
        }
        return version;
    }

    public static class PathBuilders {
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
            return buildGameDir().resolve("versions").resolve(serverVersion());
        }

        public Path buildVersionDir() {
            return buildVersionRootDir().resolve(coreTypeName());
        }

        public Path getArgsFile() {
            return buildVersionDir().resolve(String.format("%s.json", serverVersion()));
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
            return buildVersionDir().resolve(String.format("%s.jar", serverVersion()));
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
            return ServerIdentity.safePathSegment(
                    ServerIdentity.coreType(gameLauncher.gameClient),
                    ServerIdentity.DEFAULT_CORE_TYPE
            );
        }

        public String clientName() {
            return ServerIdentity.safePathSegment(
                    ServerIdentity.clientName(gameLauncher.gameClient),
                    ServerIdentity.DEFAULT_CLIENT
            );
        }

        private String serverVersion() {
            String version = gameLauncher.gameClient == null ? "" : ServerIdentity.safe(gameLauncher.gameClient.getServerVersion());
            return version.isBlank() ? "unknown" : version;
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
