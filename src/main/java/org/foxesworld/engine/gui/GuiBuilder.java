package org.foxesworld.engine.gui;

import org.foxesworld.engine.Engine;
import org.foxesworld.engine.gui.adapters.FrameAttributesLoader;
import org.foxesworld.engine.gui.adapters.FrameLoaderAdapters;
import org.foxesworld.engine.gui.components.Attributes;
import org.foxesworld.engine.gui.components.ComponentAttributes;
import org.foxesworld.engine.gui.components.ComponentFactory;
import org.foxesworld.engine.gui.components.frame.FrameConstructor;
import org.foxesworld.engine.gui.components.frame.OptionGroups;
import org.foxesworld.notification.Notification;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Advanced class for building graphical user interfaces.
 *
 * Provides synchronous and asynchronous GUI construction with support for registering additional loaders,
 * multiple event listeners, and cancellation of asynchronous tasks.
 */
public class GuiBuilder {
    private final FrameLoaderAdapters frameLoaderAdapters;
    private final FrameConstructor frameConstructor;
    private final ComponentFactory componentFactory;
    private final Notification notification;
    private final Engine engine;

    private final Map<String, JPanel> panelsMap = new ConcurrentHashMap<>();
    private final Map<String, List<JComponent>> componentsMap = new ConcurrentHashMap<>();
    private final Map<String, List<String>> childParentMap = new ConcurrentHashMap<>();
    private final Map<String, JPanel> loadPanels = new ConcurrentHashMap<>();
    private GuiBuilderListener guiBuilderListener;

    // Support for multiple GUI build listeners
    private final List<GuiBuilderListener> guiBuilderListeners = new CopyOnWriteArrayList<>();

    // Listener that will run once after all panels are built
    private Runnable onPanelsBuildTask;

    // Flag to ensure additional panels are built only once
    private final AtomicBoolean additionalPanelsBuilt = new AtomicBoolean(false);

    // Holds the current CompletableFuture for async build so it can be cancelled
    private volatile CompletableFuture<?> currentBuildFuture;

    /**
     * GUI builder constructor.
     *
     * @param engine Engine instance. If engine is null, an IllegalArgumentException is thrown.
     */
    public GuiBuilder(Engine engine) {
        if (engine == null) {
            throw new IllegalArgumentException("Engine не может быть null");
        }
        this.engine = engine;
        this.frameLoaderAdapters = new FrameLoaderAdapters(engine);
        this.frameConstructor = engine.getFrame();
        this.componentFactory = new ComponentFactory(engine);
        this.notification = new Notification();
        this.notification.setJFrame(this.frameConstructor);
        Engine.getLOGGER().debug("=== GUI BUILDER INITIATED ===");
    }

    /**
     * Synchronous GUI build.
     *
     * @param framePath path to the frame description file; must not be empty.
     * @param parent    parent panel into which components are added.
     */
    public void buildGui(String framePath, JPanel parent) {
        if (framePath == null || framePath.isEmpty()) {
            Engine.getLOGGER().error("Frame path is empty");
            return;
        }
        if (parent == null) {
            Engine.getLOGGER().error("Parent panel is null");
            return;
        }
        Attributes frameAttributes = loadFrameAttributes(framePath);
        if (frameAttributes == null) {
            Engine.getLOGGER().error("Failed to load frame attributes from: {}", framePath);
            return;
        }
        buildPanels(frameAttributes.getGroups(), parent);
        notifyPanelsBuilt();  // Called once after finishing building all panels
    }

    /**
     * Asynchronous GUI build.
     *
     * Frame attributes are loaded in a background thread while panel construction runs on the EDT.
     *
     * @param framePath path to the frame description file; must not be empty.
     * @param parent    parent panel into which components are added.
     */
    public void buildGuiAsync(String framePath, JPanel parent) {
        if (framePath == null || framePath.isEmpty()) {
            Engine.getLOGGER().error("Frame path is empty");
            return;
        }
        if (parent == null) {
            Engine.getLOGGER().error("Parent panel is null");
            return;
        }
        currentBuildFuture = CompletableFuture.supplyAsync(
                        () -> loadFrameAttributes(framePath),
                        engine.getExecutorServiceProvider().getExecutorService()
                )
                .thenAccept(attributes -> {
                    if (attributes == null) {
                        Engine.getLOGGER().error("Frame attributes are null for path: {}", framePath);
                        return;
                    }
                    SwingUtilities.invokeLater(() -> {
                        buildPanels(attributes.getGroups(), parent);
                        notifyPanelsBuilt(); // Called once after finishing building all panels
                    });
                })
                .exceptionally(ex -> {
                    Engine.getLOGGER().error("Error building GUI asynchronously", ex);
                    return null;
                });
    }

