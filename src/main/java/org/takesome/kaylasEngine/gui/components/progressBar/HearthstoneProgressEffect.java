package org.takesome.kaylasEngine.gui.components.progressBar;

import javax.swing.SwingConstants;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/** Shared painter for the Hearthstone-inspired loading-bar profile. */
final class HearthstoneProgressEffect {
    private static final long SHIMMER_CYCLE_NANOS = 1_850_000_000L;

    private HearthstoneProgressEffect() {
    }

    static boolean supportsStyle(String styleName) {
        if (styleName == null) {
            return false;
        }
        String normalized = styleName.trim().toLowerCase();
        return normalized.equals("hearthstone") || normalized.startsWith("hearthstone-");
    }

    static Rectangle contentBounds(int width, int height) {
        int insetX = clamp(Math.round(height * 0.27f), 7, 14);
        int insetY = clamp(Math.round(height * 0.20f), 5, 10);
        return new Rectangle(
                insetX,
                insetY,
                Math.max(0, width - insetX * 2),
                Math.max(0, height - insetY * 2)
        );
    }

    static void paintTrack(Graphics2D graphics, int width, int height, long elapsedNanos) {
        if (width <= 0 || height <= 0) {
            return;
        }
        configure(graphics);

        int chamfer = clamp(Math.round(height * 0.22f), 6, 13);
        Shape shadow = chamferedRect(1, 3, width - 2, height - 3, chamfer);
        graphics.setColor(new Color(0, 0, 0, 130));
        graphics.fill(shadow);

        Shape outerFrame = chamferedRect(1, 0, width - 2, height - 3, chamfer);
        graphics.setPaint(new LinearGradientPaint(
                0f,
                0f,
                0f,
                Math.max(1f, height),
                new float[]{0f, 0.18f, 0.50f, 0.78f, 1f},
                new Color[]{
                        new Color(255, 225, 141),
                        new Color(184, 132, 53),
                        new Color(89, 55, 24),
                        new Color(47, 29, 17),
                        new Color(16, 11, 10)
                }
        ));
        graphics.fill(outerFrame);
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.setColor(new Color(33, 20, 14, 245));
        graphics.draw(outerFrame);

        int metalInset = clamp(Math.round(height * 0.10f), 3, 6);
        Shape darkBevel = chamferedRect(
                metalInset,
                metalInset - 1,
                width - metalInset * 2,
                height - metalInset * 2 - 1,
                Math.max(3, chamfer - metalInset)
        );
        graphics.setPaint(new LinearGradientPaint(
                0f,
                metalInset,
                0f,
                Math.max(metalInset + 1f, height - metalInset),
                new float[]{0f, 0.24f, 0.63f, 1f},
                new Color[]{
                        new Color(91, 91, 83),
                        new Color(36, 39, 42),
                        new Color(13, 16, 20),
                        new Color(3, 5, 8)
                }
        ));
        graphics.fill(darkBevel);
        graphics.setColor(new Color(230, 188, 91, 170));
        graphics.draw(darkBevel);

        Rectangle content = contentBounds(width, height);
        Shape insetTrack = new RoundRectangle2D.Double(
                content.x,
                content.y,
                content.width,
                content.height,
                Math.max(5, content.height * 0.55),
                Math.max(5, content.height * 0.55)
        );
        graphics.setPaint(new LinearGradientPaint(
                0f,
                content.y,
                0f,
                Math.max(content.y + 1f, content.y + content.height),
                new float[]{0f, 0.16f, 0.56f, 1f},
                new Color[]{
                        new Color(22, 33, 42),
                        new Color(5, 10, 16),
                        new Color(0, 3, 8),
                        new Color(0, 0, 2)
                }
        ));
        graphics.fill(insetTrack);
        graphics.setStroke(new BasicStroke(Math.max(1f, height * 0.035f)));
        graphics.setColor(new Color(0, 0, 0, 225));
        graphics.draw(insetTrack);

        paintFrameHighlights(graphics, width, height, chamfer);
        paintEndCaps(graphics, width, height, chamfer);
        paintTrackReflection(graphics, content, elapsedNanos);
    }

