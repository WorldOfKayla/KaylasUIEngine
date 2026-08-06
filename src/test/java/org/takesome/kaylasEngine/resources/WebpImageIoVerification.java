package org.takesome.kaylasEngine.resources;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;

public final class WebpImageIoVerification {
    private WebpImageIoVerification() {
    }

    public static void main(String[] args) throws Exception {
        ImageIO.scanForPlugins();
        if (!ImageIO.getImageReadersByFormatName("webp").hasNext()) {
            throw new AssertionError("ImageIO WebP reader was not registered");
        }
        if (!ImageIO.getImageWritersByFormatName("webp").hasNext()) {
            throw new AssertionError("ImageIO WebP writer was not registered");
        }

        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xFFFF0000);
        source.setRGB(1, 0, 0xFF00FF00);
        source.setRGB(0, 1, 0xFF0000FF);
        source.setRGB(1, 1, 0xFFFFFFFF);

        byte[] encoded;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(source, "webp", output)) {
                throw new AssertionError("ImageIO rejected the WebP writer");
            }
            encoded = output.toByteArray();
        }
        BufferedImage decoded;
        try (ByteArrayInputStream input = new ByteArrayInputStream(encoded)) {
            decoded = ImageIO.read(input);
        }
        if (decoded == null || decoded.getWidth() != 2 || decoded.getHeight() != 2) {
            throw new AssertionError("Generated WebP image was not decoded");
        }

        if (args.length > 0 && !args[0].isBlank()) {
            try (var input = URI.create(args[0]).toURL().openStream()) {
                BufferedImage remote = ImageIO.read(input);
                if (remote == null) {
                    throw new AssertionError("Remote WebP image was not decoded: " + args[0]);
                }
                System.out.printf("Remote WebP decoded: %dx%d%n", remote.getWidth(), remote.getHeight());
            }
        }
        System.out.printf("WebP ImageIO verification passed: encodedBytes=%d%n", encoded.length);
    }
}
