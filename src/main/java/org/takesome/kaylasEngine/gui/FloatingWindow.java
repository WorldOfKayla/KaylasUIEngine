package org.takesome.kaylasEngine.gui;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.utils.animation.AnimationManager;
import org.takesome.kaylasEngine.utils.animation.AnimationStats;

import javax.swing.*;
import java.awt.*;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

@SuppressWarnings("unused")
public abstract class FloatingWindow extends JWindow implements AnimationStats {

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
        basePanel.setBounds(basePanel.getX(), basePanel.getY(), FRAME_WIDTH, FRAME_HEIGHT);
        backgroundPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Image bgImg = engine.getImageUtils().getScaledImage(engine.getImageUtils().getLocalImage(image), basePanel.getWidth(), basePanel.getHeight());
                g.drawImage(bgImg, 0, 0, basePanel.getWidth(), basePanel.getHeight(), this);
                g.setColor(hexToColor(color));
                g.fillRect(0, 0, basePanel.getWidth(), basePanel.getHeight());
            }
        };
        backgroundPanel.setLayout(basePanel.getLayout());
        backgroundPanel.setBounds(basePanel.getX(), basePanel.getY(), basePanel.getWidth(), basePanel.getHeight());
        backgroundPanel.setName(basePanel.getName());
        for (Component component : basePanel.getComponents()) {
            backgroundPanel.add(component);
        }
        setContentPane(backgroundPanel);
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
        SwingUtilities.invokeLater(() -> {
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
        this.engine.getExecutorServiceProvider().submitTask(() -> {
            animationManager.animate(isEntry);
        }, "animation-"+isEntry);
    }

    public void toggleVisibility() {
        this.engine.getExecutorServiceProvider().submitTask(() -> {
            if (isVisible()) {
                animateLoadingWindow(false);
            } else {
                setSize(FRAME_WIDTH, FRAME_HEIGHT);
                animateLoadingWindow(true);
            }
        }, "loaderAnimation");
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
