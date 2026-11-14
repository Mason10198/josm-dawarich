import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.plugins.dawarich.*;

class ProgressSnapshotTest {
    private static QueryBounds query(){return new QueryBounds(-1,1,-1,1,LocalDate.now().minusDays(1),LocalDate.now(),ZoneId.of("UTC"),Instant.now());}
    private static ReferenceDataset data(String s,String id,QueryBounds q){return new ReferenceDataset(s,id,q,List.of());}
    @Test void snapshotAvailableDuringLoaderAndReturnsIdleAfterCompletion() throws Exception {
        SourceRegistry r=new SourceRegistry();var a=r.add("A");var b=r.add("B");r.setEndpoint("https://example.test",false);r.setKey(a.id,"dummy-a");r.setKey(b.id,"dummy-b");
        ExecutorService worker=Executors.newSingleThreadExecutor();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try(LoadReferenceTask task=new LoadReferenceTask(r,new MainLayerManager(),worker,(s,id,k,q,n,budget)->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new DawarichException(DawarichException.Category.CANCELLED,"Request cancelled");}return data(s,id,q);} )){
            CompletableFuture<?> f=task.start(query());assertTrue(entered.await(5,TimeUnit.SECONDS));LoadProgress p=task.progress();assertNotNull(p);assertEquals(a.id,p.sourceId());assertEquals(1,p.sourceIndex());assertEquals(2,p.sourceCount());assertEquals(0,p.requestedPoints());assertTrue(p.elapsedMillis()>=0);release.countDown();f.get(5,TimeUnit.SECONDS);assertNull(task.progress());
        }finally{release.countDown();worker.shutdownNow();}
    }
    @Test void missingKeyHasNoProgressAndSnapshotsAreImmutable() throws Exception {
        SourceRegistry r=new SourceRegistry();r.add("A");r.setEndpoint("https://example.test",false);ExecutorService worker=Executors.newSingleThreadExecutor();
        try(LoadReferenceTask task=new LoadReferenceTask(r,new MainLayerManager(),worker,(s,id,k,q,n,budget)->data(s,id,q))){task.start(query()).get(5,TimeUnit.SECONDS);assertNull(task.progress());}
        finally{worker.shutdownNow();}
    }
}
