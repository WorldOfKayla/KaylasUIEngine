package org.takesome.kaylasEngine.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cfgProvider.CfgProvider;
import org.takesome.kaylasEngine.Engine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public abstract class Config {
    private final Map<String, Class<?>> configFiles;
    private final Logger logger;
    protected final Map<String, Object> config = new HashMap<>();
    protected final ReentrantReadWriteLock configLock = new ReentrantReadWriteLock();
    protected CfgProvider cfgProvider;
    protected int dirIndex = 3;
    protected String cfgFileExtension = ".json";
    protected String cfgExportDir = "cache/config/";
    protected String defaultConfFilesDir = "config/";

    public Config(Map<String, Class<?>> configFiles, Logger logger) {
        this.configFiles = configFiles;
        this.logger = logger;

    }

    public void processConfig(){
        configFiles.forEach((cfgName, clazz) -> {
            this.cfgProvider = new CfgProvider(logger);
            this.initConfig(cfgName, clazz);
        });
    }

    public void initConfig(String cfgName, Class<?> clazz) {
        String cfgFileName = cfgName + cfgFileExtension;
        cfgProvider.setCfgExportDirName(cfgExportDir);
        cfgProvider.setBaseDirPathIndex(dirIndex);
        cfgProvider.setCfgFileExtension(cfgFileExtension);
        cfgProvider.setDefaultConfFilesDir(defaultConfFilesDir);

        try {
            cfgProvider.processFile(cfgFileName);
            this.config.clear();
            this.config.putAll(Optional.ofNullable(cfgProvider.getCfgMap(cfgName)).orElseGet(HashMap::new));
            syncWithTemplate(cfgProvider.getThisTemplate());
            assignConfigValues(clazz);
        } catch (Exception e) {
            logger.error("Config init error: " + cfgFileName, e);
            this.config.clear();
        }
    }

    public void syncWithTemplate(Map<String, Object> template) {
        configLock.writeLock().lock();
        try {
            boolean isUpdated = false;

            for (Map.Entry<String, Object> entry : template.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (!config.containsKey(key)) {
                    config.put(key, value);
                    logger.info("Added missing config entry: " + key + " -> " + value);
                    isUpdated = true;
                }
            }

            if (isUpdated) {
                writeCurrentConfig();
            }
        } finally {
            configLock.writeLock().unlock();
        }
    }


    public abstract void addToConfig(Map<String, Object> inputData, List<?> values);

    public abstract void setConfigValue(String key, Object value);

    public abstract void clearConfigData(List<String> dataToClear, boolean write);

    public abstract void clearConfigData(String dataToClear, boolean write);

    public void assignConfigValues(Class<?> clazz) {
        configLock.readLock().lock();
        try {
            config.forEach((key, value) -> {
                try {
                    Field field = clazz.getDeclaredField(key);
                    field.setAccessible(true);
                    processConfigField(field, key);
                } catch (NoSuchFieldException ignored) {
                    Engine.LOGGER.warn("Field '" + key + "' not found in " + clazz.getSimpleName());
                }
            });
        } finally {
            configLock.readLock().unlock();
        }
    }

    public void writeCurrentConfig() {
        configLock.writeLock().lock();
        try {
            File configFile = new File(getFullPath() + "config/config.json");
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(configToJSON());
                Engine.LOGGER.info("Config saved: " + configFile.getPath());
            } catch (IOException e) {
                Engine.LOGGER.error("Error saving config: " + configFile.getPath(), e);
            }
        } finally {
            configLock.writeLock().unlock();
        }
    }

    public String configToJSON() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(config);
    }

    protected void processConfigField(Field field, String fieldName) {
        try {
            Object value = config.get(fieldName);

            if (value instanceof String) {
                value = resolvePlaceholders((String) value);
                config.put(fieldName, value);
            }
            Object castedValue = castValue(field.getType(), value);

            if (isValidValue(field.getType(), castedValue)) {
                field.set(this, castedValue);
            } else {
                handleInvalidValue(field, fieldName);
            }
        } catch (IllegalAccessException e) {
            Engine.LOGGER.error("Ошибка доступа к полю " + fieldName, e);
        }
    }

    private String resolvePlaceholders(String input) {
        if (input == null) return null;

        Matcher matcher = Pattern.compile("SysVal\\{([^}]+)}").matcher(input);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            result.append(input, lastEnd, matcher.start());
            String placeholder = matcher.group(1);
            String resolvedValue = resolveSysVal(placeholder);

            Engine.LOGGER.debug("Replacing SysVal: " + placeholder + " -> " + resolvedValue);

            result.append(resolvedValue != null ? resolvedValue : matcher.group(0));
            lastEnd = matcher.end();
        }
        result.append(input, lastEnd, input.length());
        return result.toString();
    }


    private String resolveSysVal(String placeholder) {
        return Optional.ofNullable(System.getProperty(placeholder))
                .orElse(System.getenv(placeholder));
    }

    private void handleInvalidValue(Field field, String key) {
        try {
            Object defaultValue = field.get(this);
            config.put(key, defaultValue);
            Engine.LOGGER.warn("Неверное значение для '" + key + "', установлено по умолчанию: " + defaultValue);
        } catch (IllegalAccessException e) {
            Engine.LOGGER.error("Ошибка доступа к полю " + key, e);
        }
    }

    private boolean isValidValue(Class<?> fieldType, Object value) {
        if (value == null) return false;
        return switch (fieldType.getName()) {
            case "boolean", "java.lang.Boolean" -> value instanceof Boolean;
            case "int", "java.lang.Integer" -> value instanceof Integer;
            case "double", "java.lang.Double" -> value instanceof Double;
            case "float", "java.lang.Float" -> value instanceof Float;
            case "long", "java.lang.Long" -> value instanceof Long;
            case "java.lang.String" -> value instanceof String;
            default -> false;
        };
    }

    private Object castValue(Class<?> fieldType, Object value) {
        if (value == null) return null;
        return switch (fieldType.getName()) {
            case "boolean", "java.lang.Boolean" -> Boolean.parseBoolean(value.toString());
            case "int", "java.lang.Integer" -> Integer.parseInt(value.toString());
            case "double", "java.lang.Double" -> Double.parseDouble(value.toString());
            case "float", "java.lang.Float" -> Float.parseFloat(value.toString());
            case "long", "java.lang.Long" -> Long.parseLong(value.toString());
            case "java.lang.String" -> value.toString();
            default -> throw new IllegalArgumentException("Unsupported field type: " + fieldType);
        };
    }

    public String getFullPath() {
        return cfgProvider.getGameFullPath() + File.separator;
    }

    public Map<String, Object> getConfig() {
        configLock.readLock().lock();
        try {
            return Collections.unmodifiableMap(config);
        } finally {
            configLock.readLock().unlock();
        }
    }
}