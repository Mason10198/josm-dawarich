package org.openstreetmap.josm.plugins.dawarich;

import java.time.*;
import java.time.format.DateTimeFormatter;
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
    private boolean stopping;
    private BooleanSupplier loadingCheck = () -> false;
    private Supplier<LoadProgress> progressSource;
    private final javax.swing.Timer progressTimer=new javax.swing.Timer(200,e->refreshProgress());

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
        progressSource=task::progress;
    }
    LoadReferenceAction(Supplier<Viewport> views, Predicate<QueryBounds> confirm,
                        Function<QueryBounds, ?> starter, Runnable cancel, BooleanSupplier loading,
                        LoadReferenceDialog panel) {
        super("Load Dawarich reference…", new ImageProvider("dawarich").setDirs(java.util.List.of("/images")),
            "Load reference data for the current view", null, false, "dawarich-load", false);
        this.viewSupplier=views; this.confirm=confirm; this.starter=starter; this.cancel=cancel;
        this.loadingCheck=loading; this.panel=panel;
        progressTimer.setCoalesce(true);
        panel.setLoadHandler(this::load); panel.setCancelHandler(this::stopDownload);
        panel.setCloseHandler(this::closeWindow);
    }
    @Override public void actionPerformed(java.awt.event.ActionEvent e) {
        if(disposed)return;
        if(window==null){window=new JDialog(MainApplication.getMainFrame(),"Check imagery alignment with Dawarich GPS",false);
            window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            window.addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosing(java.awt.event.WindowEvent e){closeWindow();}});
            window.setContentPane(panel);window.setMinimumSize(new java.awt.Dimension(680,520));window.setSize(740,700);window.setLocationRelativeTo(MainApplication.getMainFrame());}
        window.setVisible(true);
        if(loadingCheck.getAsBoolean())beginProgress();
    }
    private void stopDownload(){boolean wasLoading=loadingCheck.getAsBoolean();stopping=wasLoading;cancel.run();progressTimer.stop();panel.setLoading(false);panel.loadButton().setEnabled(!loadingCheck.getAsBoolean());if(wasLoading)panel.progressLabel().setText("Stopping download…");}
    private void closeWindow(){stopDownload();panel.clearSecretEditors();if(window!=null)window.setVisible(false);}
    private void beginProgress(){if(stopping)return;panel.setLoading(true);if(progressSource!=null&&(window==null||window.isVisible())){progressTimer.start();refreshProgress();}}
    private void refreshProgress(){if(disposed||progressSource==null||!progressTimer.isRunning()||!loadingCheck.getAsBoolean())return;LoadProgress progress=progressSource.get();if(progress!=null)panel.updateProgress(progress);}
    void setProgressSource(Supplier<LoadProgress> source){progressSource=Objects.requireNonNull(source);}
    boolean progressTimerRunning(){return progressTimer.isRunning();}
    void load(LoadReferenceDialog.LoadRequest request) {
        if(disposed)return;
        panel.clearSecretEditors();
        if(loadingCheck.getAsBoolean()){panel.loadButton().setEnabled(false);panel.statusLabel().setText("A load is already in progress");return;}
        Viewport v=viewSupplier.get();
        if(v==null||v.width()<=0||v.height()<=0){panel.statusLabel().setText("Open a map view before loading reference data; add aerial imagery and zoom to the area you want to check");return;}
        final QueryBounds query;
        try { query=(request.options.mode()==LoadOptions.Mode.MOST_RECENT ? QueryBounds.mostRecent(Math.min(v.top(),v.bottom()),Math.max(v.top(),v.bottom()),v.left(),v.right(),request.timezone,Instant.now()) : new QueryBounds(Math.min(v.top(),v.bottom()),Math.max(v.top(),v.bottom()),v.left(),v.right(),request.startDate,request.endDate,request.timezone,Instant.now())).withOptions(request.options); }
        catch(RuntimeException ex){panel.statusLabel().setText("Invalid view bounds or date range; no request started");return;}
        if(query.broadAreaNeedsConfirmation()&&!confirm.test(query)){panel.statusLabel().setText("Load cancelled; no request started");return;}
        stopping=false;beginProgress();panel.statusLabel().setText("Loading GPS for this map area…");
        try{starter.apply(query);}catch(RuntimeException ex){progressTimer.stop();panel.setLoading(false);panel.statusLabel().setText("Could not start load; check the connection and selected GPS sources");}
    }
    public void showResult(LoadReferenceTask.BatchResult result) {
        if(disposed)return;
        if(result.status()==LoadReferenceTask.Status.LOADING){if(stopping)return;beginProgress();}else{stopping=false;progressTimer.stop();panel.setLoading(false);}
        String title=switch(result.status()){case COMPLETE->"GPS ready to compare with imagery";case PARTIAL->"Some GPS sources loaded";case FAILED->"GPS could not be loaded";case CANCELLED->"Download stopped";case LOADING->"Loading GPS history…";default->"GPS reference";};
        StringBuilder text=new StringBuilder("<html><body style='width:440px'>").append(title).append(" — ").append(result.totalPoints()).append(" points in GPS layers");
        for(var source:result.sources()){
            text.append("<br>").append(escape(panel.sourceLabel(source.sourceId()))).append(": ").append(switch(source.status()){case COMPLETE->"Ready";case FAILED->"Could not load";case CANCELLED->"Stopped";case SKIPPED->"Not loaded";case LOADING->"Downloading";case PARTIAL->"Partly loaded";case IDLE->"Waiting";});
            if(source.status()==LoadReferenceTask.Status.FAILED||source.status()==LoadReferenceTask.Status.SKIPPED||source.status()==LoadReferenceTask.Status.CANCELLED)
                text.append(" — ").append(escape(safeFailureReason(source.message())));
            if(source.pointCount()>0)text.append(" (").append(source.pointCount()).append(" points)");
            if(source.status()==LoadReferenceTask.Status.COMPLETE) {
                if(source.requestedPoints()>0)text.append(source.pointCount()>=source.requestedPoints()?" — reached requested ":" — fewer than requested ").append(source.requestedPoints());
                if(source.pointCount()==0) text.append(" — no matching points");
                else if(source.oldestTimestamp()>0&&source.newestTimestamp()>0) {
                    ZoneId zone=ZoneId.systemDefault();
                    text.append("<br>&nbsp;&nbsp;Recorded: ").append(escape(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(zone).format(Instant.ofEpochSecond(source.oldestTimestamp()))))
                        .append(" to ").append(escape(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(zone).format(Instant.ofEpochSecond(source.newestTimestamp()))));

                }
            }
            if(source.stale()&&source.pointCount()>0)text.append(" — old layer retained");
        }
        if(!result.overlappingIds().isEmpty())text.append("; ").append(result.overlappingIds().size()).append(" overlapping point IDs across sources; keys may refer to the same account. Layers kept separate.");
        panel.statusLabel().setText(text.append("</body></html>").toString());
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
                "Duplicate point identifier", "Unexpected point order",
                "Point limit exceeded; narrow the area or dates, or raise the total point budget",
                "Request limit exceeded; narrow the area or dates",
                "Response byte limit exceeded; narrow the area or dates",
                "Download byte limit exceeded; narrow the area or dates",
                "Recent count exceeds this source's share; reduce the count or raise the total point budget",
                "Too many points within one second; narrow the area",
                "Request cancelled", "Skipped after shared batch failure",
                "Coordinator disposed", "Server changed", "Source removed", "Source disabled or removed",
                "Source settings changed", "Layer lifecycle changed" -> message;
            default -> "Source load failed";
        };
    }
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    public void setLoadingCheck(BooleanSupplier check){loadingCheck=Objects.requireNonNull(check);}
    public void dispose(){if(disposed)return;disposed=true;progressTimer.stop();panel.setLoading(false);panel.clearSecretEditors();if(window!=null)window.dispose();super.destroy();}
    LoadReferenceDialog panel(){return panel;}
}
