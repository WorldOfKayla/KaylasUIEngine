package org.takesome.kaylasEngine.game.argsReader.libraries;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.game.GameLauncher;
import org.takesome.kaylasEngine.game.argsReader.ArgsReader;
import org.takesome.kaylasEngine.game.argsReader.RuleChecker;
import org.takesome.kaylasEngine.utils.HashUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class LibraryReader {
    private final boolean checkHash;
    private final RuleChecker ruleChecker;
    private final String currentOS;
    private final Path path;
    private final GameLauncher gameLauncher;
    private List<Library> libraries = new ArrayList<>(); // Do not remove the initializer.
    private long size;

    public LibraryReader(ArgsReader argsReader, boolean checkHash) {
        this.gameLauncher = argsReader.getGameLauncher();
        this.checkHash = checkHash;
        this.path = this.gameLauncher.getPathBuilders().getArgsFile();
        this.ruleChecker = new RuleChecker();
        this.currentOS = Engine.currentOS;
        this.libraries = readLibraries();
    }

    private List<Library> readLibraries() {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            JsonObject jsonObject = new Gson().fromJson(reader, JsonObject.class);
            JsonArray librariesArray = jsonObject.getAsJsonArray("libraries");
            if (librariesArray == null) {
                return libraries;
            }
            Engine.LOGGER.debug("Total libraries to process: {}", librariesArray.size());

            for (JsonElement libraryElement : librariesArray) {
                if (libraryElement == null || !libraryElement.isJsonObject()) {
                    continue;
                }
                JsonObject libraryObject = libraryElement.getAsJsonObject();
                if (!isLibraryAllowed(libraryObject)) {
                    continue;
                }

                Library library = convertToLibrary(libraryObject);
                if (!hasUsableArtifact(library)) {
                    Engine.LOGGER.debug("Skipping library without artifact path: {}", library.getName());
                    continue;
                }

                String libraryFullPath = this.gameLauncher.getPathBuilders()
                        .buildLibrariesPath()
                        .resolve(library.getArtifact().getPath())
                        .toString();
                File libraryFile = new File(libraryFullPath);
                if (!libraryFile.isFile()) {
                    Engine.LOGGER.warn("Library file not found {}", libraryFullPath);
                    continue;
                }

                boolean isValidHash = !checkHash || isValidHash(library, libraryFullPath);
                if (isValidHash) {
                    size += library.getArtifact().getSize() / (1024 * 1024);
                    Engine.LOGGER.debug("Adding {} for {} ENV{}", library.getName(), currentOS,
                            checkHash ? "" : " (hash skipped by rule)");
                    libraries.add(library);
                } else {
                    Engine.LOGGER.warn("Invalid hash for {} library skipped", libraryFullPath);
                }
            }
            Engine.LOGGER.debug("{} lib num {} {}mb", currentOS, libraries.size(), size);
        } catch (IOException error) {
            Engine.LOGGER.error("Error reading libraries file {}: {}", path, error.getMessage(), error);
        }
        return libraries;
    }

    private boolean isValidHash(Library library, String libraryFullPath) {
        String expectedHash = library.getArtifact().getSha1();
        if (expectedHash == null || expectedHash.isBlank()) {
            return true;
        }
        return expectedHash.equalsIgnoreCase(HashUtils.calculateSHA1(libraryFullPath));
    }

    private boolean hasUsableArtifact(Library library) {
        return library != null
                && library.getArtifact() != null
                && library.getArtifact().getPath() != null
                && !library.getArtifact().getPath().isBlank();
    }

    private boolean isLibraryAllowed(JsonObject libraryObject) {
        return ruleChecker.checkRules(libraryObject) && isNativeAllowed(libraryObject);
    }

    private boolean isNativeAllowed(JsonObject libraryObject) {
        JsonObject natives = libraryObject.getAsJsonObject("natives");
        if (natives == null) {
            return true;
        }
        return natives.has(currentOS);
    }

    @SuppressWarnings("unused")
    public String getLibrariesAsString(String libHomeDir) {
        StringBuilder stringBuilder = new StringBuilder();
        List<URL> libraryURLs = new LinkedList<>();
        for (Library library : this.libraries) {
            String fullPath = Paths.get(libHomeDir).resolve(library.getArtifact().getPath()).toString();
            if (new File(fullPath).isFile()) {
                stringBuilder.append(fullPath).append(File.pathSeparator);
                try {
                    libraryURLs.add(Paths.get(fullPath).toUri().toURL());
                } catch (MalformedURLException error) {
                    Engine.LOGGER.error("Error creating URL for library {}: {}", fullPath, error.getMessage(), error);
                }
            } else {
                Engine.LOGGER.debug("Library {} doesn't exist!", fullPath);
            }
        }
        gameLauncher.createClassLoader(libraryURLs);
        return stringBuilder.toString();
    }

    private Library convertToLibrary(JsonObject libraryObject) {
        Gson gson = new GsonBuilder().create();
        Library library = gson.fromJson(libraryObject, Library.class);
        Artifact artifact = nativeArtifact(libraryObject);
        if (artifact == null) {
            artifact = downloadsArtifact(libraryObject);
        }
        if (artifact == null) {
            artifact = legacyArtifact(library, libraryObject);
        }
        library.setArtifact(artifact);
        return library;
    }

    private Artifact nativeArtifact(JsonObject libraryObject) {
        JsonObject natives = libraryObject.getAsJsonObject("natives");
        if (natives == null || !natives.has(currentOS)) {
            return null;
        }
        String classifier = natives.get(currentOS).getAsString().replace("${arch}", osArchBits());
        JsonObject downloads = libraryObject.getAsJsonObject("downloads");
        JsonObject classifiers = downloads == null ? null : downloads.getAsJsonObject("classifiers");
        JsonObject artifactObject = classifiers == null ? null : classifiers.getAsJsonObject(classifier);
        if (artifactObject == null) {
            JsonObject legacyClassifiers = libraryObject.getAsJsonObject("classifiers");
            artifactObject = legacyClassifiers == null ? null : legacyClassifiers.getAsJsonObject(classifier);
        }
        return artifact(artifactObject);
    }

    private Artifact downloadsArtifact(JsonObject libraryObject) {
        JsonObject downloads = libraryObject.getAsJsonObject("downloads");
        JsonObject artifactObject = downloads == null ? null : downloads.getAsJsonObject("artifact");
        return artifact(artifactObject);
    }

    private Artifact legacyArtifact(Library library, JsonObject libraryObject) {
        if (library.getArtifact() != null && library.getArtifact().getPath() != null && !library.getArtifact().getPath().isBlank()) {
            return library.getArtifact();
        }
        String packed = libraryObject.has("packed") ? libraryObject.get("packed").getAsString() : null;
        if (packed == null || library.getName() == null) {
            return null;
        }
        String[] nameParts = library.getName().split(":");
        if (nameParts.length < 3) {
            return null;
        }
        String filePath = String.format("%s/%s/%s/%s-%s.jar",
                nameParts[0].replace(".", "/"), nameParts[1], nameParts[2], nameParts[1], nameParts[2]);
        return new Artifact("", 0, filePath, "");
    }

    private Artifact artifact(JsonObject artifactObject) {
        if (artifactObject == null) {
            return null;
        }
        String path = stringOrDefault(artifactObject, "path", "");
        if (path.isBlank()) {
            return null;
        }
        String sha1 = stringOrDefault(artifactObject, "sha1", "");
        String url = stringOrDefault(artifactObject, "url", "");
        int size = intOrDefault(artifactObject, "size", 0);
        return new Artifact(sha1, size, path, url);
    }

    private String stringOrDefault(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    private int intOrDefault(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsInt();
    }

    private String osArchBits() {
        return System.getProperty("os.arch", "").contains("64") ? "64" : "32";
    }
}
