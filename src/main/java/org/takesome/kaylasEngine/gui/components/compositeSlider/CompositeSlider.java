package org.takesome.kaylasEngine.gui.components.compositeSlider;

import com.sun.management.OperatingSystemMXBean;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.components.CompositeComponent;
import org.takesome.kaylasEngine.gui.components.label.Label;
import org.takesome.kaylasEngine.gui.components.label.LabelStyle;
import org.takesome.kaylasEngine.gui.components.slider.TexturedSliderUI;
import org.takesome.kaylasEngine.gui.components.spinner.Spinner;
import org.takesome.kaylasEngine.utils.RamRangeCalculator;

import javax.swing.*;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class CompositeSlider extends CompositeComponent {
    private LabelStyle labelStyle;
    private final ComponentAttributes componentAttribute;
    private Label label;
    private JSlider slider;
    private JSpinner spinner;
    private SliderListener sliderListener;

    public CompositeSlider(ComponentFactory componentFactory) {
        this.componentFactory = componentFactory;
        this.componentAttribute = componentFactory.getComponentAttribute();
        initializeComponents();
        configureLayout();
        addListeners();
        setOpaque(componentFactory.isOpaque());
    }

    private void initializeComponents() {
        int minValue, maxValue, initialValue;
        List<Integer> values;

        if (isRamComponent()) {
            RamRangeCalculator calculator = new RamRangeCalculator();
            RamRangeCalculator.SliderRange sliderRange = calculator.calculateSliderRange(componentAttribute.getStepSize());
            minValue = sliderRange.minValue();
            maxValue = sliderRange.maxValue();
            initialValue = getInitialValue(sliderRange.initialValue());
            values = sliderRange.values();
        } else {
            minValue = componentAttribute.getMinValue();
            maxValue = componentAttribute.getMaxValue();
            initialValue = getInitialValue(minValue);
            values = getValues(minValue, maxValue, componentAttribute.getStepSize());
        }

        if (minValue >= maxValue) {
            throw new IllegalArgumentException("Invalid range: minValue (" + minValue + ") must be less than maxValue (" + maxValue + ")");
        }
        if (initialValue < minValue || initialValue > maxValue) {
            throw new IllegalArgumentException("Initial value (" + initialValue + ") must be within range: [" + minValue + ", " + maxValue + "]");
        }

        label = new Label(componentFactory);
        configureLabel();

        slider = new JSlider(minValue, maxValue, initialValue);
        configureSlider(values);

        spinner = new Spinner(initialValue, minValue, maxValue, componentAttribute.getMinorSpacing());
    }

    private int getInitialValue(int defaultValue) {
        Object initialValue = componentAttribute.getInitialValue();
        if (initialValue == null) {
            return defaultValue;
        }
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(initialValue)));
        } catch (NumberFormatException e) {
            Engine.LOGGER.warn("Invalid initial slider value '{}', using default: {}", initialValue, defaultValue);
            return defaultValue;
        }
    }

    private void configureLabel() {
        labelStyle = new LabelStyle(componentFactory);
        labelStyle.setStyle(componentFactory.getEngine().getStyleProvider().getStyle("label", styleName("label")));
        labelStyle.apply(label);
        label.setFont(componentFactory.getEngine().getFONTUTILS().getFont(labelStyle.getFontName(), componentAttribute.getFontSize()));
    }

    private boolean isRamComponent() {
        String componentId = componentAttribute.getComponentId();
        return componentId != null && componentId.toLowerCase().contains("ram");
    }

    private String styleName(String key) {
        if (componentAttribute.getStyles() == null || key == null) {
            return "default";
        }
        String value = componentAttribute.getStyles().get(key);
        return value == null || value.isBlank() ? "default" : value;
    }

    private void configureSlider(List<Integer> values) {
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setBounds(componentAttribute.getBounds());
        slider.setMajorTickSpacing(Math.max(1, (values.get(values.size() - 1) - values.get(0)) / 9));
        slider.setMinorTickSpacing(Math.max(1, (values.get(1) - values.get(0)) / 2));
        slider.setOpaque(false);
        slider.setUI(new TexturedSliderUI(componentFactory, slider,
                componentFactory.getEngine().getStyleProvider().getStyle("slider", styleName("slider"))));

        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        for (int value : values) {
            JLabel tableLabel = new JLabel(String.valueOf(value));
            tableLabel.setFont(componentFactory.getEngine().getFONTUTILS().getFont(labelStyle.getFontName(), componentAttribute.getFontSize() - 3f));
            tableLabel.setForeground(labelStyle.getActiveColor());
            labelTable.put(value, tableLabel);
        }
        slider.setLabelTable(labelTable);
    }

    /**
     * Создаем контейнер с абсолютным позиционированием для расположения метки, слайдера и спиннера,
     * затем добавляем его в наш CompositeComponent.
     */
    private void configureLayout() {
        // Создаем контейнер с null layout для абсолютного позиционирования
        JPanel container = new JPanel(null);
        // Задаем предпочтительный размер контейнера, можно использовать размеры из конфигурации
        container.setPreferredSize(new Dimension(
                componentAttribute.getBounds().width,
                componentAttribute.getBounds().height
        ));

        ComponentAttributes.LayoutConfig config = componentAttribute.getLayoutConfig();
        if (config != null) {
            applyConfiguredBounds(label, config.getLabel());
            applyConfiguredBounds(slider, config.getSlider());
            applyConfiguredBounds(spinner, config.getSpinner());
        } else {
            applyDefaultBounds();
        }

        container.add(label);
        container.add(slider);
        container.add(spinner);
        container.setOpaque(false);

        addSubComponent(container);
    }

    private void applyConfiguredBounds(JComponent component, ComponentAttributes.ComponentConfig config) {
        if (component == null) {
            return;
        }
        if (config == null) {
            return;
        }
        component.setBounds(config.getX(), config.getY(), config.getWidth(), config.getHeight());
    }

    private void applyDefaultBounds() {
        int width = Math.max(320, componentAttribute.getBounds().width);
        label.setBounds(0, 0, width, 24);
        slider.setBounds(0, 26, Math.max(120, width - 96), 44);
        spinner.setBounds(Math.max(0, width - 90), 30, 90, 32);
    }

    private void addListeners() {
        slider.addChangeListener(e -> {
            if (!slider.getValueIsAdjusting()) {
                spinner.setValue(slider.getValue());
                notifyListeners();
            }
        });

        spinner.addChangeListener(e -> {
            int newValue = (Integer) spinner.getValue();
            if (newValue >= slider.getMinimum() && newValue <= slider.getMaximum()) {
                slider.setValue(newValue);
                notifyListeners();
            }
        });
    }

    public void setSliderListener(SliderListener sliderListener) {
        this.sliderListener = sliderListener;
        slider.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void notifyListeners() {
        if (sliderListener != null) {
            sliderListener.onSliderChange(this);
        }
    }

    public JSlider getSlider() {
        return slider;
    }

    public Label getLabel() {
        return label;
    }

    public JSpinner getSpinner() {
        return spinner;
    }

    private int roundUpToPowerOfTwo(int value) {
        return value <= 0 ? 1 : Integer.highestOneBit(value - 1) << 1;
    }

    private int roundDownToPowerOfTwo(int value) {
        return value <= 0 ? 1 : Integer.highestOneBit(value);
    }

    private int roundToNearestPowerOfTwo(int value) {
        int lower = roundDownToPowerOfTwo(value);
        int upper = roundUpToPowerOfTwo(value);
        return (value - lower < upper - value) ? lower : upper;
    }

    private List<Integer> getPowerOfTwoValues(int minValue, int maxValue) {
        List<Integer> values = new ArrayList<>();
        for (int value = minValue; value <= maxValue; value <<= 1) {
            values.add(value);
        }
        return values;
    }

    private SliderRange getSliderRangeBasedOnRam() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long totalMemory = osBean.getTotalMemorySize();

        int minValue = Math.max(1024, (int) (totalMemory * 0.10 / (1024 * 1024)));
        int maxValue = Math.min(64 * 1024, (int) (totalMemory * 0.75 / (1024 * 1024)));

        minValue = roundUpToPowerOfTwo(minValue);
        maxValue = roundDownToPowerOfTwo(maxValue);

        Engine.LOGGER.info("Calculated RAM range: minValue=" + minValue + ", maxValue=" + maxValue);

        if (maxValue <= minValue) {
            throw new IllegalArgumentException("Invalid range properties: maxValue (" + maxValue + ") must be greater than minValue (" + minValue + ")");
        }

        List<Integer> values = getPowerOfTwoValues(minValue, maxValue);

        int initialValue = Math.min(Math.max(roundToNearestPowerOfTwo((int) (totalMemory * 0.25 / (1024 * 1024))), minValue), maxValue);

        Engine.LOGGER.info("RAM-based initial value: " + initialValue);

        return new SliderRange(minValue, maxValue, initialValue, values);
    }

    public List<Integer> getValues(int minValue, int maxValue, int steps) {
        int safeSteps = Math.max(2, steps);
        List<Integer> values = new ArrayList<>();
        double step = (double) (maxValue - minValue) / (safeSteps - 1);

        for (int i = 0; i < safeSteps; i++) {
            int value = minValue + (int) Math.round(i * step);
            values.add(value);
        }

        return values;
    }

    public Object getValue(){
        return this.slider.getValue();
    }

    public void setValue(int value) {
        this.slider.setValue(value);
    }
}
