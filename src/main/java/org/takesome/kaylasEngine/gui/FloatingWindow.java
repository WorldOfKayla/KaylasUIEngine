package org.takesome.kaylasEngine.gui;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.utils.animation.AnimationManager;
import org.takesome.kaylasEngine.utils.animation.AnimationStats;

import javax.swing.*;
import java.awt.*;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

@SuppressWarnings("unused")
public abstract class FloatingWindow extends JWindow implements AnimationStats {

    private static final long UI_QUEUE_WARN_NANOS = 250_000_000L;
    private static final long BACKGROUND_CREATE_WARN_NANOS = 100_000_000L;
    private static final long BACKGROUND_SCALE_WARN_NANOS = 30_000_000L;

    protected Engine engine;
    protected JPanel backgroundPanel, basePanel;
    protected boolean animating;
    protected int FRAME_WIDTH = 500;
    protected int FRAME_HEIGHT = 150;
    protected int ANIMATION_DURATION = 300;
    protected int ANIMATION_SPEED = 50;
    protected AnimationManager animationManager;
    protected FloatingWindow(Engine engine){
        this.engine = engine;
        this.animationManager = new AnimationManager(this, getANIMATION_DURATION(), getANIMATION_SPEED());
        this.animationManager.setAnimationStats(this);
        this.setLocationRelativeTo(engine.getFrame());
        this.addFrameComponentListener();
    }

    protected abstract void initializeLoadingFrame();

    protected void createBackgroundPanel(JPanel basePanel, String image, String color) {
        long startedAt = System.nanoTime();
        if (basePanel == null) {
            Engine.getLOGGER().warn("[FLOATING-WINDOW] Cannot create background: base panel is null. image={}, color={}", image, color);
            return;
        }

        Engine.getLOGGER().debug(
                "[FLOATING-WINDOW] createBackgroundPanel start: basePanel={}, components={}, image={}, color={}, frame={}x{}",
                basePanel.getName(),
                basePanel.getComponentCount(),
                image,
                color,
                FRAME_WIDTH,
                FRAME_HEIGHT
        );

        basePanel.setBounds(basePanel.getX(), basePanel.getY(), FRAME_WIDTH, FRAME_HEIGHT);
        final Image sourceImage = engine.getImageUtils().getLocalImage(image);
        final Color overlayColor = hexToColor(color);

        backgroundPanel = new JPanel() {
            private Image cachedScaledImage;
            private int cachedWidth = -1;
            private int cachedHeight = -1;
            private long lastPaintWarnNanos = 0L;

            @Override
            protected void paintComponent(Graphics g) {
                long paintStartedAt = System.nanoTime();
                super.paintComponent(g);
                int width = getWidth();
                int height = getHeight();
                if (width <= 0 || height <= 0) {
                    return;
                }

                if (sourceImage != null) {
                    if (cachedScaledImage == null || cachedWidth != width || cachedHeight != height) {
                        long scaleStartedAt = System.nanoTime();
                        cachedScaledImage = engine.getImageUtils().getScaledImage(sourceImage, width, height);
                        cachedWidth = width;
                        cachedHeight = height;
                        long scaleElapsed = System.nanoTime() - scaleStartedAt;
                        if (scaleElapsed >= BACKGROUND_SCALE_WARN_NANOS) {
                            Engine.getLOGGER().warn(
                                    "[FLOATING-WINDOW] background scale slow: panel={}, size={}x{}, elapsed={} ms",
                                    getName(),
                                    width,
                                    height,
                                    nanosToMillis(scaleElapsed)
                            );
                        }
                    }
                    g.drawImage(cachedScaledImage, 0, 0, width, height, this);
                }

                if (overlayColor != null) {
                    g.setColor(overlayColor);
                    g.fillRect(0, 0, width, height);
                }

                long paintElapsed = System.nanoTime() - paintStartedAt;
                long now = System.nanoTime();
                if (paintElapsed >= BACKGROUND_SCALE_WARN_NANOS && now - lastPaintWarnNanos > 2_000_000_000L) {
                    lastPaintWarnNanos = now;
                    Engine.getLOGGER().warn(
                            "[FLOATING-WINDOW] background paint slow: panel={}, size={}x{}, elapsed={} ms",
                            getName(),
                            width,
                            height,
                            nanosToMillis(paintElapsed)
                    );
                }
            }
        };
        backgroundPanel.setLayout(basePanel.getLayout());
        backgroundPanel.setBounds(basePanel.getX(), basePanel.getY(), basePanel.getWidth(), basePanel.getHeight());
        backgroundPanel.setName(basePanel.getName());
        for (Component component : basePanel.getComponents()) {
            backgroundPanel.add(component);
        }
        setContentPane(backgroundPanel);

        long elapsed = System.nanoTime() - startedAt;
        if (elapsed >= BACKGROUND_CREATE_WARN_NANOS) {
            Engine.getLOGGER().warn(
                    "[FLOATING-WINDOW] createBackgroundPanel slow: panel={}, components={}, elapsed={} ms",
                    backgroundPanel.getName(),
                    backgroundPanel.getComponentCount(),
                    nanosToMillis(elapsed)
            );
        } else {
            Engine.getLOGGER().debug(
                    "[FLOATING-WINDOW] createBackgroundPanel done: panel={}, components={}, elapsed={} ms",
                    backgroundPanel.getName(),
                    backgroundPanel.getComponentCount(),
                    nanosToMillis(elapsed)
            );
        }
    }

