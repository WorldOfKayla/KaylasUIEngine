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
    private Color color, hoverColor;
    private BufferedImage[] icons;
    private volatile boolean loaded = false;
    private final ComponentFactory componentFactory;
    private final Engine engine;
    private DropBoxListener dropBoxListener;
    private String[] values;
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
        this.values = values;
        this.initialY = initialY;
        this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setupListeners();
    }

    public DropBox(ComponentFactory componentFactory, int initialY) {
        this.componentFactory = componentFactory;
        this.engine = componentFactory.getEngine();
        this.initialY = initialY;
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
            return;
        }
        int width = getWidth();

        // Optimize state handling by using a method to handle the drawing
        switch (state) {
            case OPENED -> drawOpenedState(g, width);
            case ROLLOVER -> drawRolloverState(g, width);
            default -> drawDefaultState(g, width);
        }
        g.dispose();

        // Call listener once to notify about component creation
        if (!loaded) {
            this.engine.getExecutorServiceProvider().submitTask(() -> {
                if (dropBoxListener != null) {
                    dropBoxListener.onScrollBoxCreated(this);
                }
                loaded = true;
            }, "dropBoxPaint");
        }
        g.setColor(hexToColor(componentFactory.getStyle().getColor()));
    }


    private void drawOpenedState(Graphics2D g, int width) {
        int height = openedTX.getHeight();
        g.drawImage(this.engine.getImageUtils().genButton(width, height, openedTX), 0, getHeight() - height, width, height, null);
        int rightHeight = height * (values.length + 1);
        int rightY = initialY + height - rightHeight;

        updateComponentSizeAndLocation(rightY, rightHeight);

        for (int i = 0; i < values.length; i++) {
            drawPanel(g, i);
            if (i == selected) {
                g.drawImage(point, 205, panelTX.getHeight() * i + 10, this);
            }
        }
        g.setColor(color);
        g.drawString(values[selected], 10, height * (values.length + 1) - g.getFontMetrics().getHeight() / 2 - 5);
    }

    private void drawRolloverState(Graphics2D g, int width) {
        int height = rolloverTX.getHeight();
        updateComponentSizeAndLocation(initialY, height);

        g.drawImage(this.engine.getImageUtils().genButton(width, height, rolloverTX), 0, 0, width, height, null);
        g.setColor(hoverColor);
        g.drawString(values[selected], 10, height - g.getFontMetrics().getHeight() / 2 - 5);
    }

    private void drawDefaultState(Graphics2D g, int width) {
        int height = defaultTX.getHeight();
        updateComponentSizeAndLocation(initialY, height);

        g.drawImage(this.engine.getImageUtils().genButton(width, height, defaultTX), 0, 0, width, height, null);
        g.drawString(values[selected], 10, height - g.getFontMetrics().getHeight() / 2 - 5);
    }

    private void drawPanel(Graphics2D g, int index) {
        BufferedImage currentPanel = (hover == index) ? selectedTX : panelTX;
        g.drawImage(currentPanel, 0, panelTX.getHeight() * index, this);

        String text = values[index];
        FontMetrics metrics = g.getFontMetrics();
        int textX = 10;
        int textY = selectedTX.getHeight() * (index + 1) - metrics.getHeight() / 2 - 5;
        g.setColor(color);
        g.drawString(text, textX, textY);

        if (icons != null && index < icons.length && icons[index] != null) {
            int iconX = textX + metrics.stringWidth(text) + 10;
            int iconY = panelTX.getHeight() * index + (panelTX.getHeight() - 24) / 2;
            g.drawImage(icons[index], iconX, iconY, 24, 24, this);
        }
    }


    private void updateComponentSizeAndLocation(int y, int height) {
        if (getY() != y || getHeight() != height) {
            setLocation(getX(), y);
            setSize(getWidth(), height);
        }
    }

    private void closeDropBox() {
        lock.lock();
        try {
            state = State.CLOSED;
            hover = selected;
            this.engine.getFrame().repaint();
            dropBoxListener.onScrollBoxClose(this);
            repaint();
        } finally {
            lock.unlock();
        }
        if(state == State.OPENED) {
            this.engine.emitSound("dropBox", "dropBoxClose");
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (!isEnabled() || e.getButton() != MouseEvent.BUTTON1) {
            return;
        }
        bringToFront();
        grabFocus();

        lock.lock();
        try {
            if (state == State.OPENED && (hover >= 0 && hover < values.length)) {
                selected = hover;
            }

            if (state == State.OPENED) {
                dropBoxListener.onScrollBoxOpen(this);
                componentFactory.getEngine().emitSound("dropBox", "dropBoxOpen");
            } else {
                dropBoxListener.onScrollBoxClose(this);
                componentFactory.getEngine().emitSound("dropBox", "dropBoxClose");
            }

            state = (state == State.OPENED) ? State.CLOSED : State.OPENED;
            repaint();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if(isEnabled()) {
            if (state != State.OPENED) {
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
        if (!isEnabled() || state != State.OPENED) {
            return;
        }
        int newY = e.getY();
        int newHover = (state == State.OPENED) ? (newY / openedTX.getHeight()) : (newY / defaultTX.getHeight());

        if (values.length > 1) {
            if (newHover >= 0 && newHover < values.length && newHover != hover) {
                hover = newHover;
                // Trigger hover event processing if state is OPENED
                if (state == State.OPENED && dropBoxListener != null) {
                    this.engine.getExecutorServiceProvider().submitTask(() -> dropBoxListener.onServerHover(this, newHover), "dropBoxHover");
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
        return (values.length > selected) ? values[selected] : values[0];
    }

    public void setSelectedIndex(int i) {
        if (i >= 0 && i < values.length) {
            selected = i;
            repaint();
        }
    }

    public void setValues(String[] values) {
        this.values = values;
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
}
