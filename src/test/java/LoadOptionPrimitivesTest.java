package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class LoadOptionPrimitivesTest {
 @Test void defaultsAndExplicitBoundsAreValidated(){
  assertEquals(new LoadOptions(LoadOptions.Mode.DATE_RANGE,10000,50000),LoadOptions.defaults());
  assertDoesNotThrow(()->new LoadOptions(LoadOptions.Mode.DATE_RANGE,10000,1));
  assertDoesNotThrow(()->new LoadOptions(LoadOptions.Mode.MOST_RECENT,250000,250000));
  assertThrows(IllegalArgumentException.class,()->new LoadOptions(null,1,1));
  assertThrows(IllegalArgumentException.class,()->new LoadOptions(LoadOptions.Mode.DATE_RANGE,0,1));
  assertThrows(IllegalArgumentException.class,()->new LoadOptions(LoadOptions.Mode.DATE_RANGE,1,250001));
  assertThrows(IllegalArgumentException.class,()->new LoadOptions(LoadOptions.Mode.MOST_RECENT,2,1));
 }
 @Test void recentScopeIncludesFirstPositiveEpochFixRegardlessOfTimeZone(){
  for(String zone:new String[]{"America/Chicago","Asia/Tokyo","UTC"}){
   var q=QueryBounds.mostRecent(-1,1,-1,1,ZoneId.of(zone),Instant.parse("2026-01-01T00:00:00.999Z"));
   assertEquals(Instant.EPOCH,q.startInclusive());assertEquals(Instant.parse("2026-01-01T00:00:00Z"),q.endInclusive());
   var options=new LoadOptions(LoadOptions.Mode.MOST_RECENT,12,100000);var copied=q.withOptions(options);
   assertEquals(q.startInclusive(),copied.startInclusive());assertEquals(q.endInclusive(),copied.endInclusive());assertEquals(options,copied.options());assertNotEquals(options,q.options());
  }
 }
 @Test void optionsCannotAlterFrozenDstDateInstants(){
  var q=new QueryBounds(-1,1,-1,1,LocalDate.of(2026,3,8),LocalDate.of(2026,3,8),ZoneId.of("America/Chicago"),Instant.parse("2026-04-01T00:00:00Z"));
  var copy=q.withOptions(new LoadOptions(LoadOptions.Mode.DATE_RANGE,10,100000));
  assertEquals(q.startInclusive(),copy.startInclusive());assertEquals(q.endInclusive(),copy.endInclusive());assertEquals(q.zone(),copy.zone());
 }
 @Test void futureDateCapOccursBeforePotentialCalendarOverflow(){
  var q=new QueryBounds(-1,1,-1,1,LocalDate.of(2020,1,1),LocalDate.MAX,ZoneId.of("UTC"),Instant.parse("2026-01-01T00:00:00Z"));
  assertEquals(Instant.parse("2026-01-01T00:00:00Z"),q.endInclusive());
 }
 @Test void requestAndDownloadLimitsHaveDistinctFixedReasons()throws Exception{
  var requests=new BatchBudget(1,Duration.ofSeconds(10),10,10);requests.beginRequest();
  var requestFailure=assertThrows(DawarichException.class,requests::beginRequest);
  assertEquals("Request limit exceeded; narrow the area or dates",requestFailure.getMessage());
  var bytes=new BatchBudget(1,Duration.ofSeconds(10),10,10);
  var byteFailure=assertThrows(DawarichException.class,()->bytes.addBytes(11));
  assertEquals("Download byte limit exceeded; narrow the area or dates",byteFailure.getMessage());
 }
}
