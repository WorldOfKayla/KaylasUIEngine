package org.takesome.kaylasEngine.gui.components.panel.listener;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.frame.FrameConstructor;
import org.takesome.kaylasEngine.gui.components.panel.PanelAttributes;
import org.takesome.kaylasEngine.utils.DragListener;

import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of named listeners/behaviors that may be attached to declarative panels.
 *
 * <p>The registry removes behavior-specific branches from the panel renderer. Applications may
 * register additional installers and then reference them from XML, JSON or YAML through the
 * panel {@code listeners} property.</p>
 */
public final class PanelListenerRegistry {
    public static final String WINDOW_DRAG = "windowDrag";

    private final Map<String, PanelListenerInstaller> installers = new ConcurrentHashMap<>();
    private final Map<String, String> aliases = new ConcurrentHashMap<>();

    /** Creates a registry containing the engine's standard panel listeners. */
    public static PanelListenerRegistry createDefault() {
        PanelListenerRegistry registry = new PanelListenerRegistry();
        registry.register(WINDOW_DRAG, context -> {
            DragListener dragListener = new DragListener(context.window());
            dragListener.apply(context.panel());
        });
        registry.alias("dragger", WINDOW_DRAG);
        registry.alias("drag", WINDOW_DRAG);
        return registry;
    }

    /** Registers or replaces a named panel-listener installer. */
    public PanelListenerRegistry register(String name, PanelListenerInstaller installer) {
        String normalizedName = normalizeRequired(name);
        installers.put(normalizedName, Objects.requireNonNull(installer, "installer"));
        aliases.remove(normalizedName);
        return this;
    }

    /** Registers an alias for another listener name. */
    public PanelListenerRegistry alias(String alias, String targetName) {
        String normalizedAlias = normalizeRequired(alias);
        String normalizedTarget = normalizeRequired(targetName);
        if (normalizedAlias.equals(normalizedTarget)) {
            throw new IllegalArgumentException("Listener alias cannot target itself: " + alias);
        }
        aliases.put(normalizedAlias, normalizedTarget);
        return this;
    }

    /** Removes a listener and aliases that resolve directly to it. */
    public boolean unregister(String name) {
        String normalizedName = normalize(name);
        if (normalizedName.isEmpty()) {
            return false;
        }
        boolean removed = installers.remove(normalizedName) != null;
        aliases.entrySet().removeIf(entry -> entry.getKey().equals(normalizedName)
                || entry.getValue().equals(normalizedName));
        return removed;
    }

    /** Returns whether a listener or alias is currently registered. */
    public boolean contains(String name) {
        String resolved = resolve(name);
        return !resolved.isEmpty() && installers.containsKey(resolved);
    }

    /** Returns the canonical registered listener names. */
    public Set<String> names() {
        return Set.copyOf(installers.keySet());
    }

    /** Installs one listener by name. */
    public void install(String name, JPanel panel, FrameConstructor frameConstructor) {
        install(List.of(name), panel, frameConstructor, null);
    }

    /** Installs all requested listeners on a panel. */
    public void install(Collection<String> names,
                        JPanel panel,
                        FrameConstructor frameConstructor,
                        PanelAttributes attributes) {
        if (names == null || names.isEmpty()) {
            return;
        }

        PanelListenerContext context = new PanelListenerContext(panel, attributes, frameConstructor);
        LinkedHashSet<String> resolvedNames = new LinkedHashSet<>();
        for (String name : names) {
            String resolved = resolve(name);
            if (resolved.isEmpty()) {
                continue;
            }
            resolvedNames.add(resolved);
        }

        for (String resolvedName : resolvedNames) {
            PanelListenerInstaller installer = installers.get(resolvedName);
            if (installer == null) {
                warn("Unknown panel listener '{}'. Registered listeners: {}", resolvedName, names());
                continue;
            }
            try {
                installer.install(context);
            } catch (RuntimeException error) {
                error("Failed to install panel listener '{}' on panel '{}'.",
                        resolvedName,
                        panel.getName(),
                        error);
            }
        }
    }

    /** Resolves aliases and returns the canonical normalized listener name. */
    public String resolve(String name) {
        String current = normalize(name);
        if (current.isEmpty()) {
            return "";
        }

        List<String> visited = new ArrayList<>();
        while (aliases.containsKey(current)) {
            if (visited.contains(current)) {
                warn("Cyclic panel-listener alias detected: {}", visited);
                return "";
            }
            visited.add(current);
            current = aliases.get(current);
        }
        return current;
    }

    private static String normalizeRequired(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Panel-listener name cannot be blank");
        }
        return normalized;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static void warn(String message, Object... args) {
        if (Engine.LOGGER != null) {
            Engine.LOGGER.warn(message, args);
        }
    }

    private static void error(String message, Object first, Object second, Throwable error) {
        if (Engine.LOGGER != null) {
            Engine.LOGGER.error(message, first, second, error);
        }
    }
}
