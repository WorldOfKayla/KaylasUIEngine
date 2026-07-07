package org.foxesworld.engine.gui.components.fileSelector;

import org.foxesworld.engine.Engine;
import org.foxesworld.engine.gui.components.ComponentAttributes;
import org.foxesworld.engine.gui.components.CompositeComponent;
import org.foxesworld.engine.gui.components.ComponentFactory;
import org.foxesworld.engine.gui.components.button.Button;
import org.foxesworld.engine.gui.components.button.ButtonStyle;
import org.foxesworld.engine.gui.components.textfield.TextField;
import org.foxesworld.engine.gui.components.textfield.TextFieldStyle;
import org.foxesworld.engine.gui.styles.StyleAttributes;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FileSelector extends CompositeComponent {
    private final ComponentFactory componentFactory;
    private final JTextField filePathField;
    private final Button browseButton;
    private final JFileChooser fileChooser;

    public FileSelector(ComponentFactory componentFactory, SelectionMode selectionMode) {
        this.componentFactory = componentFactory;
        List<String> fileExtensions = componentFactory.getComponentAttribute().getFileExtensions();
        Map<String, String> styles = componentFactory.getComponentAttribute().getStyles();

        filePathField = createStyledTextFieldWithTexture(styleName(styles, "textField"));
        browseButton = new Button(componentFactory,
                componentFactory.getEngine().getIconUtils().getIcon(componentFactory.getComponentAttribute()),
                this.componentFactory.getEngine().getLANG().getString(componentFactory.getComponentAttribute().getLocaleKey()));
        StyleAttributes attributes = componentFactory.getEngine().getStyleProvider().getStyle("button", styleName(styles, "button"));
        componentFactory.setStyle(attributes);
        ButtonStyle buttonStyle = new ButtonStyle(componentFactory);
        buttonStyle.apply(browseButton);

        browseButton.addActionListener(new BrowseButtonListener());
        fileChooser = new JFileChooser();
        configureFileChooser(fileExtensions, selectionMode);

        JPanel container = new JPanel(new BorderLayout());
        container.add(filePathField, BorderLayout.CENTER);
        container.add(browseButton, BorderLayout.EAST);
        addSubComponent(container);
    }


    private String styleName(Map<String, String> styles, String key) {
        if (styles == null || key == null) {
            return "default";
        }
        String value = styles.get(key);
        return value == null || value.isBlank() ? "default" : value;
    }

    private TextField createStyledTextFieldWithTexture(String style) {
        StyleAttributes attributes = componentFactory.getEngine().getStyleProvider().getStyle("textField", style);
        this.componentFactory.setStyle(attributes);
        TextFieldStyle textFieldStyle = new TextFieldStyle(this.componentFactory);
        TextField textField = new TextField(this.componentFactory);
        textFieldStyle.apply(textField);
        return textField;
    }

    public String getValue() {
        return filePathField.getText();
    }

    public void setValue(String path) {
        if (path == null || path.isBlank()) {
            filePathField.setText("");
            return;
        }
        File file = new File(path);

        if (!file.exists()) {
            try {
                if (fileChooser.getFileSelectionMode() == JFileChooser.DIRECTORIES_ONLY) {
                    // Создание директории
                    if (file.mkdirs()) {
                        Engine.LOGGER.info("Created missing directory: " + path);
                    } else {
                        throw new RuntimeException("Failed to create directory: " + path);
                    }
                } else if (fileChooser.getFileSelectionMode() == JFileChooser.FILES_ONLY) {
                    if (file.getParentFile() != null && file.getParentFile().mkdirs()) {
                        Engine.LOGGER.info("Created parent directories for file: " + path);
                    }
                    if (file.createNewFile()) {
                        Engine.LOGGER.info("Created missing file: " + path);
                    } else {
                        throw new RuntimeException("Failed to create file: " + path);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Error while creating path: " + path, e);
            }
        }

        // Проверка соответствия режиму
        if ((file.isFile() && fileChooser.getFileSelectionMode() == JFileChooser.FILES_ONLY) ||
                (file.isDirectory() && fileChooser.getFileSelectionMode() == JFileChooser.DIRECTORIES_ONLY)) {
            filePathField.setText(path);
        } else {
            throw new IllegalArgumentException("The provided path does not match the current selection mode.");
        }
    }

    /**
     * Настраивает JFileChooser для работы с файлами или директориями.
     */
    private void configureFileChooser(List<String> fileExtensions, SelectionMode selectionMode) {
        // Сопоставляем режим выбора с константами JFileChooser
        ComponentAttributes componentAttribute = componentFactory.getComponentAttribute();
        Map<SelectionMode, Integer> modeMap = Map.of(
                SelectionMode.DIRECTORIES_ONLY, JFileChooser.DIRECTORIES_ONLY,
                SelectionMode.FILES_ONLY, JFileChooser.FILES_ONLY
        );
        fileChooser.setFileSelectionMode(modeMap.getOrDefault(selectionMode, JFileChooser.FILES_ONLY));
        fileChooser.setDialogTitle("KaylasUI File Selector");
        Optional.ofNullable(fileExtensions)
                .filter(ext -> !ext.isEmpty())
                .ifPresent(ext -> {
                    String localeString = componentFactory.getEngine().getLANG()
                            .getString(componentAttribute.getLocaleKey());
                    Object initialValue = componentAttribute.getInitialValue();
                    String description = String.join(", ", ext) + " " + localeString + " " + (initialValue != null ? initialValue : "");
                    FileNameExtensionFilter filter = new FileNameExtensionFilter(description, ext.toArray(new String[0]));
                    fileChooser.setFileFilter(filter);
                });
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
                filePathField.setText(selectedFile.getAbsolutePath());
            }
        }
    }
}