    static void paintFill(Graphics2D graphics,
                          Shape fillShape,
                          int width,
                          int height,
                          long elapsedNanos,
                          float externalGlow,
                          float externalShine) {
        if (width <= 0 || height <= 0) {
            return;
        }
        configure(graphics);
        Shape oldClip = graphics.getClip();
        graphics.clip(fillShape);

        graphics.setPaint(new LinearGradientPaint(
                0f,
                0f,
                0f,
                Math.max(1f, height),
                new float[]{0f, 0.13f, 0.42f, 0.74f, 1f},
                new Color[]{
                        new Color(217, 255, 255),
                        new Color(83, 235, 255),
                        new Color(12, 191, 239),
                        new Color(0, 119, 191),
                        new Color(0, 54, 117)
                }
        ));
        graphics.fill(fillShape);

        paintGlassBands(graphics, width, height);
        paintMovingShimmer(graphics, width, height, elapsedNanos, externalGlow, externalShine);
        paintFineTexture(graphics, width, height, elapsedNanos);

        graphics.setColor(new Color(226, 255, 255, 172));
        graphics.setStroke(new BasicStroke(Math.max(0.8f, height * 0.045f)));
        graphics.draw(fillShape);
        graphics.setClip(oldClip);
    }

    static void paintLeadingEdge(Graphics2D graphics,
                                 Rectangle fillBounds,
                                 int componentWidth,
                                 int componentHeight,
                                 int orientation,
                                 boolean inverted,
                                 long elapsedNanos,
                                 float externalGlow) {
        if (fillBounds == null || fillBounds.width <= 0 || fillBounds.height <= 0) {
            return;
        }
        configure(graphics);
        float strength = Math.max(externalGlow, 0.62f);

        if (orientation == SwingConstants.VERTICAL) {
            float edgeY = inverted ? fillBounds.y + fillBounds.height : fillBounds.y;
            paintHorizontalEdge(graphics, fillBounds, edgeY, componentWidth, componentHeight, strength);
        } else {
            float edgeX = inverted ? fillBounds.x : fillBounds.x + fillBounds.width;
            paintVerticalEdge(graphics, fillBounds, edgeX, componentWidth, componentHeight, strength);
        }
    }

    private static void paintFrameHighlights(Graphics2D graphics,
                                             int width,
                                             int height,
                                             int chamfer) {
        Shape topLine = chamferedRect(2, 1, width - 4, Math.max(5, height - 5), Math.max(4, chamfer - 1));
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.setColor(new Color(255, 239, 177, 115));
        graphics.draw(topLine);

        Path2D.Float lowerShadow = new Path2D.Float();
        lowerShadow.moveTo(chamfer, height - 4);
        lowerShadow.lineTo(width - chamfer, height - 4);
        graphics.setStroke(new BasicStroke(Math.max(1f, height * 0.045f)));
        graphics.setColor(new Color(0, 0, 0, 145));
        graphics.draw(lowerShadow);
    }

    private static void paintEndCaps(Graphics2D graphics,
                                     int width,
                                     int height,
                                     int chamfer) {
        int centerY = Math.max(1, (height - 3) / 2);
        int capWidth = clamp(Math.round(height * 0.26f), 7, 14);
        paintEndCap(graphics, capWidth, centerY, true, height, chamfer);
        paintEndCap(graphics, width - capWidth, centerY, false, height, chamfer);
    }

    private static void paintEndCap(Graphics2D graphics,
                                    int centerX,
                                    int centerY,
                                    boolean left,
                                    int height,
                                    int chamfer) {
        float radius = clamp(Math.round(height * 0.12f), 3, 7);
        graphics.setPaint(new RadialGradientPaint(
                new Point2D.Float(centerX, centerY),
                radius * 1.7f,
                new float[]{0f, 0.36f, 0.72f, 1f},
                new Color[]{
                        new Color(255, 233, 150),
                        new Color(167, 110, 40),
                        new Color(68, 39, 18),
                        new Color(12, 8, 7)
                }
        ));
        graphics.fillOval(
                Math.round(centerX - radius),
                Math.round(centerY - radius),
                Math.round(radius * 2f),
                Math.round(radius * 2f)
        );
        graphics.setColor(new Color(39, 23, 14, 235));
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.drawOval(
                Math.round(centerX - radius),
                Math.round(centerY - radius),
                Math.round(radius * 2f),
                Math.round(radius * 2f)
        );

        int direction = left ? 1 : -1;
        Path2D.Float notch = new Path2D.Float();
        notch.moveTo(centerX + direction * radius * 0.35f, centerY - radius * 0.56f);
        notch.lineTo(centerX + direction * radius * 0.78f, centerY);
        notch.lineTo(centerX + direction * radius * 0.35f, centerY + radius * 0.56f);
        graphics.setStroke(new BasicStroke(Math.max(1f, height * 0.025f)));
        graphics.setColor(new Color(255, 226, 137, 150));
        graphics.draw(notch);
    }

