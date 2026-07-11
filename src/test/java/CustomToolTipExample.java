import org.takesome.kaylasEngine.gui.components.utils.tooltip.CustomTooltip;

import javax.swing.*;
import java.awt.*;

import static org.takesome.kaylasEngine.utils.FontUtils.hexToColor;

class CustomToolTipExample {
    public static void main(String[] args) {
        JFrame frame = new JFrame("Custom Tooltip Example");
        frame.setLayout(new FlowLayout());

        JButton button = new JButton("Hover over me");
        JTextField textField = new JTextField(15);
        JLabel label = new JLabel("Hover over this label");

        // Create custom tooltips using explicit style parameters.
        CustomTooltip customTooltip = new CustomTooltip(hexToColor("#000000c4"), Color.WHITE, 15, new Font("Arial", Font.PLAIN, 12));
        customTooltip.attachToComponent(button, "This is a button tooltip", 2000);

        customTooltip.attachToComponent(textField, "This is a text field tooltip", 2000);

        customTooltip.attachToComponent(label, "This is a label tooltip", 2000);

        frame.add(button);
        frame.add(textField);
        frame.add(label);

        frame.setSize(300, 200);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
