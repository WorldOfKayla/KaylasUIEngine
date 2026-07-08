package org.takesome.kaylasEngine.gui.components.label;

import org.takesome.kaylasEngine.gui.components.ComponentFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.Serial;
import java.util.Arrays;
import java.util.List;

public class Label extends JLabel {
	@Serial
	private static final long serialVersionUID = 1L;

	private final ComponentFactory componentFactory;

	public Label(ComponentFactory componentFactory) {
		this.componentFactory = componentFactory;

		String localeKey = componentFactory.getComponentAttribute().getLocaleKey();
		if (localeKey != null) {
			setText(componentFactory.getEngine().getLANG().getString(localeKey));
		}

		setOpaque(componentFactory.getStyle().isOpaque());

		Dimension preferredSize = new Dimension(
				(int) componentFactory.getBounds().getWidth(),
				(int) componentFactory.getBounds().getHeight()
		);
		setPreferredSize(preferredSize);

		String alignment = componentFactory.getComponentAttribute().getAlignment();
		if (alignment != null) {
			setHorizontalAlignment(LabelAlignment.fromString(alignment).getType());
		}

		String border = componentFactory.getComponentAttribute().getBorder();
		if (border != null) {
			setBorder(parseBorder(border));
		}
	}

	private EmptyBorder parseBorder(String border) {
		List<Integer> borderValues = Arrays.stream(border.split(","))
				.map(Integer::parseInt)
				.toList();
		if (borderValues.size() != 4) {
			throw new IllegalArgumentException("Border must have exactly 4 values");
		}
		return new EmptyBorder(
				borderValues.get(0),
				borderValues.get(1),
				borderValues.get(2),
				borderValues.get(3)
		);
	}

	public void setTextColor(Color color) {
		super.setForeground(color);
		repaint();
	}
}
