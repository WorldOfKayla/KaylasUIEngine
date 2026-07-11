package org.takesome.kaylasEngine.gui.components.tabs;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Generic composite tab container.
 *
 * <p>The component has no settings-specific behavior. Pages are arbitrary engine components and
 * can be appended through the 2.2 configuration-extension registry.</p>
 */
public final class Tabs extends JPanel {
    public static final String PLACEMENT_TOP = "top";
    public static final String PLACEMENT_BOTTOM = "bottom";
    public static final String PLACEMENT_LEFT = "left";
    public static final String PLACEMENT_RIGHT = "right";

    private final JPanel headerPanel = new JPanel();
    private final JPanel contentPanel = new JPanel();
    private final CardLayout contentLayout = new CardLayout();
    private final Map<String, TabEntry> entries = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<TabChangeListener> listeners = new CopyOnWriteArrayList<>();

    private final String placement;
    private String selectedTabId;

    public Tabs(String placement, int gap) {
        this.placement = normalizePlacement(placement);
        setLayout(new BorderLayout(Math.max(0, gap), Math.max(0, gap)));
        setOpaque(false);

        configureHeader(gap);
        contentPanel.setLayout(contentLayout);
        contentPanel.setOpaque(false);

        add(headerPanel, borderPosition(this.placement));
        add(contentPanel, BorderLayout.CENTER);
    }

    public void addTab(TabDefinition definition, JComponent content) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(content, "content");
        if (entries.containsKey(definition.id())) {
            throw new IllegalArgumentException("Duplicate tab id: " + definition.id());
        }

        JButton button = new JButton(definition.title(), definition.icon());
        button.setName(definition.id() + ".tabButton");
        button.setEnabled(definition.enabled());
        button.setVisible(definition.visible());
        button.setFocusPainted(false);
        button.setMargin(new Insets(4, 10, 4, 10));
        button.addActionListener(event -> selectTab(definition.id(), "user"));

        JPanel page = new JPanel(new BorderLayout());
        page.setName(definition.id() + ".tabPage");
        page.setOpaque(false);
        page.add(content, BorderLayout.CENTER);

        TabEntry entry = new TabEntry(definition, button, page, content);
        entries.put(definition.id(), entry);
        headerPanel.add(button);
        contentPanel.add(page, definition.id());

