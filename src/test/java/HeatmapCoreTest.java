package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.gui.MapViewState;

class HeatmapCoreTest {
    @Test void rendersSourceColorAndDoesNotMutateDataset() {
        org.openstreetmap.josm.spi.preferences.Config.setPreferencesInstance(new org.openstreetmap.josm.spi.preferences.MemoryPreferences());
        org.openstreetmap.josm.data.projection.ProjectionRegistry.setProjection(org.openstreetmap.josm.data.projection.Projections.getProjectionByCode("EPSG:3857"));
        var points = List.of(new ReferencePoint(1, 0, 0, 1), new ReferencePoint(2, 0, 0, 2));
        var data = new ReferenceDataset("server", "source", new QueryBounds(-80,80,-170,10), points);
        var state = MapViewState.createDefaultState(128, 96);
        var image = HeatmapRenderer.render(data, state, new Color(30, 90, 210), 128, 96);
        boolean found = false;
        for (int y=0;y<image.getHeight();y++) for (int x=0;x<image.getWidth();x++) {
            int pixel = image.getRGB(x,y);
            if ((pixel >>> 24) != 0) {
                found = true;
                assertEquals(30, (pixel >>> 16) & 255);
                assertEquals(90, (pixel >>> 8) & 255);
                assertEquals(210, pixel & 255);
            }
        }
        assertTrue(found);
        assertEquals(List.of(1L,2L), data.points().stream().map(ReferencePoint::id).toList());
        var empty = new ReferenceDataset("server", "empty", new QueryBounds(-80,80,-170,10), List.of());
        var blank = HeatmapRenderer.render(empty, state, Color.RED, 128, 96);
        for (int y=0;y<blank.getHeight();y++) for (int x=0;x<blank.getWidth();x++) assertEquals(0, blank.getRGB(x,y));
    }
}