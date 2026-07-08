package org.takesome.kaylasEngine.utils.animation;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class KeyframeAnimation {
    private final List<Keyframe> keyframes = new ArrayList<>();
    private Timer animationTimer;
    private final int interval;
    private int currentFrame = 0;
    private final Component component;
    private final Runnable onComplete;

    public KeyframeAnimation(Component component, int interval, Runnable onComplete) {
        this.component = component;
        this.interval = interval;
        this.onComplete = onComplete;
    }

    public void addKeyframe(float opacity, Point location, int duration) {
        int steps = duration / interval;
        Keyframe lastFrame = keyframes.isEmpty() ? null : keyframes.get(keyframes.size() - 1);
        float startOpacity = lastFrame == null ? ((JWindow) component).getOpacity() : lastFrame.getOpacity();
        Point startLocation = lastFrame == null ? component.getLocation() : lastFrame.getLocation();

        for (int i = 1; i <= steps; i++) { // Start from 1 to avoid adding the initial state again
            float interpolatedOpacity = startOpacity + (opacity - startOpacity) * i / steps;
            int interpolatedX = startLocation.x + (location.x - startLocation.x) * i / steps;
            int interpolatedY = startLocation.y + (location.y - startLocation.y) * i / steps;
            keyframes.add(new Keyframe(interpolatedOpacity, new Point(interpolatedX, interpolatedY)));
        }
    }

    public void start() {
        if (keyframes.isEmpty()) return;

        animationTimer = new Timer(interval, e -> {
            if (currentFrame >= keyframes.size()) {
                animationTimer.stop();
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }

            Keyframe keyframe = keyframes.get(currentFrame);
            applyKeyframe(keyframe);

            currentFrame++;
        });

        animationTimer.start();
    }

    private void applyKeyframe(Keyframe keyframe) {
        if (keyframe.getOpacity() != null) {
            ((JWindow) component).setOpacity(keyframe.getOpacity());
        }
        if (keyframe.getLocation() != null) {
            component.setLocation(keyframe.getLocation());
        }
    }

    public static class Keyframe {
        private final Float opacity;
        private final Point location;

        public Keyframe(Float opacity, Point location) {
            this.opacity = opacity;
            this.location = location;
        }

        public Float getOpacity() {
            return opacity;
        }

        public Point getLocation() {
            return location;
        }
    }
}