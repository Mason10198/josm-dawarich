package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class CursorTraversalTest {
 final QueryBounds scope=new QueryBounds(-10,10,-10,10,LocalDate.of(1970,1,1),LocalDate.of(1970,1,1),ZoneOffset.UTC,Instant.ofEpochSecond(99));
 static ReferencePoint p(long id,long time){return new ReferencePoint(id,1,1,time);}
 static DawarichClient.Page page(int total,ReferencePoint...rows){return new DawarichClient.Page(List.of(rows),total);}
 @Test void boundaryMissingOrChangedOriginalsFailsInsteadOfPublishing(){
  for(boolean changed:new boolean[]{false,true}){
   TimeWindowFetcher.PageFetcher f=(a,b)->a==b?(changed?new DawarichClient.Page(List.of(new ReferencePoint(2,2,1,2),p(3,2)),1):page(1,p(2,2))):page(2,p(1,1),p(2,2),p(3,2));
   DawarichException ex=assertThrows(DawarichException.class,()->new TimeWindowFetcher(f).fetch("s","x","k",scope,10,new BatchBudget()));assertEquals(DawarichException.Category.PROTOCOL,ex.category());assertTrue(ex.getMessage().contains(changed?"Conflicting":"Boundary"));
  }
 }
 @Test void duplicateAcrossCursorWindowsAndUntakenBadSuffixFail(){
  var f=(TimeWindowFetcher.PageFetcher)(a,b)->a==b?page(1,p(2,2)):a==0?page(2,p(1,1),p(2,2)):page(1,p(1,3));
  assertTrue(assertThrows(DawarichException.class,()->new TimeWindowFetcher(f).fetch("s","x","k",scope,10,new BatchBudget())).getMessage().contains("Duplicate"));
  assertThrows(DawarichException.class,()->new MostRecentFetcher((a,b)->page(1,p(1,99),new ReferencePoint(2,50,1,98))).fetch("s","x","k",scope,1,new BatchBudget()));
 }
 @Test void tiedCutoffCanBeSatisfiedByDenseBoundarySubsetButNotInvented()throws Exception{
  TimeWindowFetcher.PageFetcher f=(a,b)->a==b?page(2,p(5,10),p(6,10),p(7,10),p(8,10)):page(2,p(1,11),p(2,10),p(3,10),p(4,10));
  var got=new MostRecentFetcher(f).fetch("s","x","k",scope,5,new BatchBudget());assertEquals(List.of(p(1,11),p(5,10),p(6,10),p(7,10),p(8,10)),got.points());
  assertTrue(assertThrows(DawarichException.class,()->new MostRecentFetcher(f).fetch("s","x","k",scope,6,new BatchBudget())).getMessage().contains("Too many"));
 }
 @Test void boundaryAtInclusiveEndIsResolvedAndCanPermuteTies()throws Exception{
  TimeWindowFetcher.PageFetcher f=(a,b)->a==b?page(1,p(4,99),p(3,99),p(2,99)):page(2,p(1,98),p(2,99),p(3,99));
  var got=new TimeWindowFetcher(f).fetch("s","x","k",scope,10,new BatchBudget());assertEquals(Set.of(1L,2L,3L,4L),new HashSet<>(got.points().stream().map(ReferencePoint::id).toList()));
 }
 @Test void actualHttp250kInBothDirectionsHasExactOriginalsAndProportionalRequests()throws Exception{
  long origin=1700000000L;AtomicInteger calls=new AtomicInteger();AtomicLong bytes=new AtomicLong();AtomicBoolean early=new AtomicBoolean();AtomicReference<BatchBudget> active=new AtomicReference<>();
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/api/v1/points",x->{try{
   int call=calls.incrementAndGet();if(call==2)early.set(active.get().selectedPointCount()>0);
   Map<String,String> q=new HashMap<>();for(String part:x.getRequestURI().getRawQuery().split("&")){String[] pair=part.split("=",2);q.put(pair[0],pair[1]);}
   if(!"1".equals(q.get("page"))||!"10000".equals(q.get("per_page")))throw new AssertionError("Offset or wrong page size");
   long a=Long.parseLong(q.get("start_at")),b=Long.parseLong(q.get("end_at"));int low=(int)Math.max(1,Math.min(250001,(a-origin)*3+1)),high=(int)Math.max(0,Math.min(250000,(b-origin+1)*3));int count=Math.max(0,high-low+1);boolean desc="desc".equals(q.get("order"));StringBuilder out=new StringBuilder("[");
   for(int n=0,id=desc?high:low;n<Math.min(10000,count);n++,id+=desc?-1:1){if(n>0)out.append(',');out.append("{\"id\":").append(id).append(",\"latitude\":\"").append(1+(id%101)/100000.0).append("\",\"longitude\":\"").append(1+(id%103)/100000.0).append("\",\"timestamp\":").append(origin+(id-1)/3).append(",\"velocity\":4.2,\"country_name\":\"Synthetic country\",\"tracker_id\":\"Synthetic tracker\"}");}
   byte[] body=out.append(']').toString().getBytes(StandardCharsets.UTF_8);bytes.addAndGet(body.length);x.getResponseHeaders().set("Content-Type","application/json");x.getResponseHeaders().set("X-Current-Page","1");x.getResponseHeaders().set("X-Total-Pages",Integer.toString((count+9999)/10000));x.sendResponseHeaders(200,body.length);try(var stream=x.getResponseBody()){stream.write(body);}
  }finally{x.close();}});server.start();
  try{var client=new DawarichClient(ConnectionSettings.parse("http://127.0.0.1:"+server.getAddress().getPort(),true));
   for(boolean recent:new boolean[]{false,true}){
    calls.set(0);bytes.set(0);early.set(false);BatchBudget budget=new BatchBudget();active.set(budget);QueryBounds q=new QueryBounds(-10,10,-10,10,LocalDate.of(1970,1,1),LocalDate.of(2024,1,1),ZoneOffset.UTC,Instant.ofEpochSecond(origin+100000)).withOptions(new LoadOptions(recent?LoadOptions.Mode.MOST_RECENT:LoadOptions.Mode.DATE_RANGE,250000,250000));
    var data=recent?new MostRecentFetcher(client).fetch("s","x","synthetic",q,250000,budget):new TimeWindowFetcher(client).fetch("s","x","synthetic",q,250000,budget);
    assertEquals(250000,data.points().size());BitSet ids=new BitSet(250001);for(ReferencePoint p:data.points()){int id=(int)p.id();assertTrue(id>=1&&id<=250000&&!ids.get(id));ids.set(id);assertEquals(origin+(id-1)/3,p.timestamp());assertEquals(1+(id%101)/100000.0,p.latitude());assertEquals(1+(id%103)/100000.0,p.longitude());}
    assertEquals(250000,ids.cardinality());assertTrue(early.get());assertTrue(calls.get()<=51,"Requests="+calls.get());assertEquals(calls.get(),budget.requestCount());assertEquals(bytes.get(),budget.downloadedBytes());assertEquals(250000,budget.selectedPointCount());
   }
  }finally{server.stop(0);}
 }
}
