package org.takesome.kaylasEngine.gui.animation.internal.easing;

/** Internal evaluation strategy behind the public animation-curve value object. */
public interface AnimationCurveEvaluation {
    static AnimationCurveEvaluation named(String name) {
        return DefaultAnimationCurveEvaluation.named(name);
    }

    static AnimationCurveEvaluation cubicBezier(double x1, double y1, double x2, double y2) {
        return DefaultAnimationCurveEvaluation.cubicBezier(x1, y1, x2, y2);
    }

    float apply(float progress);
}
