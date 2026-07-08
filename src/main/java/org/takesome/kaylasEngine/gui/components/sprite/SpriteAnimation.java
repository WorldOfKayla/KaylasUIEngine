package org.takesome.kaylasEngine.gui.components.sprite;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.ComponentFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

@SuppressWarnings("unused")
public class SpriteAnimation extends JComponent {

    private static final long FRAME_PREP_WARN_NANOS = 100_000_000L;
    private static final long PAINT_WARN_NANOS = 16_000_000L;
    private static final long TIMER_LAG_WARN_NANOS = 80_000_000L;
    private static final long LOG_INTERVAL_NANOS = 2_000_000_000L;

    private ComponentFactory componentFactory;

    private BufferedImage spriteSheet;
    private BufferedImage[] frames = new BufferedImage[0];

    private int rows;
    private int columns;
    private int delay;

    private int currentFrame = 0;

    /**
     * ComponentAttributes#isRepeat() is treated as the repeat flag by the existing engine config.
     */
    private boolean repeat;

    private boolean alreadyPlayed = false;
    private Timer timer;
    private boolean animationStopped = true;

    private float alpha = 1.0f;

    private Dimension frameSize = new Dimension(64, 64);
    private Rectangle spriteRect;

    private int scaledWidth = 64;
    private int scaledHeight = 64;

    private long lastPaintWarnNanos = 0L;
    private long lastTimerLagWarnNanos = 0L;
    private long lastTimerTickNanos = 0L;
    private long maxTimerLagNanos = 0L;

    public SpriteAnimation(ComponentFactory componentFactory) {
        this.componentFactory = componentFactory;
        initialize(componentFactory.getComponentAttribute());
        startAnimation(this.repeat);
    }

    public SpriteAnimation(Engine engine, String path, int rows, int columns, int delay, Rectangle spriteRect) {
        long startedAt = System.nanoTime();
        this.spriteSheet = engine.getImageUtils().getLocalImage(path);
        this.rows = Math.max(1, rows);
        this.columns = Math.max(1, columns);
        this.delay = normalizeDelay(delay);
        this.repeat = true;
        this.spriteRect = spriteRect;

        if (spriteRect != null && spriteRect.width > 0 && spriteRect.height > 0) {
            this.scaledWidth = spriteRect.width;
            this.scaledHeight = spriteRect.height;
        }

        setOpaque(false);
        setDoubleBuffered(true);

        rebuildFrames();
        startAnimation(true);
        Engine.getLOGGER().info(
                "[SPRITE] created: path={}, sheet={}x{}, rows={}, columns={}, frames={}, scaled={}x{}, delay={} ms, elapsed={} ms",
                path,
                spriteSheet == null ? -1 : spriteSheet.getWidth(),
                spriteSheet == null ? -1 : spriteSheet.getHeight(),
                this.rows,
                this.columns,
                frames.length,
                scaledWidth,
                scaledHeight,
                this.delay,
                nanosToMillis(System.nanoTime() - startedAt)
        );
    }

    private void initialize(ComponentAttributes componentAttributes) {
        long startedAt = System.nanoTime();
        this.spriteSheet = componentFactory.getEngine()
                .getImageUtils()
                .getLocalImage(componentAttributes.getImageIcon());

        this.rows = Math.max(1, componentAttributes.getRowNum());
        this.columns = Math.max(1, componentAttributes.getColNum());
        this.delay = normalizeDelay(componentAttributes.getDelay());
        this.repeat = componentAttributes.isRepeat();

        setOpaque(componentAttributes.isOpaque());
        setDoubleBuffered(true);

        rebuildFrames();
        Engine.getLOGGER().info(
                "[SPRITE] initialized from attributes: image={}, sheet={}x{}, rows={}, columns={}, frames={}, scaled={}x{}, delay={} ms, repeat={}, elapsed={} ms",
                componentAttributes.getImageIcon(),
                spriteSheet == null ? -1 : spriteSheet.getWidth(),
                spriteSheet == null ? -1 : spriteSheet.getHeight(),
                rows,
                columns,
                frames.length,
                scaledWidth,
                scaledHeight,
                delay,
                repeat,
                nanosToMillis(System.nanoTime() - startedAt)
        );
    }

    private int normalizeDelay(int delay) {
        // 16 ms is roughly 60 FPS. Lower values create avoidable pressure on Swing's EDT.
        return Math.max(16, delay);
    }

