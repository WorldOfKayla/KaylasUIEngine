package org.takesome.kaylasEngine.gui.components.fileSelector;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.components.CompositeComponent;
import org.takesome.kaylasEngine.gui.components.button.Button;
import org.takesome.kaylasEngine.gui.components.button.ButtonStyle;
import org.takesome.kaylasEngine.gui.components.textfield.TextField;
import org.takesome.kaylasEngine.gui.components.textfield.TextFieldStyle;
import org.takesome.kaylasEngine.gui.styles.StyleAttributes;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Engine-native file selector: textured {@link TextField} + textured {@link Button}.
 *
 * <p>No internal Swing panel is used, so the component remains transparent and layoutConfig-friendly.</p>
 */
public class FileSelector extends CompositeComponent {
    private static final int DEFAULT_BUTTON_WIDTH = 42;
    private static final int FIELD_BUTTON_GAP = 4;

    private final ComponentFactory componentFactory;
    private final ComponentAttributes attributes;
    private final TextField filePathField;
    private final Button browseButton;
    private final JFileChooser fileChooser;

    public FileSelector(ComponentFactory componentFactory, SelectionMode selectionMode) {
        super(LayoutMode.ABSOLUTE);
        this.componentFactory = componentFactory;
        this.attributes = componentFactory.getComponentAttribute();
        super.componentFactory = componentFactory;

        setName(attributes.getComponentId());
        setOpaque(false);
        setVisible(true);
        setLayoutConfig(attributes.getLayoutConfig());
        applyRootSize();

        Map<String, String> styles = attributes.getStyles();
        this.filePathField = createTextField(styleName(styles, "textField"));
        this.browseButton = createBrowseButton(styleName(styles, "button"));
        this.fileChooser = new JFileChooser();

        configureFileChooser(attributes.getFileExtensions(), selectionMode);
        configureLayout();
        browseButton.addActionListener(new BrowseButtonListener());
    }

    private void applyRootSize() {
        Rectangle bounds = attributes.getBounds();
        int width = Math.max(1, bounds.width);
        int height = Math.max(1, bounds.height);
        Dimension size = new Dimension(width, height);
        setBounds(bounds);
        setPreferredSize(size);
        setMinimumSize(size);
        setSize(size);
    }

    private TextField createTextField(String styleName) {
        StyleAttributes style = componentFactory.getEngine().getStyleProvider().getStyle("textField", styleName);
        return componentFactory.withStyle(style, () -> {
            TextField textField = new TextField(componentFactory);
            new TextFieldStyle(componentFactory).apply(textField);
            textField.setName(childName("Text"));
            textField.setOpaque(false);
            textField.setEditable(false);
            textField.setFocusable(false);
            return textField;
        });
    }

    private Button createBrowseButton(String styleName) {
        StyleAttributes style = componentFactory.getEngine().getStyleProvider().getStyle("button", styleName);
        return componentFactory.withStyle(style, () -> {
            Button button = new Button(
                    componentFactory,
                    componentFactory.getIconUtils().getIcon(attributes),
                    ""
            );
            new ButtonStyle(componentFactory).apply(button);
            button.setName(childName("Button"));
            button.setOpaque(false);
            return button;
        });
    }

    private void configureLayout() {
        ComponentAttributes.LayoutConfig config = attributes.getLayoutConfig();
        if (config != null) {
            applyLayoutConfig(filePathField, config.getTextField());
            applyLayoutConfig(browseButton, config.getButton());
        }
        if (filePathField.getWidth() <= 0 || filePathField.getHeight() <= 0
                || browseButton.getWidth() <= 0 || browseButton.getHeight() <= 0) {
            applyDefaultBounds();
        }
        addSubComponent(filePathField);
        addSubComponent(browseButton);
    }

    private void applyDefaultBounds() {
        Rectangle bounds = attributes.getBounds();
        int width = Math.max(1, bounds.width);
        int height = Math.max(1, bounds.height);
        int buttonWidth = resolveButtonWidth(height);
        int fieldWidth = Math.max(1, width - buttonWidth - FIELD_BUTTON_GAP);
        filePathField.setBounds(0, 0, fieldWidth, height);
        browseButton.setBounds(fieldWidth + FIELD_BUTTON_GAP, 0, buttonWidth, height);
    }

    private int resolveButtonWidth(int height) {
        int iconWidth = Math.max(0, attributes.getIconWidth());
        if (iconWidth > 0) {
            return Math.max(height, iconWidth + 18);
        }
        return Math.max(height, DEFAULT_BUTTON_WIDTH);
    }

