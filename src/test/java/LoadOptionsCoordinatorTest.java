import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.plugins.dawarich.*;

class LoadOptionsCoordinatorTest {
 @BeforeAll static void init(){org.openstreetmap.josm.spi.preferences.Config.setPreferencesInstance(new org.openstreetmap.josm.spi.preferences.MemoryPreferences());}
 static QueryBounds query(LoadOptions options){return new QueryBounds(-1,1,-1,1,LocalDate.of(2020,1,1),LocalDate.of(2020,1,2),ZoneId.of("UTC"),Instant.parse("2020-01-03T00:00:00Z")).withOptions(options);}
 static SourceRegistry registry(int n){var r=new SourceRegistry();r.setEndpoint("https://example.test",false);for(int i=0;i<n;i++){var s=r.add("Synthetic "+i);r.setKey(s.id,"synthetic-"+i);}return r;}
 static ReferenceDataset dataset(String server,String id,QueryBounds q,int count){var points=new ArrayList<ReferencePoint>();long first=q.startInclusive().getEpochSecond();for(int i=0;i<count;i++)points.add(new ReferencePoint(i+1,0,0,first+i));return new ReferenceDataset(server,id,q,points);}
 @Test void raisedPointBudgetReallyLoadsMoreThanOldHardcodedCap()throws Exception{
  var r=registry(1);var ex=Executors.newSingleThreadExecutor();var quota=new AtomicLong();
  try(var task=new LoadReferenceTask(r,new MainLayerManager(),ex,(s,id,k,q,n,b)->{quota.set(n);return dataset(s,id,q,77222);})){var result=task.start(query(new LoadOptions(LoadOptions.Mode.DATE_RANGE,10000,100000))).get(10,TimeUnit.SECONDS);assertEquals(LoadReferenceTask.Status.COMPLETE,result.status(),result.sources().toString());assertEquals(77222,result.totalPoints());assertEquals(100000,quota.get());assertEquals(77222,task.layersSnapshot().get(r.rows().get(0).id).pointCount());}finally{ex.shutdownNow();}
 }
 @Test void recentCountAboveFairShareFailsBeforeAnyLoaderCall()throws Exception{
  var r=registry(2);var ex=Executors.newSingleThreadExecutor();var calls=new AtomicInteger();
  try(var task=new LoadReferenceTask(r,new MainLayerManager(),ex,(s,id,k,q,n,b)->{calls.incrementAndGet();return dataset(s,id,q,1);})){var result=task.start(query(new LoadOptions(LoadOptions.Mode.MOST_RECENT,30000,50000))).get(5,TimeUnit.SECONDS);assertEquals(LoadReferenceTask.Status.FAILED,result.status());assertEquals(0,calls.get());assertEquals(25000,result.perSourceQuota());assertEquals(2,result.sources().size());assertTrue(result.sources().stream().allMatch(x->x.message().contains("share")));}finally{ex.shutdownNow();}
 }
 @Test void recentSuccessReportsRequestedCountAndActualRecordedCoverage()throws Exception{
  var r=registry(1);var ex=Executors.newSingleThreadExecutor();
  try(var task=new LoadReferenceTask(r,new MainLayerManager(),ex,(s,id,k,q,n,b)->dataset(s,id,q,3))){var q=query(new LoadOptions(LoadOptions.Mode.MOST_RECENT,10,100));var result=task.start(q).get(5,TimeUnit.SECONDS);var source=result.sources().get(0);assertEquals(LoadReferenceTask.Status.COMPLETE,result.status());assertEquals(10,source.requestedPoints());assertEquals(q.startInclusive().getEpochSecond(),source.oldestTimestamp());assertEquals(q.startInclusive().getEpochSecond()+2,source.newestTimestamp());assertEquals(3,source.pointCount());}finally{ex.shutdownNow();}
 }
 @Test void recentCannotPublishMoreThanTheExplicitRequestedCount()throws Exception{
  var r=registry(1);var ex=Executors.newSingleThreadExecutor();
  try(var task=new LoadReferenceTask(r,new MainLayerManager(),ex,(s,id,k,q,n,b)->dataset(s,id,q,11))){var result=task.start(query(new LoadOptions(LoadOptions.Mode.MOST_RECENT,10,100))).get(5,TimeUnit.SECONDS);assertEquals(LoadReferenceTask.Status.FAILED,result.status());assertTrue(task.layersSnapshot().isEmpty());}finally{ex.shutdownNow();}
 }
 @Test void datasetOptionsArePartOfTheFrozenIdentity()throws Exception{
  var r=registry(1);var ex=Executors.newSingleThreadExecutor();
  try(var task=new LoadReferenceTask(r,new MainLayerManager(),ex,(s,id,k,q,n,b)->dataset(s,id,q.withOptions(LoadOptions.defaults()),1))){var result=task.start(query(new LoadOptions(LoadOptions.Mode.MOST_RECENT,10,100))).get(5,TimeUnit.SECONDS);assertEquals(LoadReferenceTask.Status.FAILED,result.status());assertTrue(result.sources().get(0).message().contains("identity"));}finally{ex.shutdownNow();}
 }
 @Test void retainedDisabledLayersStillConsumeConfiguredCapacity()throws Exception{
  var r=registry(2);var rows=r.rows();r.setEnabled(rows.get(1).id,false);var ex=Executors.newSingleThreadExecutor();var calls=new AtomicInteger();
  try(var task=new LoadReferenceTask(r,new MainLayerManager(),ex,(s,id,k,q,n,b)->{calls.incrementAndGet();return dataset(s,id,q,40000);})){assertEquals(LoadReferenceTask.Status.COMPLETE,task.start(query(LoadOptions.defaults())).get(5,TimeUnit.SECONDS).status());r.setEnabled(rows.get(0).id,false);r.setEnabled(rows.get(1).id,true);var result=task.start(query(new LoadOptions(LoadOptions.Mode.MOST_RECENT,20000,50000))).get(5,TimeUnit.SECONDS);assertEquals(LoadReferenceTask.Status.FAILED,result.status());assertEquals(1,calls.get());assertEquals(40000,task.layersSnapshot().get(rows.get(0).id).pointCount());assertEquals(10000,result.perSourceQuota());}finally{ex.shutdownNow();}
 }
}
