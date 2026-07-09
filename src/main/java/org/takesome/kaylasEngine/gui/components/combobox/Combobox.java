package org.takesome.kaylasEngine.gui.components.combobox;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentFactory;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

/**
 * Responsive custom combobox component used by KaylasUIEngine layouts.
 *
 * <p>The component keeps painting pure: paintComponent only draws the current state, while size and
 * position changes happen during explicit state transitions. This avoids repaint loops caused by
 * mutating Swing geometry from paint code.</p>
 */
@SuppressWarnings("unused")
public class Combobox extends JComponent implements MouseListener, MouseMotionListener {
    private static final String EMPTY_VALUE = "";
    private static final String[] EMPTY_VALUES = new String[0];
    private static final int ICON_SIZE = 24;
    private static final int TEXT_LEFT_X = 10;
    private static final int TEXT_WITH_ICON_X = 42;
    private static final int FALLBACK_ROW_HEIGHT = 24;
    private static final long HOVER_NOTIFY_INTERVAL_NANOS = 50_000_000L;
    private static final String SOUND_CATEGORY = "combobox";
    private static final String SOUND_OPEN = "comboboxOpen";
    private static final String SOUND_CLOSE = "comboboxClose";

    private final ComponentFactory componentFactory;
    private final Engine engine;
    private final int initialY;
    private final Map<String, BufferedImage> scaledTextureCache = new ConcurrentHashMap<>();

    private Color color;
    private Color hoverColor;
    private BufferedImage[] icons;
    private String[] values = EMPTY_VALUES;
    private volatile ComboboxState state = ComboboxState.CLOSED;
    private volatile int selected = 0;
    private volatile int hover = -1;
    private volatile boolean createdNotificationSent;
    private volatile long lastHoverNotificationNanos;
    private volatile int lastNotifiedHover = -1;

    private BufferedImage defaultTX;
    private BufferedImage openedTX;
    private BufferedImage rolloverTX;
    private BufferedImage selectedTX;
    private BufferedImage panelTX;
    private BufferedImage point;
    private ComboboxListener comboboxListener;

    public Combobox(ComponentFactory componentFactory, String[] values, int initialY) {
        this.componentFactory = componentFactory;
        this.engine = componentFactory.getEngine();
        this.values = normalizeValues(values);
        this.initialY = initialY;
        initializeInteraction();
    }

    public Combobox(ComponentFactory componentFactory, int initialY) {
        this(componentFactory, EMPTY_VALUES, initialY);
    }