    private String styleName(Map<String, String> styles, String key) {
        if (styles == null || key == null) {
            return "default";
        }
        String value = styles.get(key);
        return value == null || value.isBlank() ? "default" : value;
    }

    public TextField getFilePathField() {
        return filePathField;
    }

    public Button getBrowseButton() {
        return browseButton;
    }

    public SelectionMode getSelectionMode() {
        return fileChooser.getFileSelectionMode() == JFileChooser.DIRECTORIES_ONLY
                ? SelectionMode.DIRECTORIES_ONLY
                : SelectionMode.FILES_ONLY;
    }

    public String getValue() {
        return filePathField.getText();
    }

    public void setValue(String path) {
        if (path == null || path.isBlank()) {
            filePathField.setText("");
            super.setValue((Object) "");
            return;
        }

        File file = new File(path);
        if (!file.exists()) {
            createMissingPath(file, path);
        }

        if ((file.isFile() && fileChooser.getFileSelectionMode() == JFileChooser.FILES_ONLY)
                || (file.isDirectory() && fileChooser.getFileSelectionMode() == JFileChooser.DIRECTORIES_ONLY)) {
            filePathField.setText(path);
            super.setValue((Object) path);
            return;
        }

        throw new IllegalArgumentException("The provided path does not match the current selection mode: " + path);
    }

    @Override
    public void setValue(Object value) {
        setValue(value == null ? "" : String.valueOf(value));
    }

    private void createMissingPath(File file, String path) {
        try {
            if (fileChooser.getFileSelectionMode() == JFileChooser.DIRECTORIES_ONLY) {
                if (file.mkdirs()) {
                    Engine.LOGGER.info("Created missing directory: {}", path);
                } else if (!file.isDirectory()) {
                    throw new RuntimeException("Failed to create directory: " + path);
                }
            } else if (fileChooser.getFileSelectionMode() == JFileChooser.FILES_ONLY) {
                File parent = file.getParentFile();
                if (parent != null && !parent.isDirectory() && parent.mkdirs()) {
                    Engine.LOGGER.info("Created parent directories for file: {}", path);
                }
                if (file.createNewFile()) {
                    Engine.LOGGER.info("Created missing file: {}", path);
                } else if (!file.isFile()) {
                    throw new RuntimeException("Failed to create file: " + path);
                }
            }
        } catch (Exception error) {
            throw new RuntimeException("Error while creating path: " + path, error);
        }
    }

    private void configureFileChooser(List<String> fileExtensions, SelectionMode selectionMode) {
        Map<SelectionMode, Integer> modeMap = Map.of(
                SelectionMode.DIRECTORIES_ONLY, JFileChooser.DIRECTORIES_ONLY,
                SelectionMode.FILES_ONLY, JFileChooser.FILES_ONLY
        );
        fileChooser.setFileSelectionMode(modeMap.getOrDefault(selectionMode, JFileChooser.FILES_ONLY));
        fileChooser.setDialogTitle("KaylasUI File Selector");
        Optional.ofNullable(fileExtensions)
                .filter(ext -> !ext.isEmpty())
                .ifPresent(ext -> {
                    String localeString = componentFactory.getEngine().getLANG().getString(attributes.getLocaleKey());
                    Object initialValue = attributes.getInitialValue();
                    String description = String.join(", ", ext) + " " + localeString + " " + (initialValue != null ? initialValue : "");
                    FileNameExtensionFilter filter = new FileNameExtensionFilter(description, ext.toArray(new String[0]));
                    fileChooser.setFileFilter(filter);
                });
    }

    private String childName(String suffix) {
        String base = attributes.getComponentId();
        return (base == null || base.isBlank() ? "fileSelector" : base) + suffix;
    }

    private class BrowseButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String currentPath = filePathField.getText();
            if (!currentPath.isEmpty()) {
                File currentFile = new File(currentPath);
                if (currentFile.exists()) {
                    if (currentFile.isFile()) {
                        fileChooser.setSelectedFile(currentFile);
                    } else {
                        fileChooser.setCurrentDirectory(currentFile);
                    }
                }
            }

            int returnValue = fileChooser.showOpenDialog(FileSelector.this);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                String selectedPath = selectedFile.getAbsolutePath();
                filePathField.setText(selectedPath);
                FileSelector.super.setValue((Object) selectedPath);
            }
        }
    }
}
