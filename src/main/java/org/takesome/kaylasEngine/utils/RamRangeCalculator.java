package org.takesome.kaylasEngine.utils;

import com.sun.management.OperatingSystemMXBean; // Оставляем импорт, но используем его безопасно
import org.takesome.kaylasEngine.Engine;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * Calculates a recommended RAM allocation range for a Java application.
 * This class safely determines the total system memory and suggests a sensible
 * range (min, max, initial value) for a settings slider. It provides a safe
 * fallback if system memory cannot be determined.
 */
public class RamRangeCalculator {

    // --- Configuration Constants for Clarity ---

    private static final int MB_IN_GB = 1024;

    // Default range if system RAM detection fails
    private static final int DEFAULT_MIN_RAM_MB = 1 * MB_IN_GB; // 1 GB
    private static final int DEFAULT_MAX_RAM_MB = 8 * MB_IN_GB; // 8 GB
    private static final int DEFAULT_INITIAL_RAM_MB = 2 * MB_IN_GB; // 2 GB

    // Dynamic range calculation factors
    private static final int ABSOLUTE_MIN_RAM_MB = 512; // The absolute minimum RAM we'll ever suggest
    private static final int ABSOLUTE_MAX_RAM_MB = 64 * MB_IN_GB; // The absolute max RAM we'll ever suggest (64 GB)
    private static final double MIN_RAM_FACTOR = 0.1; // Suggest at least 10% of total RAM
    private static final double MAX_RAM_FACTOR = 0.75; // Suggest at most 75% of total RAM
    private static final double INITIAL_RAM_FACTOR = 0.25; // Suggest 25% of total RAM as a starting point

    /**
     * A data record holding the calculated range for a RAM selection slider.
     *
     * @param minValue     The minimum value (in MB) for the slider.
     * @param maxValue     The maximum value (in MB) for the slider.
     * @param initialValue A suggested initial value (in MB) for the slider.
     * @param values       A list of discrete values (steps) for the slider.
     */
    public record SliderRange(int minValue, int maxValue, int initialValue, List<Integer> values) {}

    /**
     * Calculates the recommended RAM range based on the system's total physical memory.
     * If system memory cannot be detected, it returns a safe, predefined default range.
     *
     * @param numberOfSteps The desired number of steps for the slider UI.
     * @return A {@link SliderRange} object containing the calculated values.
     */
    public SliderRange calculateSliderRange(int numberOfSteps) {
        OptionalLong totalMemoryMbOpt = getTotalSystemMemoryMb();

        if (totalMemoryMbOpt.isPresent()) {
            long totalMemoryMB = totalMemoryMbOpt.getAsLong();
            Engine.LOGGER.info("Detected total system memory: {} MB", totalMemoryMB);
            return generateDynamicRange(totalMemoryMB, numberOfSteps);
        } else {
            Engine.LOGGER.warn("Could not detect system memory. Using safe default RAM range.");
            return generateDefaultRange(numberOfSteps);
        }
    }

    /**
     * Tries to get the total physical memory size from the specific Sun/Oracle MXBean.
     * This method is designed to fail gracefully if the underlying bean is not available.
     *
     * @return An {@link OptionalLong} containing the total memory in megabytes, or empty if detection fails.
     */
    private OptionalLong getTotalSystemMemoryMb() {
        try {
            // This is the critical part: safely attempt to access the proprietary API.
            var osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof OperatingSystemMXBean sunOsBean) {
                long totalMemoryBytes = sunOsBean.getTotalMemorySize();
                return OptionalLong.of(totalMemoryBytes / (1024 * 1024));
            }
        } catch (Exception e) {
            // Catch any potential errors, including ClassCastException or security exceptions.
            Engine.LOGGER.error("Failed to query OperatingSystemMXBean for total memory.", e);
        }
        return OptionalLong.empty();
    }

    /**
     * Generates a dynamic RAM range based on the provided total system memory.
     */
    private SliderRange generateDynamicRange(long totalMemoryMB, int numberOfSteps) {
        int min = (int) Math.max(ABSOLUTE_MIN_RAM_MB, totalMemoryMB * MIN_RAM_FACTOR);
        int max = (int) Math.min(ABSOLUTE_MAX_RAM_MB, totalMemoryMB * MAX_RAM_FACTOR);

        // Ensure max is always greater than min
        if (max <= min) {
            max = min + DEFAULT_MIN_RAM_MB; // Ensure a reasonable range
        }

        int step = calculateStepSize(min, max, numberOfSteps);
        List<Integer> values = generateSliderValues(min, max, step);

        // A good initial value is often 1/4 of system RAM, rounded to a power of two.
        int suggestedInitial = roundToNearestPowerOfTwo((int) (totalMemoryMB * INITIAL_RAM_FACTOR));
        int initial = Math.max(min, Math.min(max, suggestedInitial)); // Clamp it within the range

        return new SliderRange(min, max, initial, values);
    }

    /**
     * Generates a safe, hardcoded default range. Used when system RAM is unknown.
     */
    private SliderRange generateDefaultRange(int numberOfSteps) {
        int step = calculateStepSize(DEFAULT_MIN_RAM_MB, DEFAULT_MAX_RAM_MB, numberOfSteps);
        List<Integer> values = generateSliderValues(DEFAULT_MIN_RAM_MB, DEFAULT_MAX_RAM_MB, step);
        return new SliderRange(DEFAULT_MIN_RAM_MB, DEFAULT_MAX_RAM_MB, DEFAULT_INITIAL_RAM_MB, values);
    }

    private int calculateStepSize(int min, int max, int numberOfSteps) {
        if (numberOfSteps <= 0) {
            return Math.max(128, (max - min)); // Avoid division by zero, return a large step
        }
        int range = max - min;
        // Calculate a step and round it to a sensible number (e.g., a multiple of 128 or 256)
        int rawStep = Math.max(1, range / numberOfSteps);
        return (rawStep + 127) & -128; // Round up to the nearest multiple of 128
    }

    private List<Integer> generateSliderValues(int min, int max, int step) {
        List<Integer> values = new ArrayList<>();
        for (int value = min; value <= max; value += step) {
            values.add(value);
        }
        // Ensure the maximum value is always included if it's not already the last step
        if (values.isEmpty() || values.get(values.size() - 1) < max) {
            values.add(max);
        }
        return values;
    }

    /**
     * Rounds a value to the nearest power of two (e.g., 256, 512, 1024).
     * This is useful because Java memory limits (-Xmx) are often set to these values.
     */
    private int roundToNearestPowerOfTwo(int value) {
        if (value <= 0) return 1;
        int lower = Integer.highestOneBit(value);
        int upper = lower << 1;
        return (value - lower < upper - value) ? lower : upper;
    }
}