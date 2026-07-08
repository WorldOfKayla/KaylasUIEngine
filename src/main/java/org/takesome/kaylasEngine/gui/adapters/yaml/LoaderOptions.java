package org.takesome.kaylasEngine.gui.adapters.yaml;

import org.takesome.kaylasEngine.gui.components.ComponentAttributes;

import java.util.Map;

public class LoaderOptions {
    private Map<String, ComponentAttributes> panels;

    public Map<String, ComponentAttributes> getPanels() {
        return panels;
    }

    public void setPanels(Map<String, ComponentAttributes> panels) {
        this.panels = panels;
    }
}
