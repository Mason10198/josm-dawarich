package org.openstreetmap.josm.plugins.dawarich;

import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class TimeWindowFetcherTest {
    private final QueryBounds bounds = new QueryBounds(-10,10,-10,10,LocalDate.of(1970,1,1),LocalDate.of(1970,1,1),ZoneOffset.UTC,Instant.ofEpochSecond(99));
    private static class Fake implements TimeWindowFetcher.PageFetcher {
        final List<long[]> calls=new ArrayList<>(); final java.util.function.BiFunction<Long,Long,DawarichClient.Page> fn;
        Fake(java.util.function.BiFunction<Long,Long,DawarichClient.Page> fn){this.fn=fn;}
        public DawarichClient.Page fetch(long a,long b)throws DawarichException{calls.add(new long[]{a,b});return fn.apply(a,b);}
    }
    private BatchBudget budget(){return new BatchBudget(128,Duration.ofSeconds(60),64*1024*1024,8*1024*1024);}
    private static DawarichClient.Page page(List<ReferencePoint> p,int n){return new DawarichClient.Page(p,n);}
    private static ReferencePoint point(long id,long t){return new ReferencePoint(id,0,0,t);}
    @Test public void splitsOddInclusiveWindowsWithoutGaps() throws Exception {
        Fake f=new Fake((a,b)->{List<ReferencePoint> p=new ArrayList<>();for(long t=a;t<=b;t++)p.add(point(t+1,t));return page(p,p.size()>4?2:1);});
        var d=new TimeWindowFetcher(f).fetch("https://s","src","unused",bounds,500,budget());
        assertEquals(100,d.points().size()); assertEquals(100,d.points().stream().map(ReferencePoint::timestamp).distinct().count());
        assertEquals(99,d.points().get(99).timestamp());
    }
    @Test public void tiedTimestampOracleDiscardsFullParentAndRetainsEveryPoint() throws Exception{
        List<ReferencePoint> all=new ArrayList<>();for(int i=1;i<=12000;i++)all.add(point(i,i<=6000?10:11));
        Fake f=new Fake((a,b)-> {if(a==0&&b==99)return page(all.subList(0,10000),2);List<ReferencePoint> sub=all.stream().filter(p->p.timestamp()>=a&&p.timestamp()<=b).toList();return page(sub,1);});
        var d=new TimeWindowFetcher(f).fetch("https://s","src","unused",bounds,12000,budget());
        assertEquals(12000,d.points().size());assertEquals(12000,d.points().stream().map(ReferencePoint::id).distinct().count());assertEquals(3,f.calls.size());
    }
    @Test public void emptyAndExactPageAccepted() throws Exception{
        Fake empty=new Fake((a,b)->page(List.of(),0));assertTrue(new TimeWindowFetcher(empty).fetch("s","x","k",bounds,0,budget()).points().isEmpty());
        List<ReferencePoint> ten= new ArrayList<>();for(int i=0;i<10000;i++)ten.add(point(i+1,0));
        Fake full=new Fake((a,b)->page(ten,1));assertEquals(10000,new TimeWindowFetcher(full).fetch("s","x","k",bounds,10000,budget()).points().size());
    }
    @Test public void singleSecondOverflowIsDensityFailure(){Fake f=new Fake((a,b)->page(List.of(point(1,a),point(2,a)),2));try{new TimeWindowFetcher(f).fetch("s","x","k",bounds,3,budget());fail();}catch(DawarichException e){assertEquals(DawarichException.Category.PROTOCOL,e.category());assertTrue(e.getMessage().contains("Too many points within one second"));}}
    @Test public void invalidPointsAndDuplicateIdsFail(){
        Fake dup=new Fake((a,b)->page(List.of(point(1,a),point(1,b)),1));try{new TimeWindowFetcher(dup).fetch("s","x","k",bounds,10,budget());fail();}catch(DawarichException expected){}
        Fake outside=new Fake((a,b)->page(List.of(point(2,100)),1));try{new TimeWindowFetcher(outside).fetch("s","x","k",bounds,10,budget());fail();}catch(DawarichException expected){}
    }
    @Test public void quotaRejectsWholeResultAndNegative(){Fake f=new Fake((a,b)->page(List.of(point(1,a),point(2,a)),1));for(long q:new long[]{-1,1})try{new TimeWindowFetcher(f).fetch("s","x","k",bounds,q,budget());fail();}catch(DawarichException expected){}}
    @Test public void sameIdsAreIndependentAcrossSourcesAndDataImmutable() throws Exception{Fake f=new Fake((a,b)->page(List.of(point(1,a)),1));TimeWindowFetcher w=new TimeWindowFetcher(f);var a=w.fetch("s","a","k",bounds,1,budget());var b=w.fetch("s","b","k",bounds,1,budget());assertEquals(1,a.points().size());assertEquals(1,b.points().size());try{a.points().clear();fail();}catch(UnsupportedOperationException expected){}assertFalse(a.toString().contains("https://s"));}
    @Test public void cancellationStopsNextRequest(){Fake f=new Fake((a,b)->{throw new AssertionError("unexpected");});BatchBudget budget=budget();budget.cancel();try{new TimeWindowFetcher(f).fetch("s","x","k",bounds,5,budget);fail();}catch(DawarichException e){assertEquals(DawarichException.Category.CANCELLED,e.category());}assertTrue(f.calls.isEmpty());}
}
