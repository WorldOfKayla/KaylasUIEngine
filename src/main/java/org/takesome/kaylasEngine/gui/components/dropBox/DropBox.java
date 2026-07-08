package org.takesome.kaylasEngine.gui.components.dropBox;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.locks.ReentrantLock;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

@SuppressWarnings("unused")
public class DropBox extends JComponent implements MouseListener, MouseMotionListener {
    private static final String EMPTY_VALUE = "";
    private static final String[] EMPTY_VALUES = new String[0];
    private static final int ICON_SIZE = 24;
    private static final int TEXT_LEFT_X = 10;
    private static final int TEXT_WITH_ICON_X = 42;

    private Color color, hoverColor;
    private BufferedImage[] icons;
    private volatile boolean loaded = false;
    private final ComponentFactory componentFactory;
    private final Engine engine;
    private DropBoxListener dropBoxListener;
    private String[] values = EMPTY_VALUES;
    private final int initialY;
    private volatile State state = State.CLOSED;
    private volatile int selected = 0;
    private volatile int hover = -1;
    private BufferedImage defaultTX;
    private BufferedImage openedTX;
    private BufferedImage rolloverTX;
    private BufferedImage selectedTX;
    private BufferedImage panelTX;
    private BufferedImage point;
    private final ReentrantLock lock = new ReentrantLock();

    public DropBox(ComponentFactory componentFactory, String[] values, int initialY) {
        this.componentFactory = componentFactory;
        this.engine = componentFactory.getEngine();
        this.values = normalizeValues(values);
        this.initialY = initialY;
        this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setupListeners();
    }

    public DropBox(ComponentFactory componentFactory, int initialY) {
        this.componentFactory = componentFactory;
        this.engine = componentFactory.getEngine();
        this.initialY = initialY;
        this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setupListeners();
    }

    private void setupListeners() {
        addMouseListener(this);
        addMouseMotionListener(this);
        setFocusable(true);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                closeDropBox();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics gmain) {
        Graphics2D g = (Graphics2D) gmain;
        if (!isEnabled()) {
            Composite originalComposite = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
            drawDefaultState(g, getWidth());
            g.setComposite(originalComposite);
            notifyCreatedOnce();
            return;
        }
        int width = getWidth();

        switch (state) {
            case OPENED -> drawOpenedState(g, width);
            case ROLLOVER -> drawRolloverState(g, width);
            default -> drawDefaultState(g, width);
        }

        g.setColor(hexToColor(componentFactory.getStyle().getColor()));
        notifyCreatedOnce();
    }

    private void notifyCreatedOnce() {
        if (loaded) {
            return;
        }
        loaded = true;
        this.engine.getExecutorServiceProvider().submitTask(() -> {
            DropBoxListener listener = dropBoxListener;
            if (listener != null) {
                listener.onScrollBoxCreated(this);
            }
        }, "dropBoxPaint");
    }

    private void drawOpenedState(Graphics2D g, int width) {
        if (!hasValues()) {
            state = State.CLOSED;
            drawDefaultState(g, width);
            return;
        }

        int height = openedTX.getHeight();
        g.drawImage(this.engine.getImageUtils().genButton(width, height, openedTX), 0, getHeight() - height, width, height, null);
        int rightHeight = height * (values.length + 1);
        int rightY = initialY + height - rightHeight;

        updateComponentSizeAndLocation(rightY, rightHeight);

        for (int i = 0; i < values.length; i++) {
            drawPanel(g, i);
            if (i == selected && point != null) {
                g.drawImage(point, 205, panelTX.getHeight() * i + 10, this);
            }
        }
        g.setColor(color);
        drawSelectedValue(g, height, height * values.length);
    }

    private void drawRolloverState(Graphics2D g, int width) {
        int height = rolloverTX.getHeight();
        updateComponentSizeAndLocation(initialY, height);

        g.drawImage(this.engine.getImageUtils().genButton(width, height, rolloverTX), 0, 0, width, height, null);
        g.setColor(hoverColor);
        drawSelectedValue(g, height, 0);
    }

    private void drawDefaultState(Graphics2D g, int width) {
        int height = defaultTX.getHeight();
        updateComponentSizeAndLocation(initialY, height);

        g.drawImage(this.engine.getImageUtils().genButton(width, height, defaultTX), 0, 0, width, height, null);
        g.setColor(color);
        drawSelectedValue(g, height, 0);
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
        g.drawString(text, textX, yOffset + rowHeight - g.getFontMetrics().getHeight() / 2 - 5);
    }

    private BufferedImage selectedIcon() {
        return icons != null && selected >= 0 && selected < icons.length ? icons[selected] : null;
    }

