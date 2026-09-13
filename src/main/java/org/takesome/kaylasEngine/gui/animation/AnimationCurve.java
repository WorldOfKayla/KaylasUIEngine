package org.takesome.kaylasEngine.gui.animation;

import org.takesome.kaylasEngine.gui.animation.internal.easing.AnimationCurveEvaluation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Immutable easing curve used by engine animation primitives.
 *
 * <p>Applications select a named curve or provide CSS-style cubic-Bezier control points. Numerical
 * evaluation is encapsulated by the animation runtime. Named curves are cached because timeline
 * sampling may resolve the same interpolation on every rendered frame.</p>
 */
public final class AnimationCurve {
    private static final ConcurrentMap<String, AnimationCurve> NAMED_CURVES = new ConcurrentHashMap<>();

    private final String name;
    private final AnimationCurveEvaluation evaluation;

    private AnimationCurve(String name, AnimationCurveEvaluation evaluation) {
        this.name = name == null || name.isBlank() ? "linear" : name.trim();
        this.evaluation = evaluation;
    }

    /** Resolves a reusable named easing curve. */
    public static AnimationCurve named(String name) {
        String resolved = name == null || name.isBlank() ? "linear" : name.trim();
        return NAMED_CURVES.computeIfAbsent(
                resolved,
                key -> new AnimationCurve(key, AnimationCurveEvaluation.named(key))
        );
    }

    /** Creates a custom CSS-style cubic-Bezier easing curve. */
    public static AnimationCurve cubicBezier(double x1, double y1, double x2, double y2) {
        return new AnimationCurve(
                "cubicBezier",
                AnimationCurveEvaluation.cubicBezier(x1, y1, x2, y2)
        );
    }

    /** Returns the diagnostic name of this curve. */
    public String name() {
        return name;
    }

    /** Evaluates the curve at normalized progress. */
    public float apply(float progress) {
        return evaluation.apply(progress);
    }
}
