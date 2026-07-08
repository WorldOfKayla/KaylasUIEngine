package org.takesome.kaylasEngine.gui.components.button;

import org.takesome.kaylasEngine.gui.components.Align;
import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.utils.ImageUtils;

import javax.swing.*;
import java.awt.image.BufferedImage;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;


public class ButtonStyle {
	public boolean visible = true;
	public  int width,height;
	public String font,color;
	public float fontSize;
	public Align align;
	public BufferedImage texture;
	private final ComponentFactory componentFactory;
	private final ImageUtils imageUtils;
	public ButtonStyle(ComponentFactory componentFactory) {
		this.componentFactory = componentFactory;
		this.imageUtils = this.componentFactory.getEngine().getImageUtils();
		this.width = componentFactory.getStyle().getWidth();
		this.height = componentFactory.getStyle().getHeight();
		this.color = componentFactory.getStyle().getColor();
		this.font = componentFactory.getStyle().getFont();
		this.fontSize = componentFactory.getStyle().getFontSize();
		this.align = Align.valueOf(componentFactory.getStyle().getAlign());
		this.texture = this.componentFactory.getEngine().getImageUtils().getLocalImage(componentFactory.getStyle().getTexture());
	}
	public void apply(Button button) {
		button.setVisible(visible);
		button.setHorizontalAlignment(align == Align.LEFT ? SwingConstants.LEFT : align == Align.CENTER ? SwingConstants.CENTER : SwingConstants.RIGHT);
		button.setFont(componentFactory.getEngine().getFONTUTILS().getFont(font, fontSize));
		button.setHoverColor(hexToColor(this.componentFactory.getStyle().getHoverColor()).brighter());
		button.setForeground(hexToColor(color));
		int i = texture.getHeight() / 4;
		button.defaultTX = this.imageUtils.getTexture(texture, componentFactory.getStyle().getBorderRadius(), 0, 0, texture.getWidth(), i);
		button.rolloverTX = this.imageUtils.getTexture(texture, componentFactory.getStyle().getBorderRadius(),0, i, texture.getWidth(), i);
		button.pressedTX = this.imageUtils.getTexture(texture, componentFactory.getStyle().getBorderRadius(),0, i * 2, texture.getWidth(), i);
		button.lockedTX = this.imageUtils.getTexture(texture, componentFactory.getStyle().getBorderRadius(),0, i * 3, texture.getWidth(), i);
	}

	public BufferedImage getTexture(int startX, int startY, int subWidth, int subHeight) {
		BufferedImage buttTexture = texture.getSubimage(startX, startY, subWidth, subHeight);
		if(componentFactory.getStyle().getBorderRadius() != 0) {
			return this.componentFactory.getEngine().getImageUtils().getRoundedImage(buttTexture, componentFactory.getStyle().getBorderRadius());
		}
		return buttTexture;
	}
}
