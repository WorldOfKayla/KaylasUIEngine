package org.takesome.kaylasEngine.utils;

import com.sun.management.OperatingSystemMXBean;
import org.takesome.kaylasEngine.Engine;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * Calculates a compact RAM allocation range for launcher sliders.
 *
 * <p>The upper allocation limit is derived from detected physical memory and rounded to a safe
 * power-of-two boundary. That limit is then divided into an equal number of allocation quanta,
 * producing a readable scale without per-capacity profiles or threshold tables.</p>
 */
public class RamRangeCalculator {
    private static final int MB_IN_GB = 1024;
    private static final int ABSOLUTE_MIN_RAM_MB = 512;
    private static final int ABSOLUTE_MAX_RAM_MB = 64 * MB_IN_GB;
    private static final int DEFAULT_TOTAL_SYSTEM_RAM_MB = 8 * MB_IN_GB;
    private static final int DEFAULT_MARKER_COUNT = 4;
    private static final int MAX_VISIBLE_MARKERS = 4;

    /**
     * Immutable slider range expressed in megabytes.
     *
     * @param minValue minimum selectable value
     * @param maxValue maximum selectable value
     * @param initialValue recommended initial value
     * @param values ordered discrete allocation values
     */
    public record SliderRange(int minValue, int maxValue, int initialValue, List<Integer> values) {
        public SliderRange {
            values = List.copyOf(values);
            if (values.isEmpty()) {
                throw new IllegalArgumentException("RAM slider values must not be empty");
            }
            if (minValue != values.get(0) || maxValue != values.get(values.size() - 1)) {
                throw new IllegalArgumentException("RAM slider bounds must match the first and last values");
            }
            if (!values.contains(initialValue)) {
                throw new IllegalArgumentException("RAM slider initial value must be one of the discrete values");
            }
        }
    }

    /**
     * Detects total physical memory and returns an adaptive allocation range.
     *
     * <p>{@code numberOfSteps} is a marker-count hint. The compact launcher layout supports up to
     * four visible values.</p>
     */
    public SliderRange calculateSliderRange(int numberOfSteps) {
        OptionalLong totalMemoryMb = getTotalSystemMemoryMb();
        if (totalMemoryMb.isPresent()) {
            long detected = totalMemoryMb.getAsLong();
            Engine.LOGGER.info("Detected total system memory: {} MB", detected);
            return calculateSliderRangeForTotalMemory(detected, numberOfSteps);
        }

        Engine.LOGGER.warn(
                "Could not detect system memory. Using the {} GB RAM profile.",
                DEFAULT_TOTAL_SYSTEM_RAM_MB / MB_IN_GB
        );
        return calculateSliderRangeForTotalMemory(DEFAULT_TOTAL_SYSTEM_RAM_MB, numberOfSteps);
    }

    /**
     * Deterministic variant used by tests and platform integrations that already know total RAM.
     *
     * @param detectedTotalMemoryMb detected physical memory in megabytes
     * @param numberOfSteps requested marker count, capped for the compact launcher layout
     */
    public SliderRange calculateSliderRangeForTotalMemory(long detectedTotalMemoryMb,
                                                           int numberOfSteps) {
        long normalizedTotalMb = normalizeInstalledMemoryMb(detectedTotalMemoryMb);
        int markerCount = resolveMarkerCount(numberOfSteps);
        int maximum = calculateMaximumAllocationMb(normalizedTotalMb);
        List<Integer> values = generateAllocationValues(maximum, markerCount);
        int targetInitial = safeLongToInt(normalizedTotalMb / 4L);
        int initial = nearestValue(values, targetInitial);

        return new SliderRange(
                values.get(0),
                values.get(values.size() - 1),
                initial,
                values
        );
    }

    /** Returns the nearest allowed value, preferring the lower value when distances are equal. */
    public static int nearestValue(List<Integer> values, int requestedValue) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("RAM values must not be empty");
        }

        int nearest = values.get(0);
        long nearestDistance = Math.abs((long) requestedValue - nearest);
        for (int value : values) {
            long distance = Math.abs((long) requestedValue - value);
            if (distance < nearestDistance) {
                nearest = value;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private OptionalLong getTotalSystemMemoryMb() {
        try {
            var operatingSystem = ManagementFactory.getOperatingSystemMXBean();
            if (operatingSystem instanceof OperatingSystemMXBean memoryBean) {
                long totalMemoryBytes = memoryBean.getTotalMemorySize();
                if (totalMemoryBytes > 0L) {
                    return OptionalLong.of(totalMemoryBytes / (1024L * 1024L));
                }
            }
        } catch (RuntimeException | LinkageError error) {
            Engine.LOGGER.error("Failed to query OperatingSystemMXBean for total memory.", error);
        }
        return OptionalLong.empty();
    }

    private long normalizeInstalledMemoryMb(long detectedTotalMemoryMb) {
        long safeDetected = detectedTotalMemoryMb > 0L
                ? detectedTotalMemoryMb
                : DEFAULT_TOTAL_SYSTEM_RAM_MB;
        long roundedGigabytes = Math.max(1L, Math.round((double) safeDetected / MB_IN_GB));
        return Math.min((long) Integer.MAX_VALUE, roundedGigabytes * MB_IN_GB);
    }

    private int calculateMaximumAllocationMb(long normalizedTotalMb) {
        long nominalSystemPower = nearestPowerOfTwo(normalizedTotalMb);
        long candidate = Math.max(ABSOLUTE_MIN_RAM_MB, nominalSystemPower / 2L);
        long safeUpperBound = Math.max(ABSOLUTE_MIN_RAM_MB, normalizedTotalMb * 3L / 4L);

        while (candidate > safeUpperBound && candidate > ABSOLUTE_MIN_RAM_MB) {
            candidate >>= 1;
        }

        candidate = Math.min(candidate, ABSOLUTE_MAX_RAM_MB);
        return safeLongToInt(Math.max(ABSOLUTE_MIN_RAM_MB, candidate));
    }

    /**
     * Divides the calculated maximum into equal semantic steps.
     *
     * <p>For example, a 16384 MB maximum and four markers produce
     * {@code 4096, 8192, 12288, 16384}. The same formula applies to every detected RAM size.</p>
     */
    private List<Integer> generateAllocationValues(int maximum, int requestedMarkerCount) {
        int availableQuanta = Math.max(1, maximum / ABSOLUTE_MIN_RAM_MB);
        int markerCount = Math.max(1, Math.min(requestedMarkerCount, availableQuanta));
        int quantum = maximum / markerCount;

        List<Integer> values = new ArrayList<>(markerCount);
        for (int index = 1; index <= markerCount; index++) {
            values.add(index == markerCount ? maximum : quantum * index);
        }
        return List.copyOf(values);
    }

    private int resolveMarkerCount(int numberOfSteps) {
        int requested = numberOfSteps > 0 ? numberOfSteps : DEFAULT_MARKER_COUNT;
        return Math.max(2, Math.min(MAX_VISIBLE_MARKERS, requested));
    }

    private long nearestPowerOfTwo(long value) {
        if (value <= 1L) {
            return 1L;
        }

        long lower = Long.highestOneBit(value);
        if (lower >= (1L << 62)) {
            return lower;
        }
        long upper = lower << 1;
        return value - lower < upper - value ? lower : upper;
    }

    private int safeLongToInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }
}
