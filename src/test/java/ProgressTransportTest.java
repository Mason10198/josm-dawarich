import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.dawarich.*;

class ProgressTransportTest {
    @Test void budgetSnapshotCountersAreMonotonicAndSelectedCountBounded() throws Exception {
        BatchBudget budget=new BatchBudget();budget.beginRequest();budget.addBytes(17);budget.setSelectedPointCount(3);
        LoadProgress before=new LoadProgress("source",1,1,budget.requestCount(),budget.downloadedBytes(),budget.selectedPointCount(),0,budget.elapsedMillis());
        assertEquals(1,before.requests());assertEquals(17,before.downloadedBytes());assertEquals(3,before.selectedPoints());
        budget.setSelectedPointCount(0);assertEquals(0,budget.selectedPointCount());assertTrue(budget.elapsedMillis()>=before.elapsedMillis());
        assertThrows(IllegalArgumentException.class,()->budget.setSelectedPointCount(-1));
        assertThrows(IllegalArgumentException.class,()->budget.setSelectedPointCount((long)LoadOptions.MAX_TOTAL_POINTS+1));
    }
    @Test void timeFetcherCountsAcceptedPointsNotDiscardedProbePage() throws Exception {
        QueryBounds q=new QueryBounds(-1,1,-1,1,LocalDate.of(2024,1,1),LocalDate.of(2024,1,2),ZoneId.of("UTC"),Instant.now());
        BatchBudget budget=new BatchBudget();final int[] calls={0};
        TimeWindowFetcher fetcher=new TimeWindowFetcher((a,b)->{calls[0]++;if(calls[0]==1)return new DawarichClient.Page(List.of(new ReferencePoint(1,0,0,a),new ReferencePoint(2,0,0,a+1)),2);if(calls[0]==2)return new DawarichClient.Page(List.of(new ReferencePoint(2,0,0,a)),1);return new DawarichClient.Page(List.of(),0);});
        fetcher.fetch("https://example.test","source","dummy",q,10,budget);
        assertEquals(2,budget.selectedPointCount());assertEquals(0,budget.requestCount());
    }
}
