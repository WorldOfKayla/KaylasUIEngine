package org.takesome.kaylasEngine.gui.components;

import org.takesome.kaylasEngine.gui.components.frame.OptionGroups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Base descriptor node containing nested panel groups and components. */
public class Attributes {
    protected Map<String, OptionGroups> panels;
    protected List<ComponentAttributes> childComponents;

    public Map<String, OptionGroups> getGroups() {
        return panels == null || panels.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(panels);
    }

    public List<ComponentAttributes> getChildComponents() {
        return childComponents == null || childComponents.isEmpty()
                ? List.of()
                : Collections.unmodifiableList(childComponents);
    }

    public void addChild(ComponentAttributes childAttributes) {
        if (childComponents == null) {
            childComponents = new ArrayList<>();
        }
        childComponents.add(Objects.requireNonNull(childAttributes, "childAttributes"));
    }
}
