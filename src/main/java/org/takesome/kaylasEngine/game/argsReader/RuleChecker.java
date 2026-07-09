package org.takesome.kaylasEngine.game.argsReader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.takesome.kaylasEngine.Engine;

public class RuleChecker {

    public RuleChecker() {}

    public boolean checkRules(JsonObject libraryObject) {
        JsonArray rulesArray = libraryObject.getAsJsonArray("rules");
        if (rulesArray == null || rulesArray.size() == 0) {
            return true;
        }

        boolean allowed = false;
        for (JsonElement ruleElement : rulesArray) {
            if (ruleElement == null || !ruleElement.isJsonObject()) {
                continue;
            }
            JsonObject ruleObject = ruleElement.getAsJsonObject();
            if (!isRuleApplicable(ruleObject)) {
                continue;
            }
            String action = ruleObject.has("action") ? ruleObject.get("action").getAsString() : "allow";
            allowed = "allow".equals(action);
            if (!allowed) {
                Engine.LOGGER.debug("RuleChecker disallowed: {}", ruleObject);
            }
        }
        return allowed;
    }

    public boolean isRuleApplicable(JsonObject rule) {
        if (rule == null || !rule.has("os")) {
            return true;
        }
        JsonObject osObject = rule.getAsJsonObject("os");
        if (osObject == null || osObject.entrySet().isEmpty()) {
            return true;
        }
        if (!osObject.has("name")) {
            return true;
        }
        String ruleOS = osObject.get("name").getAsString().toLowerCase();
        return Engine.currentOS.contains(ruleOS);
    }

    public boolean checkPlatform(JsonObject libraryObject, String platform) {
        JsonObject platformObject = libraryObject.getAsJsonObject(platform);
        if (platformObject != null) {
            return platformObject.has(Engine.currentOS);
        }
        return true;
    }
}
