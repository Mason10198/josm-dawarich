package org.openstreetmap.josm.plugins.dawarich;

/** Immutable retrieval mode and batch point limits. */
public record LoadOptions(Mode mode, int recentPointsPerSource, int totalPointLimit) {
    public enum Mode { DATE_RANGE, MOST_RECENT }
    public static final int MAX_TOTAL_POINTS = 250_000;
    public LoadOptions {
        if (mode == null || recentPointsPerSource < 1 || recentPointsPerSource > MAX_TOTAL_POINTS
                || totalPointLimit < 1 || totalPointLimit > MAX_TOTAL_POINTS
                || (mode == Mode.MOST_RECENT && recentPointsPerSource > totalPointLimit))
            throw new IllegalArgumentException("Invalid load options");
    }
    public static LoadOptions defaults() { return new LoadOptions(Mode.DATE_RANGE, 10_000, 50_000); }
}