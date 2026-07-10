package org.takesome.kaylasEngine.gui.components;

import org.apache.logging.log4j.LogManager;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.constructor.ComponentConstructor;
import org.takesome.kaylasEngine.gui.components.constructor.ComponentNode;
import org.takesome.kaylasEngine.gui.components.constructor.CompositeComponentDefinition;
import org.takesome.kaylasEngine.gui.components.fileSelector.SelectionMode;
import org.takesome.kaylasEngine.gui.scripting.ComponentSignalRouter;
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

        verifySelectionModes();
        verifyStyleComposition();
        verifyDescriptors();
        verifyDefinitionInheritance();
        verifyComponentCatalog();
        verifySignalRouter();

        System.out.println("Component Constructor Runtime 2.1 verification passed.");
    }

    private static void verifySelectionModes() {
        require(SelectionMode.from("folder") == SelectionMode.DIRECTORIES_ONLY,
                "folder selection alias did not resolve to DIRECTORIES_ONLY");
        require(SelectionMode.from("directories-only") == SelectionMode.DIRECTORIES_ONLY,
                "directories-only alias did not resolve to DIRECTORIES_ONLY");
        require(SelectionMode.from("files") == SelectionMode.FILES_ONLY,
                "files alias did not resolve to FILES_ONLY");
    }

    private static void verifyStyleComposition() {
        StyleProvider styles = new StyleProvider(new String[]{"button", "label"});
        StyleAttributes composedButton = styles.resolveStyle(
                "button",
                List.of("buttonMain", "compact"),
                Map.of("fontSize", "13", "fontStyle", "bold")
        );

        require(composedButton.getFontSize() == 13, "inline fontSize override was not applied");
        require("bold".equalsIgnoreCase(composedButton.getFontStyle()),
                "inline fontStyle override was not applied");
        require("assets/ui/components/button/newButton.png".equals(composedButton.getTexture()),
                "inherited button texture was not preserved");
        require(composedButton.getBorderRadius() == 6, "compact mixin was not composed last");

        StyleAttributes titleBold = styles.getStyle("label", "titleBold");
        require("mcfontBold".equals(titleBold.getFont()),
                "label style inheritance did not reach titleBold");
        require("bold".equalsIgnoreCase(titleBold.getFontStyle()),
                "inherited label font style is invalid");
    }

    private static void verifyDescriptors() {
        ComponentAttributes attributes = ComponentAttributes.builder("button")
                .id("verificationButton")
                .style("buttonMain", "compact")
                .styleOverride("fontSize", 13)
                .bounds(1, 2, 100, 30)
                .enabled(true)
                .visible(true)
                .property("verification", true)
                .script("action", "assets/scripts/verification.lua")
                .build();

        require(attributes.getStyleChain().equals(List.of("buttonMain", "compact")),
                "descriptor style chain order changed");
        require(Boolean.TRUE.equals(attributes.getProperties().get("verification")),
                "descriptor client property was not retained");
        require("assets/scripts/verification.lua".equals(attributes.getScripts().get("action")),
                "descriptor event script was not retained");

        ComponentAttributes copy = attributes.copy().setComponentId("verificationCopy");
        require("verificationButton".equals(attributes.getComponentId()),
                "descriptor copy mutated the source prototype");
        require("verificationCopy".equals(copy.getComponentId()),
                "descriptor copy did not accept independent mutation");
    }

    private static ComponentDefinition<JButton> verifyDefinitionInheritance() {
        ComponentDefinition<JButton> base = ComponentDefinition.<JButton>builder("verificationBase")
                .defaultStyle("default")
                .creator(context -> new JButton())
                .configure((button, context) -> button.putClientProperty("base", true))
                .build();
        ComponentDefinition<JButton> derived = base.derive("verificationDerived")
                .defaultStyle("buttonMain")
                .configure((button, context) -> button.putClientProperty("derived", true))
                .build();

        require(derived.configuratorCount() == 2,
                "derived component did not inherit configurators");
        require("buttonMain".equals(derived.defaultStyle()),
                "derived default style was not replaced");
        require(derived.kind() == ComponentKind.BASIC,
                "derived basic component changed its kind");
        return base;
    }

    private static void verifyComponentCatalog() {
        ComponentCatalog catalog = new ComponentCatalog();
        ComponentDefinition<JButton> basic = ComponentDefinition.<JButton>builder("catalogButton")
                .alias("catalog-button")
                .creator(context -> new JButton())
                .build();

        ComponentAttributes toggle = ComponentAttributes.builder("checkBox")
                .id("toggle")
                .bounds(0, 0, 120, 28)
                .build();
        ComponentAttributes status = ComponentAttributes.builder("label")
                .id("status")
                .bounds(128, 0, 160, 28)
                .build();
        CompositeComponentDefinition composite = CompositeComponentDefinition.builder("catalogComposite")
                .alias("catalog-composite")
                .child("toggle", toggle)
                .child("status", status)
                .connect("toggle", "action", "status", "linkedChanged")
                .build();

        catalog.register(basic);
        catalog.register(composite);

        require(catalog.size() == 2, "component catalog size is invalid");
        require(catalog.size(ComponentKind.BASIC) == 1,
                "component catalog basic count is invalid");
        require(catalog.size(ComponentKind.COMPOSITE) == 1,
                "component catalog composite count is invalid");
        require(catalog.find("catalog-button").orElseThrow() == basic,
                "basic component alias was not resolved");
        require(catalog.find("catalog-composite").orElseThrow() == composite,
                "composite component alias was not resolved");
        require(composite.nodes().size() == 2 && composite.connections().size() == 1,
                "composite graph metadata is invalid");

        ComponentAttributes styledInstance = ComponentAttributes.builder("catalogComposite")
                .targetStyle("status", "titleBold")
                .targetStyle("type:checkBox", "solid1")
                .targetStyle("status.tickLabel", "promptLabel")
                .targetStyle("*", "default")
                .bounds(0, 0, 300, 30)
                .build();
        ComponentNode statusNode = composite.nodes().stream()
                .filter(node -> "status".equals(node.localId()))
                .findFirst()
                .orElseThrow();
        ComponentNode toggleNode = composite.nodes().stream()
                .filter(node -> "toggle".equals(node.localId()))
                .findFirst()
                .orElseThrow();
        require("titleBold".equals(composite.resolveNodeStyle(styledInstance, statusNode)),
                "local node style override was not resolved");
        require("solid1".equals(composite.resolveNodeStyle(styledInstance, toggleNode)),
                "component type style override was not resolved");
        require("titleBold".equals(styledInstance.getStyles().get("status")),
                "targetStyle builder did not retain the child style override");
        require("promptLabel".equals(
                        composite.resolveNestedNodeStyles(styledInstance, statusNode).get("tickLabel")
                ),
                "nested child style selector was not forwarded to the child descriptor");

        expectThrows(
                IllegalArgumentException.class,
                () -> catalog.registerAlias("catalogButton", "catalogComposite"),
                "alias was allowed to hijack a canonical component type"
        );

        CompositeComponentDefinition unknownChild = CompositeComponentDefinition
                .builder("unknownChildComposite")
                .child(
                        "missing",
                        ComponentAttributes.builder("missingComponent")
                                .bounds(0, 0, 10, 10)
                                .build()
                )
                .build();
        expectThrows(
                IllegalArgumentException.class,
                () -> ComponentConstructor.validateComposite(catalog, unknownChild),
                "composite with an unknown child type passed validation"
        );

        CompositeComponentDefinition directSelf = CompositeComponentDefinition
                .builder("directSelf")
                .child(
                        "self",
                        ComponentAttributes.builder("directSelf")
                                .bounds(0, 0, 10, 10)
                                .build()
                )
                .build();
        expectThrows(
                IllegalArgumentException.class,
                () -> ComponentConstructor.validateComposite(catalog, directSelf),
                "direct recursive composite passed validation"
        );

        CompositeComponentDefinition parent = CompositeComponentDefinition
                .builder("cycleParent")
                .child(
                        "leaf",
                        ComponentAttributes.builder("catalogButton")
                                .bounds(0, 0, 10, 10)
                                .build()
                )
                .build();
        catalog.register(parent);
        CompositeComponentDefinition replacement = CompositeComponentDefinition
                .builder("catalogButton")
                .child(
                        "parent",
                        ComponentAttributes.builder("cycleParent")
                                .bounds(0, 0, 10, 10)
                                .build()
                )
                .build();
        expectThrows(
                IllegalArgumentException.class,
                () -> ComponentConstructor.validateComposite(catalog, replacement),
                "indirect recursive composite replacement passed validation"
        );
    }

    private static void verifySignalRouter() {
        ComponentSignalRouter router = new ComponentSignalRouter();
        ComponentSignalRouter.Connection connection = router.connect(
                "form.toggle",
                "action",
                "form.status",
                "linkedChanged",
                "form"
        );

        require(connection.isActive(), "signal connection is not active");
        require(router.routesFor("form.toggle", "action").size() == 1,
                "signal route was not indexed by source");
        require("linkedChanged".equals(
                        router.routesFor("form.toggle", "action").get(0).targetEvent()),
                "custom event case was not preserved");
        require(router.disconnectScope("form") == 1,
                "signal scope cleanup did not remove its route");
        require(router.size() == 0 && !connection.isActive(),
                "signal router retained a closed route");
    }

    private static void expectThrows(Class<? extends Throwable> expectedType,
                                     Runnable action,
                                     String failureMessage) {
        try {
            action.run();
        } catch (Throwable error) {
            if (expectedType.isInstance(error)) {
                return;
            }
            throw new IllegalStateException(
                    failureMessage + ": unexpected exception " + error.getClass().getName(),
                    error
            );
        }
        throw new IllegalStateException(failureMessage);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
