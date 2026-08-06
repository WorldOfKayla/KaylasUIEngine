package org.takesome.kaylasEngine.gui.dialog;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Theme-safe message dialogs used by the engine and applications built on it.
 *
 * <p>Standard {@link JOptionPane} labels may inherit a light theme foreground while the option
 * pane itself uses a light fallback background. This renderer supplies an explicit text component
 * with verified glyph coverage and contrast, preventing visually empty message windows.</p>
 */
public final class MessageDialogs {
    public static final String EMPTY_MESSAGE_FALLBACK = "No message details available.";
    private static final int MIN_COLUMNS = 28;
    private static final int MAX_COLUMNS = 68;
    private static final int MAX_VISIBLE_ROWS = 12;

    private MessageDialogs() {
    }

    /** Shows a message dialog on the Swing event-dispatch thread. */
    public static void show(
            Component parent,
            String message,
            String title,
            int messageType,
            Font preferredFont
    ) {
        Runnable display = () -> showNow(parent, message, title, messageType, preferredFont);
        if (SwingUtilities.isEventDispatchThread()) {
            display.run();
        } else {
            SwingUtilities.invokeLater(display);
        }
    }

    /** Shows a message dialog immediately. The caller should already be on the EDT. */
    public static void showNow(
            Component parent,
            String message,
            String title,
            int messageType,
            Font preferredFont
    ) {
        JOptionPane.showMessageDialog(
                parent,
                createMessageComponent(message, preferredFont),
                normalizeTitle(title),
                messageType
        );
    }

    /** Creates the message component used inside a {@link JOptionPane}. */
    public static JComponent createMessageComponent(String message, Font preferredFont) {
        String normalized = normalizeMessage(message);
        int columns = preferredColumns(normalized);
        int rows = estimatedRows(normalized, columns);

        JTextArea text = new JTextArea(normalized, Math.min(rows, MAX_VISIBLE_ROWS), columns);
        text.setEditable(false);
        text.setFocusable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setBorder(new EmptyBorder(5, 2, 5, 2));
        text.setFont(readableFont(preferredFont, normalized));
        text.setForeground(readableForeground());
        text.setCaretColor(text.getForeground());
        text.setDisabledTextColor(text.getForeground());

        if (rows <= MAX_VISIBLE_ROWS) {
            return text;
        }

        JScrollPane scrollPane = new JScrollPane(text);
        scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setPreferredSize(new Dimension(560, 260));
        return scrollPane;
    }

    public static String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return EMPTY_MESSAGE_FALLBACK;
        }
        return message.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    public static Font readableFont(Font preferredFont, String message) {
        Font uiFont = UIManager.getFont("Label.font");
        float size = preferredFont != null && preferredFont.getSize2D() > 0
                ? preferredFont.getSize2D()
                : uiFont != null && uiFont.getSize2D() > 0 ? uiFont.getSize2D() : 12.0f;

        Font[] candidates = {
                preferredFont,
                uiFont,
                new Font(Font.DIALOG, Font.PLAIN, Math.max(12, Math.round(size))),
                new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(12, Math.round(size)))
        };
        for (Font candidate : candidates) {
            if (candidate != null && candidate.canDisplayUpTo(message) == -1) {
                return candidate.deriveFont(size);
            }
        }
        return new Font(Font.DIALOG, Font.PLAIN, Math.max(12, Math.round(size)));
    }

    public static Color readableForeground() {
        Color background = UIManager.getColor("OptionPane.background");
        if (background == null) {
            background = UIManager.getColor("Panel.background");
        }
        if (background == null) {
            background = Color.DARK_GRAY;
        }

        Color foreground = UIManager.getColor("OptionPane.messageForeground");
        if (!hasReadableContrast(foreground, background)) {
            foreground = UIManager.getColor("Label.foreground");
        }
        if (!hasReadableContrast(foreground, background)) {
            Color black = Color.BLACK;
            Color white = Color.WHITE;
            foreground = contrastRatio(black, background) >= contrastRatio(white, background)
                    ? black
                    : white;
        }
        return foreground;
    }

    public static boolean hasReadableContrast(Color foreground, Color background) {
        return foreground != null
                && background != null
                && foreground.getAlpha() > 0
                && contrastRatio(foreground, background) >= 3.0d;
    }

    public static double contrastRatio(Color first, Color second) {
        double firstLuminance = relativeLuminance(first);
        double secondLuminance = relativeLuminance(second);
        double lighter = Math.max(firstLuminance, secondLuminance);
        double darker = Math.min(firstLuminance, secondLuminance);
        return (lighter + 0.05d) / (darker + 0.05d);
    }

    private static double relativeLuminance(Color color) {
        double red = linearChannel(color.getRed() / 255.0d);
        double green = linearChannel(color.getGreen() / 255.0d);
        double blue = linearChannel(color.getBlue() / 255.0d);
        return 0.2126d * red + 0.7152d * green + 0.0722d * blue;
    }

    private static double linearChannel(double channel) {
        return channel <= 0.04045d
                ? channel / 12.92d
                : Math.pow((channel + 0.055d) / 1.055d, 2.4d);
    }

    private static int preferredColumns(String message) {
        int longestLine = 0;
        for (String line : message.split("\n", -1)) {
            longestLine = Math.max(longestLine, line.length());
        }
        return Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, longestLine + 2));
    }

    private static int estimatedRows(String message, int columns) {
        int rows = 0;
        for (String line : message.split("\n", -1)) {
            rows += Math.max(1, (line.length() + Math.max(1, columns) - 1) / Math.max(1, columns));
        }
        return Math.max(1, rows);
    }

    private static String normalizeTitle(String title) {
        return title == null || title.isBlank() ? "Kaylas UI Engine" : title.strip();
    }
}
