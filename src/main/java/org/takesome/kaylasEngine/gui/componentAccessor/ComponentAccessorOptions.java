package org.takesome.kaylasEngine.gui.componentAccessor;

import java.util.Objects;

/**
 * Immutable configuration for {@link ComponentsAccessor} indexing, binding, and value collection.
 *
 * <p>Use {@link #defaults()} to preserve legacy behavior or {@link #builder()} to opt into stricter
 * duplicate handling, native values, or nested form values.</p>
 */
public final class ComponentAccessorOptions {
    private final boolean traverseNestedComponents;
    private final boolean includeNestedValuesInForms;
    private final boolean injectAnnotatedFields;
    private final boolean failOnMissingRootPanel;
    private final boolean autoRefreshOnLookup;
    private final int maximumTraversalDepth;
    private final DuplicateComponentPolicy duplicatePolicy;
    private final UnsupportedValuePolicy unsupportedValuePolicy;
    private final ComponentValueMode valueMode;

    private ComponentAccessorOptions(Builder builder) {
        this.traverseNestedComponents = builder.traverseNestedComponents;
        this.includeNestedValuesInForms = builder.includeNestedValuesInForms;
        this.injectAnnotatedFields = builder.injectAnnotatedFields;
        this.failOnMissingRootPanel = builder.failOnMissingRootPanel;
        this.autoRefreshOnLookup = builder.autoRefreshOnLookup;
        this.maximumTraversalDepth = builder.maximumTraversalDepth;
        this.duplicatePolicy = Objects.requireNonNull(builder.duplicatePolicy, "duplicatePolicy");
        this.unsupportedValuePolicy = Objects.requireNonNull(
                builder.unsupportedValuePolicy,
                "unsupportedValuePolicy"
        );
        this.valueMode = Objects.requireNonNull(builder.valueMode, "valueMode");
    }

    /**
     * Returns options compatible with the original accessor: duplicate replacement, string values,
     * empty strings for unsupported controls, field injection, and no nested form values.
     *
     * @return default immutable options.
     */
    public static ComponentAccessorOptions defaults() {
        return builder().build();
    }

    /**
     * Creates a mutable builder initialized with compatibility-oriented defaults.
     *
     * @return options builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /** @return whether named descendants of registered components are indexed. */
    public boolean traverseNestedComponents() {
        return traverseNestedComponents;
    }

    /** @return whether nested descendants contribute entries to form-value maps. */
    public boolean includeNestedValuesInForms() {
        return includeNestedValuesInForms;
    }

    /** @return whether fields annotated with {@link Component} are injected after refresh. */
    public boolean injectAnnotatedFields() {
        return injectAnnotatedFields;
    }

    /** @return whether an absent root panel causes refresh to fail. */
    public boolean failOnMissingRootPanel() {
        return failOnMissingRootPanel;
    }

    /** @return whether lookup methods refresh the index before resolving an id. */
    public boolean autoRefreshOnLookup() {
        return autoRefreshOnLookup;
    }

    /** @return maximum component and panel traversal depth. */
    public int maximumTraversalDepth() {
        return maximumTraversalDepth;
    }

    /** @return duplicate-id policy. */
    public DuplicateComponentPolicy duplicatePolicy() {
        return duplicatePolicy;
    }

    /** @return unsupported value policy. */
    public UnsupportedValuePolicy unsupportedValuePolicy() {
        return unsupportedValuePolicy;
    }

    /** @return form-value representation mode. */
    public ComponentValueMode valueMode() {
        return valueMode;
    }

    /** Builder for {@link ComponentAccessorOptions}. */
    public static final class Builder {
        private boolean traverseNestedComponents = true;
        private boolean includeNestedValuesInForms;
        private boolean injectAnnotatedFields = true;
        private boolean failOnMissingRootPanel;
        private boolean autoRefreshOnLookup;
        private int maximumTraversalDepth = 128;
        private DuplicateComponentPolicy duplicatePolicy = DuplicateComponentPolicy.REPLACE;
        private UnsupportedValuePolicy unsupportedValuePolicy = UnsupportedValuePolicy.EMPTY_STRING;
        private ComponentValueMode valueMode = ComponentValueMode.STRING;

        private Builder() {
        }

        /**
         * Enables or disables recursive traversal of Swing containers and constructor composites.
         *
         * @param value {@code true} to index nested named controls.
         * @return this builder.
         */
        public Builder traverseNestedComponents(boolean value) {
            this.traverseNestedComponents = value;
            return this;
        }

        /**
         * Controls whether nested controls are included in collected form values.
         *
         * @param value {@code true} to include nested values.
         * @return this builder.
         */
        public Builder includeNestedValuesInForms(boolean value) {
            this.includeNestedValuesInForms = value;
            return this;
        }

        /**
         * Controls annotation-driven field injection.
         *
         * @param value {@code true} to inject fields after refresh.
         * @return this builder.
         */
        public Builder injectAnnotatedFields(boolean value) {
            this.injectAnnotatedFields = value;
            return this;
        }

        /**
         * Controls whether a missing root panel fails construction or produces an empty index.
         *
         * @param value {@code true} for strict root-panel validation.
         * @return this builder.
         */
        public Builder failOnMissingRootPanel(boolean value) {
            this.failOnMissingRootPanel = value;
            return this;
        }

        /**
         * Enables refresh before every lookup operation.
         *
         * @param value {@code true} for live lookup behavior.
         * @return this builder.
         */
        public Builder autoRefreshOnLookup(boolean value) {
            this.autoRefreshOnLookup = value;
            return this;
        }

        /**
         * Sets the traversal-depth guard used for component and panel graphs.
         *
         * @param value positive maximum depth.
         * @return this builder.
         */
        public Builder maximumTraversalDepth(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("maximumTraversalDepth must be positive");
            }
            this.maximumTraversalDepth = value;
            return this;
        }

        /**
         * Sets duplicate-id handling.
         *
         * @param value duplicate policy.
         * @return this builder.
         */
        public Builder duplicatePolicy(DuplicateComponentPolicy value) {
            this.duplicatePolicy = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Sets handling for controls without a registered value adapter.
         *
         * @param value unsupported-value policy.
         * @return this builder.
         */
        public Builder unsupportedValuePolicy(UnsupportedValuePolicy value) {
            this.unsupportedValuePolicy = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Sets native or string form-value representation.
         *
         * @param value value mode.
         * @return this builder.
         */
        public Builder valueMode(ComponentValueMode value) {
            this.valueMode = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Builds immutable options.
         *
         * @return configured options.
         */
        public ComponentAccessorOptions build() {
            return new ComponentAccessorOptions(this);
        }
    }
}
