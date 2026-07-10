package org.takesome.kaylasEngine.gui.animation;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable easing curve used by engine animation primitives.
 *
 * <p>Application scripts select a named curve or provide CSS-style cubic-bezier control points;
 * the engine only evaluates the curve.</p>
 */
public final class AnimationCurve {
    private static final double SOLVER_EPSILON = 1.0e-6;
    private static final int NEWTON_ITERATIONS = 8;
    private static final int BISECTION_ITERATIONS = 18;

    private final String name;
    private final boolean cubicBezier;
    private final double x1;
    private final double y1;
    private final double x2;
    private final double y2;

    private AnimationCurve(String name,
                           boolean cubicBezier,
                           double x1,
                           double y1,
                           double x2,
                           double y2) {
        this.name = name == null || name.isBlank() ? "linear" : name.trim();
        this.cubicBezier = cubicBezier;
        this.x1 = clamp01(x1);
        this.y1 = y1;
        this.x2 = clamp01(x2);
        this.y2 = y2;
    }

    public static AnimationCurve named(String name) {
        return new AnimationCurve(name, false, 0.0, 0.0, 1.0, 1.0);
    }

    public static AnimationCurve cubicBezier(double x1, double y1, double x2, double y2) {
        return new AnimationCurve("cubicBezier", true, x1, y1, x2, y2);
    }

    public String name() {
        return name;
    }

    public float apply(float progress) {
        double value = clamp01(progress);
        if (cubicBezier) {
            return (float) clamp01(sampleCubicBezier(value));
        }

        String normalized = normalize(name);
        double eased = switch (normalized) {
            case "easeinquad", "inquad" -> value * value;
            case "easeoutquad", "outquad" -> 1.0 - square(1.0 - value);
            case "easeinoutquad", "inoutquad" -> value < 0.5
                    ? 2.0 * value * value
                    : 1.0 - square(-2.0 * value + 2.0) / 2.0;
            case "easeincubic", "incubic" -> value * value * value;
            case "easeoutcubic", "outcubic" -> 1.0 - cube(1.0 - value);
            case "easeinoutcubic", "inoutcubic" -> value < 0.5
                    ? 4.0 * value * value * value
                    : 1.0 - cube(-2.0 * value + 2.0) / 2.0;
            case "easeinquart", "inquart" -> value * value * value * value;
            case "easeoutquart", "outquart" -> 1.0 - fourth(1.0 - value);
            case "easeinoutquart", "inoutquart" -> value < 0.5
                    ? 8.0 * fourth(value)
                    : 1.0 - fourth(-2.0 * value + 2.0) / 2.0;
            case "easeinoutsine", "inoutsine" -> -(Math.cos(Math.PI * value) - 1.0) / 2.0;
            case "easeinsine", "insine" -> 1.0 - Math.cos((value * Math.PI) / 2.0);
            case "easeoutsine", "outsine" -> Math.sin((value * Math.PI) / 2.0);
            case "smoothstep" -> value * value * (3.0 - 2.0 * value);
            case "smootherstep" -> value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
            default -> value;
        };
        return (float) clamp01(eased);
    }

    private double sampleCubicBezier(double progress) {
        if (progress <= 0.0 || progress >= 1.0) {
            return progress;
        }

        double parameter = progress;
        for (int iteration = 0; iteration < NEWTON_ITERATIONS; iteration++) {
            double x = cubic(parameter, 0.0, x1, x2, 1.0) - progress;
            if (Math.abs(x) <= SOLVER_EPSILON) {
                return cubic(parameter, 0.0, y1, y2, 1.0);
            }
            double derivative = cubicDerivative(parameter, 0.0, x1, x2, 1.0);
            if (Math.abs(derivative) <= SOLVER_EPSILON) {
                break;
            }
            parameter -= x / derivative;
            if (parameter < 0.0 || parameter > 1.0) {
                break;
            }
        }

        double lower = 0.0;
        double upper = 1.0;
        parameter = progress;
        for (int iteration = 0; iteration < BISECTION_ITERATIONS; iteration++) {
            double x = cubic(parameter, 0.0, x1, x2, 1.0);
            if (Math.abs(x - progress) <= SOLVER_EPSILON) {
                break;
            }
            if (x < progress) {
                lower = parameter;
            } else {
                upper = parameter;
            }
            parameter = (lower + upper) * 0.5;
        }
        return cubic(parameter, 0.0, y1, y2, 1.0);
    }

    private static double cubic(double t, double p0, double p1, double p2, double p3) {
        double inverse = 1.0 - t;
        return inverse * inverse * inverse * p0
                + 3.0 * inverse * inverse * t * p1
                + 3.0 * inverse * t * t * p2
                + t * t * t * p3;
    }

    private static double cubicDerivative(double t, double p0, double p1, double p2, double p3) {
        double inverse = 1.0 - t;
        return 3.0 * inverse * inverse * (p1 - p0)
                + 6.0 * inverse * t * (p2 - p1)
                + 3.0 * t * t * (p3 - p2);
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "linear")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "");
    }

    private static double square(double value) {
        return value * value;
    }

    private static double cube(double value) {
        return value * value * value;
    }

    private static double fourth(double value) {
        double squared = value * value;
        return squared * squared;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