    /**
     * Cancels the asynchronous GUI build if it is still running.
     */
    public void cancelBuild() {
        if (currentBuildFuture != null && !currentBuildFuture.isDone()) {
            currentBuildFuture.cancel(true);
            Engine.getLOGGER().info("Asynchronous GUI build canceled.");
        }
    }

    /**
     * Registers a listener for GUI build events.
     *
     * @param listener listener.
     */
    public void addGuiBuilderListener(GuiBuilderListener listener) {
        guiBuilderListeners.add(listener);
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener listener.
     */
    public void removeGuiBuilderListener(GuiBuilderListener listener) {
        guiBuilderListeners.remove(listener);
    }

    /**
     * Sets a listener that will be called once after all panels have been built.
     *
     * @param task Runnable instance to be executed upon completion of the build.
     */
    public void setOnPanelsBuild(Runnable task) {
        this.onPanelsBuildTask = task;
    }

    /**
     * Allows registering a frame attributes loader for a new file type.
     *
     * @param fileExtension file extension (for example, "xml", "ini"); must not be empty.
     * @param loader        attributes loader.
     */
    public void registerFrameAttributesLoader(String fileExtension, FrameAttributesLoader loader) {
        if (fileExtension == null || fileExtension.isEmpty()) {
            Engine.getLOGGER().error("File extension is empty.");
            return;
        }
        frameLoaderAdapters.registerAdapter(fileExtension, loader);
        Engine.getLOGGER().debug("Registered new FrameAttributesLoader for extension: {}", fileExtension);
    }

    /**
     * Clears the internal state of the builder: panels map, components map, and parent-child relations.
     */
    public void resetState() {
        panelsMap.clear();
        componentsMap.clear();
        childParentMap.clear();
        loadPanels.clear();
        additionalPanelsBuilt.set(false);
        Engine.getLOGGER().info("GuiBuilder state has been reset.");
    }

    /**
     * Loads frame attributes depending on the file type.
     *
     * @param framePath path to the configuration file.
     * @return Attributes object for the frame, or null if loading failed.
     */
    private Attributes loadFrameAttributes(String framePath) {
        String fileType = getFileType(framePath);
        FrameAttributesLoader loader = frameLoaderAdapters.getLoader(fileType);
        if (loader == null) {
            Engine.getLOGGER().error("No loader found for file type: {}", fileType);
            return null;
        }
        return loader.getAttributes(framePath);
    }

    /**
     * Determines the file type by its extension.
     *
     * @param framePath path to the file.
     * @return string representing the file type.
     */
    private String getFileType(String framePath) {
        String lowerCasePath = framePath.toLowerCase(Locale.ROOT);
        if (lowerCasePath.endsWith(".json")) {
            return "json";
        } else if (lowerCasePath.endsWith(".json5")) {
            return "json5";
        } else if (lowerCasePath.endsWith(".yaml") || lowerCasePath.endsWith(".yml")) {
            return "yaml";
        } else if (lowerCasePath.endsWith(".fxml")) {
            return "fxml";
        }
        // Default extension
        return "json";
    }

    /**
     * Returns all child components for the specified parent panel name.
     *
     * @param parentPanelName name of the parent panel.
     * @return list of child components.
     */
    public List<Component> getAllChildComponents(String parentPanelName) {
        List<Component> components = new ArrayList<>();
        childParentMap.getOrDefault(parentPanelName, Collections.emptyList())
                .forEach(child -> components.addAll(componentsMap.getOrDefault(child, Collections.emptyList())));
        return components;
    }

    /**
     * Recursively builds panels.
     *
     * @param panels      map of panel groups; may be null.
     * @param parentPanel parent panel.
     */
    private void buildPanels(Map<String, OptionGroups> panels, JPanel parentPanel) {
        if (panels != null) {
            panels.forEach((componentGroup, optionGroups) -> {
                if (optionGroups == null) {
                    Engine.getLOGGER().warn("OptionGroups is null for component group: {}", componentGroup);
                    return;
                }
                JPanel thisPanel = createPanel(optionGroups, componentGroup);
                processChildComponents(optionGroups.getChildComponents(), thisPanel);
                addChildPanelIfNeeded(panels, parentPanel, thisPanel, componentGroup);
                buildPanels(optionGroups.getGroups(), thisPanel);
                updateChildParentMap(parentPanel, thisPanel);
            });
        }
    }

    /**
     * Creates a panel based on the given options.
     *
     * @param optionGroups   options for creating the panel.
     * @param componentGroup component group name.
     * @return created panel.
     */
    private JPanel createPanel(OptionGroups optionGroups, String componentGroup) {
        JPanel panel = frameConstructor.getPanel().createGroupPanel(optionGroups.getPanelOptions(), componentGroup, frameConstructor);
        panel.setName(componentGroup);
        panel.setVisible(optionGroups.getPanelOptions().isVisible());
        Container panelParent = panel.getParent();
        if (panelParent != null) {
            panelParent.setComponentZOrder(panel, optionGroups.getPanelOptions().getzIndex());
        } else {
            // Engine.getLOGGER().warn("Parent for panel {} is null!", panel.getName());
        }
        return panel;
    }

    /**
     * Processes child components and groups defined in the attributes.
     *
     * @param childComponents list of child ComponentAttributes; may be null.
     * @param parentPanel     parent panel.
     */
    private void processChildComponents(List<ComponentAttributes> childComponents, JPanel parentPanel) {
        if (childComponents == null) {
            return;
        }
        for (ComponentAttributes componentAttributes : childComponents) {
            if (componentAttributes == null) {
                Engine.getLOGGER().warn("Found null ComponentAttributes in parent panel: {}", parentPanel.getName());
                continue;
            }
            if (componentAttributes.getComponentType() != null) {
                addComponentToParent(componentAttributes, parentPanel);
            } else if (componentAttributes.getGroups() != null) {
                buildPanels(componentAttributes.getGroups(), parentPanel);
            } else if (componentAttributes.getReadFrom() != null) {
                processReadFromAttribute(componentAttributes, parentPanel);
            } else if (componentAttributes.getLoadPanel() != null && !componentAttributes.getLoadPanel().isEmpty()) {
                loadPanels.put(componentAttributes.getLoadPanel(), parentPanel);
            }
        }
    }

    /**
     * Creates and adds a component to the parent panel.
     *
     * @param componentAttributes component attributes.
     * @param parentPanel         parent panel.
     */
    private void addComponentToParent(ComponentAttributes componentAttributes, JPanel parentPanel) {
        JComponent component = componentFactory.createComponent(componentAttributes);
        if (component == null) {
            Engine.getLOGGER().warn("Component creation returned null for attributes: {}", componentAttributes);
            return;
        }
        if (component instanceof JPanel) {
            addPanelGroup(parentPanel, (JPanel) component);
            panelsMap.put(component.getName(), (JPanel) component);
        } else {
            parentPanel.add(component);
        }
        componentsMap.computeIfAbsent(parentPanel.getName(), k -> Collections.synchronizedList(new ArrayList<>())).add(component);
    }

    /**
     * Processes the readFrom attribute to load additional components or groups.
     *
     * @param componentAttributes component attributes.
     * @param parentPanel         parent panel.
     */
    private void processReadFromAttribute(ComponentAttributes componentAttributes, JPanel parentPanel) {
        Attributes frameAttributes = loadFrameAttributes(componentAttributes.getReadFrom());
        if (frameAttributes == null) {
            Engine.getLOGGER().error("Failed to load attributes from: {}", componentAttributes.getReadFrom());
            return;
        }
        if (frameAttributes.getGroups() == null && frameAttributes.getChildComponents() != null) {
            processChildComponents(frameAttributes.getChildComponents(), parentPanel);
        } else {
            buildGui(componentAttributes.getReadFrom(), parentPanel);
        }
    }

    /**
     * Adds a child panel to the parent if it does not already exist in the map.
     *
     * @param panels         map of panel groups.
     * @param parentPanel    parent panel.
     * @param childPanel     child panel.
     * @param componentGroup component group name.
     */
    private void addChildPanelIfNeeded(Map<String, OptionGroups> panels, JPanel parentPanel, JPanel childPanel, String componentGroup) {
        if (!panelsMap.containsKey(componentGroup)) {
            addPanelGroup(parentPanel, childPanel);
            notifyPanelBuild(panels, componentGroup, parentPanel);
        }
    }

    /**
     * Updates the parent-child relationship map.
     *
     * @param parentPanel parent panel.
     * @param childPanel  child panel.
     */
    private void updateChildParentMap(JPanel parentPanel, JPanel childPanel) {
        String parentName = parentPanel.getName();
        if (parentName == null || parentName.isEmpty()) {
            parentName = UUID.randomUUID().toString();
            parentPanel.setName(parentName);
        }
        childParentMap.computeIfAbsent(parentName, k -> new ArrayList<>()).add(childPanel.getName());
    }

    /**
     * Builds additional panels that were loaded separately.
     * This is performed only once.
     */
    public void buildAdditionalPanels() {
        if (additionalPanelsBuilt.compareAndSet(false, true)) {
            Engine.getLOGGER().debug("== BUILDING ADDITIONAL PANELS ==");
            loadPanels.forEach((key, value) -> {
                notifyAdditionalPanelBuild(value);
                Engine.getLOGGER().debug("Processing additional panel key: {}", key);
                JPanel loadingPanel = panelsMap.get(key);
                if (loadingPanel != null) {
                    addPanelGroup(value, loadingPanel);
                } else {
                    Engine.getLOGGER().warn("No panel found in panelsMap for key: {}", key);
                }
            });
        } else {
            Engine.getLOGGER().error("Additional panels are already built!");
        }
    }

    /**
     * Adds a child panel to a parent.
     *
     * @param parent parent panel.
     * @param child  child panel.
     */
    private void addPanelGroup(JPanel parent, JPanel child) {
        if (parent == null || child == null) {
            Engine.getLOGGER().warn("Cannot add panel group because parent or child is null");
            return;
        }
        parent.add(child);
        panelsMap.put(child.getName(), child);
        updateChildParentMap(parent, child);
    }

    /**
     * Notifies listeners that all panels have been built.
     */
    private void notifyPanelsBuilt() {
        for (GuiBuilderListener listener : guiBuilderListeners) {
            listener.onPanelsBuilt();
        }
        if (onPanelsBuildTask != null) {
            onPanelsBuildTask.run();
        }
    }

    /**
     * Notifies listeners that a specific panel has been built.
     *
     * @param panels         map of panel groups.
     * @param componentGroup component group name.
     * @param parentPanel    parent panel.
     */
    private void notifyPanelBuild(Map<String, OptionGroups> panels, String componentGroup, JPanel parentPanel) {
        for (GuiBuilderListener listener : guiBuilderListeners) {
            listener.onPanelBuild(panels, componentGroup, parentPanel);
        }
    }

    /**
     * Notifies listeners that an additional panel has been built.
     *
     * @param panel panel.
     */
    private void notifyAdditionalPanelBuild(JPanel panel) {
        for (GuiBuilderListener listener : guiBuilderListeners) {
            listener.onAdditionalPanelBuild(panel);
        }
    }

    /**
     * Returns the component map grouped by panel name.
     *
     * @return components map.
     */
    public Map<String, List<JComponent>> getComponentsMap() {
        return componentsMap;
    }

    /**
     * Adds a panel to the panels map.
     *
     * @param panel panel; must have a non-null name.
     */
    public void addPanelToMap(JPanel panel) {
        if (panel != null && panel.getName() != null) {
            this.panelsMap.put(panel.getName(), panel);
        } else {
            Engine.getLOGGER().warn("Panel or its name is null, cannot add to panelsMap");
        }
    }

    /**
     * Returns the panels map.
     *
     * @return panels map.
     */
    public Map<String, JPanel> getPanelsMap() {
        return panelsMap;
    }

    /**
     * Returns the parent-child relationship map for panels.
     *
     * @return parent-child map.
     */
    public Map<String, List<String>> getChildParentMap() {
        return childParentMap;
    }

    /**
     * Returns the Engine instance.
     *
     * @return engine.
     */
    public Engine getEngine() {
        return engine;
    }

    /**
     * Returns the frame loader adapters.
     *
     * @return frameLoaderAdapters.
     */
    public FrameLoaderAdapters getFrameLoaderAdapters() {
        return frameLoaderAdapters;
    }

    /**
     * Returns the Notification instance.
     *
     * @return notification.
     */
    public Notification getNotification() {
        return notification;
    }


    public void setGuiBuilderListener(GuiBuilderListener guiBuilderListener) {
        this.guiBuilderListener = guiBuilderListener;
    }

    /**
     * Returns the component factory.
     *
     * @return componentFactory.
     */
    public ComponentFactory getComponentFactory() {
        return componentFactory;
    }
}