    /**
     * Cuts and scales frames once during initialization/update.
     * paintComponent() must only draw the already prepared frame.
     */
    private void rebuildFrames() {
        long startedAt = System.nanoTime();
        if (spriteSheet == null) {
            frames = new BufferedImage[0];
            frameSize = new Dimension(scaledWidth, scaledHeight);
            setPreferredSize(frameSize);
            Engine.getLOGGER().warn("[SPRITE] rebuildFrames skipped: spriteSheet is null");
            return;
        }

        int safeRows = Math.max(1, rows);
        int safeColumns = Math.max(1, columns);

        int frameWidth = Math.max(1, spriteSheet.getWidth() / safeColumns);
        int frameHeight = Math.max(1, spriteSheet.getHeight() / safeRows);

        int frameCount = safeRows * safeColumns;
        BufferedImage[] preparedFrames = new BufferedImage[frameCount];

        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            int row = frameIndex / safeColumns;
            int column = frameIndex % safeColumns;

            int sourceX = column * frameWidth;
            int sourceY = row * frameHeight;

            if (sourceX + frameWidth > spriteSheet.getWidth()
                    || sourceY + frameHeight > spriteSheet.getHeight()) {
                continue;
            }

            BufferedImage rawFrame = spriteSheet.getSubimage(sourceX, sourceY, frameWidth, frameHeight);
            BufferedImage scaledFrame = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);

