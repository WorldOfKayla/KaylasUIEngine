package org.takesome.kaylasEngine.gui.components.compositeSlider;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.ComponentFactory;
import org.takesome.kaylasEngine.gui.components.CompositeComponent;
import org.takesome.kaylasEngine.gui.components.label.Label;
import org.takesome.kaylasEngine.gui.components.label.LabelStyle;
import org.takesome.kaylasEngine.gui.components.slider.Slider;
import org.takesome.kaylasEngine.gui.components.slider.TexturedSliderUI;
import org.takesome.kaylasEngine.gui.components.spinner.Spinner;
import org.takesome.kaylasEngine.gui.lookAndFeel.components.KaylasLabel;
import org.takesome.kaylasEngine.gui.lookAndFeel.components.KaylasSlider;
import org.takesome.kaylasEngine.gui.lookAndFeel.components.KaylasSpinner;
import org.takesome.kaylasEngine.gui.styles.StyleAttributes;
import org.takesome.kaylasEngine.utils.RamRangeCalculator;

import java.awt.Cursor;
import java.awt.Dimension;
import javax.swing.SpinnerListModel;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * Engine-native composite slider: label + textured slider + spinner.
 *
 * <p>Uses only KaylasUIEngine components internally. The root is a {@link CompositeComponent}
 * with absolute layout, so layoutConfig can address child engine components directly without an
 * extra Swing JPanel wrapper.</p>
 */
public class CompositeSlider extends CompositeComponent {
    private final ComponentAttributes componentAttribute;
    private final ComponentFactory componentFactory;
    private LabelStyle labelStyle;
    private StyleAttributes resolvedLabelStyle;
    private StyleAttributes resolvedSliderStyle;
    private Label label;
    private Slider slider;
    private Spinner spinner;
    private SliderListener sliderListener;
    private List<Integer> selectableValues = List.of();
    private boolean syncing;

    public CompositeSlider(ComponentFactory componentFactory) {
        super(LayoutMode.ABSOLUTE);
        this.componentFactory = componentFactory;
        this.componentAttribute = componentFactory.getComponentAttribute();
        super.componentFactory = componentFactory;
        setOpaque(componentFactory.getStyle().isOpaque());
        setVisible(true);
        setName(componentAttribute.getComponentId());
        setLayoutConfig(componentAttribute.getLayoutConfig());
        initializeComponents();
        configureLayout();
        addListeners();
    }

    private void initializeComponents() {
        SliderRangeModel range = resolveRange();
        selectableValues = List.copyOf(range.values());
        resolvedLabelStyle = componentFactory.getEngine().getStyleProvider()
                .getStyle("label", styleName("label"));
        resolvedSliderStyle = componentFactory.getEngine().getStyleProvider()
                .getStyle("slider", styleName("slider"));

        label = componentFactory.withStyle(resolvedLabelStyle, () -> new KaylasLabel(componentFactory));
        label.setName(childName("Label"));
        configureLabel();

        slider = componentFactory.withStyle(resolvedSliderStyle, () -> new KaylasSlider(componentFactory));
        slider.setName(childName("Slider"));
        configureSlider(range);

        spinner = new KaylasSpinner(range.initialValue(), range.minValue(), range.maxValue(), spinnerStep(range));
        if (isRamComponent()) {
            spinner.setModel(new SpinnerListModel(selectableValues));
            spinner.setValue(range.initialValue());
        }
        spinner.setName(childName("Text"));
        spinner.setOpaque(false);
        setValue(range.initialValue());
    }

    private SliderRangeModel resolveRange() {
        int minValue;
        int maxValue;
        int initialValue;
        List<Integer> values;

        if (isRamComponent()) {
            RamRangeCalculator calculator = new RamRangeCalculator();
            RamRangeCalculator.SliderRange sliderRange = calculator.calculateSliderRange(safeStepCount());
            minValue = sliderRange.minValue();
            maxValue = sliderRange.maxValue();
            initialValue = intValue(componentAttribute.getInitialValue(), sliderRange.initialValue());
            values = sliderRange.values();
        } else {
            minValue = componentAttribute.getMinValue();
            maxValue = componentAttribute.getMaxValue();
            if (maxValue <= minValue) {
                maxValue = minValue + 100;
                Engine.LOGGER.warn("CompositeSlider '{}' had invalid range, normalized to {}..{}",
                        componentAttribute.getComponentId(), minValue, maxValue);
            }
            initialValue = intValue(componentAttribute.getInitialValue(), minValue);
            values = getValues(minValue, maxValue, safeStepCount());
        }

        initialValue = clamp(initialValue, minValue, maxValue);
        if (values == null || values.size() < 2) {
            values = getValues(minValue, maxValue, 2);
        }
        if (isRamComponent()) {
            initialValue = RamRangeCalculator.nearestValue(values, initialValue);
        }
        return new SliderRangeModel(minValue, maxValue, initialValue, List.copyOf(values));
    }

