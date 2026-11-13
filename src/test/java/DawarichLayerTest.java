package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
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
        assertEquals(0, layer.getMenuEntries().length);
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
}
