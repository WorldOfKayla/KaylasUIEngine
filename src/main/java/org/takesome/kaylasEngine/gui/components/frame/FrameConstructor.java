package org.takesome.kaylasEngine.gui.components.frame;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.descriptor.XmlUiDescriptorLoader;
import org.takesome.kaylasEngine.gui.components.panel.Panel;
import org.takesome.kaylasEngine.locale.LanguageProvider;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.geom.RoundRectangle2D;
import java.lang.reflect.Field;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Top-level application frame constructor.
 *
 * <p>
 * Responsible for creating and configuring the main application JFrame using {@link FrameAttributes}
 * loaded from a template or provided directly. Provides focus tracking, shaped window support,
 * seasonal background support (via {@link Panel}) and centralized logging for important lifecycle events.
 * </p>
 */
public class FrameConstructor extends JFrame {
    private final Engine engine;
    private final XmlUiDescriptorLoader descriptorLoader;
    private FocusStatusListener focusStatusListener;
    private Panel panel;
    private Dimension screenSize;
    private JPanel rootPanel;
    private final LanguageProvider LANG;
    private boolean hasFocus;

    /**
     * Creates and initializes the application frame.
     *
     * <p>
     * This constructor immediately triggers the frame build process using the template path
     * configured in the engine's file properties.
     * </p>
     *
     * @param engine engine instance used for resources, localization and configuration; must not be {@code null}.
     */
    public FrameConstructor(Engine engine) {
        Engine.LOGGER.info("FrameConstructor: initialization started");
        this.engine = engine;
        this.descriptorLoader = new XmlUiDescriptorLoader();
        this.LANG = engine.getLANG();
        this.hasFocus = false;
        this.focusStatusListener = engine;
        // build frame from engine-provided template (may log errors if template missing/invalid)
        buildFrame(engine.getFileProperties().getFrameTpl());
        Engine.LOGGER.info("FrameConstructor: initialization completed");
    }

    /**
     * Loads FrameAttributes from the given resource path and builds the frame.
     *
     * <p>
     * This method logs the path being loaded and any errors encountered while reading or parsing the template.
     * </p>
     *
     * @param path resource path to the XML frame template (for example "assets/frame/frame.xml")
     */
    private void buildFrame(String path) {
        Engine.LOGGER.info("FrameConstructor: building AppFrame from '{}'", path);

        try {
            var attributes = descriptorLoader.load(path);
            if (!(attributes instanceof FrameAttributes frameAttributes)) {
                throw new IllegalArgumentException("XML frame root must be <frame>: " + path);
            }
            Engine.LOGGER.debug("FrameConstructor: parsed FrameAttributes: width={}, height={}, resizable={}, undecorated={}, borderRadius={}",
                    frameAttributes.getWidth(), frameAttributes.getHeight(),
                    frameAttributes.isResizable(), frameAttributes.isUndecorated(), frameAttributes.getBorderRadius());
            buildFrame(frameAttributes);
            Engine.LOGGER.info("FrameConstructor: frame successfully built from '{}'", path);
        } catch (Exception ex) {
            Engine.LOGGER.error("FrameConstructor: failed to build frame from '{}'", path, ex);
        }
    }

    /**
     * Configures the Swing {@link JFrame} using provided {@link FrameAttributes}.
     *
     * <p>
     * Sets icon, title, size, decoration, window shape (if border radius provided), centers on screen,
     * and installs the {@link Panel} root panel. Also sets the frame visible.
     * </p>
     *
     * @param frameAttributes attributes describing appearance and behavior of the frame.
     */
    public void buildFrame(FrameAttributes frameAttributes) {
        try {
            Engine.LOGGER.debug("FrameConstructor: applying FrameAttributes to JFrame");

            if (frameAttributes.getAppIcon() != null && !frameAttributes.getAppIcon().isEmpty()) {
                if (!frameAttributes.getAppIcon().endsWith(".svg")) {
                    Engine.LOGGER.debug("FrameConstructor: loading raster icon '{}'", frameAttributes.getAppIcon());
                    setIconImage(this.engine.getImageUtils().getLocalImage(frameAttributes.getAppIcon()));
                } else {
                    Engine.LOGGER.debug("FrameConstructor: loading SVG icon '{}'", frameAttributes.getAppIcon());
                    setIconImage(new FlatSVGIcon(frameAttributes.getAppIcon(), 1).getImage());
                }
            } else {
                Engine.LOGGER.warn("FrameConstructor: no app icon configured in FrameAttributes");
            }

            setTitle(LANG.getString(frameAttributes.getAppTitle()));
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(frameAttributes.getWidth(), frameAttributes.getHeight());
            setResizable(frameAttributes.isResizable());
            setUndecorated(frameAttributes.isUndecorated());

            if (frameAttributes.getBorderRadius() != 0) {
                Engine.LOGGER.debug("FrameConstructor: applying window shape with border radius {}", frameAttributes.getBorderRadius());
                this.setShape(new RoundRectangle2D.Double(
                        0, 0, getWidth(), getHeight(),
                        frameAttributes.getBorderRadius(),
                        frameAttributes.getBorderRadius()
                ));
            }

            screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int x = (screenSize.width - getWidth()) / 2;
            int y = (screenSize.height - getHeight()) / 2;
            setLocation(x, y);
            Engine.LOGGER.debug("FrameConstructor: window positioned at x={}, y={}, screenWidth={}, screenHeight={}", x, y, screenSize.width, screenSize.height);

            panel = new Panel(this);
            this.rootPanel = panel.setRootPanel(frameAttributes);
            this.rootPanel.setName("rootPanel");
            setContentPane(this.rootPanel);

            // ensure focus listeners are set up before showing the frame
            setupFocusListeners();

            setVisible(true);
            Engine.LOGGER.info("FrameConstructor: frame is visible (title='{}', size={}x{})", getTitle(), getWidth(), getHeight());
        } catch (Exception ex) {
            Engine.LOGGER.error("FrameConstructor: error while applying FrameAttributes", ex);
        }
    }

