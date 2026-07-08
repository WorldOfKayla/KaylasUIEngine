package org.takesome.kaylasEngine.gui.components.multiButton;

import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.utils.ImageUtils;

import java.awt.image.BufferedImage;

public class MultiButtonStyle {
    private BufferedImage[] splitImg;
    public int width;
    public int height;

    public MultiButtonStyle(ComponentFactory componentFactory, ComponentAttributes componentAttributes) {
        this.width = componentFactory.getStyle().getWidth();
        this.height = componentFactory.getStyle().getHeight();
        this.splitImg = splitImage(componentAttributes.getRowNum(), componentAttributes.getImgCount(), componentFactory.getEngine().getImageUtils().getLocalImage(componentFactory.getStyle().getTexture()));
    }

    public void apply(MultiButton multiButton) {
        for(BufferedImage img: this.splitImg){
            multiButton.img.add(img);
        }
    }


    public BufferedImage[] splitImage(int row, int imgCount, BufferedImage texture) {
        int width = texture.getWidth() / imgCount;
        int height = texture.getHeight() / 2;

        BufferedImage[] images = new BufferedImage[imgCount];

        for (int i = 0; i < imgCount; i++) {
            int xPos = i * width;
            int yPos = row * height; // Vertical position
            BufferedImage subImage = texture.getSubimage(xPos, yPos, width, height);
            images[i] = subImage;
        }

        return images;
    }

}

