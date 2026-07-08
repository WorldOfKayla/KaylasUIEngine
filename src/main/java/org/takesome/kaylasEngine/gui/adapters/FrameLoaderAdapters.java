package org.takesome.kaylasEngine.gui.adapters;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.adapters.json.JsonFrameAttributesLoader;
import org.takesome.kaylasEngine.gui.adapters.json5.Json5FrameAttributesLoader;
import org.takesome.kaylasEngine.gui.adapters.xml.XmlFrameAttributesLoader;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter manager for frame attributes loaders.
 *
 * <p>
 * Responsible for registering, retrieving and managing {@link FrameAttributesLoader} instances
 * for different file types (json, json5, xml, etc.). Uses thread-safe maps for adapter storage.
 * </p>
 */
@SuppressWarnings("unused")
public class FrameLoaderAdapters {

    // Map of registered adapters (thread-safe)
    private final Map<String, FrameAttributesLoader> adapters = new ConcurrentHashMap<>();
    // Map of default adapters (thread-safe)
    private final Map<String, FrameAttributesLoader> defaultAdapters = new ConcurrentHashMap<>();
    private final Engine engine;

    /**
     * Constructs the adapter manager and initializes default adapters as well as adapters
     * configured via the engine.
     *
     * @param engine engine instance used to obtain configuration.
     */
    public FrameLoaderAdapters(Engine engine) {
        this.engine = engine;
        registerDefaultAdapters();
        registerEngineConfiguredAdapters();
    }

    /**
     * Registers built-in default adapters.
     *
     * <p>
     * Default mappings include JSON, JSON5 and XML loaders. Additional default mappings
     * can be added here in the future.
     * </p>
     */
    private void registerDefaultAdapters() {
        defaultAdapters.put("json", new JsonFrameAttributesLoader());
        defaultAdapters.put("json5", new Json5FrameAttributesLoader());
        defaultAdapters.put("xml", new XmlFrameAttributesLoader());
        // defaultAdapters.put("fxml", new XmlFrameAttributesLoader());
        // defaultAdapters.put("yaml", new YamlFrameAttributesLoader());
    }

    /**
     * Registers adapters specified in the engine configuration.
     *
     * <p>
     * If the engine configuration does not specify any adapters, all default adapters are
     * registered. Otherwise only the types listed in the configuration are registered
     * (if a default for that type exists).
     * </p>
     */
    private void registerEngineConfiguredAdapters() {
        String[] loadAdapters = engine.getEngineData().getLoadAdapters();
        if (loadAdapters == null) {
            // If the engine configuration does not specify specific adapters, register all defaults.
            adapters.putAll(defaultAdapters);
            Engine.LOGGER.info("No specific adapters configured. Registered all default adapters: {}", adapters.keySet());
        } else {
            for (String type : loadAdapters) {
                FrameAttributesLoader adapter = defaultAdapters.get(type);
                if (adapter != null) {
                    adapters.put(type, adapter);
                    Engine.LOGGER.info("Registering {} adapter...", type);
                } else {
                    Engine.LOGGER.warn("No default adapter found for type: {}", type);
                }
            }
            Engine.LOGGER.info("Registered adapters: {}", adapters.keySet());
        }
    }

    /**
     * Returns the loader for the specified file type.
     *
     * @param fileType file type (extension) to look up (e.g. "json", "xml").
     * @return corresponding {@link FrameAttributesLoader}.
     * @throws IllegalArgumentException if no adapter is registered for the given file type.
     */
    public FrameAttributesLoader getLoader(String fileType) {
        FrameAttributesLoader loader = adapters.get(fileType);
        if (loader == null) {
            throw new IllegalArgumentException("No adapter found for: " + fileType);
        }
        return loader;
    }

    /**
     * Registers or replaces an adapter for the specified file type.
     *
     * @param type    file type (extension).
     * @param adapter instance of {@link FrameAttributesLoader} to register.
     */
    public void registerAdapter(String type, FrameAttributesLoader adapter) {
        if (adapters.containsKey(type)) {
            Engine.LOGGER.warn("Adapter for type {} is already registered and will be overwritten.", type);
        }
        adapters.put(type, adapter);
        Engine.LOGGER.info("Registered {} adapter", type);
    }

    /**
     * Registers multiple adapters in batch.
     *
     * @param newAdapters map of file type -> {@link FrameAttributesLoader}.
     */
    public void registerAdapters(Map<String, FrameAttributesLoader> newAdapters) {
        if (newAdapters == null) {
            Engine.LOGGER.warn("Provided newAdapters map is null");
            return;
        }
        for (Map.Entry<String, FrameAttributesLoader> entry : newAdapters.entrySet()) {
            registerAdapter(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Unregisters the adapter for the given file type.
     *
     * @param type file type to unregister.
     */
    public void unregisterAdapter(String type) {
        if (adapters.remove(type) != null) {
            Engine.LOGGER.info("Unregistered {} adapter", type);
        } else {
            Engine.LOGGER.warn("No adapter found to unregister for type: {}", type);
        }
    }

    /**
     * Unregisters adapters for the provided collection of file types.
     *
     * @param types collection of file type identifiers to remove.
     */
    public void unregisterAdapters(Collection<String> types) {
        if (types == null) {
            Engine.LOGGER.warn("Provided types collection is null");
            return;
        }
        for (String type : types) {
            unregisterAdapter(type);
        }
    }

    /**
     * Checks whether an adapter is registered for the given file type.
     *
     * @param type file type to check.
     * @return true if an adapter is registered, false otherwise.
     */
    public boolean isAdapterRegistered(String type) {
        return adapters.containsKey(type);
    }

    /**
     * Returns a thread-safe copy of currently registered adapters.
     *
     * @return map of registered adapters (fileType -> loader).
     */
    public Map<String, FrameAttributesLoader> getRegisteredAdapters() {
        return new ConcurrentHashMap<>(adapters);
    }

    /**
     * Returns a thread-safe copy of the default adapters map.
     *
     * @return map of default adapters.
     */
    public Map<String, FrameAttributesLoader> getDefaultAdapters() {
        return new ConcurrentHashMap<>(defaultAdapters);
    }

    /**
     * Resets registered adapters to the default set.
     */
    public void resetAdapters() {
        adapters.clear();
        adapters.putAll(defaultAdapters);
        Engine.LOGGER.info("Adapters have been reset to default: {}", adapters.keySet());
    }
}
