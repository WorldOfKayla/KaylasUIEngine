package org.takesome.kaylasEngine.gui.lookAndFeel.theme;

import java.awt.Color;

/** Built-in Kaylas Look and Feel themes. */
public final class KaylasThemes {
    private KaylasThemes() {
    }

    /**
     * Dark KINETICA theme aligned with the engine's existing charcoal, forest and brass styles.
     *
     * @return independent immutable theme value.
     */
    public static KaylasTheme kineticaDark() {
        return new KaylasTheme(
                "KINETICA Dark",
                color("#0f1412"),
                color("#171d1a"),
                color("#202720"),
                color("#314139"),
                color("#b0976b"),
                color("#d0b98d"),
                color("#8a724d"),
                color("#f4f2ea"),
                color("#aaa89f"),
                color("#676a64"),
                color("#2dc5bb"),
                color("#07100e"),
                color("#efb85a"),
                color("#4fb477"),
                color("#da3156"),
                10,
                2,
                34,
                12
        );
    }

    private static Color color(String value) {
        return Color.decode(value);
    }
}
