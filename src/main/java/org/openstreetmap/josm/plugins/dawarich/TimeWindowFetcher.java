package org.openstreetmap.josm.plugins.dawarich;

import java.util.*;

/** Complete date-range retrieval with bounded page-one cursor traversal. */
public final class TimeWindowFetcher {
 @FunctionalInterface public interface PageFetcher {DawarichClient.Page fetch(long startInclusive,long endInclusive)throws DawarichException;}
 private final PageFetcher fetcher;
 private final DawarichClient client;
 public TimeWindowFetcher(PageFetcher fetcher){this.fetcher=Objects.requireNonNull(fetcher);client=null;}
 public TimeWindowFetcher(DawarichClient client){this.client=Objects.requireNonNull(client);fetcher=null;}
 public ReferenceDataset fetch(String server,String sourceId,String key,QueryBounds bounds,long quota,BatchBudget budget)throws DawarichException{
  Objects.requireNonNull(bounds);Objects.requireNonNull(budget);
  if(server==null||server.isBlank()||sourceId==null||sourceId.isBlank()||quota<0||quota>LoadOptions.MAX_TOTAL_POINTS)throw new DawarichException(DawarichException.Category.PROTOCOL,"Invalid dataset parameters");
  PageFetcher actual=client==null?fetcher:(a,b)->client.fetchPage(key,bounds,a,b,budget);
  return new ReferenceDataset(server,sourceId,bounds,CursorTraversal.fetch(actual,bounds,quota,false,budget));
 }
}
