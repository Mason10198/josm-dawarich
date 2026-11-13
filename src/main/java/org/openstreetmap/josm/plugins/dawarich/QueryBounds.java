package org.openstreetmap.josm.plugins.dawarich;
import java.time.*;
public final class QueryBounds {
 private final double minLat,maxLat,minLon,maxLon; private final Instant startInclusive,endInclusive; private final ZoneId zone;
 public QueryBounds(double minLat,double maxLat,double minLon,double maxLon){this(minLat,maxLat,minLon,maxLon,null,null,ZoneId.systemDefault(),Instant.now());}
 public QueryBounds(double minLat,double maxLat,double minLon,double maxLon,LocalDate start,LocalDate end,ZoneId zone,Instant now){
  if(!Double.isFinite(minLat)||!Double.isFinite(maxLat)||!Double.isFinite(minLon)||!Double.isFinite(maxLon)||minLat < -90||maxLat>90||minLat>=maxLat||minLon < -180||maxLon>180||minLon>=maxLon||maxLon-minLon>180) throw new IllegalArgumentException("Invalid geographic bounds");
  this.minLat=minLat;this.maxLat=maxLat;this.minLon=minLon;this.maxLon=maxLon;this.zone=zone;
  if(start==null||end==null){startInclusive=null;endInclusive=null;} else {if(end.isBefore(start)||start.isAfter(now.atZone(zone).toLocalDate()))throw new IllegalArgumentException("Invalid date range"); LocalDate cap=end.isAfter(now.atZone(zone).toLocalDate())?now.atZone(zone).toLocalDate():end; startInclusive=start.atStartOfDay(zone).toInstant();endInclusive=cap.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1);}
 }
 public Instant startInclusive(){return startInclusive;} public Instant endInclusive(){return endInclusive;} public Instant endExclusive(){return endInclusive == null ? null : endInclusive.plusNanos(1);} public ZoneId zone(){return zone;} public boolean broadAreaNeedsConfirmation(){return haversine(minLat,minLon,maxLat,maxLon)>160934.4;}
 private static double haversine(double a,double b,double c,double d){double x=Math.toRadians(c-a),y=Math.toRadians(d-b);return 6371000*2*Math.asin(Math.sqrt(Math.pow(Math.sin(x/2),2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.pow(Math.sin(y/2),2)));}
}
