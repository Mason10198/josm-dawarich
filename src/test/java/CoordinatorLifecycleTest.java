import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.plugins.dawarich.*;

class CoordinatorLifecycleTest {
    private static QueryBounds query(){return new QueryBounds(-1,1,-1,1,LocalDate.now().minusDays(1),LocalDate.now(),ZoneId.of("UTC"),Instant.now());}
    private static ReferenceDataset data(String server,String id,QueryBounds q,long pointId){return new ReferenceDataset(server,id,q,List.of(new ReferencePoint(pointId,0,0,10)));}
    private static ExecutorService worker(){return Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r);t.setDaemon(true);return t;});}
    private static SourceRegistry registry(SourceSettings... rows){SourceRegistry r=new SourceRegistry();for(SourceSettings s:rows)r.add(s);r.setEndpoint("https://example.test",false);for(SourceSettings s:rows)r.setKey(s.id,"key-"+s.label);return r;}
    private static void load(LoadReferenceTask task)throws Exception{assertFalse(SwingUtilities.isEventDispatchThread());assertEquals(LoadReferenceTask.Status.COMPLETE,task.start(query()).get(2,TimeUnit.SECONDS).status());}

    @Test void keyRotationRemovesOnlyThatSourcesLayerAndData()throws Exception{
        var a=new SourceSettings("a","A","#112233",true);var b=new SourceSettings("b","B","#445566",true);var r=registry(a,b);var lm=new MainLayerManager();var ex=worker();
        try(var task=new LoadReferenceTask(r,lm,ex,(s,id,k,q,n,budget)->data(s,id,q,id.equals("a")?1:2))){load(task);var bLayer=task.layersSnapshot().get("b");var aLayer=task.layersSnapshot().get("a");r.setKey("a","rotated");assertFalse(task.layersSnapshot().containsKey("a"));assertEquals(1,aLayer.pointCount());assertSame(bLayer,task.layersSnapshot().get("b"));assertEquals(1,bLayer.pointCount());}finally{ex.shutdownNow();}
    }
    @Test void serverChangeClearsAllOldLayersAndKeys()throws Exception{
        var a=new SourceSettings("a","A","#112233",true);var b=new SourceSettings("b","B","#445566",true);var r=registry(a,b);var lm=new MainLayerManager();var ex=worker();
        try(var task=new LoadReferenceTask(r,lm,ex,(s,id,k,q,n,budget)->data(s,id,q,id.equals("a")?1:2))){load(task);var old=new ArrayList<>(task.layersSnapshot().values());r.setEndpoint("https://other.example",false);assertTrue(task.layersSnapshot().isEmpty());assertTrue(old.stream().allMatch(l->l.pointCount()==1));assertTrue(r.session("a").snapshot().apiKey()==null);assertTrue(r.session("b").snapshot().apiKey()==null);}finally{ex.shutdownNow();}
    }
    @Test void disableHidesAndStyleUpdatesExistingLayer()throws Exception{
        var a=new SourceSettings("a","A","#112233",true);var r=registry(a);var lm=new MainLayerManager();var ex=worker();
        try(var task=new LoadReferenceTask(r,lm,ex,(s,id,k,q,n,budget)->data(s,id,q,1))){load(task);var layer=task.layersSnapshot().get("a");r.setEnabled("a",false);assertFalse(layer.isVisible());assertSame(layer,task.layersSnapshot().get("a"));assertEquals(1,layer.pointCount());r.setEnabled("a",true);assertTrue(layer.isVisible());r.updateSettings(new SourceSettings("a","Renamed","#ABCDEF",true));assertEquals("Renamed",layer.getName());assertEquals(new Color(0xAB,0xCD,0xEF),layer.getColor());assertEquals(1,layer.pointCount());}finally{ex.shutdownNow();}
    }
    @Test void removalDuringFetchPreventsLateLayerPublication()throws Exception{
        var a=new SourceSettings("a","A","#112233",true);var r=registry(a);var lm=new MainLayerManager();var ex=worker();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var task=new LoadReferenceTask(r,lm,ex,(s,id,k,q,n,budget)->{entered.countDown();try{if(!release.await(2,TimeUnit.SECONDS))throw new IllegalStateException("test gate timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return data(s,id,q,1);})){try{var future=task.start(query());assertTrue(entered.await(2,TimeUnit.SECONDS));r.remove("a");release.countDown();future.get(2,TimeUnit.SECONDS);assertTrue(task.layersSnapshot().isEmpty());assertFalse(lm.getLayers().stream().anyMatch(DawarichLayer.class::isInstance));}finally{release.countDown();}}finally{ex.shutdownNow();}
    }
    @Test void manualLayerRemovalDuringFetchPreventsLateRepublishButKeepsKey()throws Exception{
        var a=new SourceSettings("a","A","#112233",true);var r=registry(a);var lm=new MainLayerManager();var ex=worker();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new java.util.concurrent.atomic.AtomicInteger();
        try(var task=new LoadReferenceTask(r,lm,ex,(s,id,k,q,n,budget)->{if(calls.getAndIncrement()==0)return data(s,id,q,1);entered.countDown();try{if(!release.await(2,TimeUnit.SECONDS))throw new IllegalStateException("test gate timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return data(s,id,q,2);})){try{load(task);var old=task.layersSnapshot().get("a");var future=task.start(query());assertTrue(entered.await(2,TimeUnit.SECONDS));SwingUtilities.invokeAndWait(()->lm.removeLayer(old));release.countDown();future.get(2,TimeUnit.SECONDS);assertTrue(task.layersSnapshot().isEmpty());assertFalse(r.session("a").snapshot().apiKey()==null);assertTrue(lm.getLayers().stream().noneMatch(DawarichLayer.class::isInstance));}finally{release.countDown();}}finally{ex.shutdownNow();}
    }
}
