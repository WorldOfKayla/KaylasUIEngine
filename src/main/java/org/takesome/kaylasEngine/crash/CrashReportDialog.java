package org.takesome.kaylasEngine.crash;

import org.takesome.kaylasEngine.Engine;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Engine-level crash report UI.
 *
 * <p>Applications should pass a Throwable and optional runtime context. The engine handles report
 * formatting, auto-saving, clipboard export, manual save, folder opening, and optional asynchronous
 * delivery through an application-provided {@link CrashReportSendListener}.</p>
 */
public final class CrashReportDialog {
    private static final DateTimeFormatter REPORT_FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Map<Engine, CrashReportSendListener> SEND_LISTENERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private CrashReportDialog() {
    }

    /**
     * Subscribes an application-specific asynchronous crash-report sender for one engine instance.
     * Repeated registration replaces the previous sender for that engine.
     *
     * @param engine engine instance that owns the crash dialog
     * @param listener asynchronous sender implemented by the application
     * @return removable subscription
     */
    public static CrashReportSubscription subscribe(Engine engine, CrashReportSendListener listener) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(listener, "listener");
        synchronized (SEND_LISTENERS) {
            SEND_LISTENERS.put(engine, listener);
        }
        return () -> {
            synchronized (SEND_LISTENERS) {
                if (SEND_LISTENERS.get(engine) == listener) {
                    SEND_LISTENERS.remove(engine);
                }
            }
        };
    }

    public static void show(Engine engine, Throwable throwable, Map<String, String> context, Path reportDirectory) {
        Map<String, String> contextSnapshot = context == null ? Map.of() : new LinkedHashMap<>(context);
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> show(engine, throwable, contextSnapshot, reportDirectory));
            return;
        }
        showOnEdt(engine, throwable, contextSnapshot, reportDirectory);
    }

    private static void showOnEdt(Engine engine, Throwable throwable, Map<String, String> context, Path reportDirectory) {
        Instant generatedAt = Instant.now();
        String errorText = buildCrashReport(engine, throwable, context, generatedAt);
        Path savedReport = saveCrashReport(reportDirectory, errorText, generatedAt);
        CrashReportSubmission submission = new CrashReportSubmission(
                errorText,
                context,
                savedReport,
                applicationTitle(engine),
                engineVersion(engine),
                generatedAt
        );
        CrashReportSendListener sendListener = senderFor(engine);

        JTextArea textArea = new JTextArea(errorText);
        textArea.setEditable(false);
        textArea.setLineWrap(false);
        textArea.setWrapStyleWord(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setBackground(new Color(21, 18, 28));
        textArea.setForeground(new Color(224, 220, 235));
        textArea.setCaretColor(new Color(224, 220, 235));
        textArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        textArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(820, 460));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(89, 61, 122), 1));

        JLabel titleLabel = new JLabel("Application crashed");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setForeground(new Color(245, 232, 255));

        JLabel detailsLabel = new JLabel(reportLocationText(savedReport));
        detailsLabel.setForeground(new Color(190, 180, 205));

        JLabel sendStatusLabel = new JLabel(sendListener == null
                ? "Automatic report delivery is not configured."
                : "You can send this report to the application server.");
        sendStatusLabel.setForeground(new Color(164, 153, 181));

        JPanel titleTextPanel = new JPanel(new GridLayout(0, 1, 0, 3));
        titleTextPanel.setOpaque(false);
        titleTextPanel.add(titleLabel);
        titleTextPanel.add(detailsLabel);
        titleTextPanel.add(sendStatusLabel);

        JPanel headerPanel = new JPanel(new BorderLayout(12, 0));
        headerPanel.setOpaque(false);
        Icon bugIcon = bugIcon(engine);
        if (bugIcon != null) {
            headerPanel.add(new JLabel(bugIcon), BorderLayout.WEST);
        }
        headerPanel.add(titleTextPanel, BorderLayout.CENTER);

        JButton sendButton = new JButton("Send Report");
        sendButton.setEnabled(sendListener != null);
        if (sendListener == null) {
            sendButton.setToolTipText("The application did not register a crash-report sender.");
        }
        sendButton.addActionListener(event -> sendCrashReport(
                sendButton,
                sendStatusLabel,
                sendListener,
                submission
        ));

        JButton copyButton = new JButton("Copy");
        copyButton.addActionListener(event -> copyCrashReport(engine, errorText));

        JButton saveButton = new JButton("Save As");
        saveButton.addActionListener(event -> saveCrashReportAs(engine, errorText, savedReport));

        JButton openFolderButton = new JButton("Open Folder");
        openFolderButton.setEnabled(savedReport != null && Files.isRegularFile(savedReport));
        openFolderButton.addActionListener(event -> openCrashReportFolder(engine, savedReport));

        JButton closeButton = new JButton("Close");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(sendButton);
        buttonPanel.add(copyButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(openFolderButton);
        buttonPanel.add(closeButton);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 12));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        mainPanel.setBackground(new Color(30, 24, 42));
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        Frame owner = engine == null ? null : engine.getFrame();
        JDialog dialog = new JDialog(owner, applicationTitle(engine) + " Crash Report", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(mainPanel);
        closeButton.addActionListener(event -> dialog.dispose());
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static CrashReportSendListener senderFor(Engine engine) {
        if (engine == null) {
            return null;
        }
        synchronized (SEND_LISTENERS) {
            return SEND_LISTENERS.get(engine);
        }
    }

    private static void sendCrashReport(JButton sendButton,
                                        JLabel statusLabel,
                                        CrashReportSendListener listener,
                                        CrashReportSubmission submission) {
        if (listener == null) {
            return;
        }
        sendButton.setEnabled(false);
        sendButton.setText("Sending...");
        statusLabel.setForeground(new Color(239, 184, 90));
        statusLabel.setText("Sending crash report...");

        CompletionStage<CrashReportSendResult> stage;
        try {
            stage = listener.sendCrashReport(submission);
        } catch (Throwable error) {
            completeSend(sendButton, statusLabel, null, error);
            return;
        }
        if (stage == null) {
            completeSend(
                    sendButton,
                    statusLabel,
                    null,
                    new IllegalStateException("Crash-report sender returned no completion stage.")
            );
            return;
        }
        stage.whenComplete((result, error) -> SwingUtilities.invokeLater(
                () -> completeSend(sendButton, statusLabel, result, error)
        ));
    }

    private static void completeSend(JButton sendButton,
                                     JLabel statusLabel,
                                     CrashReportSendResult result,
                                     Throwable error) {
        if (error == null) {
            String reportId = result == null ? "" : result.reportId();
            String message = result == null ? "Crash report sent." : result.message();
            sendButton.setText("Sent");
            sendButton.setEnabled(false);
            statusLabel.setForeground(new Color(79, 180, 119));
            statusLabel.setText(reportId.isBlank() ? message : message + " Reference: " + reportId);
            return;
        }

        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;
        String message = cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName()
                : cause.getMessage();
        sendButton.setText("Send Report");
        sendButton.setEnabled(true);
        statusLabel.setForeground(new Color(218, 49, 86));
        statusLabel.setText("Failed to send report: " + message);
    }

    private static String buildCrashReport(Engine engine,
                                           Throwable throwable,
                                           Map<String, String> context,
                                           Instant generatedAt) {
        StringBuilder report = new StringBuilder();
        report.append("Crash Report\n");
        report.append("============\n");
        report.append("Date: ").append(generatedAt).append("\n");
        report.append("OS: ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append("\n");
        report.append("Java: ").append(System.getProperty("java.version")).append("\n");
        report.append("User: ").append(System.getProperty("user.name")).append("\n");
        report.append("Engine: ").append(engineVersion(engine)).append("\n");
        for (Map.Entry<String, String> entry : context.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                report.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        report.append("\n");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        if (throwable == null) {
            pw.println("No throwable was provided.");
        } else {
            throwable.printStackTrace(pw);
        }
        report.append(sw);
        return report.toString();
    }

    private static Path saveCrashReport(Path reportDirectory, String errorText, Instant generatedAt) {
        Path directory = reportDirectory == null ? Path.of("crash-reports") : reportDirectory;
        try {
            Files.createDirectories(directory);
            LocalDateTime localTime = LocalDateTime.ofInstant(
                    generatedAt == null ? Instant.now() : generatedAt,
                    ZoneId.systemDefault()
            );
            String timestamp = localTime.format(REPORT_FILE_TIME);
            Path reportPath = directory.resolve("crash-" + timestamp + ".log");
            Files.writeString(reportPath, errorText, StandardCharsets.UTF_8);
            Engine.LOGGER.error("Crash report saved to {}", reportPath.toAbsolutePath());
            return reportPath;
        } catch (Exception error) {
            Engine.LOGGER.error("Unable to auto-save crash report", error);
            return null;
        }
    }

    private static String reportLocationText(Path savedReport) {
        if (savedReport == null) {
            return "The crash report could not be auto-saved. Use Save As to export it manually.";
        }
        return "Saved to: " + savedReport.toAbsolutePath();
    }

    private static void copyCrashReport(Engine engine, String errorText) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(errorText), null);
        JOptionPane.showMessageDialog(ownerComponent(engine), "Crash report copied to clipboard.",
                applicationTitle(engine), JOptionPane.INFORMATION_MESSAGE);
    }

    private static void saveCrashReportAs(Engine engine, String errorText, Path suggestedReport) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Crash Report");
        fileChooser.setSelectedFile(suggestedReport == null
                ? new File("crash-report.log")
                : suggestedReport.getFileName().toFile());
        int userSelection = fileChooser.showSaveDialog(ownerComponent(engine));
        if (userSelection != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File fileToSave = fileChooser.getSelectedFile();
        if (!fileToSave.getName().toLowerCase().endsWith(".log")) {
            fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".log");
        }
        try {
            Files.writeString(fileToSave.toPath(), errorText, StandardCharsets.UTF_8);
        } catch (IOException error) {
            JOptionPane.showMessageDialog(ownerComponent(engine), "Failed to save crash report:\n" + error.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void openCrashReportFolder(Engine engine, Path savedReport) {
        if (savedReport == null || savedReport.getParent() == null) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(savedReport.getParent().toFile());
            }
        } catch (IOException error) {
            JOptionPane.showMessageDialog(ownerComponent(engine), "Failed to open crash report folder:\n" + error.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Icon bugIcon(Engine engine) {
        try {
            return engine != null && engine.getIconUtils() != null
                    ? engine.getIconUtils().getVectorIcon("assets/ui/icons/bug.svg", 56, 56)
                    : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static java.awt.Component ownerComponent(Engine engine) {
        return engine == null ? null : engine.getFrame();
    }

    private static String applicationTitle(Engine engine) {
        if (engine == null || engine.getEngineData() == null || engine.getEngineData().getLauncherBrand() == null) {
            return "Application";
        }
        return engine.getEngineData().getLauncherBrand();
    }

    private static String engineVersion(Engine engine) {
        if (engine == null || engine.getEngineData() == null) {
            return "unknown";
        }
        return engine.getEngineData().getLauncherVersion() + '-' + engine.getEngineData().getLauncherBuild();
    }
}
