package org.openstreetmap.josm.plugins.dawarich;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DawarichClientTest {
    HttpServer server; AtomicInteger hits; volatile String path=""; volatile String auth="";
    @BeforeEach void start() throws Exception { server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r);t.setDaemon(true);return t;}));hits=new AtomicInteger();server.createContext("/prefix/api/v1/points",x->{hits.incrementAndGet();path=x.getRequestURI().toString();auth=x.getRequestHeaders().getFirst("Authorization");respond(x,200,"application/json","X-Current-Page","1","X-Total-Pages","1","[{\"id\":9007199254740993,\"latitude\":1.25,\"longitude\":3.5,\"timestamp\":1577836800}]");});server.start(); }
    @AfterEach void stop(){server.stop(0);}
    void respond(com.sun.net.httpserver.HttpExchange x,int code,String type,String... headers)throws IOException { for(int i=0;i+1<headers.length;i+=2)x.getResponseHeaders().set(headers[i],headers[i+1]);if(type!=null)x.getResponseHeaders().set("Content-Type",type);byte[] b=headers.length%2==1?headers[headers.length-1].getBytes(StandardCharsets.UTF_8):new byte[0];x.sendResponseHeaders(code,b.length);try(OutputStream o=x.getResponseBody()){o.write(b);} }
    DawarichClient client(){return new DawarichClient(ConnectionSettings.parse("http://127.0.0.1:"+server.getAddress().getPort()+"/prefix/",true));}
    QueryBounds bounds(){return new QueryBounds(1,2,3,4,LocalDate.of(2020,1,1),LocalDate.of(2020,1,2),ZoneOffset.UTC,Instant.parse("2020-02-01T00:00:00Z"));}
    BatchBudget budget(){return new BatchBudget(8,Duration.ofSeconds(3),1024*1024,100*1024);}
    @Test void exactNonemptyQueryAndAlternatingSourceKeys() throws Exception {
        DawarichClient c=client();var first=c.fetchPage("key-A",bounds(),100,200,budget());var second=c.fetchPage("key-B",bounds(),100,200,budget());
        assertEquals(9007199254740993L,first.points().get(0).id());assertEquals(1.25,first.points().get(0).latitude());assertEquals(3.5,first.points().get(0).longitude());assertEquals(1,first.totalPages());assertEquals("Bearer key-B",auth);
        Map<String,String> q=new HashMap<>();for(String pair:path.substring(path.indexOf('?')+1).split("&")){String[] a=pair.split("=",2);q.put(a[0],a.length>1?a[1]:"");}
        assertEquals(Map.ofEntries(Map.entry("page","1"),Map.entry("per_page","10000"),Map.entry("order","asc"),Map.entry("slim","true"),Map.entry("include_anomalies","false"),Map.entry("mask_privacy_zones","false"),Map.entry("min_latitude","1.0"),Map.entry("max_latitude","2.0"),Map.entry("min_longitude","3.0"),Map.entry("max_longitude","4.0"),Map.entry("start_at","100"),Map.entry("end_at","200")),q);
        assertFalse(path.contains("api_key"));assertFalse(q.containsKey("min_lat"));assertTrue(path.startsWith("/prefix/api/v1/points?"));assertEquals(2,hits.get());
    }
    @Test void cancelStalledHeadersIsImmediateAndNoSecondRequest() throws Exception { CountDownLatch entered=new CountDownLatch(1);server.removeContext("/prefix/api/v1/points");server.createContext("/prefix/api/v1/points",x->{hits.incrementAndGet();entered.countDown();try{Thread.sleep(5000);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}try{x.close();}catch(Exception ignored){}});BatchBudget b=budget();ExecutorService e=Executors.newSingleThreadExecutor();try{Future<DawarichException> f=e.submit(()->{try{client().fetchPage("k",bounds(),1,2,b);return null;}catch(DawarichException z){return z;}});assertTrue(entered.await(2,TimeUnit.SECONDS));long start=System.nanoTime();b.cancel();assertEquals(DawarichException.Category.CANCELLED,f.get(1,TimeUnit.SECONDS).category());assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1000);assertEquals(DawarichException.Category.CANCELLED,assertThrows(DawarichException.class,()->client().fetchPage("k",bounds(),1,2,b)).category());assertEquals(1,hits.get());}finally{e.shutdownNow();}}
    @Test void deadlineBoundsStalledHeaders() throws Exception {CountDownLatch entered=new CountDownLatch(1);server.removeContext("/prefix/api/v1/points");server.createContext("/prefix/api/v1/points",x->{entered.countDown();try{Thread.sleep(1500);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}try{x.close();}catch(Exception ignored){}});BatchBudget b=new BatchBudget(2,Duration.ofMillis(100),10000,10000);long start=System.nanoTime();assertEquals(DawarichException.Category.DEADLINE,assertThrows(DawarichException.class,()->client().fetchPage("k",bounds(),1,2,b)).category());assertTrue(entered.await(2,TimeUnit.SECONDS));assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<800);}
    @Test void rejectsInvalidAuthorizationSafely(){for(String k:new String[]{null,"bad\rkey","bad\nkey"}){DawarichException e=assertThrows(DawarichException.class,()->client().fetchPage(k,bounds(),1,2,budget()));assertEquals(DawarichException.Category.AUTH,e.category());}assertEquals(0,hits.get());}
    @Test void enforcesRequestBudget() throws Exception {BatchBudget b=new BatchBudget(1,Duration.ofSeconds(3),100000,10000);client().fetchPage("k",bounds(),1,2,b);assertEquals(DawarichException.Category.LIMIT,assertThrows(DawarichException.class,()->client().fetchPage("k",bounds(),1,2,b)).category());}
    @Test void responseAndAggregateByteLimits() throws Exception {BatchBudget b=new BatchBudget(4,Duration.ofSeconds(3),120,10000);client().fetchPage("k",bounds(),1,2,b);assertEquals(DawarichException.Category.LIMIT,assertThrows(DawarichException.class,()->client().fetchPage("k",bounds(),1,2,b)).category());}
    @Test void statusErrorsAreCategorized() {for(int code:new int[]{403,500}){server.removeContext("/prefix/api/v1/points");server.createContext("/prefix/api/v1/points",x->{hits.incrementAndGet();try{respond(x,code,"application/json","{}");}catch(IOException ignored){}});DawarichException e=assertThrows(DawarichException.class,()->client().fetchPage("k",bounds(),1,2,budget()));assertEquals(code==403?DawarichException.Category.AUTH:DawarichException.Category.SERVER,e.category());}}
    @Test void rejectsContradictoryPagination() {server.removeContext("/prefix/api/v1/points");server.createContext("/prefix/api/v1/points",x->{try{byte[] body="[{\"id\":9007199254740993,\"latitude\":1,\"longitude\":2,\"timestamp\":1}]".getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.getResponseHeaders().set("X-Current-Page","1");x.getResponseHeaders().set("X-Total-Pages","0");x.sendResponseHeaders(200,body.length);x.getResponseBody().write(body);x.close();}catch(IOException ignored){}});assertEquals(DawarichException.Category.PROTOCOL,assertThrows(DawarichException.class,()->client().fetchPage("k",bounds(),1,2,budget())).category());}
}
