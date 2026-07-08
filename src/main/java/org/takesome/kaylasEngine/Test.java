package org.takesome.kaylasEngine;

import org.takesome.kaylasEngine.gui.ComponentValue;
import org.takesome.kaylasEngine.gui.GuiBuilder;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.ComponentFactoryListener;
import org.takesome.kaylasEngine.gui.components.button.Button;
import org.takesome.kaylasEngine.gui.components.frame.OptionGroups;
import org.takesome.kaylasEngine.gui.components.multiButton.MultiButton;
import org.takesome.kaylasEngine.utils.environment.LaunchEnvironment;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Reference bootstrap for running the engine as a small local demo application.
 *
 * <p>This class is intentionally lightweight: it demonstrates the correct lifecycle wiring without
 * hiding errors behind empty methods. It is safe to keep in {@code src/main/java} because it compiles
 * as a real application entry point and exercises the same Engine path used by launchers.</p>
 */
public final class Test extends Engine {
    private static final int DEFAULT_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final String DEFAULT_WORKER_NAME = "kaylasengine-test";
    private static final String MAIN_PANEL_ID = "mainFrame";

    private static final LaunchEnvironment LAUNCH_ENVIRONMENT = LaunchEnvironment.detect();
    private DemoActionHandler demoActionHandler;

    public Test(int poolSize, String worker, Map<String, Class<?>> configFiles) {
        super(poolSize, worker, configFiles);
    }

    public static void main(String[] args) {
        Thread.currentThread().setName("kaylasengine-test-main");
        if (Arrays.asList(args).contains("--smoke")) {
            System.setProperty("kaylasengine.test.exitAfterInit", "true");
        }
        startSmokeWatchdogIfRequested();
        SwingUtilities.invokeLater(() -> new Test(DEFAULT_POOL_SIZE, DEFAULT_WORKER_NAME, null));
    }

    @Override
    public void init() {
        SwingUtilities.invokeLater(() -> {
            try {
                if (LAUNCH_ENVIRONMENT.isHeadless()) {
                    LOGGER.warn("Headless runtime detected; GUI bootstrap is skipped.");
                    postInit();
                    exitAfterSmokeTestIfRequested();
                    return;
                }

                if (getFrame().getRootPanel() == null) {
                    LOGGER.error("Test bootstrap cannot build GUI: root panel is null. Check engine.json frameTpl.");
                    return;
                }

                buildGui(new InitialValueResolver(this));

                String mainFrame = fileProperties.getMainFrame();
                if (hasText(mainFrame)) {
                    loadMainPanel(mainFrame);
                } else {
                    LOGGER.warn("Test bootstrap started without mainFrame resource. Frame template only mode is active.");
                    postInit();
                }
            } catch (Exception ex) {
                LOGGER.error("Test bootstrap initialization failed", ex);
            }
        });
    }

    @Override
    protected void preInit() {
        LOGGER.info("Test bootstrap environment: {}", LAUNCH_ENVIRONMENT.toLogLine());
        System.setProperty("AppDir", resolveApplicationDirectory());
        System.setProperty("RamAmount", String.valueOf(Runtime.getRuntime().maxMemory() / (1024L * 1024L)));
    }

    @Override
    protected void postInit() {
        installActionHandlerIfPossible();
        setInit(true);
    }

    @Override
    public void onPanelsBuilt() {
        installActionHandlerIfPossible();
        if (getFrame() != null) {
            getFrame().revalidate();
            getFrame().repaint();
        }
        setInit(true);
        LOGGER.info("Test bootstrap GUI panels are built.");
        exitAfterSmokeTestIfRequested();
    }

    @Override
    public void onAdditionalPanelBuild(JPanel panel) {
        if (panel != null) {
            LOGGER.debug("Additional panel built: {}", panel.getName());
        }
    }

    @Override
    public void onGuiBuilt() {
        LOGGER.info("Test bootstrap GUI build completed.");
    }

    @Override
    public void onPanelBuild(Map<String, OptionGroups> panels, String componentGroup, Container parentPanel) {
        LOGGER.debug("Panel built: group={}, parent={}", componentGroup, parentPanel != null ? parentPanel.getName() : "null");
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        if (event == null) {
            LOGGER.warn("Ignoring null ActionEvent");
            return;
        }
        if (actionHandler == null) {
            LOGGER.warn("Ignoring action '{}' because ActionHandler is not initialized yet", event.getActionCommand());
            return;
        }
        actionHandler.handleAction(event);
    }

    @Override
    public void updateFocus(boolean hasFocus) {
        LOGGER.debug("Test bootstrap focus changed: {}", hasFocus);
    }

