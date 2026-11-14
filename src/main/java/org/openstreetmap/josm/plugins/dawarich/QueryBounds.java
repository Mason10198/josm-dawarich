package org.openstreetmap.josm.plugins.dawarich;

import java.time.*;
import java.util.Objects;

public final class QueryBounds {
    private final double minLat,maxLat,minLon,maxLon;
    private final Instant startInclusive,endInclusive;
    private final ZoneId zone;
    private final LoadOptions options;
    public QueryBounds(double minLat,double maxLat,double minLon,double maxLon){this(minLat,maxLat,minLon,maxLon,LocalDate.now().minusMonths(12),LocalDate.now(),ZoneId.systemDefault(),Instant.now());}
    public QueryBounds(double minLat,double maxLat,double minLon,double maxLon,LocalDate start,LocalDate end,ZoneId zone,Instant now){this(minLat,maxLat,minLon,maxLon,start,end,zone,now,LoadOptions.defaults());}
    private QueryBounds(double minLat,double maxLat,double minLon,double maxLon,LocalDate start,LocalDate end,ZoneId zone,Instant now,LoadOptions options){
        if(!Double.isFinite(minLat)||!Double.isFinite(maxLat)||!Double.isFinite(minLon)||!Double.isFinite(maxLon)||minLat < -90||maxLat>90||minLat>=maxLat||minLon < -180||maxLon>180||minLon>=maxLon||maxLon-minLon>180||(maxLat-minLat>=179.999&&maxLon-minLon>=359.999))throw new IllegalArgumentException("Invalid geographic bounds");
        this.minLat=minLat;this.maxLat=maxLat;this.minLon=minLon;this.maxLon=maxLon;this.zone=Objects.requireNonNull(zone);Objects.requireNonNull(now);this.options=Objects.requireNonNull(options);
        if(start==null||end==null||end.isBefore(start)||start.isAfter(now.atZone(zone).toLocalDate()))throw new IllegalArgumentException("Invalid date range");
        startInclusive=start.atStartOfDay(zone).toInstant();Instant exactNow=now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        LocalDate capped=end.isAfter(now.atZone(zone).toLocalDate())?now.atZone(zone).toLocalDate():end;
        Instant last=capped.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1);endInclusive=last.isAfter(exactNow)?exactNow:last;
        if(endInclusive.isBefore(startInclusive))throw new IllegalArgumentException("Invalid date range");
    }
    private QueryBounds(QueryBounds prior,Instant first,Instant last,LoadOptions options){
        this.minLat=prior.minLat;this.maxLat=prior.maxLat;this.minLon=prior.minLon;this.maxLon=prior.maxLon;
        this.startInclusive=first;this.endInclusive=last;this.zone=prior.zone;this.options=Objects.requireNonNull(options);
    }
    public static QueryBounds mostRecent(double minLat,double maxLat,double minLon,double maxLon,ZoneId zone,Instant now){
        Objects.requireNonNull(now);if(now.isBefore(Instant.EPOCH))throw new IllegalArgumentException("Invalid current time");
        QueryBounds scope=new QueryBounds(minLat,maxLat,minLon,maxLon,LocalDate.of(1969,12,31),now.atZone(zone).toLocalDate(),zone,now);
        return new QueryBounds(scope,Instant.EPOCH,scope.endInclusive,new LoadOptions(LoadOptions.Mode.MOST_RECENT,10000,50000));
    }
    public QueryBounds withOptions(LoadOptions options){return new QueryBounds(this,startInclusive,endInclusive,options);}
    public LoadOptions options(){return options;}
    public double minLat(){return minLat;} public double maxLat(){return maxLat;} public double minLon(){return minLon;} public double maxLon(){return maxLon;}
    public Instant startInclusive(){return startInclusive;} public Instant endInclusive(){return endInclusive;} public Instant endExclusive(){return endInclusive.plusSeconds(1);} public ZoneId zone(){return zone;}
    public boolean broadAreaNeedsConfirmation(){return haversine(minLat,minLon,maxLat,maxLon)>160934.4;}
    private static double haversine(double a,double b,double c,double d){double x=Math.toRadians(c-a),y=Math.toRadians(d-b);return 6371000*2*Math.asin(Math.sqrt(Math.pow(Math.sin(x/2),2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.pow(Math.sin(y/2),2)));}
}
