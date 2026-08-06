package org.takesome.kaylasEngine.gui.components.progressBar;

import javax.imageio.ImageIO;
import javax.accessibility.AccessibleRole;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Headless visual-contract verification for the Hearthstone progress profile. */
public final class HearthstoneProgressBarVerification {
    private static final double EPSILON = 0.0001;

    private HearthstoneProgressBarVerification() {
    }

    public static void verify() {
        require(HearthstoneProgressEffect.supportsStyle("hearthstone"),
                "canonical hearthstone progress style was not recognized");
        require(HearthstoneProgressEffect.supportsStyle("hearthstone-loading"),
                "hearthstone style variants were not recognized");
        require(!HearthstoneProgressEffect.supportsStyle("progressMini"),
                "ordinary progress style was classified as hearthstone");

        ProgressBar progressBar = new ProgressBar();
        progressBar.setStyleName("hearthstone");
        require(progressBar.getAccessibleContext() != null,
                "ProgressBar AccessibleContext is missing");
        require(AccessibleRole.PROGRESS_BAR.equals(
                        progressBar.getAccessibleContext().getAccessibleRole()),
                "ProgressBar exposes the wrong accessible role");
        progressBar.setSize(430, 44);
        progressBar.setRange(0, 100, 64);
        progressBar.setStringPainted(true);
        progressBar.setShowPercent(false);
        progressBar.setString("Corralling Murlocs");
        progressBar.setTextOffsetX(-5);
        progressBar.doLayout();
        require(progressBar.getTextOffsetX() == -5,
                "Progress text left offset was not applied");

        progressBar.setValueImmediately(4);
        require(progressBar.getValue() == 4 && progressBar.getFillLayer().getWidth() > 0,
                "Immediate progress value did not create visible fill");
        progressBar.setValueImmediately(64);

        Rectangle stableFrameBounds = progressBar.getTrackLayer().getBounds();
        Rectangle stableFillBounds = progressBar.getFillLayer().getBounds();
        Rectangle stableTextBounds = progressBar.getTextLabel().getBounds();
        BufferedImage idleImage = render(progressBar);
        PixelProfile idle = profile(idleImage);

        require(idle.goldPixels() > 500,
                "Hearthstone frame does not contain enough gold/bronze structure: " + idle.goldPixels());
        require(idle.cyanPixels() > 1_000,
                "Hearthstone fill does not contain enough cyan glass: " + idle.cyanPixels());
        require(idle.darkInsetPixels() > 1_000,
                "Hearthstone frame does not contain a deep dark inset: " + idle.darkInsetPixels());
        require(idle.brightEdgePixels() > 40,
                "Hearthstone fill does not contain a bright leading edge: " + idle.brightEdgePixels());

        // Entrance starts above the opening and slightly oversized. Only content transforms.
        progressBar.setContentAnimationScale(1.05, 1.05);
        progressBar.setContentAnimationOffsetY(-10);
        require(stableFrameBounds.equals(progressBar.getTrackLayer().getBounds()),
                "Hearthstone frame moved during oversized entrance preparation");
        require(progressBar.getFillLayer().getY() == stableFillBounds.y - 10,
                "Hearthstone fill did not begin above the frame");
        require(progressBar.getTextLabel().getY() == stableTextBounds.y - 10,
                "Hearthstone progress text did not follow entrance content");
        require(close(progressBar.getContentAnimationScaleX(), 1.05)
                        && close(progressBar.getContentAnimationScaleY(), 1.05),
                "Hearthstone entrance content did not begin exactly five percent oversized");
        BufferedImage entranceStartImage = render(progressBar);
        PixelProfile entranceStart = profile(entranceStartImage);
        require(entranceStart.goldPixels() >= idle.goldPixels() * 0.90,
                "Hearthstone frame changed while entrance content was above it");
        require(entranceStart.cyanPixels() > idle.cyanPixels() / 5,
                "Hearthstone entrance begins fully hidden instead of visibly sliding in");
        require(entranceStart.cyanPixels() < idle.cyanPixels() * 0.80,
                "Hearthstone entrance no longer starts partially clipped by the fixed opening");

        // The content first finishes moving at 105%, then settles to exact scale 1.0.
        progressBar.setContentAnimationOffsetY(0);
        progressBar.setContentAnimationScale(1.05, 1.05);
        require(stableFrameBounds.equals(progressBar.getTrackLayer().getBounds()),
                "Hearthstone frame moved during entrance settling");
        require(close(progressBar.getContentAnimationScaleX(), 1.05)
                        && close(progressBar.getContentAnimationScaleY(), 1.05),
                "Hearthstone entrance changed scale before finishing its movement");
        BufferedImage entranceSettleImage = render(progressBar);
        PixelProfile entranceSettle = profile(entranceSettleImage);
        require(entranceSettle.cyanPixels() > idle.cyanPixels() * 0.75,
                "Hearthstone entrance did not visibly settle into the opening");

        progressBar.resetContentAnimation();
        require(progressBar.getContentAnimationOffsetY() == 0,
                "Hearthstone content offset did not reset after entrance");
        require(close(progressBar.getContentAnimationScaleX(), 1.0)
                        && close(progressBar.getContentAnimationScaleY(), 1.0),
                "Hearthstone content scale did not reset after entrance");

        // Exit first contracts in place without moving the frame.
        progressBar.setContentAnimationScale(0.95, 0.95);
        require(stableFrameBounds.equals(progressBar.getTrackLayer().getBounds()),
                "Hearthstone frame moved during exit contraction");
        require(progressBar.getFillLayer().getBounds().equals(stableFillBounds),
                "Hearthstone fill bounds moved before the exit flight");
        BufferedImage exitContractImage = render(progressBar);
        PixelProfile exitContract = profile(exitContractImage);
        require(exitContract.goldPixels() >= idle.goldPixels() * 0.90,
                "Hearthstone frame changed during exit contraction");
        require(exitContract.cyanPixels() < idle.cyanPixels()
                        && exitContract.cyanPixels() > idle.cyanPixels() * 0.70,
                "Hearthstone five-percent exit contraction is missing or excessively strong");

        // The contracted content then accelerates downward and disappears behind the opening mask.
        progressBar.setContentAnimationScale(0.95, 0.95);
        progressBar.setContentAnimationOffsetY(48);
        require(stableFrameBounds.equals(progressBar.getTrackLayer().getBounds()),
                "Hearthstone frame moved during accelerated exit");
        require(progressBar.getFillLayer().getY() == stableFillBounds.y + 48,
                "Hearthstone fill did not move below the frame");
        require(progressBar.getTextLabel().getY() == stableTextBounds.y + 48,
                "Hearthstone progress text did not leave with the fill");
        BufferedImage exitFlightImage = render(progressBar);
        PixelProfile exitFlight = profile(exitFlightImage);
        require(exitFlight.goldPixels() >= idle.goldPixels() * 0.90,
                "Hearthstone frame disappeared during progress exit");
        require(exitFlight.cyanPixels() < idle.cyanPixels() / 5,
                "Hearthstone content remained visible after flying below the opening");

        progressBar.resetContentAnimation();
        require(progressBar.getTrackLayer().getBounds().equals(stableFrameBounds),
                "Hearthstone frame changed after resetting content animation");

        try {
            Path reports = Path.of("build", "reports");
            Files.createDirectories(reports);
            ImageIO.write(idleImage, "png", reports.resolve("hearthstone-progress-verification.png").toFile());
            ImageIO.write(
                    entranceStartImage,
                    "png",
                    reports.resolve("hearthstone-progress-entrance-start-verification.png").toFile()
            );
            ImageIO.write(
                    entranceSettleImage,
                    "png",
                    reports.resolve("hearthstone-progress-entrance-settle-verification.png").toFile()
            );
            ImageIO.write(
                    exitContractImage,
                    "png",
                    reports.resolve("hearthstone-progress-exit-contract-verification.png").toFile()
            );
            ImageIO.write(
                    exitFlightImage,
                    "png",
                    reports.resolve("hearthstone-progress-exit-flight-verification.png").toFile()
            );
        } catch (Exception error) {
            throw new IllegalStateException("Unable to write Hearthstone progress verification renders", error);
        }
    }

    private static BufferedImage render(ProgressBar progressBar) {
        BufferedImage image = new BufferedImage(430, 44, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            progressBar.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static PixelProfile profile(BufferedImage image) {
        int goldPixels = 0;
        int cyanPixels = 0;
        int darkInsetPixels = 0;
        int brightEdgePixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24 & 0xff;
                int red = argb >>> 16 & 0xff;
                int green = argb >>> 8 & 0xff;
                int blue = argb & 0xff;
                if (alpha < 100) {
                    continue;
                }
                if (red > 105 && red > green + 22 && green > blue + 12) {
                    goldPixels++;
                }
                if (green > 135 && blue > 175 && blue > red + 18) {
                    cyanPixels++;
                }
                if (red < 28 && green < 38 && blue < 52) {
                    darkInsetPixels++;
                }
                if (red > 224 && green > 238 && blue > 238) {
                    brightEdgePixels++;
                }
            }
        }
        return new PixelProfile(goldPixels, cyanPixels, darkInsetPixels, brightEdgePixels);
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= EPSILON;
    }

    private record PixelProfile(
            int goldPixels,
            int cyanPixels,
            int darkInsetPixels,
            int brightEdgePixels
    ) { }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