    private void installActionHandlerIfPossible() {
        if (demoActionHandler != null) {
            return;
        }
        GuiBuilder builder = getGuiBuilder();
        if (builder == null) {
            LOGGER.debug("ActionHandler installation skipped: GuiBuilder is not ready yet.");
            return;
        }
        demoActionHandler = new DemoActionHandler(builder, MAIN_PANEL_ID, List.of(MultiButton.class, Button.class));
        setActionHandler(demoActionHandler);
        LOGGER.info("Test bootstrap ActionHandler installed for panel '{}'.", MAIN_PANEL_ID);
    }

    private static void startSmokeWatchdogIfRequested() {
        if (Boolean.getBoolean("kaylasengine.test.exitAfterInit")) {
            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(12_000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
                System.err.println("KaylasEngine smoke watchdog timeout; forcing JVM exit.");
                System.exit(2);
            }, "kaylasengine-smoke-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
        }
    }

    private void exitAfterSmokeTestIfRequested() {
        if (Boolean.getBoolean("kaylasengine.test.exitAfterInit")) {
            LOGGER.info("Smoke-test exit requested after successful GUI initialization.");
            shutdownExecutorService();
            getFrame().dispose();
            System.exit(0);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String resolveApplicationDirectory() {
        String appData = System.getenv("APPDATA");
        if (hasText(appData)) {
            return appData;
        }
        return Path.of(System.getProperty("user.home", "."), ".kaylasengine-test").toString();
    }

    /**
     * Resolves symbolic initial values declared in frame/component resources.
     */
    private static final class InitialValueResolver extends ComponentValue implements ComponentFactoryListener {
        private final Test test;
        private int createdComponents;

        private InitialValueResolver(Test test) {
            super(test);
            this.test = Objects.requireNonNull(test, "test");
        }

        @Override
        public void onComponentCreation(ComponentAttributes componentAttributes) {
            if (componentAttributes == null) {
                return;
            }
            if (componentAttributes.getInitialValue() != null) {
                setInitialData(componentAttributes);
            }
            createdComponents++;
        }

        @Override
        public void setInitialData(ComponentAttributes componentAttributes) {
            String token = String.valueOf(componentAttributes.getInitialValue())
                    .split("#", 2)[0]
                    .trim()
                    .toLowerCase(Locale.ROOT);

            switch (token) {
                case "version" -> componentAttributes.setInitialValue(test.getEngineData().getLauncherVersion());
                case "build" -> componentAttributes.setInitialValue(test.getEngineData().getLauncherBuild());
                case "brand" -> componentAttributes.setInitialValue(test.getEngineData().getLauncherBrand());
                case "os" -> componentAttributes.setInitialValue(currentOS);
                case "java" -> componentAttributes.setInitialValue(System.getProperty("java.version"));
                case "appdir" -> componentAttributes.setInitialValue(System.getProperty("AppDir"));
                case "memory", "ram" -> componentAttributes.setInitialValue(System.getProperty("RamAmount") + " MB");
                default -> {
                    // Keep custom literal values unchanged.
                }
            }
        }

        public int getCreatedComponents() {
            return createdComponents;
        }
    }

    /**
     * Minimal but real command registry used by the demo application.
     */
    private static final class DemoActionHandler extends org.takesome.kaylasEngine.gui.ActionHandler {
        private final Map<String, Consumer<ActionEvent>> commands = new ConcurrentHashMap<>();

        private DemoActionHandler(GuiBuilder guiBuilder, String panelId, List<Class<?>> componentTypes) {
            super(guiBuilder, panelId, componentTypes);
            registerDefaultCommands();
        }

        @Override
        public void handleAction(ActionEvent event) {
            if (event == null || event.getActionCommand() == null) {
                Engine.LOGGER.warn("Action event without command: {}", event);
                return;
            }
            executeCommand(event.getActionCommand(), event);
        }

        @Override
        public void registerCommand(String key, Consumer<ActionEvent> command) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Command key must not be blank");
            }
            commands.put(key, Objects.requireNonNull(command, "command"));
        }

        @Override
        public void unregisterCommand(String key) {
            if (key != null) {
                commands.remove(key);
            }
        }

        @Override
        public void executeCommand(String key, ActionEvent event) {
            Consumer<ActionEvent> command = commands.get(key);
            if (command == null) {
                Engine.LOGGER.warn("No command registered for action '{}'", key);
                return;
            }
            command.accept(event);
        }

        private void registerDefaultCommands() {
            registerCommand("closeButton", event -> shutdownApplication());
            registerCommand("exit", event -> shutdownApplication());
            registerCommand("hideButton", event -> engine.getFrame().setExtendedState(Frame.ICONIFIED));
            registerCommand("minimize", event -> engine.getFrame().setExtendedState(Frame.ICONIFIED));
            registerCommand("taskManager", event -> engine.getExecutorServiceProvider().getExecutorProgress().showTaskMgr());
        }

        private void shutdownApplication() {
            Engine.LOGGER.info("Test bootstrap shutdown requested.");
            engine.shutdownExecutorService();
            engine.getFrame().dispose();
            System.exit(0);
        }
    }
}