    private static void paintTrackReflection(Graphics2D graphics,
                                             Rectangle content,
                                             long elapsedNanos) {
        if (content.width <= 0 || content.height <= 0) {
            return;
        }
        double shimmer = phase(elapsedNanos, SHIMMER_CYCLE_NANOS);
        float center = content.x + (float) ((shimmer * 1.35 - 0.18) * content.width);
        float spread = Math.max(12f, content.width * 0.09f);
        Shape oldClip = graphics.getClip();
        graphics.clip(new RoundRectangle2D.Double(
                content.x,
                content.y,
                content.width,
                content.height,
                content.height * 0.55,
                content.height * 0.55
        ));
        graphics.setPaint(new LinearGradientPaint(
                center - spread,
                0f,
                center + spread,
                0f,
                new float[]{0f, 0.5f, 1f},
                new Color[]{
                        new Color(255, 255, 255, 0),
                        new Color(93, 185, 220, 16),
                        new Color(255, 255, 255, 0)
                }
        ));
        graphics.fillRect(Math.round(center - spread), content.y,
                Math.max(1, Math.round(spread * 2f)), content.height);
        graphics.setClip(oldClip);
    }

    private static void paintGlassBands(Graphics2D graphics, int width, int height) {
        graphics.setPaint(new LinearGradientPaint(
                0f,
                0f,
                0f,
                Math.max(1f, height),
                new float[]{0f, 0.20f, 0.46f, 0.72f, 1f},
                new Color[]{
                        new Color(255, 255, 255, 150),
                        new Color(255, 255, 255, 48),
                        new Color(255, 255, 255, 0),
                        new Color(0, 34, 90, 32),
                        new Color(0, 12, 54, 108)
                }
        ));
        graphics.fillRect(0, 0, width, height);

        graphics.setColor(new Color(255, 255, 255, 74));
        graphics.fillRect(0, Math.max(1, Math.round(height * 0.16f)), width,
                Math.max(1, Math.round(height * 0.055f)));
    }

    private static void paintMovingShimmer(Graphics2D graphics,
                                           int width,
                                           int height,
                                           long elapsedNanos,
                                           float externalGlow,
                                           float externalShine) {
        double cycle = phase(elapsedNanos, SHIMMER_CYCLE_NANOS);
        float position = externalShine >= -0.5f
                ? externalShine
                : (float) (cycle * 1.42 - 0.21);
        float center = position * width;
        float spread = Math.max(14f, width * 0.115f);
        int centerAlpha = Math.min(210,
                Math.round(142f + 54f * Math.max(0.0f, externalGlow)));

        graphics.setPaint(new LinearGradientPaint(
                center - spread,
                0f,
                center + spread,
                0f,
                new float[]{0f, 0.34f, 0.47f, 0.53f, 0.66f, 1f},
                new Color[]{
                        new Color(255, 255, 255, 0),
                        new Color(202, 251, 255, 26),
                        new Color(234, 255, 255, 108),
                        new Color(255, 255, 255, centerAlpha),
                        new Color(155, 239, 255, 32),
                        new Color(255, 255, 255, 0)
                }
        ));
        graphics.fillRect(
                Math.round(center - spread),
                0,
                Math.max(1, Math.round(spread * 2f)),
                height
        );
    }

    private static void paintFineTexture(Graphics2D graphics,
                                         int width,
                                         int height,
                                         long elapsedNanos) {
        double phase = phase(elapsedNanos, SHIMMER_CYCLE_NANOS);
        graphics.setComposite(AlphaComposite.SrcOver.derive(0.22f));
        for (int x = -height; x < width + height; x += Math.max(6, height / 3)) {
            int shifted = x + (int) Math.round(phase * Math.max(6, height / 3));
            Path2D.Float streak = new Path2D.Float();
            streak.moveTo(shifted, height);
            streak.lineTo(shifted + height * 0.42f, 0);
            graphics.setStroke(new BasicStroke(Math.max(0.6f, height * 0.025f)));
            graphics.setColor(new Color(225, 253, 255, 65));
            graphics.draw(streak);
        }
        graphics.setComposite(AlphaComposite.SrcOver);
    }