    private void initializeInteraction() {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(true);
        setDoubleBuffered(true);
        addMouseListener(this);
        addMouseMotionListener(this);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                closeCombobox();
            }
        });
        installKeyboardActions();
    }

    private void installKeyboardActions() {
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "combobox.toggle");
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "combobox.toggle");
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "combobox.close");
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "combobox.up");
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "combobox.down");

        getActionMap().put("combobox.toggle", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (state == ComboboxState.OPENED && isValidIndex(hover)) {
                    selected = hover;
                }
                toggleCombobox();
            }
        });
        getActionMap().put("combobox.close", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                closeCombobox();
            }
        });
        getActionMap().put("combobox.up", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                moveHover(-1);
            }
        });
        getActionMap().put("combobox.down", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                moveHover(1);
            }
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        updateBoundsForState();
        notifyCreatedOnce();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            if (!isEnabled()) {
                Composite originalComposite = g.getComposite();
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
                drawDefaultState(g, getWidth());
                g.setComposite(originalComposite);
                return;
            }

            int width = getWidth();
            switch (state) {
                case OPENED -> drawOpenedState(g, width);
                case ROLLOVER -> drawRolloverState(g, width);
                default -> drawDefaultState(g, width);
            }
        } finally {
            g.dispose();
        }
    }

    private void drawOpenedState(Graphics2D g, int width) {
        if (!hasValues()) {
            state = ComboboxState.CLOSED;
            drawDefaultState(g, width);
            return;
        }

        int rowHeight = rowHeight();
        for (int i = 0; i < values.length; i++) {
            drawPanel(g, i, rowHeight);
        }
        drawButton(g, openedTX, width, rowHeight, rowHeight * values.length, colorOrDefault(color));
    }

    private void drawRolloverState(Graphics2D g, int width) {
        drawButton(g, rolloverTX, width, rowHeight(), 0, colorOrDefault(hoverColor));
    }

    private void drawDefaultState(Graphics2D g, int width) {
        drawButton(g, defaultTX, width, rowHeight(), 0, colorOrDefault(color));
    }

    private void drawButton(Graphics2D g, BufferedImage texture, int width, int height, int yOffset, Color textColor) {
        if (texture != null) {
            g.drawImage(scaledTexture("button", texture, width, height), 0, yOffset, width, height, this);
        } else {
            g.setColor(new Color(0, 0, 0, 90));
            g.fillRoundRect(0, yOffset, width, height, 8, 8);
        }
        g.setColor(textColor);
        drawSelectedValue(g, height, yOffset);
    }

    private void drawSelectedValue(Graphics2D g, int rowHeight, int yOffset) {
        String text = selectedValue();
        int textX = TEXT_LEFT_X;
        BufferedImage icon = selectedIcon();
        if (icon != null) {
            int iconY = yOffset + Math.max(0, (rowHeight - ICON_SIZE) / 2);
            g.drawImage(icon, TEXT_LEFT_X, iconY, ICON_SIZE, ICON_SIZE, this);
            textX = TEXT_WITH_ICON_X;
        }
        FontMetrics metrics = g.getFontMetrics();
        int textY = yOffset + (rowHeight + metrics.getAscent() - metrics.getDescent()) / 2;
        g.drawString(text, textX, textY);
    }

    private void drawPanel(Graphics2D g, int index, int rowHeight) {
        if (!isValidIndex(index)) {
            return;
        }

        int y = rowHeight * index;
        BufferedImage currentPanel = hover == index ? selectedTX : panelTX;
        if (currentPanel != null) {
            g.drawImage(scaledTexture("panel-" + (hover == index ? "selected" : "normal"), currentPanel, getWidth(), rowHeight), 0, y, getWidth(), rowHeight, this);
        } else {
            g.setColor(hover == index ? new Color(255, 255, 255, 35) : new Color(0, 0, 0, 70));
            g.fillRect(0, y, getWidth(), rowHeight);
        }

        if (index == selected && point != null) {
            int pointX = Math.max(TEXT_LEFT_X, getWidth() - point.getWidth() - TEXT_LEFT_X);
            int pointY = y + Math.max(0, (rowHeight - point.getHeight()) / 2);
            g.drawImage(point, pointX, pointY, this);
        }

        int textX = TEXT_LEFT_X;
        BufferedImage icon = iconAt(index);
        if (icon != null) {
            int iconY = y + Math.max(0, (rowHeight - ICON_SIZE) / 2);
            g.drawImage(icon, TEXT_LEFT_X, iconY, ICON_SIZE, ICON_SIZE, this);
            textX = TEXT_WITH_ICON_X;
        }

        FontMetrics metrics = g.getFontMetrics();
        int textY = y + (rowHeight + metrics.getAscent() - metrics.getDescent()) / 2;
        g.setColor(colorOrDefault(color));
        g.drawString(values[index], textX, textY);
    }

    private BufferedImage scaledTexture(String role, BufferedImage source, int width, int height) {
        String key = role + ':' + width + 'x' + height + ':' + System.identityHashCode(source);
        return scaledTextureCache.computeIfAbsent(key, ignored -> engine.getImageUtils().genButton(width, height, source));
    }

    private BufferedImage selectedIcon() {
        return iconAt(selected);
    }

    private BufferedImage iconAt(int index) {
        return icons != null && index >= 0 && index < icons.length ? icons[index] : null;
    }

    private void updateBoundsForState() {
        int rowHeight = rowHeight();
        int targetHeight = state == ComboboxState.OPENED && hasValues()
                ? rowHeight * (values.length + 1)
                : rowHeight;
        int targetY = state == ComboboxState.OPENED && hasValues()
                ? initialY + rowHeight - targetHeight
                : initialY;
        if (getY() != targetY || getHeight() != targetHeight) {
            super.setBounds(getX(), targetY, Math.max(1, getWidth()), targetHeight);
            revalidate();
        }
    }

    private void openCombobox() {
        if (!hasValues()) {
            closeCombobox();
            return;
        }
        bringToFront();
        requestFocusInWindow();
        hover = selected;
        state = ComboboxState.OPENED;
        updateBoundsForState();
        notifyOpened();
        engine.emitSound(SOUND_CATEGORY, SOUND_OPEN);
        repaint();
    }

    private void closeCombobox() {
        boolean wasOpened = state == ComboboxState.OPENED;
        state = containsMouse() ? ComboboxState.ROLLOVER : ComboboxState.CLOSED;
        hover = hasValues() ? selected : -1;
        updateBoundsForState();
        if (wasOpened) {
            notifyClosed();
            engine.emitSound(SOUND_CATEGORY, SOUND_CLOSE);
        }
        repaint();
    }

    private void toggleCombobox() {
        if (state == ComboboxState.OPENED) {
            closeCombobox();
        } else {
            openCombobox();
        }
    }

    private void moveHover(int delta) {
        if (!hasValues()) {
            return;
        }
        if (state != ComboboxState.OPENED) {
            openCombobox();
            return;
        }
        int base = isValidIndex(hover) ? hover : selected;
        int next = Math.max(0, Math.min(values.length - 1, base + delta));
        updateHover(next);
    }

    private void updateHover(int newHover) {
        if (!isValidIndex(newHover) || newHover == hover) {
            return;
        }
        hover = newHover;
        notifyHoverDebounced(newHover);
        repaint();
    }

    private void notifyCreatedOnce() {
        if (createdNotificationSent || comboboxListener == null) {
            return;
        }
        createdNotificationSent = true;
        ComboboxListener listener = comboboxListener;
        SwingUtilities.invokeLater(() -> listener.onComboboxCreated(this));
    }

    private void notifyOpened() {
        ComboboxListener listener = comboboxListener;
        if (listener != null) {
            listener.onComboboxOpen(this);
        }
    }

    private void notifyClosed() {
        ComboboxListener listener = comboboxListener;
        if (listener != null) {
            listener.onComboboxClose(this);
        }
    }

    private void notifyHoverDebounced(int hoverIndex) {
        ComboboxListener listener = comboboxListener;
        if (listener == null) {
            return;
        }
        long now = System.nanoTime();
        if (hoverIndex == lastNotifiedHover && now - lastHoverNotificationNanos < HOVER_NOTIFY_INTERVAL_NANOS) {
            return;
        }
        lastNotifiedHover = hoverIndex;
        lastHoverNotificationNanos = now;
        engine.getExecutorServiceProvider().submitTask(() -> listener.onComboboxHover(this, hoverIndex), "comboboxHover");
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (!isEnabled() || e.getButton() != MouseEvent.BUTTON1) {
            return;
        }
        if (state == ComboboxState.OPENED && isValidIndex(hover)) {
            selected = hover;
        }
        toggleCombobox();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if (!isEnabled()) {
            return;
        }
        if (state != ComboboxState.OPENED) {
            state = ComboboxState.ROLLOVER;
            hover = -1;
            if (hasValues()) {
                engine.emitSound("button", "hover");
            }
            repaint();
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        Point point = e.getPoint();
        if (point != null && contains(point)) {
            return;
        }
        closeCombobox();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (!isEnabled() || state != ComboboxState.OPENED || !hasValues()) {
            return;
        }
        int rowHeight = rowHeight();
        int newHover = rowHeight <= 0 ? -1 : e.getY() / rowHeight;
        updateHover(newHover);
    }

    public int getSelectedIndex() {
        return selected;
    }

    public int getHoverIndex() {
        return hover;
    }

    public String getValue() {
        return selectedValue();
    }

    public void setSelectedIndex(int index) {
        if (isValidIndex(index)) {
            selected = index;
            hover = index;
            repaint();
        }
    }

    public void setValues(String[] values) {
        this.values = normalizeValues(values);
        if (!hasValues()) {
            selected = 0;
            hover = -1;
            state = ComboboxState.CLOSED;
        } else if (selected >= this.values.length) {
            selected = 0;
            hover = 0;
        }
        updateBoundsForState();
        repaint();
    }

    public void bringToFront() {
        if (getParent() != null) {
            getParent().setComponentZOrder(this, 0);
            getParent().repaint();
        }
    }

    public void setIcons(BufferedImage[] icons) {
        this.icons = icons;
        repaint();
    }

    public boolean isOpened() {
        return state == ComboboxState.OPENED;
    }

    public void setComboboxListener(ComboboxListener comboboxListener) {
        this.comboboxListener = comboboxListener;
        notifyCreatedOnce();
    }

    /** Compatibility alias for older launcher code that used scroll-box naming. */
    public void setScrollBoxListener(ComboboxListener comboboxListener) {
        setComboboxListener(comboboxListener);
    }

    public void setLoaded(boolean loaded) {
        this.createdNotificationSent = loaded;
    }

    public void setDefaultTX(BufferedImage defaultTX) {
        this.defaultTX = defaultTX;
        clearTextureCache();
        updateBoundsForState();
    }

    public void setOpenedTX(BufferedImage openedTX) {
        this.openedTX = openedTX;
        clearTextureCache();
    }

    public void setRolloverTX(BufferedImage rolloverTX) {
        this.rolloverTX = rolloverTX;
        clearTextureCache();
    }

    public void setSelectedTX(BufferedImage selectedTX) {
        this.selectedTX = selectedTX;
        clearTextureCache();
    }

    public void setPanelTX(BufferedImage panelTX) {
        this.panelTX = panelTX;
        clearTextureCache();
    }

    public void setPoint(BufferedImage point) {
        this.point = point;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public void setHoverColor(Color hoverColor) {
        this.hoverColor = hoverColor;
    }

    public String[] getValues() {
        return values;
    }

    public BufferedImage getOpenedTX() {
        return openedTX;
    }

    public ComboboxState getComboboxState() {
        return state;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            state = ComboboxState.CLOSED;
            updateBoundsForState();
        }
        repaint();
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, state == ComboboxState.OPENED ? getY() : y, width, height);
        updateBoundsForState();
    }

    private int rowHeight() {
        if (defaultTX != null && defaultTX.getHeight() > 0) {
            return defaultTX.getHeight();
        }
        if (openedTX != null && openedTX.getHeight() > 0) {
            return openedTX.getHeight();
        }
        return getHeight() > 0 ? getHeight() : FALLBACK_ROW_HEIGHT;
    }

    private String selectedValue() {
        return hasValues() && selected >= 0 && selected < values.length ? values[selected] : EMPTY_VALUE;
    }

    private boolean hasValues() {
        return values != null && values.length > 0;
    }

    private boolean isValidIndex(int index) {
        return hasValues() && index >= 0 && index < values.length;
    }

    private String[] normalizeValues(String[] sourceValues) {
        return sourceValues == null ? EMPTY_VALUES : sourceValues;
    }

    private Color colorOrDefault(Color input) {
        if (input != null) {
            return input;
        }
        return componentFactory.getStyle() != null && componentFactory.getStyle().getColor() != null
                ? hexToColor(componentFactory.getStyle().getColor())
                : Color.WHITE;
    }

    private boolean containsMouse() {
        Point mousePosition;
        try {
            mousePosition = getMousePosition();
        } catch (IllegalStateException ex) {
            return false;
        }
        return mousePosition != null && contains(mousePosition);
    }

    private void clearTextureCache() {
        scaledTextureCache.clear();
    }
}
