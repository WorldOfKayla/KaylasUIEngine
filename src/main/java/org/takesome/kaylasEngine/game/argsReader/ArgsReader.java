package org.takesome.kaylasEngine.game.argsReader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.game.GameLauncher;
import org.takesome.kaylasEngine.game.argsReader.libraries.LibraryReader;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArgsReader {
    private static final Set<String> OPTIONAL_VALUE_ARGUMENTS = Set.of(
            "--clientId",
            "--xuid",
            "--quickPlayPath",
            "--quickPlaySingleplayer",
            "--quickPlayMultiplayer",
            "--quickPlayRealms"
    );
    private static final Set<String> OPTIONAL_FLAG_ARGUMENTS = Set.of(
            "--demo"
    );

    private final RuleChecker ruleChecker;
    private JsonArray jvmArguments = new JsonArray();
    private JsonArray gameArguments = new JsonArray();
    private LibraryReader libraryReader;
    private final Path path;
    private final GameLauncher gameLauncher;
    private String mainClass;
    private String assets;
    private boolean authLib;

    public ArgsReader(GameLauncher gameLauncher, boolean checkHash) {
        this.gameLauncher = gameLauncher;
        this.path = gameLauncher.getPathBuilders().getArgsFile();
        this.ruleChecker = new RuleChecker();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Minecraft arguments file not found: " + path.toAbsolutePath());
        }
        this.libraryReader = new LibraryReader(this, checkHash);
        this.readArgs();
    }

    private void readArgs() {
        try (FileReader fileReader = new FileReader(path.toFile())) {
            JsonObject jsonObject = JsonParser.parseReader(fileReader).getAsJsonObject();

            mainClass = stringOrDefault(jsonObject, "mainClass", "net.minecraft.client.main.Main");
            authLib = booleanOrDefault(jsonObject, "authLib", false);
            assets = assetIndex(jsonObject);

            JsonObject argumentsObject = jsonObject.getAsJsonObject("arguments");
            if (argumentsObject != null) {
                jvmArguments = applyRules(arrayOrEmpty(argumentsObject, "jvm"));
                gameArguments = applyRules(arrayOrEmpty(argumentsObject, "game"));
            } else {
                gameArguments = minecraftArgumentsArray(jsonObject);
            }
        } catch (IOException error) {
            Engine.LOGGER.error("Error reading args file {}: {}", path, error.getMessage(), error);
        }
    }

    private JsonArray minecraftArgumentsArray(JsonObject jsonObject) {
        JsonArray arguments = new JsonArray();
        String minecraftArguments = stringOrDefault(jsonObject, "minecraftArguments", "");
        if (!minecraftArguments.isBlank()) {
            for (String argument : minecraftArguments.split("\\s+")) {
                if (!argument.isBlank()) {
                    arguments.add(argument);
                }
            }
        }
        return arguments;
    }

    private JsonArray arrayOrEmpty(JsonObject jsonObject, String key) {
        JsonElement element = jsonObject.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private String stringOrDefault(JsonObject jsonObject, String key, String fallback) {
        JsonElement element = jsonObject.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    private boolean booleanOrDefault(JsonObject jsonObject, String key, boolean fallback) {
        JsonElement element = jsonObject.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
    }

    private String assetIndex(JsonObject jsonObject) {
        String explicitAssets = stringOrDefault(jsonObject, "assets", null);
        if (explicitAssets != null && !explicitAssets.isBlank()) {
            return explicitAssets;
        }
        JsonObject assetIndex = jsonObject.getAsJsonObject("assetIndex");
        if (assetIndex != null) {
            return stringOrDefault(assetIndex, "id", versionFromArgsFile());
        }
        return versionFromArgsFile();
    }

    private String versionFromArgsFile() {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        if (fileName.endsWith(".json")) {
            return fileName.substring(0, fileName.length() - ".json".length());
        }
        return fileName.isBlank() ? "unknown" : fileName;
    }

    private JsonArray applyRules(JsonArray argumentsArray) {
        JsonArray resultArray = new JsonArray();
        for (JsonElement argumentElement : argumentsArray) {
            if (argumentElement == null || argumentElement.isJsonNull()) {
                continue;
            }
            if (!argumentElement.isJsonObject()) {
                resultArray.add(argumentElement);
                continue;
            }

            JsonObject argumentObject = argumentElement.getAsJsonObject();
            JsonArray rulesArray = argumentObject.getAsJsonArray("rules");
            if (rulesArray == null || rulesArray.size() == 0 || hasApplicableRule(rulesArray)) {
                resultArray.add(argumentElement);
            }
        }
        return resultArray;
    }

    private boolean hasApplicableRule(JsonArray rulesArray) {
        boolean allowed = false;
        for (JsonElement ruleElement : rulesArray) {
            if (ruleElement == null || !ruleElement.isJsonObject()) {
                continue;
            }
            JsonObject ruleObject = ruleElement.getAsJsonObject();
            if (!ruleChecker.isRuleApplicable(ruleObject)) {
                continue;
            }
            String action = stringOrDefault(ruleObject, "action", "allow");
            allowed = "allow".equals(action);
        }
        return allowed;
    }

    public List<String> replaceMask(JsonArray arguments, Map<String, String> variables) {
        List<String> argsAndValues = new ArrayList<>();
        if (arguments == null) {
            return argsAndValues;
        }

        for (JsonElement argumentElement : arguments) {
            appendArgument(argsAndValues, argumentElement, variables);
        }
        return removeUnresolvedTemplateArguments(argsAndValues);
    }

    private void appendArgument(List<String> argsAndValues, JsonElement argumentElement, Map<String, String> variables) {
        if (argumentElement == null || argumentElement.isJsonNull()) {
            return;
        }
        if (argumentElement.isJsonPrimitive()) {
            argsAndValues.add(replaceVariables(argumentElement.getAsString(), variables));
            return;
        }
        if (!argumentElement.isJsonObject()) {
            return;
        }

        JsonObject argumentObject = argumentElement.getAsJsonObject();
        JsonElement valueElement = argumentObject.has("values") ? argumentObject.get("values") : argumentObject.get("value");
        if (valueElement == null || valueElement.isJsonNull()) {
            return;
        }
        if (valueElement.isJsonArray()) {
            for (JsonElement element : valueElement.getAsJsonArray()) {
                appendArgumentValue(argsAndValues, element, variables);
            }
            return;
        }
        appendArgumentValue(argsAndValues, valueElement, variables);
    }

    private void appendArgumentValue(List<String> argsAndValues, JsonElement valueElement, Map<String, String> variables) {
        if (valueElement == null || valueElement.isJsonNull()) {
            return;
        }
        argsAndValues.add(replaceVariables(valueElement.getAsString(), variables));
    }

    private List<String> removeUnresolvedTemplateArguments(List<String> argsAndValues) {
        List<String> sanitized = new ArrayList<>();
        for (int index = 0; index < argsAndValues.size(); index++) {
            String argument = argsAndValues.get(index);
            if (argument == null || argument.isBlank()) {
                continue;
            }
            if (isOptionalFlagArgument(argument)) {
                Engine.LOGGER.debug("Removed optional Minecraft flag: {}", argument);
                continue;
            }
            if (isOptionalValueArgument(argument)) {
                String value = index + 1 < argsAndValues.size() ? argsAndValues.get(index + 1) : "";
                Engine.LOGGER.debug("Removed optional Minecraft argument pair: {} {}", argument, value);
                index++;
                continue;
            }
            if (containsTemplatePlaceholder(argument)) {
                if (!sanitized.isEmpty() && isOptionRequiringSeparateValue(sanitized.get(sanitized.size() - 1))) {
                    String removedOption = sanitized.remove(sanitized.size() - 1);
                    Engine.LOGGER.warn("Removed unresolved Minecraft argument pair: {} {}", removedOption, argument);
                } else {
                    Engine.LOGGER.warn("Removed unresolved Minecraft argument: {}", argument);
                }
                continue;
            }
            sanitized.add(argument);
        }
        return sanitized;
    }

    private boolean isOptionalFlagArgument(String argument) {
        return OPTIONAL_FLAG_ARGUMENTS.contains(argument);
    }

    private boolean isOptionalValueArgument(String argument) {
        return OPTIONAL_VALUE_ARGUMENTS.contains(argument);
    }

    private boolean containsTemplatePlaceholder(String argument) {
        return argument != null && Pattern.compile("\\$\\{[^}]+}").matcher(argument).find();
    }

    private boolean isOptionRequiringSeparateValue(String argument) {
        if (argument == null || argument.isBlank()) {
            return false;
        }
        return argument.startsWith("-") && !argument.contains("=");
    }

    private String replaceVariables(String value, Map<String, String> variables) {
        Pattern pattern = Pattern.compile("\\$\\{([^}]*)\\}");
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            String variableName = matcher.group(1);
            if (variables.containsKey(variableName)) {
                value = value.replace("${" + variableName + "}", variables.get(variableName));
            }
        }
        return value;
    }

    public JsonArray getJvmArguments() { return jvmArguments; }
    public JsonArray getGameArguments() { return gameArguments; }
    public String getMainClass() { return mainClass; }
    public String getAssets() { return assets; }
    public boolean isAuthLib() { return authLib; }
    public GameLauncher getGameLauncher() { return gameLauncher; }
    public LibraryReader getLibraryReader() { return libraryReader; }
}
