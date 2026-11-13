package org.openstreetmap.josm.plugins.dawarich;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Objects;
import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Icon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.Layer;

import org.openstreetmap.josm.gui.layer.Layer.SeparatorLayerAction;
import org.openstreetmap.josm.gui.layer.CustomizeColor;
import org.openstreetmap.josm.actions.RenameLayerAction;
import org.openstreetmap.josm.gui.dialogs.LayerListPopup;
import org.openstreetmap.josm.tools.I18n;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.openstreetmap.josm.tools.ImageProvider;

/** Source-scoped, read-only reference fixes. */
public final class DawarichLayer extends Layer {
    private final String serverIdentity;
    private final String sourceId;
    private volatile ReferenceDataset dataset;
    private volatile Color color;

    public DawarichLayer(ReferenceDataset dataset, String label, Color color) {
        super(Objects.requireNonNull(label));
        this.dataset = Objects.requireNonNull(dataset);
        this.serverIdentity = dataset.serverIdentity();
        this.sourceId = dataset.sourceId();
        this.color = Objects.requireNonNull(color);
    }

    public String serverIdentity() { return serverIdentity; }
    public String sourceId() { return sourceId; }
    public int pointCount() { ReferenceDataset d = dataset; return d == null ? 0 : d.points().size(); }
    ReferenceDataset datasetPoints() { return dataset; }

    public synchronized void replaceDataset(ReferenceDataset replacement) {
        Objects.requireNonNull(replacement);
        if (dataset == null) throw new IllegalStateException("Layer has been destroyed");
        if (!serverIdentity.equals(replacement.serverIdentity()) || !sourceId.equals(replacement.sourceId()))
            throw new IllegalArgumentException("Dataset identity does not match layer");
        dataset = replacement;
        invalidate();
    }

    @Override public void paint(Graphics2D graphics, MapView mapView, org.openstreetmap.josm.data.Bounds bounds) {
        ReferenceDataset current = dataset;
        if (current != null) paintPoints(graphics, p -> mapView.getPoint(new LatLon(p.latitude(), p.longitude())),
                graphics.getClipBounds(), current.points(), color);
    }

    /** Package-visible production painter seam: projects fresh on every paint, never caches positions. */
    static void paintPoints(Graphics2D g, java.util.function.Function<ReferencePoint, Point> projection,
            Rectangle clip, java.util.List<ReferencePoint> points, Color color) {
        if (g == null || points == null || color == null) return;
        g.setColor(color);
        for (ReferencePoint p : points) {
            Point at = projection.apply(p);
            if (at != null && (clip == null || clip.intersects(at.x - 3, at.y - 3, 7, 7)))
                g.fillOval(at.x - 2, at.y - 2, 5, 5);
        }
    }

    @Override public Icon getIcon() { return new ImageProvider("dawarich").setDirs(java.util.List.of("/images")).get(); }
    @Override public boolean hasColor() { return true; }
    @Override public synchronized Color getColor() { return color; }
    @Override public synchronized void setColor(Color color) { this.color = Objects.requireNonNull(color); invalidate(); }
    @Override public String getToolTipText() { return getName() + " — " + pointCount() + " reference points"; }
    @Override public Object getInfoComponent() { return new JLabel(getToolTipText()); }
    @Override public Action[] getMenuEntries() {
        return new Action[] {
            new AbstractAction(I18n.tr("Show/hide layer")) {
                @Override public void actionPerformed(ActionEvent e) { setVisible(!isVisible()); }
            },
            new AbstractAction(I18n.tr("Delete layer")) {
                @Override public void actionPerformed(ActionEvent e) {
                    org.openstreetmap.josm.gui.MainApplication.getLayerManager().removeLayer(DawarichLayer.this);
                }
            },
            new AbstractAction(I18n.tr("Zoom to layer")) {
                @Override public void actionPerformed(ActionEvent e) {
                    org.openstreetmap.josm.gui.MapFrame map = org.openstreetmap.josm.gui.MainApplication.getMap();
                    if (map != null) {
                        org.openstreetmap.josm.data.ProjectionBounds bounds = getViewProjectionBounds();
                        if (bounds != null) map.mapView.scheduleZoomTo(new org.openstreetmap.josm.data.ViewportData(bounds));
                    }
                }
            },
            SeparatorLayerAction.INSTANCE,
            new CustomizeColor(this),
            SeparatorLayerAction.INSTANCE,
            new RenameLayerAction(getAssociatedFile(), this),
            SeparatorLayerAction.INSTANCE,
            new LayerListPopup.InfoAction(this)
        };
    }
    @Override public void mergeFrom(Layer layer) { throw new UnsupportedOperationException("Reference layers cannot be merged"); }
    @Override public boolean isMergable(Layer layer) { return false; }
    @Override public void visitBoundingBox(BoundingXYVisitor visitor) {
        ReferenceDataset current = dataset;
        if (current != null) for (ReferencePoint p : current.points()) visitor.visit(new LatLon(p.latitude(), p.longitude()));
    }
    @Override public boolean isSavable() { return false; }
    @Override public boolean checkSaveConditions() { return false; }
    @Override public synchronized void destroy() { dataset = null; super.destroy(); }
}
