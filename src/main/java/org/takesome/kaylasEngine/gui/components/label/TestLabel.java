package org.takesome.kaylasEngine.gui.components.label;

import javax.swing.*;
import java.awt.*;

public class TestLabel {
    public static void main(String[] args) {
        // Create the main application window.
        JFrame frame = new JFrame("Gradient Text");

        // Configure window dimensions and close behavior.
        frame.setSize(400, 200);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Create and configure a gradient label.
        Label label = new Label(null);  // A ComponentFactory may be supplied in an integration test.
        label.setText("Gradient Text");
        label.setFont(new Font("Arial", Font.PLAIN, 40));

        // Add the label to the content pane.
        frame.getContentPane().add(label);

        // Display the window.
        frame.setVisible(true);
    }
}
