package org.takesome.kaylasEngine;

import com.formdev.flatlaf.FlatIntelliJLaf;
import com.google.gson.Gson;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.takesome.kaylasEngine.config.Config;
import org.takesome.kaylasEngine.discord.Discord;
import org.takesome.kaylasEngine.events.EventBus;
import org.takesome.kaylasEngine.events.EventDispatchResult;
import org.takesome.kaylasEngine.events.SoundEvent;
import org.takesome.kaylasEngine.gui.*;
import org.takesome.kaylasEngine.gui.components.ComponentFactoryListener;
import org.takesome.kaylasEngine.gui.components.frame.FocusStatusListener;
import org.takesome.kaylasEngine.gui.components.frame.FrameConstructor;
import org.takesome.kaylasEngine.gui.components.frame.OptionGroups;
import org.takesome.kaylasEngine.gui.components.panel.PanelVisibility;
import org.takesome.kaylasEngine.gui.styles.StyleProvider;
import org.takesome.kaylasEngine.locale.LanguageProvider;
import org.takesome.kaylasEngine.news.News;
import org.takesome.kaylasEngine.service.ExecutorServiceProvider;
import org.takesome.kaylasEngine.sound.Sound;
import org.takesome.kaylasEngine.utils.*;
import org.takesome.kaylasEngine.utils.Crypt.CryptUtils;
import org.takesome.kaylasEngine.gui.loadingManager.LoadingManager;
import org.takesome.kaylasEngine.utils.hook.BiHookSet;
import org.takesome.kaylasEngine.utils.request.RequestClient;
import org.fusesource.jansi.AnsiConsole;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unused")
/**
 * Base abstract engine class for KaylasEngine applications.
 * <p>
 * The Engine encapsulates common application subsystems: configuration, GUI builder, sound subsystem,
 * background task provider, loading manager, localization, and more. The class is intended to be
 * subclassed by a concrete application implementation (for example, `Launcher`).
 * </p>
 *
 * <h3>Main responsibilities</h3>
 * <ul>
 *   <li>Initialize and hold common services (ExecutorServiceProvider, FontUtils, ImageUtils, etc.).</li>
 *   <li>Manage application lifecycle and safe shutdown.</li>
 *   <li>Provide APIs for working with the GUI (GuiBuilder, FrameConstructor, PanelVisibility).</li>
 *   <li>Utilities for showing dialogs and restarting the application.</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> The Engine is not fully thread-safe for arbitrary access. Public methods that
 * modify the UI or interact with Swing must be called from the EDT. For background work, use
 * {@link #getExecutorServiceProvider()} and {@link #safeSubmitTask}.</p>
 */
public abstract class Engine implements ActionListener, GuiBuilderListener, FocusStatusListener {
    /** Background task provider. */
    private final ExecutorServiceProvider executorServiceProvider;

    /** Unified HTTP/WS/WSS request dispatcher. */
    private final RequestClient requestClient;

    /** Central event bus for reliable in-process event delivery. */
    private final EventBus eventBus;

    /** File properties and path helpers. */
    protected final FileProperties fileProperties;

    /** Operating system MXBean. */
    private final OperatingSystemMXBean osBean;

    /** Current OS identifier string. */
    public static String currentOS = "";

    /** Loading indicator manager. */
    protected LoadingManager loadingManager;

    /** Map of configuration files passed to the constructor. */
    private Map<String, Class<?>> configFiles = new HashMap<>();

    /** Application title (brand + version). */
    private final String appTitle;

    /** Sound subsystem. */
    protected Sound SOUND;

    /** Application configuration object. */
    protected Config config;

    /** Localization provider. */
    protected LanguageProvider LANG;

    protected ServerInfo serverInfo;

    protected ImageUtils imageUtils;

    private News news;

    /** Shared engine logger (static for convenient access). */
    public static Logger LOGGER;

    protected Discord discord;

    /** Font utilities. */
    private final FontUtils FONTUTILS;

    private IconUtils iconUtils;

    protected CryptUtils CRYPTO;

    protected FrameConstructor frameConstructor;

    private final PanelVisibility panelVisibility;

    private GuiBuilder guiBuilder;

    private StyleProvider styleProvider;

