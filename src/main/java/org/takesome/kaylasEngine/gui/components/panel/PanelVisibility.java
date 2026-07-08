package org.takesome.kaylasEngine.gui.components.panel;

import org.takesome.kaylasEngine.Engine;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class PanelVisibility {
    private final Map<String, Boolean> activePanels = new HashMap<>();

    private final Engine engine;

    public PanelVisibility(Engine engine){
        this.engine = engine;
    }

    public void displayPanel(String displayString) {
        String[] panelElements = displayString.split("\\|");
        if (panelElements.length <= 1) {
            this.panelVisibility(displayString);
        } else {
            for (String panelElement : panelElements) {
                this.panelVisibility(panelElement);
            }
        }
    }
    private void panelVisibility(String panelElement) {
        String[] parts = panelElement.split("->");
        if (parts.length == 2) {
            String panelName = parts[0];
            boolean displayValue = Boolean.parseBoolean(parts[1]);
            JPanel groupPanel = engine.getGuiBuilder().getPanelsMap().get(panelName);
            if (groupPanel != null) {
                groupPanel.setVisible(displayValue);
            }
            activePanels.put(panelName, displayValue);
        }
    }
    public Map<String, Boolean> getActivePanels() {
        return activePanels;
    }
}
