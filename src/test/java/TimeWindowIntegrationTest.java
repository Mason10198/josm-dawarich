package org.openstreetmap.josm.plugins.dawarich;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class TimeWindowIntegrationTest {
    private static QueryBounds bounds(){return new QueryBounds(-1,1,-1,1,LocalDate.of(1970,1,1),LocalDate.of(1970,1,1),ZoneOffset.UTC,Instant.ofEpochSecond(99));}
    private static BatchBudget budget(){return new BatchBudget(128,Duration.ofSeconds(60),64*1024*1024,8*1024*1024);}
    private static final class Fixture implements AutoCloseable {
        final HttpServer server; final List<long[]> windows=Collections.synchronizedList(new ArrayList<>());
        Fixture(List<ReferencePoint> points) throws IOException {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/api/v1/points",exchange->{
                try {
                    Map<String,String> q=new HashMap<>();for(String part:exchange.getRequestURI().getRawQuery().split("&")){String[] kv=part.split("=",2);q.put(kv[0],kv.length>1?kv[1]:"");}
                    if(!"1".equals(q.get("page"))||!"10000".equals(q.get("per_page"))||!"asc".equals(q.get("order"))||!"true".equals(q.get("slim"))||!"false".equals(q.get("include_anomalies"))||!"false".equals(q.get("mask_privacy_zones"))||!"-1.0".equals(q.get("min_latitude"))||!"1.0".equals(q.get("max_latitude"))||!"-1.0".equals(q.get("min_longitude"))||!"1.0".equals(q.get("max_longitude"))){exchange.sendResponseHeaders(400,-1);return;}
                    long a=Long.parseLong(q.get("start_at")),b=Long.parseLong(q.get("end_at"));if(a<0||b>99||a>b){exchange.sendResponseHeaders(400,-1);return;}windows.add(new long[]{a,b});
                    List<ReferencePoint> selected=new ArrayList<>();for(ReferencePoint p:points)if(p.timestamp()>=a&&p.timestamp()<=b)selected.add(p);
                    int total=(selected.size()+9999)/10000;StringBuilder json=new StringBuilder("[");for(int i=0;i<Math.min(10000,selected.size());i++){ReferencePoint p=selected.get(i);if(i>0)json.append(',');json.append("{\"id\":").append(p.id()).append(",\"latitude\":0,\"longitude\":0,\"timestamp\":").append(p.timestamp()).append('}');}json.append(']');
                    byte[] body=json.toString().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.getResponseHeaders().set("X-Current-Page","1");exchange.getResponseHeaders().set("X-Total-Pages",Integer.toString(total));exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
                }catch(Exception e){exchange.sendResponseHeaders(500,-1);}finally{exchange.close();}
            });server.start();
        }
        DawarichClient client(){return new DawarichClient(ConnectionSettings.parse("http://127.0.0.1:"+server.getAddress().getPort(),true));}
        public void close(){server.stop(0);}
    }
    private static List<ReferencePoint> data(int n){List<ReferencePoint> p=new ArrayList<>();for(int i=1;i<=n;i++)p.add(new ReferencePoint(i,0,0,i<=n/2?10:11));return p;}
    @Test public void realClientAndFetcherRepeatedlySplitAndKeepAllRows() throws Exception {
        List<ReferencePoint> expected=data(12000);try(Fixture f=new Fixture(expected)){
            ReferenceDataset actual=new TimeWindowFetcher(f.client()).fetch("local","source","test-key",bounds(),12000,budget());
            Map<Long,ReferencePoint> byId=new HashMap<>();for(ReferencePoint p:expected)byId.put(p.id(),p);
            assertEquals(expected.size(),actual.points().size());for(ReferencePoint p:actual.points())assertEquals(byId.get(p.id()),p);
            assertTrue(f.windows.size()>3);for(long[] w:f.windows)assertTrue(w[0]<=w[1]);
        }
    }
    @Test public void realClientReportsUndividableTimestampDensityWithoutPartialDataset() throws Exception {
        List<ReferencePoint> expected=new ArrayList<>();for(int i=1;i<=10001;i++)expected.add(new ReferencePoint(i,0,0,10));
        try(Fixture f=new Fixture(expected)){
            try{new TimeWindowFetcher(f.client()).fetch("local","source","test-key",bounds(),12000,budget());fail();}
            catch(DawarichException e){assertEquals(DawarichException.Category.PROTOCOL,e.category());assertTrue(e.getMessage().contains("Too many points within one second"));}
            assertTrue(f.windows.size()<=128);
        }
    }
}