    private EngineData engineData;

    /** External action handler. */
    public ActionHandler actionHandler;

    /** Initialization flag. */
    protected final AtomicBoolean initialized = new AtomicBoolean(false);

    private final EngineInfo engineInfo;

    private final BiHookSet<Void, Void> preInitHooks = new BiHookSet<>();
    private final BiHookSet<Void, Void> postInitHooks = new BiHookSet<>();
    private final BiHookSet<String, Object> customHooks = new BiHookSet<>();

    /**
     * Engine constructor.
     *
     * @param poolSize    thread pool size for {@link ExecutorServiceProvider}.
     * @param worker      worker thread name (used in thread naming).
     * @param configFiles map of configuration files (name -> class) — may be {@code null}.
     */
    public Engine(int poolSize, String worker, Map<String, Class<?>> configFiles) {
        currentOS = OS.determineCurrentOS();
        osBean = ManagementFactory.getOperatingSystemMXBean();
        this.engineData = new EngineData();
        if(configFiles!=null) {
            this.configFiles = configFiles;
        }

        InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("buildInfo.json")),
                StandardCharsets.UTF_8
        );
        this.engineInfo = new Gson().fromJson(reader, EngineInfo.class);
        setEngineData(engineData.initEngineValues("engine.json"));
        fileProperties = new FileProperties(this);
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", engineData.getLogLevel());
        LOGGER = LogManager.getLogger(this.getClass());
        AnsiConsole.systemInstall();

        Runtime.getRuntime().addShutdownHook(new Thread(AnsiConsole::systemUninstall, "ansi-console-shutdown"));
        appTitle = engineData.getLauncherBrand() + '-' + engineData.getLauncherVersion();
        this.panelVisibility = new PanelVisibility(this);

        logEngineInfoBox(
                LOGGER,
                this.engineInfo.getEngineBrand(),
                this.engineInfo.getEngineVersion(),
                appTitle,
                currentOS,
                osBean,
                engineData.getLogLevel(),
                configFiles
        );


        this.FONTUTILS = new FontUtils(this);
        setLogLevel(Level.valueOf(engineData.getLogLevel()));
        executorServiceProvider = new ExecutorServiceProvider(poolSize, worker);
        eventBus = new EventBus(executorServiceProvider.getExecutorService(), LOGGER);
        requestClient = new RequestClient(this);

        this.imageUtils = new ImageUtils();
        FlatIntelliJLaf.setup();

        //Basic Components Initialisation
        this.LANG = new LanguageProvider(this, fileProperties.getLocaleFile(), 0);
        this.SOUND = new Sound(this, getClass().getClassLoader().getResourceAsStream(fileProperties.getSoundsFile()));
        this.frameConstructor = new FrameConstructor(this);
        this.CRYPTO = new CryptUtils();

        preInit();
        init();
    }

    /**
     * Sets the logging level for the engine logger.
     *
     * @param level logging level.
     */
    public void setLogLevel(Level level) {
        Configurator.setLevel(LOGGER.getName(), level);
        LOGGER.info("Log level set to " + level);
    }

    protected void buildGui(ComponentFactoryListener componentFactoryListener) {
        setStyleProvider(new StyleProvider(this.engineData.getStyles()));
        setGuiBuilder(new GuiBuilder(this));
        getGuiBuilder().getComponentFactory().setComponentFactoryListener(componentFactoryListener);
        getGuiBuilder().addGuiBuilderListener(this);
        getGuiBuilder().buildGuiAsync(fileProperties.getFrameTpl(), getFrame().getRootPanel());
        this.setIconUtils(new IconUtils(this));
    }

    public static void logEngineInfoBox(
            Logger LOGGER,
            String engineBrand,
            String engineVersion,
            String appTitle,
            String currentOS,
            OperatingSystemMXBean osBean,
            String logLevel,
            Map<String, ?> configFiles // nullable
    ) {
        String header = String.format(
                "%s \n— %s",
                engineBrand != null ? engineBrand : "Unknown Engine",
                appTitle != null ? appTitle : "Untitled"
        );

        // Prepare lines
        List<String> lines = new ArrayList<>();
        lines.add("===== Kayla's Initialization =====");
        lines.add(String.format("Engine Version: %s", engineVersion));
        lines.add(String.format("Operating System: %s", currentOS));
        if (osBean != null) {
            lines.add(String.format("Available Processors: %d", osBean.getAvailableProcessors()));
            lines.add(String.format("System Load Average: %.2f", osBean.getSystemLoadAverage()));
        }
        lines.add(String.format("Log Level: %s", logLevel));
        if (configFiles != null && !configFiles.isEmpty()) {
            lines.add(String.format("Configuration Files: %s", configFiles.keySet()));
        }

        // Compute max width
        int max = 0;
        for (String l : lines) if (l.length() > max) max = l.length();
        int padding = 2; // left + right inner padding
        int innerWidth = max + padding * 2;

        // Box drawing
        String top    = "╔" + "═".repeat(innerWidth) + "╗\n";
        StringBuilder middle = new StringBuilder(lines.size() * (innerWidth + 4));
        String bottom = "╚" + "═".repeat(innerWidth) + "╝";

        for (String l : lines) {
            String padded = " ".repeat(padding) + l + " ".repeat(innerWidth - padding - l.length());
            middle.append("\u2551").append(padded).append("\u2551\n");
        }
        String box = top + middle + bottom;
        LOGGER.info("\n" + box + "\n" + header );
    }


    /**
     * Abstract method for initializing the concrete engine implementation.
     * Implement application-specific initialization (load services, GUI, etc.).
     */
    public abstract void init();

    /**
     * Called before the main initialization (`init`). Use for early setup.
     */
    protected abstract void preInit();

    /**
     * Called after the main initialization (`init`). Use for actions that must run
     * after panels are built and main services loaded.
     */
    protected abstract void postInit();

    @Override
    public abstract void onPanelsBuilt();

    @Override
    public abstract void onPanelBuild(Map<String, OptionGroups> panels, String componentGroup, Container parentPanel);

    @Override
    public abstract void actionPerformed(ActionEvent e);

    /**
     * Loads the main GUI panel asynchronously.
     *
     * @param path path to the GUI description (for example XML or JSON) handled by GuiBuilder.
     */
    protected void loadMainPanel(String path) {
        this.guiBuilder.buildGuiAsync(path, this.getFrame().getRootPanel());
        if (!initialized.get()) {
            this.postInit();
        }
    }

    /**
     * Returns the path to the running jar/class of the application.
     * May return {@code null} if URI decoding fails.
     *
     * @return application path or {@code null}.
     */
    public String appPath() {
        try {
            return URLDecoder.decode(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath(),StandardCharsets.UTF_8);
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }

    /**
     * Restarts the application with the specified Xmx and JVM located in the given directory.
     * Starts a new process and terminates the current one.
     *
     * @param xmx    maximum heap size in megabytes.
     * @param jvmDir JVM directory relative to runtime (used to compose the path).
     */
    public void restartApplication(int xmx, String jvmDir) {
        String path = this.config.getFullPath();
        List<String> params = new LinkedList<>();
        params.add(path + "/runtime/"+ jvmDir + "/bin/java");
        params.add("-Xmx"+xmx+"M");
        params.add("-jar");
        params.add(appPath().substring(1));

        ProcessBuilder builder = new ProcessBuilder(params);
        builder.redirectErrorStream(true);
        builder.directory(new File(path + File.separator));
        try {
            builder.start();
            shutdownExecutorService();
            System.exit(0);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Restart Error occurred \n PLease try again" + e, "Restart Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Shows a dialog with a localized message and optionally terminates the application.
     * Always runs on the EDT.
     *
     * @param messageKey    localization key for the message.
     * @param errorTitle    dialog title.
     * @param warningMessage message type (for example {@link JOptionPane#ERROR_MESSAGE}).
     * @param terminate     if {@code true} — {@code System.exit(0)} will be called after dialog closes.
     */
    public void showDialog(String messageKey, String errorTitle, int warningMessage, boolean terminate) {
        SwingUtilities.invokeLater(() -> {
            String errorMessage = this.getLANG().getString(messageKey);
            this.emitSound("other", messageKey);
            UIManager.put("OptionPane.messageFont", this.getFONTUTILS().getFont("mcfont", 12.0F));
            JOptionPane.showMessageDialog(this.getFrame().getRootPane(), errorMessage, errorTitle, warningMessage);
            if (terminate) {
                System.exit(0);
            }
        });
    }

    /**
     * Internal class used to deserialize engine info from buildInfo.json.
     */
    private static class EngineInfo {
        private String engineVersion, engineBrand;

        public String getEngineVersion() {
            return engineVersion;
        }

        public String getEngineBrand() {
            return engineBrand;
        }
    }

    /**
     * Method to update window focus status — must be implemented by subclasses.
     *
     * @param hasFocus {@code true} if the window has focus.
     */
    public abstract void updateFocus(boolean hasFocus);

    /**
     * Shuts down the {@link ExecutorServiceProvider} gracefully, waiting for tasks to finish.
     * Forces shutdown on timeout.
     */
    public void shutdownExecutorService(){
        executorServiceProvider.shutdown();
    }

    /**
     * Wrapper for submitting tasks safely to the executor — logs exceptions thrown by tasks.
     *
     * @param task     task to execute.
     * @param taskName task name (used for logging).
     */
    protected void safeSubmitTask(Runnable task, String taskName) {
        this.getExecutorServiceProvider().submitTask(() -> {
            try {
                task.run();
            } catch (Exception e) {
                getLOGGER().error("Task error -  " + taskName, e);
            }
        }, taskName);
    }

    /**
     * Returns the map of configuration files passed to the constructor.
     *
     * @return map (may be empty but never {@code null}).
     */
    public Map<String, Class<?>> getConfigFiles() {
        return configFiles;
    }

    /**
     * Checks whether the engine has been initialized.
     *
     * @return {@code true} if initialization has completed.
     */
    protected boolean isInit() {
        return initialized.get();
    }

    /**
     * Returns the main application frame constructor.
     *
     * @return {@link FrameConstructor} — interface for interacting with the main window.
     */
    public FrameConstructor getFrame() {
        return this.frameConstructor;
    }

    /**
     * Returns the GUI builder used by the engine.
     *
     * @return current {@link GuiBuilder}.
     */
    public GuiBuilder getGuiBuilder() {
        return guiBuilder;
    }

    /**
     * Returns the global engine logger.
     *
     * @return logger.
     */
    public static Logger getLOGGER() {
        return LOGGER;
    }

    /**
     * Returns the localization provider.
     *
     * @return {@link LanguageProvider}.
     */
    public LanguageProvider getLANG() {
        return LANG;
    }

    /**
     * Returns the font utilities.
     *
     * @return {@link FontUtils}.
     */
    public FontUtils getFONTUTILS() {
        return FONTUTILS;
    }

    /**
     * Returns the GUI style provider.
     *
     * @return {@link StyleProvider} or {@code null} if not set.
     */
    public StyleProvider getStyleProvider() {
        return styleProvider;
    }

    /**
     * Returns the sound subsystem.
     *
     * @return {@link Sound}.
     */
    public Sound getSOUND() {
        return SOUND;
    }

    /**
     * Returns the engine data (engine.json etc.).
     *
     * @return {@link EngineData}.
     */
    public EngineData getEngineData() {
        return engineData;
    }

    /**
     * Sets the GUI style provider.
     *
     * @param styleProvider style provider.
     */
    public void setStyleProvider(StyleProvider styleProvider) {
        this.styleProvider = styleProvider;
    }

    /**
     * Replaces the {@link EngineData} instance (used during initialization).
     *
     * @param engineData new engine data.
     */
    public void setEngineData(EngineData engineData) {
        this.engineData = engineData;
    }

    /**
     * Returns server information.
     *
     * @return {@link ServerInfo} or {@code null}.
     */
    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    /**
     * Returns Discord integration if configured.
     *
     * @return {@link Discord} or {@code null}.
     */
    public Discord getDiscord() {
        return discord;
    }

    /**
     * Returns the application title (brand-version).
     *
     * @return application title.
     */
    public String getAppTitle() {
        return appTitle;
    }

    /**
     * Returns the panel visibility manager.
     *
     * @return {@link PanelVisibility}.
     */
    public PanelVisibility getPanelVisibility() {
        return panelVisibility;
    }

    /**
     * Sets an external action handler.
     *
     * @param actionHandler an instance of {@link ActionHandler}.
     */
    public void setActionHandler(ActionHandler actionHandler) {
        this.actionHandler = actionHandler;
    }

    /**
     * Sets the GUI builder used by the engine.
     *
     * @param guiBuilder an instance of {@link GuiBuilder}.
     */
    public void setGuiBuilder(GuiBuilder guiBuilder) {
        this.guiBuilder = guiBuilder;
    }

    /**
     * Marks initialization state.
     *
     * @param init {@code true} if the engine is initialized.
     */
    public void setInit(boolean init) {
        this.initialized.set(init);
    }

    /**
     * Returns the loading manager.
     *
     * @return {@link LoadingManager} or {@code null}.
     */
    public LoadingManager getLoadingManager() {
        return loadingManager;
    }

    /**
     * Sets news to be displayed in the UI.
     *
     * @param news a {@link News} instance.
     */
    public void setNews(News news) {
        this.news = news;
    }

    /**
     * Returns the news object.
     *
     * @return {@link News} or {@code null}.
     */
    public News getNews() {
        return news;
    }

    /**
     * Returns image utilities.
     *
     * @return {@link ImageUtils}.
     */
    public ImageUtils getImageUtils() {
        return imageUtils;
    }

    /**
     * Returns cryptographic utilities.
     *
     * @return {@link CryptUtils} or {@code null}.
     */
    public CryptUtils getCRYPTO() {
        return CRYPTO;
    }

    /**
     * Returns the application configuration object.
     *
     * @return {@link Config}.
     */
    public Config getConfig() {
        return config;
    }

    /**
     * Returns parsed engine info from buildInfo.json.
     *
     * @return {@link EngineInfo}.
     */
    public EngineInfo getEngineInfo(){
        return this.engineInfo;
    }

    /**
     * Returns the Java OperatingSystemMXBean.
     *
     * @return {@link OperatingSystemMXBean}.
     */
    public OperatingSystemMXBean getOsBean() {
        return osBean;
    }

    /**
     * Returns the background task provider.
     *
     * @return {@link ExecutorServiceProvider}.
     */
    public ExecutorServiceProvider getExecutorServiceProvider() {
        return executorServiceProvider;
    }

    /**
     * Returns the unified request dispatcher.
     *
     * @return request client with HTTP, WS and WSS providers.
     */
    public RequestClient getRequestClient() {
        return requestClient;
    }

    /**
     * Returns the central event bus.
     *
     * @return event bus with delivery tracking and dead-letter diagnostics.
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Publishes a bundled sound event through the central event bus.
     *
     * @param category sound category.
     * @param subCategory sound subcategory.
     * @return dispatch result with delivery tracking id.
     */
    public EventDispatchResult emitSound(String category, String subCategory) {
        return eventBus.publish(SoundEvent.of(this, category, subCategory));
    }

    public EventDispatchResult emitSound(String category, String subCategory, boolean loop) {
        return eventBus.publish(SoundEvent.of(this, category, subCategory, loop));
    }

    /**
     * Returns the set of hooks executed before initialization.
     *
     * @return {@link BiHookSet} for preInit.
     */
    public BiHookSet<Void, Void> getPreInitHooks() {
        return preInitHooks;
    }

    /**
     * Returns the set of hooks executed after initialization.
     *
     * @return {@link BiHookSet} for postInit.
     */
    public BiHookSet<Void, Void> getPostInitHooks() {
        return postInitHooks;
    }

    /**
     * Returns a custom hook set for arbitrary events/data.
     *
     * @return {@link BiHookSet} for custom hooks.
     */
    public BiHookSet<String, Object> getCustomHooks() {
        return customHooks;
    }

    /**
     * Returns icon utilities.
     *
     * @return {@link IconUtils}.
     */
    public IconUtils getIconUtils() {
        return iconUtils;
    }

    public FileProperties getFileProperties() {
        return fileProperties;
    }

    /**
     * Sets the icon utilities.
     *
     * @param iconUtils {@link IconUtils}.
     */
    public void setIconUtils(IconUtils iconUtils) {
        this.iconUtils = iconUtils;
    }
}
