package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class MostRecentIntegrationTest {
 @Test void realDescendingTransportMatchesViewportFilteredNewestOracleOverSeveralPages()throws Exception{
  var all=new ArrayList<ReferencePoint>();
  for(int i=1;i<=23000;i++)all.add(new ReferencePoint(i,(i%10)*0.01,(i%20)*0.01,10000+i/3));
  // Newer points outside the viewport must not displace in-view observations.
  for(int i=1;i<=100;i++)all.add(new ReferencePoint(50000+i,20,20,50000+i));
  var expected=all.stream().filter(p->p.latitude()>=-1&&p.latitude()<=1&&p.longitude()>=-1&&p.longitude()<=1).sorted(Comparator.comparingLong(ReferencePoint::timestamp).reversed().thenComparing(Comparator.comparingLong(ReferencePoint::id).reversed())).limit(15000).toList();
  var requestCount=new AtomicInteger();var contractFailure=new AtomicReference<Throwable>();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/v1/points",exchange->{try{
   requestCount.incrementAndGet();Map<String,String> q=new HashMap<>();for(String pair:exchange.getRequestURI().getRawQuery().split("&")){String[] parts=pair.split("=",2);q.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),URLDecoder.decode(parts[1],StandardCharsets.UTF_8));}
   assertEquals("GET",exchange.getRequestMethod());assertEquals("Bearer synthetic-only",exchange.getRequestHeaders().getFirst("Authorization"));assertEquals("1",q.get("page"));assertEquals("desc",q.get("order"));assertEquals("10000",q.get("per_page"));assertEquals("-1.0",q.get("min_latitude"));assertEquals("1.0",q.get("max_latitude"));assertEquals("-1.0",q.get("min_longitude"));assertEquals("1.0",q.get("max_longitude"));
   long a=Long.parseLong(q.get("start_at")),b=Long.parseLong(q.get("end_at"));
   var matches=all.stream().filter(p->p.timestamp()>=a&&p.timestamp()<=b&&p.latitude()>=Double.parseDouble(q.get("min_latitude"))&&p.latitude()<=Double.parseDouble(q.get("max_latitude"))&&p.longitude()>=Double.parseDouble(q.get("min_longitude"))&&p.longitude()<=Double.parseDouble(q.get("max_longitude"))).sorted(Comparator.comparingLong(ReferencePoint::timestamp).reversed().thenComparing(Comparator.comparingLong(ReferencePoint::id).reversed())).toList();
   StringJoiner body=new StringJoiner(",","[","]");for(var p:matches.subList(0,Math.min(10000,matches.size())))body.add("{\"id\":"+p.id()+",\"latitude\":"+p.latitude()+",\"longitude\":"+p.longitude()+",\"timestamp\":"+p.timestamp()+"}");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().set("Content-Type","application/json");exchange.getResponseHeaders().set("X-Current-Page","1");exchange.getResponseHeaders().set("X-Total-Pages",Integer.toString((matches.size()+9999)/10000));exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
  }catch(Throwable t){contractFailure.set(t);exchange.close();}});server.start();
  try{
   String endpoint="http://127.0.0.1:"+server.getAddress().getPort();
   var bounds=QueryBounds.mostRecent(-1,1,-1,1,ZoneId.of("UTC"),Instant.ofEpochSecond(100000)).withOptions(new LoadOptions(LoadOptions.Mode.MOST_RECENT,15000,100000));
   var actual=new MostRecentFetcher(new DawarichClient(ConnectionSettings.parse(endpoint,true))).fetch(endpoint,"synthetic-source","synthetic-only",bounds,15000,new BatchBudget());
   assertNull(contractFailure.get());assertTrue(requestCount.get()>1);assertTrue(requestCount.get()<128);assertEquals(expected,actual.points());
  }finally{server.stop(0);}
 }
}
