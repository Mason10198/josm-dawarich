package org.openstreetmap.josm.plugins.dawarich;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.*;
import org.openstreetmap.josm.gui.layer.MainLayerManager;

/** Opt-in, synthetic end-to-end HTTP/coordinator benchmark; emits build/benchmark-results.json. */
public final class PerformanceBenchmark {
    private static final String ENDPOINT = "http://127.0.0.1";
    private static final QueryBounds QUERY = new QueryBounds(-0.01,0.01,-0.01,0.01,LocalDate.of(1970,1,1),LocalDate.of(1970,1,1),ZoneOffset.UTC,Instant.ofEpochSecond(99));
    private static final int[] TOTALS={1_000,10_000,50_000};
    private static final int[] SOURCES={1,2,4};
    private static final long[] LOAD_MS=new long[9];
    private static final List<Map<String,Object>> RESULTS=new ArrayList<>();
    private static long peakHeap;
    private PerformanceBenchmark(){}

    public static void main(String[] args) throws Exception {
        System.setProperty("josm.home",Files.createTempDirectory("dawarich-benchmark-josm").toString());
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicInteger requests=new AtomicInteger(); AtomicInteger pointsPerSource=new AtomicInteger();
        server.createContext("/api/v1/points", exchange -> serve(exchange,requests,pointsPerSource)); server.start();
        try {
            String endpoint="http://127.0.0.1:"+server.getAddress().getPort(); int trial=0;
            for(int total:TOTALS) for(int sourceCount:SOURCES) {
                trial++; pointsPerSource.set(total/sourceCount); long before=usedHeap(); System.gc();Thread.sleep(100);before=usedHeap();
                SourceRegistry registry=new SourceRegistry();registry.setEndpoint(endpoint,true);
                List<String> ids=new ArrayList<>(); for(int s=0;s<sourceCount;s++){SourceSettings row=registry.add("synthetic-"+s);ids.add(row.id);registry.setKey(row.id,"benchmark-key-"+s);}
                MainLayerManager manager=new MainLayerManager();ExecutorService worker=Executors.newSingleThreadExecutor();
                long requestBefore=requests.get(),started=System.nanoTime();long paintNanos=0;int rendered=0;
                try(LoadReferenceTask task=new LoadReferenceTask(registry,manager,worker)) {
                    LoadReferenceTask.BatchResult result=task.start(QUERY).get(60,TimeUnit.SECONDS);
                    long loadNanos=System.nanoTime()-started;
                    if(result.status()!=LoadReferenceTask.Status.COMPLETE||result.totalPoints()!=total)throw new AssertionError("load mismatch total="+total+" sources="+sourceCount+" result="+result);
                    for(int i=0;i<sourceCount;i++) {
                        List<ReferencePoint> actual=task.layersSnapshot().get(ids.get(i)).datasetPoints().points();
                        int firstId=i*50_000+1, expectedCount=total/sourceCount;
                        if(actual.size()!=expectedCount)throw new AssertionError("source count mismatch");
                        Set<Long> seen=new HashSet<>();
                        for(ReferencePoint point:actual) {
                            long expectedId=point.id();
                            if(expectedId<firstId || expectedId>=firstId+expectedCount || !seen.add(expectedId)
                                || point.latitude()!=0.001 || point.longitude()!=0.001 || point.timestamp()!=(expectedId-1)%100)
                                throw new AssertionError("point oracle mismatch source="+i+" point="+point);
                        }
                        if(seen.size()!=expectedCount)throw new AssertionError("missing source points");
                    }
                    System.gc();Thread.sleep(100);long after=usedHeap();peakHeap=Math.max(peakHeap,peakHeap());
                    BufferedImage image=new BufferedImage(1024,768,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();
                    try { for(int frame=0;frame<20;frame++){long p=System.nanoTime();for(DawarichLayer layer:task.layersSnapshot().values())DawarichLayer.paintPoints(graphics, point -> new Point((int)((point.longitude()+0.01)*51_200),(int)((0.01-point.latitude())*38_400)), new Rectangle(0,0,1024,768), layer.datasetPoints().points(), layer.getColor());paintNanos+=System.nanoTime()-p;rendered+=total;} }
                    finally{graphics.dispose();}
                    Map<String,Object> row=new LinkedHashMap<>();row.put("points",total);row.put("sources",sourceCount);row.put("load_ms",millis(loadNanos));row.put("used_heap_delta_bytes",Math.max(0,after-before));row.put("painter_20_frames_ms",millis(paintNanos));row.put("painted_point_visits",rendered);row.put("http_requests",requests.get()-requestBefore);row.put("per_source_points",total/sourceCount);RESULTS.add(row);LOAD_MS[trial-1]=millis(loadNanos);
                    System.out.println(row);
                } finally {worker.shutdownNow();worker.awaitTermination(5,TimeUnit.SECONDS);}
            }
            long cancelHeaderMs=measureCancellation(false);long cancelBodyMs=measureCancellation(true);long jar=Files.size(Path.of("dist/dawarich.jar"));
            String json="{\n  \"method\": \"single measured run per configuration; production JDK HttpClient -> DawarichClient -> TimeWindowFetcher -> LoadReferenceTask -> MainLayerManager; 20 production-painter frames\",\n  \"render_caveat\": \"synthetic painter seam; not full JOSM MapView/viewport frame-rate\",\n  \"peak_heap_pool_sum_bytes\": "+peakHeap+",\n  \"cancel_header_ms\": "+cancelHeaderMs+",\n  \"cancel_body_ms\": "+cancelBodyMs+",\n  \"plugin_jar_bytes\": "+jar+",\n  \"load_ms\": "+array(LOAD_MS)+",\n  \"results\": [\n"+String.join(",\n",RESULTS.stream().map(PerformanceBenchmark::jsonObject).toList())+"\n  ]\n}\n";
            Path out=Path.of("build/benchmark-results.json");Files.createDirectories(out.getParent());Files.writeString(out,json,StandardCharsets.UTF_8);System.out.println("Wrote "+out.toAbsolutePath());
        } finally {server.stop(0);}
    }
    private static void serve(HttpExchange x,AtomicInteger requests,AtomicInteger pointsPerSource)throws IOException {
        requests.incrementAndGet();String auth=x.getRequestHeaders().getFirst("Authorization");if(auth==null||!auth.startsWith("Bearer benchmark-key-")){x.sendResponseHeaders(401,-1);x.close();return;}
        Map<String,String> q=new HashMap<>();for(String pair:x.getRequestURI().getRawQuery().split("&")){String[] p=pair.split("=",2);q.put(p[0],p.length>1?p[1]:"");}
        long a=Long.parseLong(q.get("start_at")),b=Long.parseLong(q.get("end_at"));int source=Integer.parseInt(auth.substring("Bearer benchmark-key-".length()));int count=0;
        for(int id=source*50_000+1;id<=source*50_000+pointsPerSource.get();id++){long timestamp=(id-1)%100;if(timestamp>=a&&timestamp<=b)count++;}
        StringBuilder body=new StringBuilder(Math.min(1_500_000,count*45)).append('[');boolean first=true;
        int emitted=0;for(int id=source*50_000+1;id<=source*50_000+pointsPerSource.get()&&emitted<10_000;id++){long t=(id-1)%100;if(t<a||t>b)continue;if(!first)body.append(',');first=false;body.append("{\"id\":").append(id).append(",\"latitude\":0.001,\"longitude\":0.001,\"timestamp\":").append(t).append('}');emitted++;}
        body.append(']');byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.getResponseHeaders().set("X-Current-Page","1");x.getResponseHeaders().set("X-Total-Pages",Integer.toString((count+9_999)/10_000));x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);x.close();
    }
    private static long measureCancellation(boolean bodyStall)throws Exception {
        HttpServer stalled=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);CountDownLatch reached=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService handlers=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"benchmark-stalled-http");t.setDaemon(true);return t;});
        stalled.setExecutor(handlers);stalled.createContext("/api/v1/points",x->{try{if(bodyStall){x.getResponseHeaders().set("Content-Type","application/json");x.getResponseHeaders().set("X-Current-Page","1");x.getResponseHeaders().set("X-Total-Pages","1");x.sendResponseHeaders(200,0);x.getResponseBody().write('[');x.getResponseBody().flush();}reached.countDown();release.await(3,TimeUnit.SECONDS);}catch(Exception ignored){}finally{try{x.close();}catch(Exception ignored){}}});stalled.start();
        SourceRegistry r=new SourceRegistry();r.setEndpoint("http://127.0.0.1:"+stalled.getAddress().getPort(),true);SourceSettings s=r.add("cancel");r.setKey(s.id,"cancel-key");ExecutorService e=Executors.newSingleThreadExecutor();try(LoadReferenceTask task=new LoadReferenceTask(r,new MainLayerManager(),e)){var f=task.start(QUERY);if(!reached.await(2,TimeUnit.SECONDS))throw new AssertionError("cancel fixture not reached");long cancelStart=System.nanoTime();task.cancel();var result=f.get(1,TimeUnit.SECONDS);long elapsed=System.nanoTime()-cancelStart;if(result.status()!=LoadReferenceTask.Status.CANCELLED)throw new AssertionError("expected cancellation, got "+result.status());return millis(elapsed);}finally{release.countDown();stalled.stop(0);handlers.shutdownNow();e.shutdownNow();e.awaitTermination(2,TimeUnit.SECONDS);}
    }
    private static long peakHeap(){long total=0;for(var p:ManagementFactory.getMemoryPoolMXBeans())if(p.getType()==java.lang.management.MemoryType.HEAP&&p.getPeakUsage()!=null)total+=p.getPeakUsage().getUsed();return total;}
    private static long usedHeap(){Runtime r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
    private static long millis(long nanos){return TimeUnit.NANOSECONDS.toMillis(nanos);}
    private static String array(long[] a){return Arrays.toString(a);}
    private static String jsonObject(Map<String,Object> m){StringJoiner j=new StringJoiner(", ","    {","}");m.forEach((k,v)->j.add("\""+k+"\": "+v));return j.toString();}
}
