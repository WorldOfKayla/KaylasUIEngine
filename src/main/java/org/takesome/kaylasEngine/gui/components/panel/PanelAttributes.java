package org.takesome.kaylasEngine.gui.components.panel;

import org.takesome.kaylasEngine.gui.components.Bounds;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Declarative panel options with transparent and absolute-layout defaults. */
@SuppressWarnings("unused")
public class PanelAttributes {
    private boolean opaque;
    private boolean visible = true;
    private boolean focusable;
    private boolean doubleBuffered = true;
    private int cornerRadius;
    private int zIndex;
    private String border = "";

    /** Legacy singular listener property. Prefer {@link #listeners}. */
    private String listener = "";

    /** Named panel listeners/behaviors installed by the engine registry. */
    private List<String> listeners = new ArrayList<>();

    private String background = "#00000000";
    private String backgroundImage;
    private String layout;
    private Bounds bounds = new Bounds(0, 0, 0, 0);

    public boolean isOpaque() {
        return opaque;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isFocusable() {
        return focusable;
    }

    public int getCornerRadius() {
        return Math.max(0, cornerRadius);
    }

    public String getBorder() {
        return border == null ? "" : border;
    }

    /**
     * Returns the legacy singular listener value.
     *
     * @deprecated use {@link #getListeners()} to support multiple listeners
     */
    @Deprecated
    public String getListener() {
        return listener == null ? "" : listener;
    }

    /**
     * Returns all configured listener names in declaration order without duplicates.
     *
     * <p>Both the legacy {@code listener} property and the new {@code listeners} collection are
     * accepted. Individual values may contain comma-, semicolon- or whitespace-separated names.</p>
     */
    public List<String> getListeners() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addListenerTokens(result, listener);
        if (listeners != null) {
            for (String configuredListener : listeners) {
                addListenerTokens(result, configuredListener);
            }
        }
        return List.copyOf(result);
    }

    public String getLayout() {
        return layout;
    }

    public boolean isDoubleBuffered() {
        return doubleBuffered;
    }

    public String getBackground() {
        return background == null || background.isBlank() ? "#00000000" : background;
    }

    public String getBackgroundImage() {
        return backgroundImage == null || backgroundImage.isBlank() ? null : backgroundImage;
    }

    public Bounds getBounds() {
        return bounds == null ? new Bounds(0, 0, 0, 0) : bounds;
    }

    public int getzIndex() {
        return Math.max(0, zIndex);
    }

    public void setOpaque(boolean opaque) {
        this.opaque = opaque;
    }

    private static void addListenerTokens(LinkedHashSet<String> target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String token : value.split("[,;\\s]+")) {
            if (!token.isBlank()) {
                target.add(token.trim());
            }
        }
    }
}
