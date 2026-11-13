package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.Action;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.plugins.dawarich.*;

class DawarichLayerTest {
    private static final Color INK = new Color(220, 30, 40);
    private static ReferenceDataset data(String server, String source, ReferencePoint... points) {
        return new ReferenceDataset(server, source, new QueryBounds(-80, 80, -170, 10), List.of(points));
    }
    private static ReferencePoint p(long id, double lat, double lon) { return new ReferencePoint(id, lat, lon, id); }

    @Test void rendersSeparateDotsWithoutConnectingLineAndUsesFreshProjection() {
        var points = List.of(p(1, 0, 0), p(2, 1, 1));
        BufferedImage image = new BufferedImage(100, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        DawarichLayer.paintPoints(g, p -> new Point(p.id() == 1 ? 15 : 75, 30), null, points, INK);
        assertEquals(INK.getRGB(), image.getRGB(15, 30));
        assertEquals(INK.getRGB(), image.getRGB(75, 30));
        assertEquals(0, image.getRGB(45, 30));
        g.dispose();
        BufferedImage panned = new BufferedImage(100, 60, BufferedImage.TYPE_INT_ARGB);
        g = panned.createGraphics();
        DawarichLayer.paintPoints(g, p -> new Point(p.id() == 1 ? 25 : 85, 30), null, points, INK);
        assertEquals(INK.getRGB(), panned.getRGB(25, 30));
        assertEquals(0, panned.getRGB(15, 30));
        assertEquals(0, panned.getRGB(45, 30));
        g.dispose();
        assertEquals(0, points.get(0).longitude());
    }

    @Test void clipsAndIsReadOnlyAndLifecycleIsSourceBound() {
        org.openstreetmap.josm.spi.preferences.Config.setPreferencesInstance(new org.openstreetmap.josm.spi.preferences.MemoryPreferences());
        ReferenceDataset original = data("server", "source", p(1, 4, 5), p(2, -4, -5));
        DawarichLayer layer = new DawarichLayer(original, "reference", INK);
        BufferedImage image = new BufferedImage(100, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        DawarichLayer.paintPoints(g, p -> new Point(p.id() == 1 ? 20 : 80, 30), new Rectangle(0, 0, 40, 60), original.points(), INK);
        assertEquals(INK.getRGB(), image.getRGB(20, 30));
        assertEquals(0, image.getRGB(80, 30));
        g.dispose();
        assertEquals(2, layer.pointCount());
        assertTrue(layer.isSavable() == false);
        assertFalse(layer.checkSaveConditions());
        assertFalse(layer.isMergable(layer));
        Action[] entries = layer.getMenuEntries();
        assertTrue(entries.length >= 8);
        assertTrue(java.util.Arrays.stream(entries).anyMatch(a -> a instanceof org.openstreetmap.josm.gui.layer.CustomizeColor));
        assertFalse(java.util.Arrays.stream(entries).anyMatch(a -> a instanceof org.openstreetmap.josm.gui.layer.Layer.LayerSaveAction));
        assertFalse(java.util.Arrays.stream(entries).anyMatch(a -> a instanceof org.openstreetmap.josm.gui.dialogs.layer.MergeAction));
        assertTrue(layer.hasColor());
        assertNotNull(layer.getIcon());
        assertTrue(layer.pointCount() > 0);
        assertEquals(2, layer.pointCount());
        assertThrows(IllegalArgumentException.class, () -> layer.replaceDataset(data("other", "source", p(3, 0, 0))));
        layer.replaceDataset(data("server", "source", p(4, 8, 9)));
        assertEquals(1, layer.pointCount());
        layer.setColor(Color.BLUE);
        assertEquals(Color.BLUE, layer.getColor());
        layer.destroy();
        assertEquals(0, layer.pointCount());
        assertThrows(IllegalStateException.class, () -> layer.replaceDataset(original));
    }

    @Test void layerVisibilityOpacityAndColorAreIndependent() {
        org.openstreetmap.josm.spi.preferences.Config.setPreferencesInstance(new org.openstreetmap.josm.spi.preferences.MemoryPreferences());
        DawarichLayer first = new DawarichLayer(data("s", "a", p(1, 0, 0)), "one", INK);
        DawarichLayer second = new DawarichLayer(data("s", "b", p(2, 1, 1)), "two", Color.BLUE);
        first.setVisible(false);
        first.setOpacity(.4);
        first.setColor(Color.GREEN);
        assertFalse(first.isVisible());
        assertEquals(.4, first.getOpacity(), 0.0001);
        assertEquals(Color.GREEN, first.getColor());
        assertTrue(second.isVisible());
        assertEquals(1.0, second.getOpacity(), 0.0001);
        assertEquals(Color.BLUE, second.getColor());
    }

    @Test void standardVisibilityMenuActionTogglesOnlyItsLayer() {
        org.openstreetmap.josm.spi.preferences.Config.setPreferencesInstance(new org.openstreetmap.josm.spi.preferences.MemoryPreferences());
        DawarichLayer first = new DawarichLayer(data("s", "a", p(1, 0, 0)), "one", INK);
        DawarichLayer second = new DawarichLayer(data("s", "b", p(2, 1, 1)), "two", Color.BLUE);
        first.getMenuEntries()[0].actionPerformed(new java.awt.event.ActionEvent(first, 0, "toggle"));
        assertFalse(first.isVisible());
        assertTrue(second.isVisible());
    }

    @Test void painterUsesCurrentLayerColorForRenderedPixels() {
        org.openstreetmap.josm.spi.preferences.Config.setPreferencesInstance(new org.openstreetmap.josm.spi.preferences.MemoryPreferences());
        DawarichLayer layer = new DawarichLayer(data("s", "a", p(1, 0, 0)), "paint", INK);
        layer.setColor(Color.MAGENTA);
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        DawarichLayer.paintPoints(g, p -> new Point(10, 10), null, List.of(p(1, 0, 0)), layer.getColor());
        g.dispose();
        assertEquals(Color.MAGENTA.getRGB(), image.getRGB(10, 10));
    }

    @Test void boundingBoxUsesEveryPointAndEmptyOrDestroyedLayerAddsNothing() {
        org.openstreetmap.josm.spi.preferences.Config.setPreferencesInstance(new org.openstreetmap.josm.spi.preferences.MemoryPreferences());
        org.openstreetmap.josm.data.projection.Projection old = org.openstreetmap.josm.data.projection.ProjectionRegistry.getProjection();
        org.openstreetmap.josm.data.projection.Projection mercator = org.openstreetmap.josm.data.projection.Projections.getProjectionByCode("EPSG:3857");
        if (old == null) old = mercator;
        try {
            org.openstreetmap.josm.data.projection.ProjectionRegistry.setProjection(mercator);
            DawarichLayer layer = new DawarichLayer(data("s", "a", p(1, 10, 20), p(2, -10, -20)), "box", INK);
            BoundingXYVisitor box = new BoundingXYVisitor();
            layer.visitBoundingBox(box);
            assertNotNull(box.getBounds());
            assertTrue(box.getBounds().getMin().east() < -2_000_000);
            assertTrue(box.getBounds().getMax().east() > 2_000_000);
            DawarichLayer empty = new DawarichLayer(data("s", "e"), "empty", INK);
            BoundingXYVisitor emptyBox = new BoundingXYVisitor();
            empty.visitBoundingBox(emptyBox);
            assertNull(emptyBox.getBounds());
            layer.destroy();
            BoundingXYVisitor destroyedBox = new BoundingXYVisitor();
            layer.visitBoundingBox(destroyedBox);
            assertNull(destroyedBox.getBounds());
        } finally {
            org.openstreetmap.josm.data.projection.ProjectionRegistry.setProjection(old);
        }
    }
}
