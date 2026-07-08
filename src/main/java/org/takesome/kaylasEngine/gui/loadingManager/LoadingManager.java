package org.takesome.kaylasEngine.gui.loadingManager;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.FloatingWindow;
import org.takesome.kaylasEngine.utils.animation.AnimationStats;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unused")
public abstract class LoadingManager extends FloatingWindow implements AnimationStats {
    private static final long UI_QUEUE_WARN_NANOS = 250_000_000L;
    private static final long COMPONENT_STATUS_WARN_NANOS = 50_000_000L;

    protected List<LoadManagerAttributes> attributesList;
    protected String loadingText, loadingTitle, labelFont;
    protected Timer loadingTimer;
    protected JLabel loaderText, titleLabel;

    private final AtomicBoolean loadingTextUpdateQueued = new AtomicBoolean(false);
    private volatile String pendingLoadingText;
    private volatile String pendingLoadingTitle;

    protected LoadingManager(Engine engine){
        super(engine);
    }

    protected abstract void initializeLoadingFrame(int index);

    public void setLoadingText(String loadingTextKey, String loadingTitleKey) {
        long requestedAt = System.nanoTime();
        pendingLoadingText = this.engine.getLANG().getString(loadingTextKey);
        pendingLoadingTitle = this.engine.getLANG().getString(loadingTitleKey);

        if (!loadingTextUpdateQueued.compareAndSet(false, true)) {
            Engine.getLOGGER().debug(
                    "[LOAD-MGR] setLoadingText coalesced: descKey={}, titleKey={}",
                    loadingTextKey,
                    loadingTitleKey
            );
            return;
        }

        SwingUtilities.invokeLater(() -> {
            logUiQueueDelay("setLoadingText", requestedAt);
            loadingTextUpdateQueued.set(false);

            this.loadingText = pendingLoadingText;
            this.loadingTitle = pendingLoadingTitle;

            if (loaderText == null || titleLabel == null) {
                Engine.getLOGGER().debug(
                        "[LOAD-MGR] setLoadingText deferred but labels are not bound yet: loaderText={}, titleLabel={}",
                        loaderText != null,
                        titleLabel != null
                );
            }

            if (loaderText != null && !Objects.equals(loaderText.getText(), this.loadingText)) {
                loaderText.setText(this.loadingText);
            }

            if (titleLabel != null && !Objects.equals(titleLabel.getText(), this.loadingTitle)) {
                titleLabel.setText(this.loadingTitle);
            }
        });
    }

    protected void changeComponentStatus(Map<String, JComponent> componentsMap, JPanel panel, boolean enabled) {
        long requestedAt = System.nanoTime();
        SwingUtilities.invokeLater(() -> {
            logUiQueueDelay("changeComponentStatus", requestedAt);
            long startedAt = System.nanoTime();

            if (componentsMap == null) {
                Engine.getLOGGER().warn("[LOAD-MGR] changeComponentStatus skipped: componentsMap is null");
                return;
            }
            if (panel == null) {
                Engine.getLOGGER().warn("[LOAD-MGR] changeComponentStatus skipped: panel is null");
                return;
            }

            for (Map.Entry<String, JComponent> componentMap : componentsMap.entrySet()) {
                componentMap.getValue().setEnabled(enabled);
            }
            panel.revalidate();
            panel.repaint();

            long elapsed = System.nanoTime() - startedAt;
            if (elapsed >= COMPONENT_STATUS_WARN_NANOS) {
                Engine.getLOGGER().warn(
                        "[LOAD-MGR] changeComponentStatus slow: components={}, enabled={}, elapsed={} ms",
                        componentsMap.size(),
                        enabled,
                        nanosToMillis(elapsed)
                );
            } else {
                Engine.getLOGGER().debug(
                        "[LOAD-MGR] changeComponentStatus: components={}, enabled={}, elapsed={} ms",
                        componentsMap.size(),
                        enabled,
                        nanosToMillis(elapsed)
                );
            }
        });
    }

    /**
     * Provides a transparent overlay container only.
     *
     * Rendering policy belongs to the application layer, not to KaylasUIEngine.
     */
    protected JPanel getOverlay() {
        JPanel loadingOverlay = new JPanel();
        loadingOverlay.setOpaque(false);
        loadingOverlay.setLayout(null);
        return loadingOverlay;
    }


    public void setLabelFont(String labelFont) {
        this.labelFont = labelFont;
    }

    public Timer getLoadingTimer() {
        return loadingTimer;
    }

    private void logUiQueueDelay(String operation, long queuedAtNanos) {
        long delay = System.nanoTime() - queuedAtNanos;
        if (delay >= UI_QUEUE_WARN_NANOS) {
            Engine.getLOGGER().warn("[LOAD-MGR][EDT-QUEUE] {} waited {} ms", operation, nanosToMillis(delay));
        }
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }
}
