package org.openstreetmap.josm.plugins.dawarich;

import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.openstreetmap.josm.gui.layer.*;

/** Sequential source-scoped fetch coordinator; UI adapters call start/cancel and observe results. */
public final class LoadReferenceTask implements LayerManager.LayerChangeListener, AutoCloseable {
    public enum Status { IDLE, LOADING, COMPLETE, PARTIAL, FAILED, CANCELLED, SKIPPED }
    @FunctionalInterface public interface SourceLoader { ReferenceDataset fetch(String server,String sourceId,String key,QueryBounds bounds,long quota,BatchBudget budget) throws DawarichException; }
    public record SourceResult(String sourceId,Status status,String message,boolean stale,int pointCount,
                               long oldestTimestamp,long newestTimestamp,int requestedPoints) {
        public SourceResult(String sourceId,Status status,String message,boolean stale,int pointCount){this(sourceId,status,message,stale,pointCount,0,0,0);}
    }
    public record BatchResult(Status status,List<SourceResult> sources,int totalPoints,Set<String> overlappingIds,long perSourceQuota) {
        public BatchResult { sources=List.copyOf(sources);overlappingIds=Set.copyOf(overlappingIds); }
        public BatchResult(Status status,List<SourceResult> sources,int totalPoints,Set<String> overlappingIds){this(status,sources,totalPoints,overlappingIds,0);}
    }
    private final SourceRegistry registry; private final MainLayerManager layers; private final SourceRegistry.ChangeListener registryListener=this::registryChanged; private final Executor executor; private final SourceLoader loader;
    private final AtomicBoolean active=new AtomicBoolean(); private final Map<String,DawarichLayer> owned=new LinkedHashMap<>(); private final Map<String,Long> epochs=new HashMap<>();
    private volatile BatchBudget budget; private volatile boolean disposed; private volatile Consumer<BatchResult> listener=r->{};
    public LoadReferenceTask(SourceRegistry r,MainLayerManager m,Executor e){this(r,m,e,(server,id,key,q,n,b)->{
        DawarichClient client=new DawarichClient(ConnectionSettings.parse(server,server.startsWith("http://")));
        return q.options().mode()==LoadOptions.Mode.MOST_RECENT
            ?new MostRecentFetcher(client).fetch(server,id,key,q,q.options().recentPointsPerSource(),b)
            :new TimeWindowFetcher(client).fetch(server,id,key,q,n,b);
    });}
    public LoadReferenceTask(SourceRegistry r,MainLayerManager m,Executor e,SourceLoader l){registry=Objects.requireNonNull(r);layers=Objects.requireNonNull(m);executor=Objects.requireNonNull(e);loader=Objects.requireNonNull(l);layers.addLayerChangeListener(this);registry.addChangeListener(registryListener);}
    public void setListener(Consumer<BatchResult> l){listener=Objects.requireNonNull(l);} public boolean isLoading(){return active.get();}
    public synchronized Map<String,DawarichLayer> layersSnapshot(){return Map.copyOf(owned);}
    public CompletableFuture<BatchResult> start(QueryBounds query){
        Objects.requireNonNull(query);if(disposed||!active.compareAndSet(false,true))throw new IllegalStateException("Load already active or disposed");
        String server=registry.endpoint();if(server==null){active.set(false);throw new IllegalStateException("Server endpoint is not configured");}
        List<SourceSettings> selected=registry.rows().stream().filter(s->s.enabled).toList();if(selected.isEmpty()){active.set(false);throw new IllegalStateException("No enabled sources selected");}
        long sg=registry.endpointGeneration();BatchBudget b=new BatchBudget();budget=b;Map<String,SourceSession.KeySnapshot> keys=new LinkedHashMap<>();Map<String,Long> ep=new HashMap<>();
        for(SourceSettings s:selected){SourceSession.KeySnapshot k=registry.session(s.id).snapshot();if(k.apiKey()!=null&&!k.apiKey().isBlank())keys.put(s.id,k);synchronized(this){ep.put(s.id,epochs.getOrDefault(s.id,0L));}}
        Set<String> selectedIds=new HashSet<>();selected.forEach(s->selectedIds.add(s.id));int retained;synchronized(this){retained=owned.entrySet().stream().filter(x->!selectedIds.contains(x.getKey())).mapToInt(x->x.getValue().pointCount()).sum();}
        long quota=Math.max(0,(query.options().totalPointLimit()-retained)/selected.size());CompletableFuture<BatchResult> f=new CompletableFuture<>();
        try{executor.execute(()->run(f,server,sg,query,selected,keys,ep,b,quota));}catch(RuntimeException e){budget=null;active.set(false);throw e;}return f;
    }
    private void run(CompletableFuture<BatchResult> f,String server,long sg,QueryBounds q,List<SourceSettings> selected,Map<String,SourceSession.KeySnapshot> keys,Map<String,Long> ep,BatchBudget b,long quota){
        List<SourceResult> out=new ArrayList<>();Set<String> overlaps=new HashSet<>();boolean stop=false;
        try {
            for(SourceSettings s:selected){
                if(b.isCancelled()){out.add(result(s,Status.CANCELLED,"Request cancelled",false));continue;}
                SourceSession.KeySnapshot k=keys.get(s.id);
                if(k==null){out.add(result(s,Status.FAILED,"API key is not configured",false));continue;}
                notifyProgress(new BatchResult(Status.LOADING,outWithPending(out,selected,s),countOwned(),overlaps,quota));
                try{
                    if(q.options().mode()==LoadOptions.Mode.MOST_RECENT&&q.options().recentPointsPerSource()>quota)
                        throw new DawarichException(DawarichException.Category.CAPACITY,"Recent count exceeds this source's share; reduce the count or raise the total point budget");
                    if(quota==0)throw new DawarichException(DawarichException.Category.CAPACITY,"Point limit exceeded; narrow the area or dates, or raise the total point budget");
                    ReferenceDataset d=loader.fetch(server,s.id,k.apiKey(),q,quota,b);b.check();
                    if(!Objects.equals(d.serverIdentity(),server)||!Objects.equals(d.sourceId(),s.id)||!sameQuery(d.query(),q))throw new DawarichException(DawarichException.Category.PROTOCOL,"Source returned mismatched dataset identity");
                    if(d.points().size()>quota)throw new DawarichException(DawarichException.Category.CAPACITY,"Source exceeds its fair point capacity");
                    if(q.options().mode()==LoadOptions.Mode.MOST_RECENT&&d.points().size()>q.options().recentPointsPerSource())
                        throw new DawarichException(DawarichException.Category.CAPACITY,"Source exceeds its fair point capacity");
                    String stale=invalid(s.id,sg,k.generation(),ep.get(s.id));if(stale!=null){out.add(result(s,Status.SKIPPED,stale,false));continue;}
                    publish(d,s,sg,k.generation(),ep.get(s.id),overlaps,quota);out.add(completedResult(s,d));
                }catch(DawarichException x){boolean old=layerCount(s.id)>0;out.add(result(s,x.category()==DawarichException.Category.CANCELLED?Status.CANCELLED:Status.FAILED,x.getMessage(),old,old?layerCount(s.id):0));if(x.category()==DawarichException.Category.CANCELLED){stop=true;continue;}if(switch(x.category()){case SERVER,RATE_LIMIT,CONNECTIVITY,REDIRECT,DEADLINE,LIMIT->true;default->false;})stop=true;
                }catch(Exception x){boolean old=layerCount(s.id)>0;boolean cancelled=b.isCancelled();out.add(result(s,cancelled?Status.CANCELLED:Status.FAILED,cancelled?"Request cancelled":"Source load failed",old,old?layerCount(s.id):0));if(cancelled)stop=true;}
                if(stop){Status skipped=b.isCancelled()?Status.CANCELLED:Status.SKIPPED;String why=b.isCancelled()?"Request cancelled":"Skipped after shared batch failure";for(SourceSettings rest:selected.subList(out.size(),selected.size()))out.add(result(rest,skipped,why,false));break;}
            }
            Status status=b.isCancelled()?Status.CANCELLED:out.stream().anyMatch(x->x.status()==Status.FAILED||x.status()==Status.SKIPPED)?(out.stream().anyMatch(x->x.status()==Status.COMPLETE)?Status.PARTIAL:Status.FAILED):Status.COMPLETE;
            overlaps.clear();overlaps.addAll(currentOverlaps());
            BatchResult finalResult=new BatchResult(status,out,countOwned(),overlaps,quota);
            runEdt(()->{budget=null;active.set(false);try{listener.accept(finalResult);}catch(RuntimeException ignored){}});
            f.complete(finalResult);
        }catch(Throwable fatal){f.completeExceptionally(fatal);}finally{if(budget==b){budget=null;active.set(false);}}
    }
    private static boolean sameQuery(QueryBounds a,QueryBounds b){return Double.compare(a.minLat(),b.minLat())==0&&Double.compare(a.maxLat(),b.maxLat())==0&&Double.compare(a.minLon(),b.minLon())==0&&Double.compare(a.maxLon(),b.maxLon())==0&&a.startInclusive().equals(b.startInclusive())&&a.endInclusive().equals(b.endInclusive())&&a.zone().equals(b.zone())&&a.options().equals(b.options());}
    private List<SourceResult> outWithPending(List<SourceResult> out,List<SourceSettings> all,SourceSettings current){List<SourceResult> v=new ArrayList<>(out);v.add(result(current,Status.LOADING,"Loading",false));for(SourceSettings s:all.subList(v.size(),all.size()))v.add(result(s,Status.IDLE,"Waiting",false));return v;}
    private SourceResult result(SourceSettings s,Status st,String msg,boolean stale){return result(s,st,msg,stale,0);} private SourceResult result(SourceSettings s,Status st,String msg,boolean stale,int count){return new SourceResult(s.id,st,msg,stale,count);}
    private SourceResult completedResult(SourceSettings s,ReferenceDataset d){
        LongSummaryStatistics timestamps=d.points().stream().mapToLong(ReferencePoint::timestamp).summaryStatistics();
        return new SourceResult(s.id,Status.COMPLETE,"Loaded",false,d.points().size(),
            timestamps.getCount()==0?0:timestamps.getMin(),timestamps.getCount()==0?0:timestamps.getMax(),
            d.query().options().mode()==LoadOptions.Mode.MOST_RECENT?d.query().options().recentPointsPerSource():0);
    }
    private int countOwned(){synchronized(this){return owned.values().stream().mapToInt(DawarichLayer::pointCount).sum();}} private int layerCount(String id){synchronized(this){DawarichLayer l=owned.get(id);return l==null?0:l.pointCount();}}
    private synchronized Set<String> currentOverlaps(){
        Set<Long> seen=new HashSet<>();Set<String> overlaps=new HashSet<>();
        for(DawarichLayer layer:owned.values())for(ReferencePoint p:layer.datasetPoints().points())if(!seen.add(p.id()))overlaps.add(Long.toString(p.id()));
        return overlaps;
    }
    private void notifyProgress(BatchResult r){runEdt(()->{try{listener.accept(r);}catch(RuntimeException ignored){}});}
    private String invalid(String id,long sg,long kg,long epoch){if(disposed)return "Coordinator disposed";if(registry.endpointGeneration()!=sg)return "Server changed";SourceSession s=registry.session(id);if(s==null)return "Source removed";SourceSession.KeySnapshot k=s.snapshot();if(k.removed()||!k.enabled())return "Source disabled or removed";if(k.generation()!=kg)return "Source settings changed";synchronized(this){if(epochs.getOrDefault(id,0L)!=epoch)return "Layer lifecycle changed";}return null;}
    private void publish(ReferenceDataset d,SourceSettings s,long sg,long kg,long epoch,Set<String> overlaps,long quota)throws DawarichException{try{runEdt(()->{String stale=invalid(s.id,sg,kg,epoch);if(stale!=null)throw new IllegalStateException(stale);synchronized(this){int total=d.points().size()+owned.entrySet().stream().filter(x->!x.getKey().equals(s.id)).mapToInt(x->x.getValue().pointCount()).sum();if(total>d.query().options().totalPointLimit())throw new IllegalStateException("CAPACITY: Retained point capacity exceeded");for(var e:owned.entrySet())if(!e.getKey().equals(s.id)){Set<Long> ids=new HashSet<>();e.getValue().datasetPoints().points().forEach(p->ids.add(p.id()));d.points().stream().filter(p->ids.contains(p.id())).forEach(p->overlaps.add(Long.toString(p.id())));}DawarichLayer old=owned.get(s.id);if(old!=null&&layers.containsLayer(old)){old.replaceDataset(d);old.setName(s.label);}else{DawarichLayer l=new DawarichLayer(d,s.label,Color.decode(s.color));layers.addLayer(l);owned.put(s.id,l);}}});}catch(IllegalStateException e){if(e.getMessage()!=null&&e.getMessage().startsWith("CAPACITY:"))throw new DawarichException(DawarichException.Category.CAPACITY,e.getMessage().substring(10).trim());throw e;}}
    private void registryChanged(SourceRegistry.Change c){if(disposed)return;String id=c.sourceId();switch(c.kind()){case KEY->{bump(id);removeOwned(id);}case SERVER->{cancel();synchronized(this){for(String key:new HashSet<>(owned.keySet()))epochs.merge(key,1L,Long::sum);}runEdt(()->{synchronized(this){for(DawarichLayer l:new ArrayList<>(owned.values()))if(layers.containsLayer(l))layers.removeLayer(l);owned.clear();}});}case REMOVED->{bump(id);removeOwned(id);}case ENABLED->{SourceSession session=registry.session(id);boolean enabled=session!=null&&session.snapshot().enabled();runEdt(()->{synchronized(this){DawarichLayer l=owned.get(id);if(l!=null&&layers.containsLayer(l))l.setVisible(enabled);}});}case STYLE->{SourceSettings setting=registry.rows().stream().filter(x->x.id.equals(id)).findFirst().orElse(null);if(setting!=null)runEdt(()->{synchronized(this){DawarichLayer l=owned.get(id);if(l!=null&&layers.containsLayer(l)){l.setName(setting.label);l.setColor(Color.decode(setting.color));}}});}}}
    private void bump(String id){synchronized(this){epochs.merge(id,1L,Long::sum);}}
    private void removeOwned(String id){runEdt(()->{synchronized(this){DawarichLayer l=owned.remove(id);if(l!=null&&layers.containsLayer(l))layers.removeLayer(l);}});}
    public void cancel(){BatchBudget b=budget;if(b!=null)b.cancel();} public void layerAdded(LayerManager.LayerAddEvent e){}
    public void layerRemoving(LayerManager.LayerRemoveEvent e){if(e.getRemovedLayer() instanceof DawarichLayer d)synchronized(this){if(owned.get(d.sourceId())==d){owned.remove(d.sourceId());epochs.merge(d.sourceId(),1L,Long::sum);}}} public void layerOrderChanged(LayerManager.LayerOrderChangeEvent e){}
    public void close(){synchronized(this){if(disposed)return;disposed=true;}cancel();registry.removeChangeListener(registryListener);layers.removeLayerChangeListener(this);registry.clearKeys();runEdt(()->{synchronized(this){for(DawarichLayer l:new ArrayList<>(owned.values()))if(layers.containsLayer(l))layers.removeLayer(l);owned.clear();}});}
    private static void runEdt(Runnable r){try{if(SwingUtilities.isEventDispatchThread())r.run();else SwingUtilities.invokeAndWait(r);}catch(InterruptedException x){Thread.currentThread().interrupt();throw new IllegalStateException(x);}catch(InvocationTargetException x){throw new IllegalStateException(x.getCause());}}
}