            Graphics2D g2d = scaledFrame.createGraphics();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                g2d.drawImage(rawFrame, 0, 0, scaledWidth, scaledHeight, null);
            } finally {
                g2d.dispose();
            }

            preparedFrames[frameIndex] = scaledFrame;
        }

        this.frames = preparedFrames;
        this.frameSize = new Dimension(scaledWidth, scaledHeight);

        setPreferredSize(frameSize);
        setMinimumSize(frameSize);

        if (getWidth() <= 0 || getHeight() <= 0) {
            setSize(frameSize);
        }

        if (currentFrame >= frames.length) {
            currentFrame = Math.max(0, frames.length - 1);
        }

        long elapsed = System.nanoTime() - startedAt;
        if (elapsed >= FRAME_PREP_WARN_NANOS) {
            Engine.getLOGGER().warn(
                    "[SPRITE] rebuildFrames slow: sheet={}x{}, frame={}x{}, preparedFrames={}, scaled={}x{}, elapsed={} ms",
                    spriteSheet.getWidth(),
                    spriteSheet.getHeight(),
                    frameWidth,
                    frameHeight,
                    frameCount,
                    scaledWidth,
                    scaledHeight,
                    nanosToMillis(elapsed)
            );
        } else {
            Engine.getLOGGER().debug(
                    "[SPRITE] rebuildFrames: preparedFrames={}, scaled={}x{}, elapsed={} ms",
                    frameCount,
                    scaledWidth,
                    scaledHeight,
                    nanosToMillis(elapsed)
            );
        }
    }

    private void startAnimation(boolean repeat) {
        if (!repeat && alreadyPlayed) {
            Engine.getLOGGER().debug("[SPRITE] startAnimation skipped: play-once already completed");
            return;
        }

        stopTimerOnly();

        int lastFrame = calculateLastFrame();
        if (lastFrame <= 0) {
            animationStopped = true;
            Engine.getLOGGER().warn("[SPRITE] startAnimation skipped: no frames available");
            return;
        }

        animationStopped = false;
        lastTimerTickNanos = System.nanoTime();
        maxTimerLagNanos = 0L;

        Engine.getLOGGER().debug(
                "[SPRITE] animation start: frames={}, delay={} ms, repeat={}",
                lastFrame,
                delay,
                repeat
        );

        timer = new Timer(delay, event -> {
            long now = System.nanoTime();
            long timerLag = Math.max(0L, now - lastTimerTickNanos - delay * 1_000_000L);
            lastTimerTickNanos = now;
            maxTimerLagNanos = Math.max(maxTimerLagNanos, timerLag);

            if (timerLag >= TIMER_LAG_WARN_NANOS && now - lastTimerLagWarnNanos >= LOG_INTERVAL_NANOS) {
                lastTimerLagWarnNanos = now;
                Engine.getLOGGER().warn(
                        "[SPRITE][TIMER-LAG] delay={} ms, frame={}, maxLag={} ms",
                        nanosToMillis(timerLag),
                        currentFrame,
                        nanosToMillis(maxTimerLagNanos)
                );
            }

            if (animationStopped || frames.length == 0) {
                return;
            }

            if (currentFrame < lastFrame - 1) {
                currentFrame++;
            } else {
                if (repeat) {
                    currentFrame = 0;
                } else {
                    currentFrame = lastFrame - 1;
                    alreadyPlayed = true;
                    stop();
                }
            }

            repaint(0, 0, Math.max(getWidth(), scaledWidth), Math.max(getHeight(), scaledHeight));
        });

        timer.setCoalesce(true);
        timer.start();
    }

    private int calculateLastFrame() {
        return frames != null ? frames.length : 0;
    }

    @Override
    protected void paintComponent(Graphics g) {
        long startedAt = System.nanoTime();
        super.paintComponent(g);

        if (!isVisible() || frames == null || frames.length == 0) {
            return;
        }

        int safeFrame = Math.max(0, Math.min(currentFrame, frames.length - 1));
        BufferedImage frame = frames[safeFrame];

        if (frame == null) {
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();
        try {
            if (alpha < 1.0f) {
                g2d.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER,
                        Math.max(0.0f, Math.min(1.0f, alpha))
                ));
            }

            g2d.drawImage(frame, 0, 0, null);
        } finally {
            g2d.dispose();
        }

        long elapsed = System.nanoTime() - startedAt;
        long now = System.nanoTime();
        if (elapsed >= PAINT_WARN_NANOS && now - lastPaintWarnNanos >= LOG_INTERVAL_NANOS) {
            lastPaintWarnNanos = now;
            Engine.getLOGGER().warn(
                    "[SPRITE] paint slow: frame={}, component={}x{}, elapsed={} ms",
                    safeFrame,
                    getWidth(),
                    getHeight(),
                    nanosToMillis(elapsed)
            );
        }
    }

    public void updateImage(BufferedImage newSpriteSheet, int cols, int rows, int delay, boolean repeat) {
        long startedAt = System.nanoTime();
        stop();

        this.spriteSheet = newSpriteSheet;
        this.columns = Math.max(1, cols);
        this.rows = Math.max(1, rows);
        this.delay = normalizeDelay(delay);
        this.repeat = repeat;
        this.alreadyPlayed = false;
        this.alpha = 1.0f;
        this.currentFrame = 0;

        rebuildFrames();
        revalidate();
        repaint();

        start(repeat);
        Engine.getLOGGER().info(
                "[SPRITE] updateImage: sheet={}x{}, rows={}, columns={}, frames={}, delay={} ms, repeat={}, elapsed={} ms",
                spriteSheet == null ? -1 : spriteSheet.getWidth(),
                spriteSheet == null ? -1 : spriteSheet.getHeight(),
                this.rows,
                this.columns,
                frames.length,
                this.delay,
                this.repeat,
                nanosToMillis(System.nanoTime() - startedAt)
        );
    }

    private void stop() {
        stopTimerOnly();
        animationStopped = true;
    }

    private void stopTimerOnly() {
        if (timer != null) {
            timer.stop();
            timer = null;
            Engine.getLOGGER().debug("[SPRITE] timer stopped; maxTimerLag={} ms", nanosToMillis(maxTimerLagNanos));
        }
    }

    private void start(boolean repeat) {
        currentFrame = 0;
        animationStopped = false;
        startAnimation(repeat);
    }

    @Override
    public void addNotify() {
        super.addNotify();

        if ((timer == null || !timer.isRunning()) && frames.length > 0 && (repeat || !alreadyPlayed)) {
            animationStopped = false;
            startAnimation(repeat);
        }
    }

    @Override
    public void removeNotify() {
        stop();
        super.removeNotify();
    }

    public void setAlpha(float alpha) {
        this.alpha = Math.max(0, Math.min(1, alpha));
        repaint();
    }

    public void resetAnimation() {
        stop();
        currentFrame = 0;
        alpha = 1.0f;
        alreadyPlayed = false;
        start(repeat);
    }

    public boolean isAnimationStopped() {
        return animationStopped;
    }

    public void setSpriteSheet(BufferedImage spriteSheet) {
        this.spriteSheet = spriteSheet;
        rebuildFrames();
        revalidate();
        repaint();
    }

    public void setRows(int rows) {
        this.rows = Math.max(1, rows);
        rebuildFrames();
        revalidate();
        repaint();
    }

    public void setFrameSize(Dimension frameSize) {
        if (frameSize == null || frameSize.width <= 0 || frameSize.height <= 0) {
            return;
        }

        this.frameSize = frameSize;
        this.scaledWidth = frameSize.width;
        this.scaledHeight = frameSize.height;

        rebuildFrames();
        revalidate();
        repaint();
    }

    public void setColumns(int columns) {
        this.columns = Math.max(1, columns);
        rebuildFrames();
        revalidate();
        repaint();
    }

    public void setDelay(int delay) {
        this.delay = normalizeDelay(delay);

        if (timer != null && timer.isRunning()) {
            stopTimerOnly();
            startAnimation(repeat);
        }
    }

    public void setPlayOnce(boolean playOnce) {
        // Kept for source compatibility with the old API.
        this.repeat = playOnce;
    }

    public void setAnimationStopped(boolean animationStopped) {
        this.animationStopped = animationStopped;

        if (animationStopped) {
            stopTimerOnly();
        } else if (timer == null || !timer.isRunning()) {
            startAnimation(repeat);
        }
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public Rectangle getSpriteRect() {
        return spriteRect;
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }
}
