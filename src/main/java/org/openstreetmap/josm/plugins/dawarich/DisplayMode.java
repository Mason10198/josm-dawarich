package org.openstreetmap.josm.plugins.dawarich;

/** Visualization only: never changes fetched or retained observations. */
public enum DisplayMode {
    POINTS("Points"), HEATMAP("Heatmap"), BOTH("Both");
    private final String label;
    DisplayMode(String label){this.label=label;}
    @Override public String toString(){return label;}
}
