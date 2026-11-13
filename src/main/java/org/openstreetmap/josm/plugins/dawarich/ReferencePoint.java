package org.openstreetmap.josm.plugins.dawarich;

/** Immutable, unmodified point values returned by the reference service. */
public record ReferencePoint(long id, double latitude, double longitude, long timestamp) {
    public ReferencePoint {
        if (id <= 0 || !Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180 || timestamp < 0) {
            throw new IllegalArgumentException("Invalid reference point");
        }
    }

    /** Avoid exposing location or time data through incidental logging. */
    @Override public String toString() { return "ReferencePoint"; }
}
