package org.takesome.kaylasEngine.utils.animation;

import org.takesome.kaylasEngine.gui.animation.AnimationEngine;

import javax.swing.JWindow;
import java.awt.Component;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/** Legacy keyframe sequence retained as an adapter over the unified AnimationEngine. */
class KeyframeAnimation {
    private final List<Keyframe> keyframes = new ArrayList<>();
    private AnimationEngine.Handle animation;
    private final int interval;
    private int currentFrame;
    private final Component component;
    private final Runnable onComplete;

    KeyframeAnimation(Component component, int interval, Runnable onComplete) {
        this.component = component;
        this.interval = Math.max(1, interval);
        this.onComplete = onComplete;
    }

    void addKeyframe(float opacity, Point location, int duration) {
        int steps = Math.max(1, duration / interval);
        Keyframe lastFrame = keyframes.isEmpty() ? null : keyframes.get(keyframes.size() - 1);
        float startOpacity = lastFrame == null ? ((JWindow) component).getOpacity() : lastFrame.getOpacity();
        Point startLocation = lastFrame == null ? component.getLocation() : lastFrame.getLocation();

        for (int i = 1; i <= steps; i++) {
            float interpolatedOpacity = startOpacity + (opacity - startOpacity) * i / steps;
            int interpolatedX = startLocation.x + (location.x - startLocation.x) * i / steps;
            int interpolatedY = startLocation.y + (location.y - startLocation.y) * i / steps;
            keyframes.add(new Keyframe(interpolatedOpacity, new Point(interpolatedX, interpolatedY)));
        }
    }

    void start() {
        if (keyframes.isEmpty()) {
            return;
        }
        stop();
        currentFrame = 0;
        animation = AnimationEngine.shared().interval(interval, 0, () -> {
            if (currentFrame >= keyframes.size()) {
                animation = null;
                if (onComplete != null) {
                    onComplete.run();
                }
                return false;
            }
            applyKeyframe(keyframes.get(currentFrame++));
            return true;
        });
    }

    void stop() {
        if (animation != null) {
            animation.cancel();
            animation = null;
        }
    }

    private void applyKeyframe(Keyframe keyframe) {
        if (keyframe.getOpacity() != null) {
            ((JWindow) component).setOpacity(keyframe.getOpacity());
        }
        if (keyframe.getLocation() != null) {
            component.setLocation(keyframe.getLocation());
        }
    }

    static final class Keyframe {
        private final Float opacity;
        private final Point location;

        Keyframe(Float opacity, Point location) {
            this.opacity = opacity;
            this.location = location;
        }

        Float getOpacity() {
            return opacity;
        }

        Point getLocation() {
            return location;
        }
    }
}
