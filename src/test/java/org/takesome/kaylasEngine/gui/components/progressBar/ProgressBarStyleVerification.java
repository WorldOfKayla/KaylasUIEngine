package org.takesome.kaylasEngine.gui.components.progressBar;

/** Regression verification for numeric progress descriptor values. */
public final class ProgressBarStyleVerification {
    private ProgressBarStyleVerification() {
    }

    public static void verify() {
        require(ProgressBarStyle.integerValue(0.0d) == 0,
                "integral Double progress value was rejected");
        require(ProgressBarStyle.integerValue(42.0f) == 42,
                "integral Float progress value was rejected");
        require(ProgressBarStyle.integerValue("75.0") == 75,
                "integral decimal string progress value was rejected");
        expectArithmeticFailure(1.5d);
    }

    private static void expectArithmeticFailure(Object value) {
        try {
            ProgressBarStyle.integerValue(value);
            throw new IllegalStateException("fractional progress value was accepted: " + value);
        } catch (ArithmeticException expected) {
            // Fractional progress values are invalid for the integer model.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
