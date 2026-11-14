package org.openstreetmap.josm.plugins.dawarich;

import java.time.*;
import java.util.*;
import java.util.function.*;
import javax.swing.*;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.tools.ImageProvider;

/** GPS-menu entry point; request bounds are captured once at Load press. */
public final class LoadReferenceAction extends JosmAction {
    record Viewport(double top, double left, double bottom, double right, int width, int height) { }
    private final Supplier<Viewport> viewSupplier;
    private final Predicate<QueryBounds> confirm;
    private final Function<QueryBounds, ?> starter;
    private final Runnable cancel;
    private final LoadReferenceDialog panel;
    private JDialog window;
    private boolean disposed;
    private BooleanSupplier loadingCheck = () -> false;

    public LoadReferenceAction(SourceRegistry registry, LoadReferenceTask task, LocalDate start, LocalDate end) {
        this(() -> {
            MapView v=MainApplication.getMap()==null?null:MainApplication.getMap().mapView;
            if(v==null||v.getWidth()<=0||v.getHeight()<=0)return null;
            LatLon a=v.getLatLon(0,0), b=v.getLatLon(v.getWidth(),v.getHeight());
            return new Viewport(a.lat(),a.lon(),b.lat(),b.lon(),v.getWidth(),v.getHeight());
        }, q -> JOptionPane.showConfirmDialog(MainApplication.getMainFrame(),
                "This view covers more than 100 miles. Load it anyway?", "Large reference-data area",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION,
            task::start, task::cancel, task::isLoading, new LoadReferenceDialog(registry,start,end));
    }
    LoadReferenceAction(Supplier<Viewport> views, Predicate<QueryBounds> confirm,
                        Function<QueryBounds, ?> starter, Runnable cancel, BooleanSupplier loading,
                        LoadReferenceDialog panel) {
        super("Load Dawarich reference…", new ImageProvider("dawarich").setDirs(java.util.List.of("/images")),
            "Load reference data for the current view", null, false, "dawarich-load", false);
        this.viewSupplier=views; this.confirm=confirm; this.starter=starter; this.cancel=cancel;
        this.loadingCheck=loading; this.panel=panel;
        panel.setLoadHandler(this::load); panel.setCancelHandler(cancel);
    }
    @Override public void actionPerformed(java.awt.event.ActionEvent e) {
        if(disposed)return;
        if(window==null){window=new JDialog(MainApplication.getMainFrame(),"Dawarich reference data",false);
            window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            window.addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosing(java.awt.event.WindowEvent e){panel.clearSecretEditors();cancel.run();window.setVisible(false);}});
            window.setContentPane(panel);window.setSize(620,430);window.setLocationRelativeTo(MainApplication.getMainFrame());}
        window.setVisible(true);
    }
    void load(LoadReferenceDialog.LoadRequest request) {
        if(disposed)return;
        panel.clearSecretEditors();
        if(loadingCheck.getAsBoolean()){panel.loadButton().setEnabled(false);panel.statusLabel().setText("A load is already in progress");return;}
        Viewport v=viewSupplier.get();
        if(v==null||v.width()<=0||v.height()<=0){panel.statusLabel().setText("Open a map view before loading reference data");return;}
        final QueryBounds query;
        try { query=new QueryBounds(Math.min(v.top(),v.bottom()),Math.max(v.top(),v.bottom()),v.left(),v.right(),request.startDate,request.endDate,request.timezone,Instant.now()); }
        catch(RuntimeException ex){panel.statusLabel().setText("Invalid view bounds or date range; no request started");return;}
        if(query.broadAreaNeedsConfirmation()&&!confirm.test(query)){panel.statusLabel().setText("Load cancelled; no request started");return;}
        panel.loadButton().setEnabled(false);panel.statusLabel().setText("Loading reference data…");
        try{starter.apply(query);}catch(RuntimeException ex){panel.loadButton().setEnabled(true);panel.statusLabel().setText("Could not start load");}
    }
    public void showResult(LoadReferenceTask.BatchResult result) {
        if(disposed)return;
        StringBuilder text=new StringBuilder("<html>").append(escape(result.status().toString())).append(" — ").append(result.totalPoints()).append(" points; fair per-source quota ").append(result.perSourceQuota());
        for(var source:result.sources()){
            text.append("<br>").append(escape(panel.sourceLabel(source.sourceId()))).append(": ").append(escape(source.status().toString()));
            if(source.status()==LoadReferenceTask.Status.FAILED||source.status()==LoadReferenceTask.Status.SKIPPED||source.status()==LoadReferenceTask.Status.CANCELLED)
                text.append(" — ").append(escape(safeFailureReason(source.message())));
            if(source.pointCount()>0)text.append(" (").append(source.pointCount()).append(" points)");
            if(source.stale()&&source.pointCount()>0)text.append(" — old layer retained");
        }
        if(!result.overlappingIds().isEmpty())text.append("; ").append(result.overlappingIds().size()).append(" overlapping point IDs across sources; keys may refer to the same account. Layers kept separate.");
        panel.statusLabel().setText(text.append("</html>").toString());
        panel.loadButton().setEnabled(result.status()!=LoadReferenceTask.Status.LOADING&&!loadingCheck.getAsBoolean());
    }
    /** Only fixed local diagnostics are displayable; never expose raw exception/HTTP text. */
    private static String safeFailureReason(String message){
        if(message==null)return "Source load failed";
        return switch(message){
            case "API key is not configured", "Invalid source authorization", "Source authorization failed",
                "Request timed out", "Service connection failed", "Service rate limit", "Service unavailable",
                "Redirect refused", "Unexpected service response", "Invalid response content type",
                "Unexpected content encoding", "Invalid pagination headers", "Missing or invalid pagination headers",
                "Invalid point data", "Contradictory pagination response", "Invalid request parameters",
                "Invalid request or response", "Batch limit exceeded", "Batch deadline exceeded",
                "Source exceeds its fair point capacity", "Source returned mismatched dataset identity",
                "Retained point capacity exceeded", "Invalid dataset parameters", "Point outside requested bounds",
                "Duplicate point identifier", "Too many points within one second; narrow the area",
                "Request cancelled", "Skipped after shared batch failure",
                "Coordinator disposed", "Server changed", "Source removed", "Source disabled or removed",
                "Source settings changed", "Layer lifecycle changed" -> message;
            default -> "Source load failed";
        };
    }
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    public void setLoadingCheck(BooleanSupplier check){loadingCheck=Objects.requireNonNull(check);}
    public void dispose(){if(disposed)return;disposed=true;panel.clearSecretEditors();if(window!=null)window.dispose();super.destroy();}
    LoadReferenceDialog panel(){return panel;}
}
