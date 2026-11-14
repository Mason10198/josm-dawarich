package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LargeLoadBudgetTest {
 @Test void defaultAllowsRealisticLargeSlimTransferButRemainsBounded()throws Exception{
  BatchBudget b=new BatchBudget();assertTrue(b.remainingNanos()>TimeUnit.SECONDS.toNanos(170));assertTrue(b.remainingNanos()<=TimeUnit.SECONDS.toNanos(180));
  for(int i=0;i<100;i++){b.beginRequest();b.addBytes(1024*1024);}
  assertEquals(100L*1024*1024,b.downloadedBytes());assertEquals(100,b.requestCount());
  assertEquals(8*1024*1024,b.maxResponseBytes());
  DawarichException ex=assertThrows(DawarichException.class,()->b.addBytes(29*1024*1024));assertEquals(DawarichException.Category.LIMIT,ex.category());assertTrue(ex.getMessage().contains("Download byte limit"));
 }
 @Test void requestLimitAndCancellationAreNotRelaxed()throws Exception{
  BatchBudget b=new BatchBudget();for(int i=0;i<128;i++)b.beginRequest();assertThrows(DawarichException.class,b::beginRequest);
  BatchBudget cancelled=new BatchBudget();cancelled.cancel();assertEquals(DawarichException.Category.CANCELLED,assertThrows(DawarichException.class,cancelled::check).category());
  BatchBudget tiny=new BatchBudget(1,Duration.ofNanos(1),1,1);assertEquals(DawarichException.Category.DEADLINE,assertThrows(DawarichException.class,tiny::check).category());
 }
}
