package org.takesome.kaylasEngine.gui.loadingManager;

import org.takesome.kaylasEngine.gui.animation.AnimationCurve;

import java.awt.Color;
import java.util.List;

/** Central fallback profile used when a loading UI script is absent or invalid. */
final class ScriptedLoadingUiDefaults {
    private ScriptedLoadingUiDefaults() {
    }

    static ScriptedLoadingUi create() {
        return new ScriptedLoadingUi(
                new ScriptedLoadingUi.Overlay(
                        true,
                        "loadingOverlay",
                        Color.BLACK,
                        179,
                        new ScriptedLoadingUi.Fade(220, 16, AnimationCurve.named("easeOutQuad")),
                        new ScriptedLoadingUi.Fade(240, 16, AnimationCurve.named("easeOutQuad")),
                        new ScriptedLoadingUi.Bounds(0, 0, -1, -1)
                ),
                new ScriptedLoadingUi.Window(500, 150, true, 15),
                new ScriptedLoadingUi.Loader(
                        true,
                        "assets/ui/sprites/loaderFox.png",
                        3,
                        5,
                        55,
                        new ScriptedLoadingUi.Bounds(30, 40, 64, 64),
                        new ScriptedLoadingUi.Background(
                                "assets/ui/img/bg/season/summer.png",
                                "#b3a8998a"
                        ),
                        "#252424",
                        "#7534d4"
                ),
                new ScriptedLoadingUi.Typography(
                        new ScriptedLoadingUi.TextStyle("titleBold", "", 16, "plain", ""),
                        new ScriptedLoadingUi.TextStyle("title", "", 11, "plain", "")
                ),
                new ScriptedLoadingUi.Progress(
                        true,
                        100,
                        1,
                        0,
                        0,
                        500,
                        16,
                        -1,
                        true,
                        true,
                        true,
                        false,
                        true,
                        false,
                        true,
                        true,
                        "progressMessages",
                        "assets/messages.json",
                        "assets/animation_config.json",
                        List.of(),
                        "progressMini",
                        "",
                        0,
                        "",
                        ""
                ),
                new ScriptedLoadingUi.Transition(
                        true,
                        new ScriptedLoadingUi.Phase(
                                new ScriptedLoadingUi.Motion(
                                        true,
                                        0,
                                        300,
                                        16,
                                        AnimationCurve.named("easeInOutSine"),
                                        ScriptedLoadingUi.Position.frame(0.5, 0.0, 0.5, 1.0, 0, 0),
                                        ScriptedLoadingUi.Position.frame(0.5, 0.5, 0.5, 0.5, 0, 0)
                                ),
                                new ScriptedLoadingUi.Opacity(
                                        true,
                                        0,
                                        300,
                                        16,
                                        AnimationCurve.named("easeInOutSine"),
                                        0.0f,
                                        1.0f
                                )
                        ),
                        new ScriptedLoadingUi.Phase(
                                new ScriptedLoadingUi.Motion(
                                        true,
                                        0,
                                        300,
                                        16,
                                        AnimationCurve.named("easeInOutSine"),
                                        ScriptedLoadingUi.Position.current(0, 0),
                                        ScriptedLoadingUi.Position.frame(1.0, 0.5, 0.0, 0.5, 0, 0)
                                ),
                                new ScriptedLoadingUi.Opacity(
                                        true,
                                        0,
                                        300,
                                        16,
                                        AnimationCurve.named("easeInOutSine"),
                                        1.0f,
                                        0.0f
                                )
                        )
                )
        );
    }
}
