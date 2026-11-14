package org.openstreetmap.josm.plugins.dawarich;

import java.util.*;

/** Page-one cursor traversal; only a possibly incomplete boundary second is re-read. */
final class CursorTraversal {
 private CursorTraversal(){}
 static List<ReferencePoint> fetch(TimeWindowFetcher.PageFetcher fetcher,QueryBounds bounds,long limit,boolean recent,BatchBudget budget)throws DawarichException{
  long lower=bounds.startInclusive().getEpochSecond(),upper=bounds.endInclusive().getEpochSecond();
  List<ReferencePoint> out=new ArrayList<>();Set<Long> ids=new HashSet<>();
  while(lower<=upper){
   budget.check();DawarichClient.Page page=fetcher.fetch(lower,upper);budget.check();validate(page,lower,upper,bounds,recent,ids,budget);
   List<ReferencePoint> rows=page.points();
   if(page.totalPages()<=1||(recent&&limit-out.size()<=rows.size())){
    append(rows,out,ids,limit,recent,budget);return out;
   }
   if(lower==upper)throw density();
   long edge=rows.get(rows.size()-1).timestamp();
   Map<Long,ReferencePoint> overlap=new HashMap<>();List<ReferencePoint> interior=new ArrayList<>();
   for(ReferencePoint point:rows){if(point.timestamp()==edge)overlap.put(point.id(),point);else interior.add(point);}
   append(interior,out,ids,limit,recent,budget);
   budget.check();DawarichClient.Page boundary=fetcher.fetch(edge,edge);budget.check();validate(boundary,edge,edge,bounds,recent,ids,budget);
   Map<Long,ReferencePoint> returned=new HashMap<>();for(ReferencePoint point:boundary.points()){
    returned.put(point.id(),point);ReferencePoint old=overlap.get(point.id());if(old!=null&&!old.equals(point))throw protocol("Conflicting duplicate point identifier");
   }
   if(boundary.totalPages()<=1){
    for(var entry:overlap.entrySet())if(!entry.getValue().equals(returned.get(entry.getKey())))throw protocol("Boundary observations changed during download; retry the load");
   }else if(!recent||limit-out.size()>boundary.points().size())throw density();
   append(boundary.points(),out,ids,limit,recent,budget);
   if(recent&&out.size()==limit)return out;
   if(recent){if(edge==lower)return out;upper=edge-1;}else{if(edge==upper)return out;lower=edge+1;}
  }
  return out;
 }
 private static void append(List<ReferencePoint> rows,List<ReferencePoint> out,Set<Long> ids,long limit,boolean recent,BatchBudget budget)throws DawarichException{
  for(ReferencePoint point:rows){budget.check();if(out.size()>=limit){if(recent)break;throw new DawarichException(DawarichException.Category.CAPACITY,"Point limit exceeded; narrow the area or dates, or raise the total point budget");}if(!ids.add(point.id()))throw protocol("Duplicate point identifier");out.add(point);}
  budget.setSelectedPointCount(out.size());
 }
 private static void validate(DawarichClient.Page page,long start,long end,QueryBounds bounds,boolean descending,Set<Long> accepted,BatchBudget budget)throws DawarichException{
  List<ReferencePoint> rows=page.points();if(page.totalPages()<0||rows.size()>10000||(page.totalPages()==0&&!rows.isEmpty())||(page.totalPages()>1&&rows.isEmpty()))throw protocol("Contradictory pagination response");
  long prior=descending?Long.MAX_VALUE:Long.MIN_VALUE;Set<Long> seen=new HashSet<>();
  for(ReferencePoint point:rows){
   budget.check();if(descending?point.timestamp()>prior:point.timestamp()<prior)throw protocol("Unexpected point order");prior=point.timestamp();
   if(!seen.add(point.id())||accepted.contains(point.id()))throw protocol("Duplicate point identifier");
   if(point.timestamp()<start||point.timestamp()>end||point.latitude()<bounds.minLat()||point.latitude()>bounds.maxLat()||point.longitude()<bounds.minLon()||point.longitude()>bounds.maxLon())throw protocol("Point outside requested bounds");
  }
 }
 private static DawarichException density(){return protocol("Too many points within one second; narrow the area");}
 private static DawarichException protocol(String message){return new DawarichException(DawarichException.Category.PROTOCOL,message);}
}
