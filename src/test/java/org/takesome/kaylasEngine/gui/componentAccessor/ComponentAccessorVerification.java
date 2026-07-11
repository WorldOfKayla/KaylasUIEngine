package org.takesome.kaylasEngine.gui.componentAccessor;

import org.apache.logging.log4j.LogManager;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.constructor.ConstructedCompositeComponent;
import org.takesome.kaylasEngine.gui.components.combobox.Combobox;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Executable regression verification for the component-accessor runtime. */
public final class ComponentAccessorVerification {
    private ComponentAccessorVerification() {
    }

    public static void main(String[] args) {
        System.setProperty("log.dir", System.getProperty("user.dir", "."));
        System.setProperty("log.level", "INFO");
        Engine.LOGGER = LogManager.getLogger(ComponentAccessorVerification.class);

        verifyTraversalLookupAndBinding();
        verifyValueModesAndRefresh();
        verifyCustomValueAdapter();
        verifyBuiltInComboboxAdapter();
        verifyDuplicatePolicy();
        verifyPackageArchitecture();

        System.out.println("Component Accessor Runtime verification passed.");
    }

    private static void verifyTraversalLookupAndBinding() {
        Fixture fixture = fixture();
        TestAccessor accessor = new TestAccessor(
                fixture.source(),
                ComponentAccessorOptions.defaults(),
                ComponentValueRegistry.defaults()
        );

        require(accessor.login == fixture.login(), "Inherited @Component field was not injected");
        require(accessor.remember == fixture.remember(), "Exact-id @Component field was not injected");
        require(accessor.slider == fixture.slider(), "Scoped constructor field was not injected");
        require(accessor.missing != null && accessor.missing.isEmpty(),
                "Optional missing field was not injected as Optional.empty()");

        require(accessor.requireLocal("volume", "slider", JSlider.class) == fixture.slider(),
                "Scoped lookup did not resolve volume.slider");
        require(accessor.getComponent("volume.slider") == fixture.slider(),
                "Nested component was not added to the global index");
        require(accessor.getComponentsForPanel("settings").equals(
                        List.of(fixture.login(), fixture.remember())),
                "Panel view leaked unselected nested components");

        IndexedComponent sliderMetadata = accessor.findIndexedComponent("volume.slider").orElseThrow();
        require(sliderMetadata.nested(), "Nested slider metadata is not marked nested");
        require(!sliderMetadata.selected(), "Unconfigured nested slider was marked selected");
        require("volume".equals(sliderMetadata.compositeScope()),
                "Composite scope metadata was not retained");
        require("slider".equals(sliderMetadata.localId()),
                "Composite local id metadata was not retained");

        Map<String, Object> values = accessor.getFormCredentials();
        require("Kayla".equals(values.get("login")), "Text value was not collected as string");
        require("true".equals(values.get("remember")), "Boolean value was not stringified");
        require("4".equals(values.get("threads")), "Child-panel spinner value was not collected");
        require(!values.containsKey("volume.slider"),
                "Nested slider leaked into compatibility form values");

        require(accessor.snapshot().size() >= 5, "Snapshot did not include named descendants");
    }

    private static void verifyValueModesAndRefresh() {
        Fixture fixture = fixture();
        ComponentAccessorOptions options = ComponentAccessorOptions.builder()
                .valueMode(ComponentValueMode.NATIVE)
                .includeNestedValuesInForms(true)
                .unsupportedValuePolicy(UnsupportedValuePolicy.SKIP)
                .build();
        ComponentsAccessor accessor = new ComponentsAccessor(
                fixture.source(),
                "settings",
                List.of(JTextField.class, JCheckBox.class, JSpinner.class, JSlider.class),
                options,
                ComponentValueRegistry.defaults()
        );

        require(Integer.valueOf(75).equals(accessor.readValue("volume.slider")),
                "Slider native value was not returned");
        accessor.writeValue("volume.slider", 82);
        require(fixture.slider().getValue() == 82, "Slider adapter did not write the value");

        Map<String, Object> values = accessor.collectNativeFormValuesForPanel("settings");
        require(Boolean.TRUE.equals(values.get("remember")),
                "Native checkbox value was not Boolean");
        require(Integer.valueOf(82).equals(values.get("volume.slider")),
                "Nested native slider value was not collected");

        long revision = accessor.getRevision();
        JTextField nickname = named(new JTextField("V"), "nickname");
        fixture.source().addComponent("settings", nickname);
        accessor.refresh();
        require(accessor.getRevision() == revision + 1, "Refresh did not advance revision");
        require(accessor.requireComponent("nickname", JTextField.class) == nickname,
                "Refresh did not observe a newly registered component");
    }

