import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.plugins.dawarich.*;

class LoadReferenceTaskTest {
    private static QueryBounds query(){return new QueryBounds(-1,1,-1,1,LocalDate.now().minusDays(1),LocalDate.now(),ZoneId.of("UTC"),Instant.now());}
    private static ReferenceDataset data(String server,String id,QueryBounds q,long pointId){return new ReferenceDataset(server,id,q,List.of(new ReferencePoint(pointId,0,0,Instant.now().getEpochSecond())));}
    @Test void sequentialSourcesPublishSeparateLayersAndSameIdsRemainSeparate() throws Exception {
        SourceRegistry r=new SourceRegistry();var a=r.add("A");var b=r.add("B");r.setEndpoint("https://example.test",false);r.setKey(a.id,"a");r.setKey(b.id,"b");MainLayerManager lm=new MainLayerManager();ExecutorService worker=Executors.newSingleThreadExecutor();
        try(LoadReferenceTask task=new LoadReferenceTask(r,lm,worker,(s,id,k,q,n,budget)->{assertFalse(SwingUtilities.isEventDispatchThread());return data(s,id,q,7);})){var result=task.start(query()).get(10,TimeUnit.SECONDS);assertEquals(LoadReferenceTask.Status.COMPLETE,result.status());assertEquals(2,task.layersSnapshot().size());assertEquals(2,result.overlappingIds().size()+1);assertEquals(1,task.layersSnapshot().get(a.id).pointCount());assertEquals(1,task.layersSnapshot().get(b.id).pointCount());}finally{worker.shutdown();worker.awaitTermination(5,TimeUnit.SECONDS);}
    }
    @Test void sourceFailureDoesNotEraseItsOldLayerOrPreventNextSource() throws Exception {
        SourceRegistry r=new SourceRegistry();var a=r.add("A");var b=r.add("B");r.setEndpoint("https://example.test",false);r.setKey(a.id,"a");r.setKey(b.id,"b");MainLayerManager lm=new MainLayerManager();ExecutorService worker=Executors.newSingleThreadExecutor();AtomicInteger pass=new AtomicInteger();
        try(LoadReferenceTask task=new LoadReferenceTask(r,lm,worker,(s,id,k,q,n,budget)->{if(pass.getAndIncrement()==0)return data(s,id,q,1);throw new IllegalStateException("synthetic source failure");})){var first=task.start(query()).get(10,TimeUnit.SECONDS);assertEquals(LoadReferenceTask.Status.PARTIAL,first.status());assertEquals(1,task.layersSnapshot().size());assertEquals(2,first.sources().size());}finally{worker.shutdown();worker.awaitTermination(5,TimeUnit.SECONDS);}
    }
    @Test void cancellationPreventsLaterSourceAndDoesNotPublishLateCurrentResult() throws Exception {
        SourceRegistry r=new SourceRegistry();var a=r.add("A");var b=r.add("B");r.setEndpoint("https://example.test",false);r.setKey(a.id,"a");r.setKey(b.id,"b");MainLayerManager lm=new MainLayerManager();ExecutorService worker=Executors.newSingleThreadExecutor();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        try(LoadReferenceTask task=new LoadReferenceTask(r,lm,worker,(s,id,k,q,n,budget)->{calls.incrementAndGet();entered.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}return data(s,id,q,1);})){var f=task.start(query());assertTrue(entered.await(5,TimeUnit.SECONDS));task.cancel();release.countDown();var result=f.get(10,TimeUnit.SECONDS);assertEquals(LoadReferenceTask.Status.CANCELLED,result.status());assertEquals(1,calls.get());assertTrue(task.layersSnapshot().isEmpty());}finally{release.countDown();worker.shutdown();worker.awaitTermination(5,TimeUnit.SECONDS);}
    }
    @Test void closeOffEdtRemovesCompletedLayersAndClearsKeys() throws Exception {
        SourceRegistry r=new SourceRegistry();var a=r.add("A");r.setEndpoint("https://example.test",false);r.setKey(a.id,"secret");MainLayerManager lm=new MainLayerManager();ExecutorService worker=Executors.newSingleThreadExecutor(x->{Thread t=new Thread(x);t.setDaemon(true);return t;});
        LoadReferenceTask task=new LoadReferenceTask(r,lm,worker,(s,id,k,q,n,budget)->data(s,id,q,1));
        try{task.start(query()).get(5,TimeUnit.SECONDS);assertEquals(1,task.layersSnapshot().size());ExecutorService closer=Executors.newSingleThreadExecutor();try{closer.submit(task::close).get(1,TimeUnit.SECONDS);}finally{closer.shutdownNow();}assertTrue(task.layersSnapshot().isEmpty());assertFalse(lm.getLayers().stream().anyMatch(x->x instanceof DawarichLayer));assertTrue(r.session(a.id).snapshot().apiKey()==null||r.session(a.id).snapshot().apiKey().isEmpty());}finally{worker.shutdownNow();}
    }
    @Test void closeOnEdtIsIdempotent() throws Exception {
        SourceRegistry r=new SourceRegistry();var a=r.add("A");r.setEndpoint("https://example.test",false);r.setKey(a.id,"secret");MainLayerManager lm=new MainLayerManager();ExecutorService worker=Executors.newSingleThreadExecutor(x->{Thread t=new Thread(x);t.setDaemon(true);return t;});LoadReferenceTask task=new LoadReferenceTask(r,lm,worker,(s,id,k,q,n,budget)->data(s,id,q,2));
        try{task.start(query()).get(5,TimeUnit.SECONDS);SwingUtilities.invokeAndWait(()->{task.close();task.close();});assertTrue(task.layersSnapshot().isEmpty());assertFalse(lm.getLayers().stream().anyMatch(x->x instanceof DawarichLayer));assertTrue(r.session(a.id).snapshot().apiKey()==null||r.session(a.id).snapshot().apiKey().isEmpty());}finally{worker.shutdownNow();}
    }
}
