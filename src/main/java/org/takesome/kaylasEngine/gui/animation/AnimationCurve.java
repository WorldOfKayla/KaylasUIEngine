package org.takesome.kaylasEngine.gui.animation;

import org.takesome.kaylasEngine.gui.animation.internal.easing.AnimationCurveEvaluation;

/**
 * Immutable easing curve used by engine animation primitives.
 *
 * <p>Applications select a named curve or provide CSS-style cubic-Bezier control points. Numerical
 * evaluation is encapsulated by the animation runtime.</p>
 */
public final class AnimationCurve {
    private final String name;
    private final AnimationCurveEvaluation evaluation;

    private AnimationCurve(String name, AnimationCurveEvaluation evaluation) {
        this.name = name == null || name.isBlank() ? "linear" : name.trim();
        this.evaluation = evaluation;
    }

    public static AnimationCurve named(String name) {
        String resolved = name == null || name.isBlank() ? "linear" : name.trim();
        return new AnimationCurve(resolved, AnimationCurveEvaluation.named(resolved));
    }

    public static AnimationCurve cubicBezier(double x1, double y1, double x2, double y2) {
        return new AnimationCurve(
                "cubicBezier",
                AnimationCurveEvaluation.cubicBezier(x1, y1, x2, y2)
        );
    }

    public String name() {
        return name;
    }

    public float apply(float progress) {
        return evaluation.apply(progress);
    }
}