    private void configureLabel() {
        labelStyle = new LabelStyle(resolvedLabelStyle);
        labelStyle.apply(label);
        float fontSize = componentAttribute.getFontSize() > 0 ? componentAttribute.getFontSize() : labelStyle.getFontSize();
        label.setFont(componentFactory.getEngine().getFONTUTILS().getFont(labelStyle.getFontName(), fontSize));
    }

    private void configureSlider(SliderRangeModel range) {
        slider.setMinimum(range.minValue());
        slider.setMaximum(range.maxValue());
        if (isRamComponent()) {
            slider.setAllowedValues(range.values());
        }
        slider.setValue(range.initialValue());
        slider.setPaintTicks(true);
        boolean showWordMarkers = !componentAttribute.isHideWordMarkers();
        slider.setPaintLabels(showWordMarkers);
        slider.setMajorTickSpacing(majorTickSpacing(range));
        slider.setMinorTickSpacing(minorTickSpacing(range));
        slider.setOpaque(false);

        slider.setUI(new TexturedSliderUI(componentFactory, slider, resolvedSliderStyle));
        if (showWordMarkers) {
            slider.setLabelTable(createLabelTable(range.values()));
        }
    }

    private Hashtable<Integer, Label> createLabelTable(List<Integer> values) {
        Hashtable<Integer, Label> labelTable = new Hashtable<>();
        int fontSize = Math.max(8, componentAttribute.getFontSize() - 3);
        for (int value : values) {
            Label tickLabel = componentFactory.withStyle(
                    resolvedLabelStyle,
                    () -> new KaylasLabel(componentFactory)
            );
            tickLabel.setName(childName("Tick" + value));
            tickLabel.setText(String.valueOf(value));
            tickLabel.setOpaque(false);
            tickLabel.setFont(componentFactory.getEngine().getFONTUTILS().getFont(labelStyle.getFontName(), fontSize));
            tickLabel.setForeground(labelStyle.getActiveColor());
            normalizeTickLabelSize(tickLabel);
            labelTable.put(value, tickLabel);
        }
        return labelTable;
    }

    private void normalizeTickLabelSize(Label tickLabel) {
        // Label inherits the parent component descriptor and therefore receives the composite
        // slider bounds as an explicit preferred size. BasicSliderUI interprets that preferred size
        // as the size of every tick label, which can collapse trackRect to a negative width.
        tickLabel.setPreferredSize(null);
        tickLabel.setMinimumSize(null);
        tickLabel.setMaximumSize(null);

        Dimension intrinsic = tickLabel.getPreferredSize();
        int width = Math.max(1, intrinsic.width + 2);
        int height = Math.max(1, intrinsic.height);
        Dimension compact = new Dimension(width, height);
        tickLabel.setPreferredSize(compact);
        tickLabel.setMinimumSize(compact);
        tickLabel.setMaximumSize(compact);
        tickLabel.setSize(compact);
    }

    private void configureLayout() {
        Dimension size = resolveSize();
        setPreferredSize(size);
        setMinimumSize(size);
        setSize(size);
        setBounds(componentAttribute.getBounds());

        ComponentAttributes.LayoutConfig config = componentAttribute.getLayoutConfig();
        if (config != null) {
            applyLayoutConfig(label, config.getLabel());
            applyLayoutConfig(slider, config.getSlider());
            applyLayoutConfig(spinner, config.getSpinner());
        } else {
            applyDefaultBounds(size.width, size.height);
        }

        addSubComponent(label);
        addSubComponent(slider);
        addSubComponent(spinner);
    }

    private Dimension resolveSize() {
        int width = Math.max(320, componentAttribute.getBounds().width);
        int height = Math.max(76, componentAttribute.getBounds().height);
        return new Dimension(width, height);
    }

