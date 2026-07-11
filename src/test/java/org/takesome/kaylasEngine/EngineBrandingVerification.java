package org.takesome.kaylasEngine;

/** Regression verification for dynamic engine generation branding. */
public final class EngineBrandingVerification {
    private EngineBrandingVerification() {
    }

    public static void verify() {
        require("KAYLAS UI ENGINE // KINETICA 2.3".equals(
                        Engine.engineGenerationLabel("2.3.0-KINETICA")
                ),
                "KINETICA version did not produce the expected engine generation label");
        require("KAYLAS UI ENGINE // AURELIA 2.2".equals(
                        Engine.engineGenerationLabel("2.2.0-AURELIA")
                ),
                "AURELIA compatibility label was not derived from the version");
        require("KAYLAS UI ENGINE".equals(Engine.engineGenerationLabel(null)),
                "missing version did not produce the fallback label");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