    /**
     * Installs a WindowFocusListener that updates the configured {@link FocusStatusListener}.
     *
     * <p>
     * This method logs listener setup and will report focus gained/lost events to the listener.
     * </p>
     */
    private void setupFocusListeners() {
        Engine.LOGGER.debug("FrameConstructor: setting up focus listeners");
        addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                Engine.LOGGER.debug("FrameConstructor: window gained focus event");
                onFrameFocusGained();
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                Engine.LOGGER.debug("FrameConstructor: window lost focus event");
                onFrameFocusLost();
            }
        });
    }

    /**
     * Internal handler for frame gaining focus.
     * Notifies the {@link FocusStatusListener} and logs the change.
     */
    private void onFrameFocusGained() {
        hasFocus = true;
        Engine.LOGGER.info("FrameConstructor: frame focus gained");
        if (focusStatusListener != null) {
            try {
                focusStatusListener.updateFocus(hasFocus);
            } catch (Exception ex) {
                Engine.LOGGER.error("FrameConstructor: error notifying focusStatusListener on focus gained", ex);
            }
        } else {
            Engine.LOGGER.warn("FrameConstructor: focusStatusListener is null when focus gained");
        }
    }

    /**
     * Internal handler for frame losing focus.
     * Notifies the {@link FocusStatusListener} and logs the change.
     */
    private void onFrameFocusLost() {
        hasFocus = false;
        Engine.LOGGER.info("FrameConstructor: frame focus lost");
        if (focusStatusListener != null) {
            try {
                focusStatusListener.updateFocus(hasFocus);
            } catch (Exception ex) {
                Engine.LOGGER.error("FrameConstructor: error notifying focusStatusListener on focus lost", ex);
            }
        } else {
            Engine.LOGGER.warn("FrameConstructor: focusStatusListener is null when focus lost");
        }
    }

    /**
     * Returns whether the frame currently has focus.
     *
     * @return {@code true} if frame has focus; {@code false} otherwise.
     */
    public boolean hasFocus() {
        return hasFocus;
    }

    /**
     * Returns the screen size where the frame was created.
     *
     * @return screen {@link Dimension}.
     */
    public Dimension getScreenSize() {
        return screenSize;
    }

    /**
     * Returns the root content panel that was installed on this frame.
     *
     * @return root {@link JPanel}.
     */
    public JPanel getRootPanel() {
        return this.rootPanel;
    }

    /**
     * Convenience accessor for the underlying {@link Engine} instance.
     *
     * @return engine.
     */
    public Engine getAppFrame() {
        return engine;
    }

    /**
     * Returns the internal {@link Panel} helper instance used by this frame.
     *
     * @return panel.
     */
    public Panel getPanel() {
        return panel;
    }

    /**
     * Changes frame size and logs the new dimensions.
     *
     * @param width  new width in pixels.
     * @param height new height in pixels.
     */
    public void setFrameSize(int width, int height) {
        Engine.LOGGER.info("FrameConstructor: resizing frame to {}x{}", width, height);
        this.setSize(width, height);
    }

    /**
     * Sets the listener that will be updated on window focus changes.
     *
     * @param focusStatusListener listener to notify when frame focus changes.
     */
    public void setFocusStatusListener(FocusStatusListener focusStatusListener) {
        Engine.LOGGER.debug("FrameConstructor: setting FocusStatusListener -> {}", focusStatusListener);
        this.focusStatusListener = focusStatusListener;
        setupFocusListeners();
    }
}
