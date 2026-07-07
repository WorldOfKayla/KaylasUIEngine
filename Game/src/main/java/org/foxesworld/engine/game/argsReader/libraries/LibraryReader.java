package org.foxesworld.engine.game.argsReader.libraries;


import com.google.gson.*;
import org.foxesworld.engine.Engine;
import org.foxesworld.engine.game.GameLauncher;
import org.foxesworld.engine.game.argsReader.ArgsReader;
import org.foxesworld.engine.game.argsReader.RuleChecker;
import org.foxesworld.engine.utils.HashUtils;

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
    private List<Library> libraries = new ArrayList<>(); //Do not remove the initializer!11!!!1
    private long size;

    public LibraryReader(ArgsReader argsReader, boolean checkHash) {
        this.gameLauncher = argsReader.getGameLauncher();
        this.checkHash = checkHash;
        this.path = this.gameLauncher.getPathBuilders().getArgsFile();
        this.ruleChecker = new RuleChecker();
        currentOS = Engine.currentOS;
        this.libraries = readLibraries();
    }

    private List<Library> readLibraries() {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String libraryFullPath;
            JsonObject jsonObject = new Gson().fromJson(reader, JsonObject.class);
            JsonArray librariesArray = jsonObject.getAsJsonArray("libraries");
            Engine.LOGGER.debug("Total libraries to process: {}", librariesArray.size());

            if (libraries.size() != librariesArray.size()) {
                for (JsonElement libraryElement : librariesArray) {
                    JsonObject libraryObject = libraryElement.getAsJsonObject();
                    if (isLibraryAllowed(libraryObject)) {
                        Library library = convertToLibrary(libraryObject);
                        libraryFullPath = this.gameLauncher.getPathBuilders().buildLibrariesPath()
                                + File.separator + library.getArtifact().getPath();

                        File libraryFile = new File(libraryFullPath);
                        if (libraryFile.exists()) {
                            boolean isValidHash = library.getArtifact().getSha1().equals(HashUtils.sha1String(libraryFullPath));

                            if (this.checkHash && isValidHash) {
                                size += library.getArtifact().getSize() / (1024 * 1024);
                                Engine.LOGGER.debug("Adding {} for {} ENV", library.getName(), currentOS);
                                libraries.add(library);
                            } else if (!this.checkHash) {
                                size += library.getArtifact().getSize() / (1024 * 1024);
                                Engine.LOGGER.debug("Adding {} for {} ENV (hash skipped by rule)", library.getName(), currentOS);
                                libraries.add(library);
                            } else {
                                Engine.LOGGER.warn("Invalid hash for {} library skipped", libraryFullPath);
                            }
                        } else {
                            Engine.LOGGER.warn("Library file not found {}", libraryFullPath);
                        }
                    }
                }
            }
            Engine.LOGGER.debug("{} lib num {} {}mb", currentOS, libraries.size(), size);

        } catch (IOException e) {
            Engine.LOGGER.error("Error reading libraries file {}: {}", path, e.getMessage());
        }
        return libraries;
    }



    private boolean isLibraryAllowed(JsonObject libraryObject) {
        return ruleChecker.checkRules(libraryObject) && ruleChecker.checkPlatform(libraryObject, "natives") && ruleChecker.checkPlatform(libraryObject, "classifies");
    }

    @SuppressWarnings("unused")
    public String getLibrariesAsString(String libHomeDir) {
        StringBuilder stringBuilder = new StringBuilder();
        //List<Library> libraries = readLibraries();
        List<URL> libraryURLs = new LinkedList<>();
        for (Library library : this.libraries) {
            String fullPath = libHomeDir + File.separator + library.getArtifact().getPath();
            if (new File(fullPath).exists()) {
                stringBuilder.append(fullPath).append(File.pathSeparator);
                try {
                    libraryURLs.add(Paths.get(fullPath).toUri().toURL());
                } catch (MalformedURLException e) {
                    // Log the error message
                    Engine.LOGGER.error("Error creating URL for library {}: {}", fullPath, e.getMessage());
                    e.printStackTrace();
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
        if (library.getArtifact() == null) {
            String name = library.getName();
            String packed = libraryObject.has("packed") ? libraryObject.get("packed").getAsString() : null;
            String[] nameParts = name.split(":");
            String filePath = "";
            if (packed != null) {
                filePath = String.format("%s/%s/%s/%s-%s.jar", nameParts[0].replace(".", File.separator), nameParts[1], nameParts[2], nameParts[1], nameParts[2]);
            } /*else {
                filePath = String.format("/%s/%s/%s-%s.jar", nameParts[0], nameParts[1], nameParts[1], nameParts[2]);
            }
            */
            Artifact artifact = new Artifact("", 0, filePath, "");
            library.setArtifact(artifact);
        }

        JsonObject classifiesObject = libraryObject.getAsJsonObject("classifies");
        if (classifiesObject != null) {
            JsonObject platformObject = classifiesObject.getAsJsonObject(currentOS);
            if (platformObject != null) {
                String sha1 = platformObject.get("sha1").getAsString();
                int size = platformObject.get("size").getAsInt();
                String path = platformObject.get("path").getAsString();
                String url = platformObject.get("url").getAsString();

                Artifact artifact = new Artifact(sha1, size, path, url);
                library.setArtifact(artifact);
            }
        }

        return library;
    }
}