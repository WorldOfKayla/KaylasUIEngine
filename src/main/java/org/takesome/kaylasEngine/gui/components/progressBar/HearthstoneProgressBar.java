package org.takesome.kaylasEngine.gui.components.progressBar;

import javax.swing.*;
import java.awt.*;
import java.util.TimerTask;

public class HearthstoneProgressBar extends JProgressBar {

    public HearthstoneProgressBar() {
        setMinimum(0);
        setMaximum(100);
        setBorderPainted(false); // Отключаем стандартную обводку
        setOpaque(false); // Делаем компонент прозрачным для кастомной отрисовки
    }

    /**
     * Устанавливает значение прогресса (0.0...1.0).
     */
    public void setProgress(double progress) {
        progress = Math.max(0.0, Math.min(1.0, progress));
        setValue((int) (progress * 100));
    }

    /**
     * Возвращает текущее значение прогресса (0.0...1.0).
     */
    public double getProgress() {
        return (double) getValue() / 100.0;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int arc = 20;

        // Фон
        g2.setColor(new Color(40, 40, 40));
        g2.fillRoundRect(0, 0, width, height, arc, arc);

        // Заливка с градиентом
        int fillWidth = (int) (width * getProgress());
        if (fillWidth > 0) {
            GradientPaint gradient = new GradientPaint(0, 0, new Color(0, 200, 0), fillWidth, 0, new Color(255, 215, 0));
            g2.setPaint(gradient);
            g2.fillRoundRect(0, 0, fillWidth, height, arc, arc);
        }

        // Обводка
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);

        if (getValue() > 0 && getValue() < 100) {
            int sparkSize = 20;
            int sparkX = Math.min(fillWidth, width - sparkSize) - sparkSize / 2;
            int sparkY = height / 2 - sparkSize / 2;
            RadialGradientPaint sparkPaint = new RadialGradientPaint(
                    new Point(sparkX + sparkSize / 2, sparkY + sparkSize / 2),
                    sparkSize / 2,
                    new float[]{0f, 1f},
                    new Color[]{Color.WHITE, new Color(255, 255, 255, 0)}
            );
            g2.setPaint(sparkPaint);
            g2.fillOval(sparkX, sparkY, sparkSize, sparkSize);
        }

        g2.dispose();
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Hearthstone Progress Bar Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 150);
        frame.setLocationRelativeTo(null);

        HearthstoneProgressBar progressBar = new HearthstoneProgressBar();
        progressBar.setPreferredSize(new Dimension(350, 50));

        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 20));
        panel.add(progressBar);
        frame.add(panel);

        frame.setVisible(true);

        Timer timer = new Timer(20, e -> {
            double currentProgress = progressBar.getProgress();
            if (currentProgress >= 1.0) {
                ((Timer) e.getSource()).stop();
            } else {
                progressBar.setProgress(currentProgress + 0.01);
            }
        });
        timer.start();
    }
}