    protected void addFrameComponentListener() {
        this.engine.getFrame().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentMoved(java.awt.event.ComponentEvent evt) {
                updateLoadingFramePosition();
            }
        });
    }

    protected void updateLoadingFramePosition() {
        long queuedAt = System.nanoTime();
        SwingUtilities.invokeLater(() -> {
            logUiQueueDelay("updateLoadingFramePosition", queuedAt);
            Point mainFrameCenter = getCenterPoint(this.engine.getFrame());
            setLocation(mainFrameCenter.x - getWidth() / 2, mainFrameCenter.y - getHeight() / 2);
        });
    }

    public Point getCenterPoint(JFrame frame) {
        int centerX = frame.getX() + frame.getWidth() / 2;
        int centerY = frame.getY() + frame.getHeight() / 2;
        return new Point(centerX, centerY);
    }

    public void animateLoadingWindow(boolean isEntry) {
        Engine.getLOGGER().debug("[FLOATING-WINDOW] animateLoadingWindow requested: entry={}, visible={}, animating={}", isEntry, isVisible(), isAnimating());
        runOnEdt(() -> animationManager.animate(isEntry), "animateLoadingWindow");
    }

    public void toggleVisibility() {
        Engine.getLOGGER().debug("[FLOATING-WINDOW] toggleVisibility requested: visible={}, animating={}", isVisible(), isAnimating());
        runOnEdt(() -> {
            if (isAnimating()) {
                Engine.getLOGGER().debug("[FLOATING-WINDOW] toggleVisibility skipped: already animating");
                return;
            }
            if (isVisible()) {
                animateLoadingWindow(false);
            } else {
                setSize(FRAME_WIDTH, FRAME_HEIGHT);
                animateLoadingWindow(true);
            }
        }, "toggleVisibility");
    }

    private void runOnEdt(Runnable task, String operation) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            long queuedAt = System.nanoTime();
            SwingUtilities.invokeLater(() -> {
                logUiQueueDelay(operation, queuedAt);
                task.run();
            });
        }
    }

    private void logUiQueueDelay(String operation, long queuedAtNanos) {
        long delay = System.nanoTime() - queuedAtNanos;
        if (delay >= UI_QUEUE_WARN_NANOS) {
            Engine.getLOGGER().warn("[FLOATING-WINDOW][EDT-QUEUE] {} waited {} ms", operation, nanosToMillis(delay));
        }
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    public Engine getEngine() {
        return engine;
    }

    public boolean isAnimating() {
        return animating;
    }

    public void setAnimating(boolean animating) {
        this.animating = animating;
    }

    protected int getANIMATION_DURATION() {
        return ANIMATION_DURATION;
    }

    protected int getANIMATION_SPEED() {
        return ANIMATION_SPEED;
    }

    public void setANIMATION_DURATION(int ANIMATION_DURATION) {
        this.ANIMATION_DURATION = ANIMATION_DURATION;
    }

    public void setANIMATION_SPEED(int ANIMATION_SPEED) {
        this.ANIMATION_SPEED = ANIMATION_SPEED;
    }

    protected int getFrameWidth() {
        return this.FRAME_WIDTH;
    }

    protected int getFrameHeight() {
        return this.FRAME_HEIGHT;
    }

    @Override
    public void fadeIn() {
        this.setVisible(true);
    }

    @Override
    public void fadeOut() {
        this.setVisible(false);
    }
}