    private static void verifyCustomValueAdapter() {
        MutableSource source = new MutableSource();
        JPanel panel = named(new JPanel(), "labels");
        source.addPanel("labels", panel);
        JLabel status = named(new JLabel("ready"), "status");
        source.addComponent("labels", status);

        ComponentValueRegistry registry = ComponentValueRegistry.defaults()
                .registerWritable(
                        JLabel.class,
                        JLabel::getText,
                        (label, value) -> label.setText(String.valueOf(value)),
                        200
                );
        ComponentsAccessor accessor = new ComponentsAccessor(
                source,
                "labels",
                List.of(JLabel.class),
                ComponentAccessorOptions.builder()
                        .valueMode(ComponentValueMode.NATIVE)
                        .build(),
                registry
        );

        require("ready".equals(accessor.readValue("status")),
                "Custom JLabel adapter did not read text");
        accessor.writeValue("status", "online");
        require("online".equals(status.getText()), "Custom JLabel adapter did not write text");
    }

    private static void verifyBuiltInComboboxAdapter() {
        boolean registered = ComponentValueRegistry.defaults().adapters().stream()
                .anyMatch(adapter -> adapter.componentType().equals(Combobox.class));
        require(registered, "KaylasUI Combobox is missing from the default value registry");
    }

    private static void verifyDuplicatePolicy() {
        MutableSource source = new MutableSource();
        source.addPanel("duplicates", named(new JPanel(), "duplicates"));
        source.addComponent("duplicates", named(new JTextField("first"), "same"));
        source.addComponent("duplicates", named(new JTextField("second"), "same"));

        expectThrows(
                ComponentAccessException.class,
                () -> new ComponentsAccessor(
                        source,
                        "duplicates",
                        List.of(JTextField.class),
                        ComponentAccessorOptions.builder()
                                .duplicatePolicy(DuplicateComponentPolicy.FAIL)
                                .build(),
                        ComponentValueRegistry.defaults()
                ),
                "Strict duplicate policy accepted duplicate ids"
        );
    }

