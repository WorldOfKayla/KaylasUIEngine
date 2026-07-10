package org.takesome.kaylasEngine.gui.components;

import javax.swing.JComponent;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Common engine-level definition for every basic and composite component type.
 *
 * <p>The definition is deliberately separate from the produced Swing class. Swing controls already
 * inherit from different framework classes ({@code JButton}, {@code JSlider}, and so on), while the
 * engine requires one common hierarchy for cataloging, composing and instantiating them.</p>
 *
 * @param <T> Swing component produced by the definition.
 */
public abstract class AbstractComponentDefinition<T extends JComponent> {
    private final String type;
    private final ComponentKind kind;
    private final String defaultStyle;
    private final boolean applyBaseStyle;
    private final Set<String> aliases;

    protected AbstractComponentDefinition(String type,
                                          ComponentKind kind,
                                          String defaultStyle,
                                          boolean applyBaseStyle,
                                          Set<String> aliases) {
        this.type = requireIdentifier(type, "type");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.defaultStyle = normalizeStyle(defaultStyle);
        this.applyBaseStyle = applyBaseStyle;
        this.aliases = aliases == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(aliases));
    }

    public final String type() {
        return type;
    }

    public final ComponentKind kind() {
        return kind;
    }

    public final String defaultStyle() {
        return defaultStyle;
    }

    public final boolean applyBaseStyle() {
        return applyBaseStyle;
    }

    public final Set<String> aliases() {
        return aliases;
    }

    /** Creates one runtime Swing component for the supplied immutable creation context. */
    public abstract T create(ComponentCreationContext context);

    protected static String requireIdentifier(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Component " + label + " must not be blank");
        }
        return value.trim();
    }

    protected static String normalizeStyle(String value) {
        return value == null || value.isBlank() ? "default" : value.trim();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "type='" + type + '\'' +
                ", kind=" + kind +
                ", defaultStyle='" + defaultStyle + '\'' +
                ", aliases=" + aliases +
                '}';
    }
}
