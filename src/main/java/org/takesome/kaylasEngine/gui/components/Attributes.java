package org.takesome.kaylasEngine.gui.components;

import org.takesome.kaylasEngine.gui.components.frame.OptionGroups;

import java.util.List;
import java.util.Map;

public class Attributes {
    protected Map<String, OptionGroups> panels;
    protected List<ComponentAttributes> childComponents;

    public Map<String, OptionGroups> getGroups() {
        return panels;
    }
    public List<ComponentAttributes> getChildComponents() {
        return childComponents;
    }

    public void addChild(ComponentAttributes childAttributes) {
        childComponents.add(childAttributes);
    }
}
