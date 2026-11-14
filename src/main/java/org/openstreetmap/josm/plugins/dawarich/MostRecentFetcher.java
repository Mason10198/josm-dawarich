package org.openstreetmap.josm.plugins.dawarich;

import java.util.*;

/** Newest-N retrieval with bounded page-one descending cursor traversal. */
public final class MostRecentFetcher {
 private final TimeWindowFetcher.PageFetcher fetcher;
 private final DawarichClient client;
 public MostRecentFetcher(DawarichClient client){this.client=Objects.requireNonNull(client);fetcher=null;}
 public MostRecentFetcher(TimeWindowFetcher.PageFetcher fetcher){this.fetcher=Objects.requireNonNull(fetcher);client=null;}
 public ReferenceDataset fetch(String server,String sourceId,String key,QueryBounds bounds,long requestedPoints,BatchBudget budget)throws DawarichException{
  Objects.requireNonNull(bounds);Objects.requireNonNull(budget);
  if(server==null||server.isBlank()||sourceId==null||sourceId.isBlank()||requestedPoints<1||requestedPoints>LoadOptions.MAX_TOTAL_POINTS||(bounds.options().mode()==LoadOptions.Mode.MOST_RECENT&&requestedPoints!=bounds.options().recentPointsPerSource()))throw new DawarichException(DawarichException.Category.PROTOCOL,"Invalid dataset parameters");
  TimeWindowFetcher.PageFetcher actual=client==null?fetcher:(a,b)->client.fetchPageDescending(key,bounds,a,b,budget);
  return new ReferenceDataset(server,sourceId,bounds,CursorTraversal.fetch(actual,bounds,requestedPoints,true,budget));
 }
}
