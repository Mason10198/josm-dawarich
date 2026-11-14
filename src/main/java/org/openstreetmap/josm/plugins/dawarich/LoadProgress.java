package org.openstreetmap.josm.plugins.dawarich;

/** Immutable, inexpensive snapshot of one active source download. */
public record LoadProgress(String sourceId, int sourceIndex, int sourceCount,
                           long requests, long downloadedBytes, long selectedPoints,
                           int requestedPoints, long elapsedMillis) { }