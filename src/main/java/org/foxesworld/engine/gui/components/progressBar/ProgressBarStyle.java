package org.foxesworld.engine.gui.components.progressBar;

import org.foxesworld.engine.gui.components.ComponentFactory;

import javax.swing.*;

import static org.foxesworld.engine.utils.FontUtils.hexToColor;

public class ProgressBarStyle {
    private final String background, foreground,border;
    private final ComponentFactory componentFactory;

    public ProgressBarStyle(ComponentFactory componentFactory) {
        this.componentFactory = componentFactory;
        this.background = componentFactory.getStyle().getBackground();
        this.foreground = componentFactory.getStyle().getColor();
        this.border = componentFactory.getStyle().getBorderColor();
    }

    public void apply(JProgressBar progressBar) {
        if (border != null && !border.isBlank()) {
            progressBar.setBorder(BorderFactory.createLineBorder(hexToColor(border)));
        } else {
            progressBar.setBorder(null);
        }
        progressBar.setBackground(hexToColor(this.background));
        progressBar.setForeground(hexToColor(this.foreground));
        this.setTexture(progressBar, componentFactory.getStyle().getTexture());
    }

    private void setTexture(JProgressBar progressBar, String imagePath) {
        progressBar.setUI(new TexturedProgressBar(componentFactory, imagePath));
    }
}
