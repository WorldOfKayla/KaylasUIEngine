package org.takesome.kaylasEngine.service;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExecutorProgress {
    private static final int UPDATE_INTERVAL_MS = 250;

    private final ConcurrentHashMap<String, TaskProgress> progressMap = new ConcurrentHashMap<>();
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Task ID", "Task Name", "Progress", "Memory Usage", "Status"}, 0
    );
    private final JTable taskTable = new JTable(tableModel);
    private final JLabel totalTasksLabel = new JLabel();
    private final JLabel totalMemoryLabel = new JLabel();
    private final JLabel systemMemoryLabel = new JLabel();

    private JFrame statusFrame;
    private JProgressBar memoryProgressBar;
    private Timer updateTimer;
    private boolean initialized;

    public void showTaskMgr() {
        runOnEdt(() -> {
            if (!initialized) {
                initializeFrame();
                initialized = true;
            }
            statusFrame.setVisible(true);
            startUpdating();
        });
    }

    public String generateTaskId() {
        return UUID.randomUUID().toString();
    }

    public void addTask(String taskId, String taskName) {
        progressMap.put(taskId, new TaskProgress(taskName));
        runOnEdt(() -> {
            if (findTaskRow(taskId) == -1) {
                tableModel.addRow(new Object[]{taskId, taskName, 0, "0 bytes", "Not Started"});
            }
            updateStatisticsOnEdt();
        });
    }

    public void updateTask(String taskId, int progress) {
        TaskProgress taskProgress = progressMap.get(taskId);
        if (taskProgress != null) {
            taskProgress.setProgress(progress);
            runOnEdt(() -> updateTaskInTable(taskId));
        }
    }

    public void updateTaskMemoryUsage(String taskId, long memoryUsage) {
        TaskProgress taskProgress = progressMap.get(taskId);
        if (taskProgress != null) {
            taskProgress.setMemoryUsage(memoryUsage);
            runOnEdt(() -> updateTaskInTable(taskId));
        }
    }

    public void removeTask(String taskId) {
        TaskProgress taskProgress = progressMap.get(taskId);
        if (taskProgress != null) {
            taskProgress.complete();
        }
        runOnEdt(() -> {
            updateTaskInTable(taskId);
            Timer removalTimer = new Timer(500, event -> {
                progressMap.remove(taskId);
                int row = findTaskRow(taskId);
                if (row != -1) {
                    tableModel.removeRow(row);
                }
                updateStatisticsOnEdt();
            });
            removalTimer.setRepeats(false);
            removalTimer.start();
            updateStatisticsOnEdt();
        });
    }

    private void updateStatistics() {
        runOnEdt(this::updateStatisticsOnEdt);
    }

    private void updateStatisticsOnEdt() {
        int activeTasks = progressMap.size();
        long totalMemoryUsage = progressMap.values().stream().mapToLong(TaskProgress::getMemoryUsage).sum();
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        long maxMemory = Math.max(1L, heapMemoryUsage.getMax());
        long usedMemory = Math.max(0L, heapMemoryUsage.getUsed());
        int memoryPercentage = (int) Math.min(100L, (usedMemory * 100L) / maxMemory);

        totalTasksLabel.setText("Active Tasks: " + activeTasks);
        totalMemoryLabel.setText("Tracked Task Memory: " + formatMemory(totalMemoryUsage));
        systemMemoryLabel.setText("Heap Memory: " + getMemoryStats(heapMemoryUsage));

        if (memoryProgressBar != null) {
            memoryProgressBar.setValue(memoryPercentage);
            memoryProgressBar.setString(memoryPercentage + "% Used");
        }
    }

    private void startUpdating() {
        if (updateTimer != null && updateTimer.isRunning()) {
            return;
        }
        updateTimer = new Timer(UPDATE_INTERVAL_MS, event -> updateStatisticsOnEdt());
        updateTimer.setInitialDelay(0);
        updateTimer.start();
    }

    private void stopUpdating() {
        if (updateTimer != null) {
            updateTimer.stop();
            updateTimer = null;
        }
    }

    private void updateTaskInTable(String taskId) {
        int row = findTaskRow(taskId);
        if (row == -1) {
            return;
        }
        TaskProgress taskProgress = progressMap.get(taskId);
        if (taskProgress == null) {
            return;
        }
        tableModel.setValueAt(taskProgress.getProgress(), row, 2);
        tableModel.setValueAt(formatMemory(taskProgress.getMemoryUsage()), row, 3);
        tableModel.setValueAt(getTaskStatus(taskProgress), row, 4);
    }

    private int findTaskRow(String taskId) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (taskId.equals(tableModel.getValueAt(i, 0))) {
                return i;
            }
        }
        return -1;
    }

    private void initializeFrame() {
        statusFrame = new JFrame("Task Manager");
        statusFrame.setLayout(new BorderLayout());
        statusFrame.setSize(800, 600);
        statusFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        statusFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopUpdating();
            }
        });

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (screenSize.width - statusFrame.getWidth()) / 2;
        int y = (screenSize.height - statusFrame.getHeight()) / 2;
        statusFrame.setLocation(x, y);

        taskTable.setFillsViewportHeight(true);
        taskTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
        taskTable.setRowHeight(25);
        taskTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 14));
        taskTable.getTableHeader().setBorder(new EmptyBorder(5, 5, 5, 5));
        JScrollPane tableScrollPane = new JScrollPane(taskTable);

        memoryProgressBar = new JProgressBar(0, 100);
        memoryProgressBar.setStringPainted(true);

        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setBorder(BorderFactory.createTitledBorder("Memory Usage"));
        progressPanel.add(memoryProgressBar, BorderLayout.CENTER);

        JPanel statisticsPanel = new JPanel(new GridLayout(4, 1));
        statisticsPanel.setBorder(BorderFactory.createTitledBorder("Statistics"));
        statisticsPanel.add(totalTasksLabel);
        statisticsPanel.add(totalMemoryLabel);
        statisticsPanel.add(systemMemoryLabel);
        statisticsPanel.add(progressPanel);

        JButton terminateButton = new JButton("Terminate Task");
        terminateButton.addActionListener(e -> {
            int selectedRow = taskTable.getSelectedRow();
            if (selectedRow != -1) {
                String taskId = (String) tableModel.getValueAt(selectedRow, 0);
                removeTask(taskId);
            }
        });

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(tableScrollPane, BorderLayout.CENTER);
        mainPanel.add(statisticsPanel, BorderLayout.NORTH);
        mainPanel.add(terminateButton, BorderLayout.SOUTH);

        statusFrame.add(mainPanel, BorderLayout.CENTER);
        updateStatisticsOnEdt();
    }

    private String getTaskStatus(TaskProgress taskProgress) {
        if (taskProgress.isCompleted()) {
            return "Complete";
        }
        int progress = taskProgress.getProgress();
        if (progress >= 100) {
            return "Complete";
        }
        if (progress > 0) {
            return "In Progress";
        }
        return "Queued";
    }

    private String formatMemory(long memory) {
        if (memory < 1024) {
            return memory + " bytes";
        }
        if (memory < 1024L * 1024L) {
            return (memory / 1024L) + " KB";
        }
        if (memory < 1024L * 1024L * 1024L) {
            return (memory / (1024L * 1024L)) + " MB";
        }
        return (memory / (1024L * 1024L * 1024L)) + " GB";
    }

    private String getMemoryStats(MemoryUsage heapMemoryUsage) {
        long maxMemory = Math.max(1L, heapMemoryUsage.getMax());
        long usedMemory = Math.max(0L, heapMemoryUsage.getUsed());
        long freeMemory = Math.max(0L, maxMemory - usedMemory);

        return String.format("Used: %s | Free: %s | Max: %s",
                formatMemory(usedMemory),
                formatMemory(freeMemory),
                formatMemory(maxMemory));
    }

    private void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    public JFrame getStatusFrame() {
        return statusFrame;
    }
}
