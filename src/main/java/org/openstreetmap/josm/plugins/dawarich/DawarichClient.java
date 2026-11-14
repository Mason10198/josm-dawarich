package org.openstreetmap.josm.plugins.dawarich;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.Flow;
import java.util.*;
import java.util.concurrent.*;

/** GET-only, fail-closed Dawarich transport. No credential is retained by the client. */
public final class DawarichClient {
    public record Page(List<ReferencePoint> points, int totalPages) { public Page { points=List.copyOf(points); } }
    private final ConnectionSettings settings;
    private final HttpClient http;
    public DawarichClient(ConnectionSettings settings) {
        this.settings=Objects.requireNonNull(settings);
        HttpClient.Builder b=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER);
        ProxySelector proxy=ProxySelector.getDefault(); if(proxy!=null)b.proxy(proxy);
        this.http=b.build();
    }
    public Page fetchPage(String key, QueryBounds bounds, long startInclusive, long endInclusive, BatchBudget budget) throws DawarichException {
        return fetchPageOrdered(key,bounds,startInclusive,endInclusive,budget,"asc");
    }
    public Page fetchPageDescending(String key, QueryBounds bounds, long startInclusive, long endInclusive, BatchBudget budget) throws DawarichException {
        return fetchPageOrdered(key,bounds,startInclusive,endInclusive,budget,"desc");
    }
    private Page fetchPageOrdered(String key, QueryBounds bounds, long startInclusive, long endInclusive, BatchBudget budget,String order) throws DawarichException {
        if(key==null || key.indexOf('\r')>=0 || key.indexOf('\n')>=0) throw new DawarichException(DawarichException.Category.AUTH,"Invalid source authorization");
        if(bounds==null||budget==null) throw new DawarichException(DawarichException.Category.PROTOCOL,"Invalid request parameters");
        budget.beginRequest();
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            URI uri=endpoint(bounds,startInclusive,endInclusive,order);
            HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+key)
                    .header("User-Agent","josm-dawarich/0.2.0").header("Accept","application/json").header("Accept-Encoding","identity").GET().build();
            pending=http.sendAsync(request, info -> new LimitedSubscriber(budget));
            budget.register(pending);
            long remaining=budget.remainingNanos(); if(remaining<=0)throw DawarichException.deadline();
            HttpResponse<byte[]> response=pending.get(Math.min(remaining,Duration.ofSeconds(20).toNanos()),TimeUnit.NANOSECONDS);
            budget.check();
            int status=response.statusCode();
            if(status>=300&&status<400)throw new DawarichException(DawarichException.Category.REDIRECT,"Redirect refused");
            if(status==401||status==403)throw new DawarichException(DawarichException.Category.AUTH,"Source authorization failed");
            if(status==429)throw new DawarichException(DawarichException.Category.RATE_LIMIT,"Service rate limit",safeRetry(response.headers().firstValue("Retry-After").orElse(null)));
            if(status>=500)throw new DawarichException(DawarichException.Category.SERVER,"Service unavailable");
            if(status!=200)throw new DawarichException(DawarichException.Category.PROTOCOL,"Unexpected service response");
            String ct=response.headers().firstValue("Content-Type").orElse("");
            if(!ct.toLowerCase(Locale.ROOT).matches("application/json(?:\\s*;.*)?"))throw new DawarichException(DawarichException.Category.PROTOCOL,"Invalid response content type");
            if(!response.headers().firstValue("Content-Encoding").orElse("identity").equalsIgnoreCase("identity"))throw new DawarichException(DawarichException.Category.PROTOCOL,"Unexpected content encoding");
            int page=requiredInt(response,"X-Current-Page"), total=requiredInt(response,"X-Total-Pages");
            if(page!=1||total<0)throw new DawarichException(DawarichException.Category.PROTOCOL,"Invalid pagination headers");
            List<ReferencePoint> points;
            try { points=CompactPointParser.parse(new ByteArrayInputStream(response.body()),10000); }
            catch(RuntimeException e){throw new DawarichException(DawarichException.Category.PROTOCOL,"Invalid point data");}
            if((total==0&&!points.isEmpty())||(total==1&&points.size()>10000)||(total>1&&points.size()!=10000))throw new DawarichException(DawarichException.Category.PROTOCOL,"Contradictory pagination response");
            return new Page(points,total);
        } catch (DawarichException e) { if(pending!=null)pending.cancel(true); throw e; }
        catch (CancellationException e) { if(budget.isCancelled())throw DawarichException.cancelled(); budget.check(); throw new DawarichException(DawarichException.Category.CONNECTIVITY,"Service connection failed"); }
        catch (TimeoutException e) { if(pending!=null)pending.cancel(true); budget.check(); throw new DawarichException(DawarichException.Category.DEADLINE,"Request timed out"); }
        catch (InterruptedException e) { if(pending!=null)pending.cancel(true); Thread.currentThread().interrupt(); throw DawarichException.cancelled(); }
        catch (ExecutionException|CompletionException e) { budget.check(); Throwable cause=e.getCause(); while(cause instanceof CompletionException && cause.getCause()!=null)cause=cause.getCause(); if(cause instanceof DawarichException de)throw de; throw new DawarichException(DawarichException.Category.CONNECTIVITY,"Service connection failed"); }
        catch (RuntimeException e) { throw new DawarichException(DawarichException.Category.PROTOCOL,"Invalid request or response"); }
        finally { if(pending!=null)budget.unregister(pending); }
    }
    private URI endpoint(QueryBounds b,long start,long end,String order) throws DawarichException {
        try {
            String q="?page=1&per_page=10000&order="+order+"&slim=true&include_anomalies=false&mask_privacy_zones=false"
                    +"&min_latitude="+b.minLat()+"&max_latitude="+b.maxLat()+"&min_longitude="+b.minLon()+"&max_longitude="+b.maxLon()
                    +"&start_at="+start+"&end_at="+end;
            return URI.create(settings.pointsUri().toASCIIString()+q);
        } catch(RuntimeException e){throw new DawarichException(DawarichException.Category.PROTOCOL,"Invalid request parameters");}
    }
    private static int requiredInt(HttpResponse<?> r,String name)throws DawarichException {
        try{return Integer.parseInt(r.headers().firstValue(name).orElseThrow());}
        catch(RuntimeException e){throw new DawarichException(DawarichException.Category.PROTOCOL,"Missing or invalid pagination headers");}
    }
    private static Integer safeRetry(String s){try{int n=Integer.parseInt(s);return n>=0?Math.min(n,86400):null;}catch(Exception e){return null;}}
    private static final class LimitedSubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>(); private final BatchBudget budget; private final ByteArrayOutputStream out=new ByteArrayOutputStream(); private Flow.Subscription subscription;
        LimitedSubscriber(BatchBudget b){budget=b;}
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> buffers){try{for(ByteBuffer bb:buffers){int n=bb.remaining();if(n>budget.maxResponseBytes()-out.size())throw new DawarichException(DawarichException.Category.LIMIT,"Response byte limit exceeded; narrow the area or dates");budget.addBytes(n);byte[] chunk=new byte[n];bb.get(chunk);out.write(chunk);}subscription.request(1);}catch(Exception t){subscription.cancel();result.completeExceptionally(t);}}
        public void onError(Throwable t){result.completeExceptionally(t);}
        public void onComplete(){result.complete(out.toByteArray());}
    }
}