    private void applyDefaultBounds(int width, int height) {
        int labelHeight = Math.min(26, Math.max(20, height / 3));
        int spinnerWidth = Math.min(96, Math.max(72, width / 4));
        int sliderY = labelHeight + 4;
        int sliderHeight = Math.max(42, height - sliderY);
        label.setBounds(0, 0, width, labelHeight);
        slider.setBounds(0, sliderY, Math.max(120, width - spinnerWidth - 8), sliderHeight);
        spinner.setBounds(Math.max(0, width - spinnerWidth), sliderY + 4, spinnerWidth, Math.max(28, sliderHeight - 12));
    }

    private void addListeners() {
        slider.setSliderListener(source -> syncFromSlider());
        slider.addChangeListener(event -> syncFromSlider());

        spinner.setSpinnerListener(source -> syncFromSpinner());
        spinner.init();
    }

    private void syncFromSlider() {
        if (syncing) {
            return;
        }
        syncing = true;
        try {
            int value = normalizeSelectableValue(slider.getValue());
            if (value != slider.getValue()) {
                slider.setValue(value);
            }
            if (!spinner.getValue().equals(value)) {
                spinner.setValue(value);
            }
            super.setValue(value);
        } finally {
            syncing = false;
        }
        notifyListeners();
    }

    private void syncFromSpinner() {
        if (syncing) {
            return;
        }
        syncing = true;
        try {
            int value = clamp(((Number) spinner.getValue()).intValue(), slider.getMinimum(), slider.getMaximum());
            if (value != slider.getValue()) {
                slider.setValue(value);
            }
            if (!spinner.getValue().equals(value)) {
                spinner.setValue(value);
            }
            super.setValue(value);
        } finally {
            syncing = false;
        }
        notifyListeners();
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

    public Slider getSlider() {
        return slider;
    }

    public Label getLabel() {
        return label;
    }

    public Spinner getSpinner() {
        return spinner;
    }

    public List<Integer> getValues(int minValue, int maxValue, int steps) {
        int safeSteps = Math.max(2, steps);
        List<Integer> values = new ArrayList<>();
        double step = (double) (maxValue - minValue) / (safeSteps - 1);
        for (int index = 0; index < safeSteps; index++) {
            values.add(minValue + (int) Math.round(index * step));
        }
        return values;
    }

    @Override
    public Object getValue() {
        return slider == null ? super.getValue() : slider.getValue();
    }

    public void setValue(int value) {
        if (slider == null || spinner == null) {
            super.setValue(value);
            return;
        }
        int clamped = normalizeSelectableValue(
                clamp(value, slider.getMinimum(), slider.getMaximum())
        );
        syncing = true;
        try {
            slider.setValue(clamped);
            spinner.setValue(clamped);
            super.setValue(clamped);
        } finally {
            syncing = false;
        }
    }

    @Override
    public void setValue(Object value) {
        setValue(intValue(value, slider == null ? 0 : slider.getValue()));
    }

    private String childName(String suffix) {
        String base = componentAttribute.getComponentId();
        return (base == null || base.isBlank() ? "compositeSlider" : base) + suffix;
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

    private int safeStepCount() {
        return Math.max(2, componentAttribute.getStepSize());
    }

    private int spinnerStep(SliderRangeModel range) {
        if (componentAttribute.getMinorSpacing() > 0) {
            return componentAttribute.getMinorSpacing();
        }
        return Math.max(1, (range.maxValue() - range.minValue()) / Math.max(1, safeStepCount() - 1));
    }

    private int majorTickSpacing(SliderRangeModel range) {
        if (componentAttribute.getMajorSpacing() > 0) {
            return componentAttribute.getMajorSpacing();
        }
        return Math.max(1, (range.maxValue() - range.minValue()) / Math.min(9, Math.max(1, range.values().size() - 1)));
    }

    private int minorTickSpacing(SliderRangeModel range) {
        if (componentAttribute.getMinorSpacing() > 0) {
            return componentAttribute.getMinorSpacing();
        }
        return Math.max(1, (range.maxValue() - range.minValue()) / Math.max(10, safeStepCount() * 2));
    }

    private int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(value)));
        } catch (NumberFormatException error) {
            Engine.LOGGER.warn("Invalid composite slider value '{}', using fallback: {}", value, fallback);
            return fallback;
        }
    }

    private int normalizeSelectableValue(int value) {
        if (!isRamComponent() || selectableValues.isEmpty()) {
            return value;
        }
        return RamRangeCalculator.nearestValue(selectableValues, value);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record SliderRangeModel(int minValue, int maxValue, int initialValue, List<Integer> values) {}
}
