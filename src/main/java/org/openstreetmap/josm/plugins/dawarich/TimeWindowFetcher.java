package org.openstreetmap.josm.plugins.dawarich;

import java.util.*;

/** Bounded inclusive interval fetcher; all state belongs to one source invocation. */
public final class TimeWindowFetcher {
    @FunctionalInterface public interface PageFetcher { DawarichClient.Page fetch(long startInclusive,long endInclusive) throws DawarichException; }
    private final PageFetcher fetcher;
    public TimeWindowFetcher(PageFetcher fetcher){this.fetcher=Objects.requireNonNull(fetcher);}
    public TimeWindowFetcher(DawarichClient client){this((a,b)->{throw new IllegalStateException("Use authenticated fetch overload");});this.client=Objects.requireNonNull(client);}
    private DawarichClient client;
    public ReferenceDataset fetch(String serverIdentity,String sourceId,String key,QueryBounds bounds,long quota,BatchBudget budget)throws DawarichException{
        Objects.requireNonNull(serverIdentity); Objects.requireNonNull(sourceId); Objects.requireNonNull(bounds); Objects.requireNonNull(budget);
        if(serverIdentity.isBlank()||sourceId.isBlank()||quota<0||quota>LoadOptions.MAX_TOTAL_POINTS)throw protocol("Invalid dataset parameters");
        PageFetcher actual=client==null?fetcher:(a,b)->client.fetchPage(key,bounds,a,b,budget);
        record Window(long a,long b){} ArrayDeque<Window> stack=new ArrayDeque<>();stack.push(new Window(bounds.startInclusive().getEpochSecond(),bounds.endInclusive().getEpochSecond()));
        ArrayList<ReferencePoint> result=new ArrayList<>();HashSet<Long> ids=new HashSet<>();
        while(!stack.isEmpty()){
            budget.check();Window w=stack.pop();DawarichClient.Page p=actual.fetch(w.a,w.b);budget.check();
            if(p.totalPages()>1){if(w.a==w.b)throw protocol("Too many points within one second; narrow the area");long m=w.a+(w.b-w.a)/2;stack.push(new Window(m+1,w.b));stack.push(new Window(w.a,m));continue;}
            for(ReferencePoint point:p.points()){
                budget.check();if(point.timestamp()<w.a||point.timestamp()>w.b||point.timestamp()<bounds.startInclusive().getEpochSecond()||point.timestamp()>bounds.endInclusive().getEpochSecond()||point.latitude()<bounds.minLat()||point.latitude()>bounds.maxLat()||point.longitude()<bounds.minLon()||point.longitude()>bounds.maxLon())throw protocol("Point outside requested bounds");
                if(!ids.add(point.id()))throw protocol("Duplicate point identifier");if(result.size()>=quota)throw new DawarichException(DawarichException.Category.CAPACITY,"Point limit exceeded; narrow the area or dates, or raise the total point budget");result.add(point);
            }
        }
        return new ReferenceDataset(serverIdentity,sourceId,bounds,result);
    }
    private static DawarichException protocol(String s){return new DawarichException(DawarichException.Category.PROTOCOL,s);}
}