    private void drawPanel(Graphics2D g, int index) {
        if (!isValidIndex(index)) {
            return;
        }

        BufferedImage currentPanel = (hover == index) ? selectedTX : panelTX;
        g.drawImage(currentPanel, 0, panelTX.getHeight() * index, this);

        String text = values[index];
        FontMetrics metrics = g.getFontMetrics();
        int textX = TEXT_LEFT_X;
        int textY = selectedTX.getHeight() * (index + 1) - metrics.getHeight() / 2 - 5;

        if (icons != null && index < icons.length && icons[index] != null) {
            int iconY = panelTX.getHeight() * index + Math.max(0, (panelTX.getHeight() - ICON_SIZE) / 2);
            g.drawImage(icons[index], TEXT_LEFT_X, iconY, ICON_SIZE, ICON_SIZE, this);
            textX = TEXT_WITH_ICON_X;
        }

        g.setColor(color);
        g.drawString(text, textX, textY);
    }


    private void updateComponentSizeAndLocation(int y, int height) {
        if (getY() != y || getHeight() != height) {
            setLocation(getX(), y);
            setSize(getWidth(), height);
        }
    }

    private void closeDropBox() {
        boolean wasOpened;
        lock.lock();
        try {
            wasOpened = state == State.OPENED;
            state = State.CLOSED;
            hover = hasValues() ? selected : -1;
            this.engine.getFrame().repaint();
            notifyClosed();
            repaint();
        } finally {
            lock.unlock();
        }
        if (wasOpened) {
            this.engine.emitSound("dropBox", "dropBoxClose");
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (!isEnabled() || e.getButton() != MouseEvent.BUTTON1 || !hasValues()) {
            closeDropBox();
            return;
        }
        bringToFront();
        grabFocus();

        lock.lock();
        try {
            if (state == State.OPENED) {
                if (isValidIndex(hover)) {
                    selected = hover;
                }
                state = State.CLOSED;
                notifyClosed();
                componentFactory.getEngine().emitSound("dropBox", "dropBoxClose");
            } else {
                state = State.OPENED;
                notifyOpened();
                componentFactory.getEngine().emitSound("dropBox", "dropBoxOpen");
            }
            repaint();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if(isEnabled()) {
            if (state != State.OPENED && hasValues()) {
                componentFactory.getEngine().emitSound("button", "hover");
            }
            state = State.ROLLOVER;
            hover = -1;
            repaint();
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        closeDropBox();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        grabFocus();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (!isEnabled() || state != State.OPENED || !hasValues()) {
            return;
        }
        int newY = e.getY();
        int newHover = (state == State.OPENED) ? (newY / openedTX.getHeight()) : (newY / defaultTX.getHeight());

        if (values.length > 1) {
            if (newHover >= 0 && newHover < values.length && newHover != hover) {
                hover = newHover;
                if (state == State.OPENED) {
                    DropBoxListener listener = dropBoxListener;
                    if (listener != null) {
                        this.engine.getExecutorServiceProvider().submitTask(() -> listener.onServerHover(this, newHover), "dropBoxHover");
                    }
                }
                repaint();
            }
        } else {
            hover = 0;
            repaint();
        }
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

    public void setSelectedIndex(int i) {
        if (isValidIndex(i)) {
            selected = i;
            repaint();
        }
    }

    public void setValues(String[] values) {
        this.values = normalizeValues(values);
        if (!hasValues()) {
            selected = 0;
            hover = -1;
            state = State.CLOSED;
        } else if (selected >= this.values.length) {
            selected = 0;
            hover = -1;
        }
        repaint();
    }

    public void bringToFront() {
        if (getParent() != null) {
            getParent().setComponentZOrder(this, 0);
        }
    }

    public void setIcons(BufferedImage[] icons) {
        this.icons = icons;
        repaint();
    }


    public boolean isOpened() {
        return state == State.OPENED;
    }

    public void setScrollBoxListener(DropBoxListener dropBoxListener) {
        this.dropBoxListener = dropBoxListener;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public void setDefaultTX(BufferedImage defaultTX) {
        this.defaultTX = defaultTX;
    }

    public void setOpenedTX(BufferedImage openedTX) {
        this.openedTX = openedTX;
    }

    public void setRolloverTX(BufferedImage rolloverTX) {
        this.rolloverTX = rolloverTX;
    }

    public void setSelectedTX(BufferedImage selectedTX) {
        this.selectedTX = selectedTX;
    }

    public void setPanelTX(BufferedImage panelTX) {
        this.panelTX = panelTX;
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

    public State getState() {
        return state;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        repaint();
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

    private void notifyOpened() {
        DropBoxListener listener = dropBoxListener;
        if (listener != null) {
            listener.onScrollBoxOpen(this);
        }
    }

    private void notifyClosed() {
        DropBoxListener listener = dropBoxListener;
        if (listener != null) {
            listener.onScrollBoxClose(this);
        }
    }
}