    private static Fixture fixture() {
        MutableSource source = new MutableSource();
        source.addPanel("settings", named(new JPanel(), "settings"));
        source.addPanel("advanced", named(new JPanel(), "advanced"));
        source.addChildPanel("settings", "advanced");
        source.addChildPanel("advanced", "settings"); // cycle guard regression

        JTextField login = named(new JTextField("Kayla"), "login");
        JCheckBox remember = named(new JCheckBox(), "remember");
        remember.setSelected(true);

        JPanel volume = named(new JPanel(), "volume");
        JSlider slider = named(new JSlider(0, 100, 75), "volume.slider");
        slider.putClientProperty(ConstructedCompositeComponent.SCOPE_PROPERTY, "volume");
        slider.putClientProperty(ConstructedCompositeComponent.LOCAL_ID_PROPERTY, "slider");
        volume.add(slider);

        JSpinner threads = named(new JSpinner(), "threads");
        threads.setValue(4);

        source.addComponent("settings", login);
        source.addComponent("settings", remember);
        source.addComponent("settings", volume);
        source.addComponent("advanced", threads);
        return new Fixture(source, login, remember, slider);
    }

    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name);
        return component;
    }

    private static void verifyPackageArchitecture() {
        requireHiddenImplementation(
                "org.takesome.kaylasEngine.gui.componentAccessor.internal.binding.ReflectionComponentFieldBinder"
        );
        requireHiddenImplementation(
                "org.takesome.kaylasEngine.gui.componentAccessor.internal.index.DefaultComponentGraphIndexer"
        );
        requireHiddenImplementation(
                "org.takesome.kaylasEngine.gui.componentAccessor.internal.source.GuiBuilderAccessSource"
        );
        requireHiddenImplementation(
                "org.takesome.kaylasEngine.gui.componentAccessor.internal.state.LockedComponentIndexState"
        );
        requireHiddenImplementation(
                "org.takesome.kaylasEngine.gui.componentAccessor.internal.value.DefaultComponentFormValueCollector"
        );

        require(ComponentAccessSource.class.isInterface(),
                "ComponentAccessSource must remain the public source boundary");
        requireDeprecatedCompatibilityType(
                "org.takesome.kaylasEngine.gui.componentAccessor.GuiBuilderComponentAccessSource"
        );

    }

    private static void requireDeprecatedCompatibilityType(String className) {
        try {
            Class<?> compatibilityType = Class.forName(className);
            require(compatibilityType.isAnnotationPresent(Deprecated.class),
                    "Compatibility type must be deprecated: " + className);
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException("Compatibility type not found: " + className, error);
        }
    }

    private static void requireHiddenImplementation(String className) {
        try {
            Class<?> implementation = Class.forName(className);
            require(!Modifier.isPublic(implementation.getModifiers()),
                    "Internal implementation is public: " + className);
            require(Modifier.isFinal(implementation.getModifiers()),
                    "Internal implementation must be final: " + className);
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException("Internal implementation not found: " + className, error);
        }
    }

    private static void expectThrows(Class<? extends Throwable> expected,
                                     Runnable action,
                                     String message) {
        try {
            action.run();
        } catch (Throwable error) {
            if (expected.isInstance(error)) {
                return;
            }
            throw new IllegalStateException(message + ": " + error.getClass().getName(), error);
        }
        throw new IllegalStateException(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Fixture(
            MutableSource source,
            JTextField login,
            JCheckBox remember,
            JSlider slider
    ) {
    }

    private static class BaseAccessor extends ComponentsAccessor {
        @Component
        protected JTextField login;

        private BaseAccessor(ComponentAccessSource source,
                             ComponentAccessorOptions options,
                             ComponentValueRegistry registry) {
            super(
                    source,
                    "settings",
                    List.of(JTextField.class, JCheckBox.class, JSpinner.class),
                    options,
                    registry
            );
        }
    }

    private static final class TestAccessor extends BaseAccessor {
        @Component("remember")
        private JCheckBox remember;

        @Component(scope = "volume", localId = "slider")
        private JSlider slider;

        @Component(value = "missing", required = false)
        private Optional<JComponent> missing;

        private TestAccessor(ComponentAccessSource source,
                             ComponentAccessorOptions options,
                             ComponentValueRegistry registry) {
            super(source, options, registry);
        }
    }

    private static final class MutableSource implements ComponentAccessSource {
        private final Map<String, JPanel> panels = new LinkedHashMap<>();
        private final Map<String, List<JComponent>> components = new LinkedHashMap<>();
        private final Map<String, List<String>> children = new LinkedHashMap<>();

        private void addPanel(String id, JPanel panel) {
            panels.put(id, panel);
        }

        private void addComponent(String panelId, JComponent component) {
            components.computeIfAbsent(panelId, ignored -> new ArrayList<>()).add(component);
        }

        private void addChildPanel(String panelId, String childPanelId) {
            children.computeIfAbsent(panelId, ignored -> new ArrayList<>()).add(childPanelId);
        }

        @Override
        public Optional<JPanel> findPanel(String panelId) {
            return Optional.ofNullable(panels.get(panelId));
        }

        @Override
        public List<JComponent> components(String panelId) {
            return List.copyOf(components.getOrDefault(panelId, List.of()));
        }

        @Override
        public List<String> childPanels(String panelId) {
            return List.copyOf(children.getOrDefault(panelId, List.of()));
        }
    }
}
