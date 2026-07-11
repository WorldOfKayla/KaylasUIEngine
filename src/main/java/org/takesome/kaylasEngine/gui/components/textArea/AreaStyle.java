package org.takesome.kaylasEngine.gui.components.textArea;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;

import java.awt.*;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

public class AreaStyle {
	public String fontName;
	public float fontSize;
	public Color idleColor;
	public Color activeColor;

	public AreaStyle(ComponentFactory componentFactory) {
		this.fontName = componentFactory.getStyle().getFont();
		this.fontSize = componentFactory.getStyle().getFontSize();
		this.idleColor = hexToColor(componentFactory.getStyle().getColor());
		this.activeColor = hexToColor(componentFactory.getStyle().getColor());
	}

	public void apply(TextArea textArea) {
		textArea.setTextColor(activeColor);
	}
}
