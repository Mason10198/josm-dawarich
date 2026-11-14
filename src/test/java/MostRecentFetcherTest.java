package org.openstreetmap.josm.plugins.dawarich;

import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class MostRecentFetcherTest {
    private final QueryBounds bounds = new QueryBounds(-10,10,-10,10,LocalDate.of(1970,1,1),LocalDate.of(1970,1,1),ZoneOffset.UTC,Instant.ofEpochSecond(99));
    private static ReferencePoint p(long id,long time){return new ReferencePoint(id,0,0,time);}
    private static DawarichClient.Page page(List<ReferencePoint> points,int total){return new DawarichClient.Page(points,total);}
    private BatchBudget budget(){return new BatchBudget(128,Duration.ofSeconds(60),64*1024*1024,8*1024*1024);}
    private static List<ReferencePoint> descending(List<ReferencePoint> source,long a,long b){return source.stream().filter(x->x.timestamp()>=a&&x.timestamp()<=b).sorted(Comparator.comparingLong(ReferencePoint::timestamp).reversed().thenComparing(Comparator.comparingLong(ReferencePoint::id).reversed())).toList();}
    @Test public void saturatedPagesReturnExactNewestValuesAndTiedTimestamps() throws Exception {
        List<ReferencePoint> oracle=new ArrayList<>(); for(int i=1;i<=23000;i++) oracle.add(new ReferencePoint(i,i%5,i%7,i<=10000?50:i<=18000?80:90));
        List<ReferencePoint> expected=oracle.stream().sorted(Comparator.comparingLong(ReferencePoint::timestamp).reversed().thenComparing(Comparator.comparingLong(ReferencePoint::id).reversed())).limit(15000).toList();
        TimeWindowFetcher.PageFetcher f=(a,b)->{List<ReferencePoint> sub=descending(oracle,a,b);return page(sub.subList(0,Math.min(10000,sub.size())),(sub.size()+9999)/10000);};
        ReferenceDataset got=new MostRecentFetcher(f).fetch("server","source","unused",bounds,15000,budget()); assertEquals(expected,got.points()); assertEquals("server",got.serverIdentity());assertEquals("source",got.sourceId());assertSame(bounds,got.query());
    }
    @Test public void fewerAvailableReturnsAllAndSinglePageTruncates() throws Exception {
        List<ReferencePoint> all=List.of(p(1,1),p(2,2),p(3,3)); assertEquals(descending(all,0,99),new MostRecentFetcher((a,b)->page(descending(all,a,b),1)).fetch("s","x","k",bounds,5,budget()).points());
        List<ReferencePoint> ten=new ArrayList<>();for(int i=0;i<10000;i++)ten.add(p(i+1,99-(i%99))); ten.sort(Comparator.comparingLong(ReferencePoint::timestamp).reversed().thenComparing(Comparator.comparingLong(ReferencePoint::id).reversed())); assertEquals(5,new MostRecentFetcher((a,b)->page(ten,2)).fetch("s","x","k",bounds,5,budget()).points().size());
    }
    @Test public void outsideViewportAscendingAndDuplicateProbeAreRejected() {
        List<TimeWindowFetcher.PageFetcher> bad=List.of((a,b)->page(List.of(new ReferencePoint(1,20,0,a)),1),(a,b)->page(List.of(p(1,a),p(2,b)),1),(a,b)->page(List.of(p(1,a),p(1,a)),2));
        for(var f:bad)try{new MostRecentFetcher(f).fetch("s","x","k",bounds,1,budget());fail("expected protocol failure");}catch(DawarichException e){assertEquals(DawarichException.Category.PROTOCOL,e.category());}
    }
    @Test public void duplicateAcrossAcceptedIntervalsAndDenseSecondRejected() throws Exception {
        TimeWindowFetcher.PageFetcher duplicate=(a,b)-> a==0&&b==99?page(List.of(),2):a>=50?page(List.of(p(7,60)),1):page(List.of(p(7,40)),1);
        try{new MostRecentFetcher(duplicate).fetch("s","x","k",bounds,3,budget());fail();}catch(DawarichException e){assertEquals(DawarichException.Category.PROTOCOL,e.category());}
        try{new MostRecentFetcher((a,b)->page(List.of(p(1,a)),2)).fetch("s","x","k",bounds,2,budget());fail();}catch(DawarichException e){assertTrue(e.getMessage().contains("Too many points within one second"));}
        assertEquals(1,new MostRecentFetcher((a,b)->page(List.of(p(1,a)),1)).fetch("s","x","k",bounds,1,budget()).points().size());
    }
    @Test public void rejectsInvalidLimitsAndHonorsCancellation() {
        for(long n:new long[]{0,LoadOptions.MAX_TOTAL_POINTS+1L})try{new MostRecentFetcher((a,b)->page(List.of(),0)).fetch("s","x","k",bounds,n,budget());fail();}catch(DawarichException e){assertEquals(DawarichException.Category.PROTOCOL,e.category());}
        BatchBudget cancelled=budget();cancelled.cancel();try{new MostRecentFetcher((a,b)->{fail("must not fetch");return page(List.of(),0);}).fetch("s","x","k",bounds,1,cancelled);fail();}catch(DawarichException e){assertEquals(DawarichException.Category.CANCELLED,e.category());}
    }
}
