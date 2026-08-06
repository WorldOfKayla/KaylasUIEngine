package org.takesome.kaylasEngine.game.argsReader;

import com.google.gson.JsonArray;
import org.apache.logging.log4j.LogManager;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.game.GameLauncher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Regression verification for pre-1.13 Minecraft version metadata. */
public final class ArgsReaderLegacyJvmArgumentsVerification {
    private ArgsReaderLegacyJvmArgumentsVerification() {
    }

    public static void main(String[] args) throws Exception {
        Engine.LOGGER = LogManager.getLogger(ArgsReaderLegacyJvmArgumentsVerification.class);
        Path root = Files.createTempDirectory("legacy-minecraft-args-");
        try {
            verifyLegacyMetadataReceivesClasspath(root);
            verifyLegacyUserPropertiesArgumentIsRetained(root);
            verifyPrefixedLegacyLibraryPathIsNormalized(root);
            verifyModernMetadataKeepsDeclaredJvmArguments(root);
            System.out.println("Legacy Minecraft JVM argument verification passed.");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void verifyLegacyMetadataReceivesClasspath(Path root) throws IOException {
        Path argsFile = root.resolve("legacy.json");
        Files.writeString(argsFile, """
                {
                  "mainClass": "net.minecraft.launchwrapper.Launch",
                  "minecraftArguments": "--username ${auth_player_name}",
                  "libraries": []
                }
                """);

        ArgsReader reader = new ArgsReader(new StubGameLauncher(root, argsFile), false);
        JsonArray jvm = reader.getJvmArguments();

        require(contains(jvm, "-Djava.library.path=${natives_directory}"),
                "legacy metadata did not receive java.library.path");
        require(contains(jvm, "-cp"),
                "legacy metadata did not receive the JVM classpath option");
        require(contains(jvm, "${classpath}"),
                "legacy metadata did not receive the classpath template");
    }

    private static void verifyLegacyUserPropertiesArgumentIsRetained(Path root) throws IOException {
        Path argsFile = root.resolve("legacy-user-properties.json");
        Files.writeString(argsFile, """
                {
                  "mainClass": "net.minecraft.client.main.Main",
                  "minecraftArguments": "--username ${auth_player_name} --userProperties ${user_properties}",
                  "libraries": []
                }
                """);

        ArgsReader reader = new ArgsReader(new StubGameLauncher(root, argsFile), false);
        java.util.Map<String, String> values = new java.util.HashMap<>();
        values.put("auth_player_name", "Kayla");
        values.put("user_properties", "{}");
        java.util.List<String> arguments = reader.replaceMask(reader.getGameArguments(), values);

        int option = arguments.indexOf("--userProperties");
        require(option >= 0, "legacy --userProperties option was removed");
        require(option + 1 < arguments.size() && "{}".equals(arguments.get(option + 1)),
                "legacy --userProperties value was not preserved");
    }

    private static void verifyPrefixedLegacyLibraryPathIsNormalized(Path root) throws IOException {
        Path librariesRoot = root.resolve("libraries");
        Path launchWrapper = librariesRoot.resolve("net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar");
        Files.createDirectories(launchWrapper.getParent());
        Files.write(launchWrapper, new byte[]{0});

        Path argsFile = root.resolve("legacy-prefixed-library.json");
        Files.writeString(argsFile, """
                {
                  "mainClass": "net.minecraft.launchwrapper.Launch",
                  "minecraftArguments": "--tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker",
                  "libraries": [
                    {
                      "name": "net.minecraft:launchwrapper:1.12",
                      "artifact": {
                        "path": "libraries/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar",
                        "size": 1
                      }
                    }
                  ]
                }
                """);

        ArgsReader reader = new ArgsReader(new StubGameLauncher(root, argsFile), false);
        String classpath = reader.getLibraryReader().getLibrariesAsString(librariesRoot.toString());
        String expected = launchWrapper.toString() + File.pathSeparator;

        require(expected.equals(classpath),
                "legacy artifact path retained a duplicate libraries directory: " + classpath);
    }

    private static void verifyModernMetadataKeepsDeclaredJvmArguments(Path root) throws IOException {
        Path argsFile = root.resolve("modern.json");
        Files.writeString(argsFile, """
                {
                  "mainClass": "net.minecraft.client.main.Main",
                  "arguments": {
                    "jvm": ["-Dcustom.flag=true"],
                    "game": []
                  },
                  "libraries": []
                }
                """);

        ArgsReader reader = new ArgsReader(new StubGameLauncher(root, argsFile), false);
        JsonArray jvm = reader.getJvmArguments();

        require(jvm.size() == 1 && contains(jvm, "-Dcustom.flag=true"),
                "modern metadata JVM arguments were replaced by legacy defaults");
    }

    private static boolean contains(JsonArray arguments, String expected) {
        for (int index = 0; index < arguments.size(); index++) {
            if (expected.equals(arguments.get(index).getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class StubGameLauncher extends GameLauncher {
        private StubGameLauncher(Path root, Path argsFile) {
            Path libraries = root.resolve("libraries");
            Path natives = root.resolve("natives");
            this.pathBuilders = new PathBuilders(this, root.toString()) {
                @Override
                public Path getArgsFile() {
                    return argsFile;
                }

                @Override
                public Path buildLibrariesPath() {
                    return libraries;
                }

                @Override
                public Path buildNativesPath() {
                    return natives;
                }
            };
        }

        @Override
        protected void setJreArgs() {
        }

        @Override
        protected void setGameArgs() {
        }

        @Override
        protected String addTweakClass() {
            return "";
        }

        @Override
        protected void launchGame() {
        }
    }
}
