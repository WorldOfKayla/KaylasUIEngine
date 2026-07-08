package org.takesome.kaylasEngine.gui.components.frame;

import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.panel.PanelAttributes;

import java.util.List;
import java.util.Map;

public class OptionGroups {
    private PanelAttributes panelOptions;
    private List<ComponentAttributes> childComponents;
    private Map<String, OptionGroups> panels;

    public PanelAttributes getPanelOptions() {
        return panelOptions;
    }
    public List<ComponentAttributes> getChildComponents() {
        return childComponents;
    }
    public Map<String, OptionGroups> getGroups() {
        return panels;
    }
}
