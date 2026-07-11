package org.takesome.kaylasEngine.gui.componentAccessor.internal.source;

import org.takesome.kaylasEngine.gui.GuiBuilder;
import org.takesome.kaylasEngine.gui.componentAccessor.ComponentAccessSource;

import java.util.Objects;

/** Internal factories for live component graph sources. */
public final class ComponentAccessSources {
    private ComponentAccessSources() {
    }

    public static ComponentAccessSource fromGuiBuilder(GuiBuilder guiBuilder) {
        return new GuiBuilderAccessSource(Objects.requireNonNull(guiBuilder, "guiBuilder"));
    }
}
