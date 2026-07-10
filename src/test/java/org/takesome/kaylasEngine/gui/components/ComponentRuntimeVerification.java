package org.takesome.kaylasEngine.gui.components;

import org.apache.logging.log4j.LogManager;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.fileSelector.SelectionMode;
import org.takesome.kaylasEngine.gui.styles.StyleAttributes;
import org.takesome.kaylasEngine.gui.styles.StyleProvider;

import javax.swing.JButton;
import java.util.List;
import java.util.Map;

/** Executable verification used by the Gradle componentRuntimeCheck task. */
public final class ComponentRuntimeVerification {
    private ComponentRuntimeVerification() {
    }

    public static void main(String[] args) {
        System.setProperty("log.dir", System.getProperty("user.dir", "."));
        System.setProperty("log.level", "INFO");
        Engine.LOGGER = LogManager.getLogger(ComponentRuntimeVerification.class);

        require(SelectionMode.from("folder") == SelectionMode.DIRECTORIES_ONLY,
                "folder selection alias did not resolve to DIRECTORIES_ONLY");
        require(SelectionMode.from("directories-only") == SelectionMode.DIRECTORIES_ONLY,
                "directories-only alias did not resolve to DIRECTORIES_ONLY");
        require(SelectionMode.from("files") == SelectionMode.FILES_ONLY,
                "files alias did not resolve to FILES_ONLY");

        StyleProvider styles = new StyleProvider(new String[]{"button", "label"});
        StyleAttributes composedButton = styles.resolveStyle(
                "button",
                List.of("buttonMain", "compact"),
                Map.of("fontSize", "13", "fontStyle", "bold")
        );

        require(composedButton.getFontSize() == 13, "inline fontSize override was not applied");
        require("bold".equalsIgnoreCase(composedButton.getFontStyle()), "inline fontStyle override was not applied");
        require("assets/ui/components/button/newButton.png".equals(composedButton.getTexture()),
                "inherited button texture was not preserved");
        require(composedButton.getBorderRadius() == 6, "compact mixin was not composed last");

        StyleAttributes titleBold = styles.getStyle("label", "titleBold");
        require("mcfontBold".equals(titleBold.getFont()), "label style inheritance did not reach titleBold");
        require("bold".equalsIgnoreCase(titleBold.getFontStyle()), "inherited label font style is invalid");

        ComponentAttributes attributes = ComponentAttributes.builder("button")
                .id("verificationButton")
                .style("buttonMain", "compact")
                .styleOverride("fontSize", 13)
                .bounds(1, 2, 100, 30)
                .enabled(true)
                .visible(true)
                .property("verification", true)
                .build();

        require(attributes.getStyleChain().equals(List.of("buttonMain", "compact")),
                "descriptor style chain order changed");
        require(Boolean.TRUE.equals(attributes.getProperties().get("verification")),
                "descriptor client property was not retained");

        ComponentDefinition<JButton> base = ComponentDefinition.<JButton>builder("verificationBase")
                .defaultStyle("default")
                .creator(context -> new JButton())
                .configure((button, context) -> button.putClientProperty("base", true))
                .build();
        ComponentDefinition<JButton> derived = base.derive("verificationDerived")
                .defaultStyle("buttonMain")
                .configure((button, context) -> button.putClientProperty("derived", true))
                .build();

        require(derived.configuratorCount() == 2, "derived component did not inherit configurators");
        require("buttonMain".equals(derived.defaultStyle()), "derived default style was not replaced");

        System.out.println("Component Runtime 2.0 verification passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
