package org.takesome.kaylasEngine.game.argsReader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.takesome.kaylasEngine.Engine;

public class RuleChecker {

    public RuleChecker(){}

    public boolean checkRules(JsonObject libraryObject) {
        JsonArray rulesArray = libraryObject.getAsJsonArray("rules");
        if (rulesArray == null || rulesArray.size() == 0) {
            return true;
        }
        boolean allow = false;
        boolean disallow = false;

        for (JsonElement ruleElement : rulesArray) {
            JsonObject ruleObject = ruleElement.getAsJsonObject();
            String action = ruleObject.get("action").getAsString();

            if ("allow".equals(action)) {
                allow = true;
            } else if ("disallow".equals(action)) {
                if (isRuleApplicable(ruleObject)) {
                    disallow = true;
                    Engine.LOGGER.debug("RuleChecker disallowed: {}", ruleObject);
                }
            }
        }

        return allow && !disallow;
    }

    public boolean isRuleApplicable(JsonObject rule) {
        if (rule != null && rule.has("os")) {
            JsonObject osObject = rule.getAsJsonObject("os");
            if (osObject.entrySet().isEmpty()) {
                return true;
            }
            String ruleOS = osObject.get("name").getAsString().toLowerCase();
            return Engine.currentOS.contains(ruleOS);
        }
        return true;
    }

    public boolean checkPlatform(JsonObject libraryObject, String platform) {
        JsonObject platformObject = libraryObject.getAsJsonObject(platform);
        if (platformObject != null) {
            return platformObject.has(Engine.currentOS);
        }
        return true;
    }

}
