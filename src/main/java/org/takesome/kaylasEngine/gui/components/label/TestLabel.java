package org.takesome.kaylasEngine.gui.components.label;

import javax.swing.*;
import java.awt.*;

public class TestLabel {
    public static void main(String[] args) {
        // Создаем JFrame (главное окно)
        JFrame frame = new JFrame("Градиентный текст");

        // Устанавливаем размер окна и поведение при закрытии
        frame.setSize(400, 200);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Создаем и настраиваем Label с градиентом
        Label label = new Label(null);  // Параметр componentFactory можно передать в тестовом примере
        label.setText("Градиентный Текст");
        label.setFont(new Font("Arial", Font.PLAIN, 40));

        // Добавляем Label на панель
        frame.getContentPane().add(label);

        // Отображаем окно
        frame.setVisible(true);
    }
}