    private static void paintVerticalEdge(Graphics2D graphics,
                                          Rectangle fillBounds,
                                          float edgeX,
                                          int componentWidth,
                                          int componentHeight,
                                          float strength) {
        float radiusX = Math.max(12f, componentHeight * 0.55f);
        float radiusY = Math.max(8f, fillBounds.height * 0.62f);
        graphics.setPaint(new LinearGradientPaint(
                edgeX - radiusX,
                0f,
                edgeX + radiusX,
                0f,
                new float[]{0f, 0.34f, 0.47f, 0.5f, 0.53f, 0.66f, 1f},
                new Color[]{
                        new Color(255, 255, 255, 0),
                        new Color(93, 222, 255, 25),
                        new Color(207, 251, 255, 150),
                        new Color(255, 255, 255, Math.min(255, Math.round(210 + strength * 45))),
                        new Color(160, 239, 255, 125),
                        new Color(39, 149, 239, 24),
                        new Color(255, 255, 255, 0)
                }
        ));
        graphics.fillRect(
                Math.round(edgeX - radiusX),
                fillBounds.y - 2,
                Math.round(radiusX * 2f),
                fillBounds.height + 4
        );
        int edgePixel = Math.round(edgeX);
        graphics.setColor(new Color(255, 255, 255, 242));
        graphics.fillRoundRect(
                edgePixel - 1,
                fillBounds.y + 1,
                3,
                Math.max(1, fillBounds.height - 3),
                3,
                3
        );
        graphics.setColor(new Color(255, 255, 255));
        graphics.setStroke(new BasicStroke(Math.max(1.4f, fillBounds.height * 0.065f)));
        graphics.drawLine(
                edgePixel,
                fillBounds.y + 2,
                edgePixel,
                fillBounds.y + fillBounds.height - 3
        );
    }

    private static void paintHorizontalEdge(Graphics2D graphics,
                                            Rectangle fillBounds,
                                            float edgeY,
                                            int componentWidth,
                                            int componentHeight,
                                            float strength) {
        float radius = Math.max(10f, componentWidth * 0.08f);
        graphics.setPaint(new LinearGradientPaint(
                0f,
                edgeY - radius,
                0f,
                edgeY + radius,
                new float[]{0f, 0.42f, 0.5f, 0.58f, 1f},
                new Color[]{
                        new Color(255, 255, 255, 0),
                        new Color(166, 242, 255, 90),
                        new Color(255, 255, 255, Math.min(255, Math.round(205 + strength * 45))),
                        new Color(84, 194, 255, 70),
                        new Color(255, 255, 255, 0)
                }
        ));
        graphics.fillRect(fillBounds.x - 2, Math.round(edgeY - radius),
                fillBounds.width + 4, Math.round(radius * 2f));
        graphics.setColor(new Color(245, 255, 255, 230));
        graphics.setStroke(new BasicStroke(Math.max(1.2f, fillBounds.width * 0.012f)));
        graphics.drawLine(fillBounds.x + 1, Math.round(edgeY),
                fillBounds.x + fillBounds.width - 2, Math.round(edgeY));
    }

    private static Shape chamferedRect(float x, float y, float width, float height, float chamfer) {
        float safeWidth = Math.max(0f, width);
        float safeHeight = Math.max(0f, height);
        float cut = Math.min(Math.max(0f, chamfer), Math.min(safeWidth, safeHeight) / 2f);
        Path2D.Float path = new Path2D.Float();
        path.moveTo(x + cut, y);
        path.lineTo(x + safeWidth - cut, y);
        path.lineTo(x + safeWidth, y + cut);
        path.lineTo(x + safeWidth, y + safeHeight - cut);
        path.lineTo(x + safeWidth - cut, y + safeHeight);
        path.lineTo(x + cut, y + safeHeight);
        path.lineTo(x, y + safeHeight - cut);
        path.lineTo(x, y + cut);
        path.closePath();
        return path;
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    }

    private static double phase(long elapsedNanos, long cycleNanos) {
        long safeCycle = Math.max(1L, cycleNanos);
        return Math.floorMod(elapsedNanos, safeCycle) / (double) safeCycle;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