        if (selectedTabId == null && definition.visible() && definition.enabled()) {
            selectInitial(definition.id());
        } else {
            updateButtonState();
        }
        revalidate();
        repaint();
    }

    public boolean removeTab(String tabId) {
        TabEntry removed = entries.remove(normalizeId(tabId));
        if (removed == null) {
            return false;
        }
        headerPanel.remove(removed.button());
        contentPanel.remove(removed.page());
        if (Objects.equals(selectedTabId, removed.definition().id())) {
            selectedTabId = null;
            selectFirstAvailable("remove");
        }
        revalidate();
        repaint();
        return true;
    }

    public boolean selectTab(String tabId) {
        return selectTab(tabId, "api");
    }

    public boolean selectTab(String tabId, String source) {
        TabEntry target = entries.get(normalizeId(tabId));
        if (target == null || !target.button().isVisible() || !target.button().isEnabled()) {
            return false;
        }
        if (Objects.equals(selectedTabId, target.definition().id())) {
            return true;
        }

        String previous = selectedTabId;
        TabChangeEvent event = new TabChangeEvent(
                previous,
                target.definition().id(),
                indexOf(target.definition().id()),
                source == null || source.isBlank() ? "api" : source
        );
        listeners.forEach(listener -> listener.tabChanging(event));

        selectedTabId = target.definition().id();
        contentLayout.show(contentPanel, selectedTabId);
        updateButtonState();
        listeners.forEach(listener -> listener.tabChanged(event));
        return true;
    }

    public boolean next() {
        return move(1, "next");
    }

    public boolean previous() {
        return move(-1, "previous");
    }

    public boolean setTabEnabled(String tabId, boolean enabled) {
        TabEntry entry = entries.get(normalizeId(tabId));
        if (entry == null) {
            return false;
        }
        entry.button().setEnabled(enabled);
        if (!enabled && Objects.equals(selectedTabId, entry.definition().id())) {
            selectFirstAvailable("disabled");
        }
        return true;
    }

    public boolean setTabVisible(String tabId, boolean visible) {
        TabEntry entry = entries.get(normalizeId(tabId));
        if (entry == null) {
            return false;
        }
        entry.button().setVisible(visible);
        if (!visible && Objects.equals(selectedTabId, entry.definition().id())) {
            selectFirstAvailable("hidden");
        }
        revalidate();
        repaint();
        return true;
    }

    /**
     * Updates a tab title without rebuilding its content.
     *
     * @param tabId tab identifier.
     * @param title new display title; {@code null} clears the title.
     * @return {@code true} when the tab exists.
     */
    public boolean setTabTitle(String tabId, String title) {
        TabEntry entry = entries.get(normalizeId(tabId));
        if (entry == null) {
            return false;
        }
        entry.button().setText(title == null ? "" : title);
        headerPanel.revalidate();
        headerPanel.repaint();
        return true;
    }

    public String getSelectedTabId() {
        return selectedTabId;
    }

    public int getSelectedIndex() {
        return selectedTabId == null ? -1 : indexOf(selectedTabId);
    }

    public int getTabCount() {
        return entries.size();
    }

    public List<String> getTabIds() {
        return List.copyOf(entries.keySet());
    }

    public JComponent getTabContent(String tabId) {
        TabEntry entry = entries.get(normalizeId(tabId));
        return entry == null ? null : entry.content();
    }

    public String getPlacement() {
        return placement;
    }

    public AutoCloseable addTabChangeListener(TabChangeListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    private void configureHeader(int gap) {
        headerPanel.setOpaque(false);
        if (PLACEMENT_LEFT.equals(placement) || PLACEMENT_RIGHT.equals(placement)) {
            headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        } else {
            headerPanel.setLayout(new FlowLayout(FlowLayout.LEADING, Math.max(0, gap), 0));
        }
    }

    private boolean move(int direction, String source) {
        if (entries.isEmpty()) {
            return false;
        }
        List<TabEntry> available = entries.values().stream()
                .filter(entry -> entry.button().isVisible() && entry.button().isEnabled())
                .toList();
        if (available.isEmpty()) {
            return false;
        }
        int current = 0;
        for (int index = 0; index < available.size(); index++) {
            if (Objects.equals(available.get(index).definition().id(), selectedTabId)) {
                current = index;
                break;
            }
        }
        int next = Math.floorMod(current + direction, available.size());
        return selectTab(available.get(next).definition().id(), source);
    }

    private void selectInitial(String tabId) {
        selectedTabId = tabId;
        contentLayout.show(contentPanel, tabId);
        updateButtonState();
    }

    private void selectFirstAvailable(String source) {
        for (TabEntry entry : entries.values()) {
            if (entry.button().isVisible() && entry.button().isEnabled()) {
                selectTab(entry.definition().id(), source);
                return;
            }
        }
        selectedTabId = null;
        updateButtonState();
    }

    private void updateButtonState() {
        entries.forEach((id, entry) -> {
            boolean selected = Objects.equals(id, selectedTabId);
            entry.button().putClientProperty("kaylas.tabs.selected", selected);
            entry.button().setSelected(selected);
        });
    }

    private int indexOf(String tabId) {
        int index = 0;
        for (String id : entries.keySet()) {
            if (id.equals(tabId)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static String normalizePlacement(String placement) {
        String normalized = placement == null ? PLACEMENT_TOP : placement.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case PLACEMENT_BOTTOM, PLACEMENT_LEFT, PLACEMENT_RIGHT -> normalized;
            default -> PLACEMENT_TOP;
        };
    }

    private static String borderPosition(String placement) {
        return switch (placement) {
            case PLACEMENT_BOTTOM -> BorderLayout.SOUTH;
            case PLACEMENT_LEFT -> BorderLayout.WEST;
            case PLACEMENT_RIGHT -> BorderLayout.EAST;
            default -> BorderLayout.NORTH;
        };
    }

    private static String normalizeId(String tabId) {
        return tabId == null ? "" : tabId.trim();
    }

    private record TabEntry(
            TabDefinition definition,
            JButton button,
            JPanel page,
            JComponent content
    ) {
    }
}
