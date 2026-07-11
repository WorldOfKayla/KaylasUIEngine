package org.takesome.kaylasEngine.gui.lookAndFeel;

import com.formdev.flatlaf.FlatIntelliJLaf;
import org.takesome.kaylasEngine.gui.lookAndFeel.theme.KaylasTheme;
import org.takesome.kaylasEngine.gui.lookAndFeel.theme.KaylasThemes;

import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.Window;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * KaylasUIEngine Look and Feel built on FlatLaf and the engine's component language.
 */
public final class KaylasLookAndFeel extends FlatIntelliJLaf {
    /** Client property marking components created by the Kaylas LAF component set. */
    public static final String COMPONENT_PROPERTY = "kaylas.lookAndFeel.component";
    /** Client property containing the semantic component role. */
    public static final String ROLE_PROPERTY = "kaylas.lookAndFeel.role";
    private static final AtomicReference<KaylasTheme> ACTIVE_THEME =
            new AtomicReference<>(KaylasThemes.kineticaDark());

    private final KaylasTheme theme;

    /** Creates the Look and Feel using the active theme. */
    public KaylasLookAndFeel() {
        this(ACTIVE_THEME.get());
    }

    /** Creates the Look and Feel using an explicit theme. */
    public KaylasLookAndFeel(KaylasTheme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    /**
     * Installs the active theme.
     *
     * @return {@code true} when installation succeeded.
     */
    public static boolean setup() {
        return install(ACTIVE_THEME.get());
    }

    /**
     * Installs a theme and refreshes every displayable Swing window.
     *
     * @param theme theme to install.
     * @return {@code true} when installation succeeded.
     */
    public static boolean install(KaylasTheme theme) {
        Objects.requireNonNull(theme, "theme");
        KaylasTheme previous = ACTIVE_THEME.get();
        try {
            UIManager.setLookAndFeel(new KaylasLookAndFeel(theme));
            ACTIVE_THEME.set(theme);
            refreshOpenWindows();
            return true;
        } catch (UnsupportedLookAndFeelException error) {
            ACTIVE_THEME.set(previous);
            return false;
        }
    }

    /** @return currently selected engine theme. */
    public static KaylasTheme currentTheme() {
        return ACTIVE_THEME.get();
    }

    /** Re-applies the current Look and Feel to open windows. */
    public static void refreshOpenWindows() {
        Runnable refresh = () -> {
            for (Window window : Window.getWindows()) {
                if (window.isDisplayable()) {
                    SwingUtilities.updateComponentTreeUI(window);
                    window.invalidate();
                    window.validate();
                    window.repaint();
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            refresh.run();
        } else {
            SwingUtilities.invokeLater(refresh);
        }
    }

    @Override
    public String getName() {
        return "Kaylas Look and Feel";
    }

    @Override
    public String getID() {
        return "KaylasLaf";
    }

    @Override
    public String getDescription() {
        return "KINETICA Look and Feel for KaylasUIEngine";
    }

    @Override
    public UIDefaults getDefaults() {
        UIDefaults defaults = super.getDefaults();
        KaylasUIDefaults.apply(defaults, theme);
        return defaults;
    }
}
