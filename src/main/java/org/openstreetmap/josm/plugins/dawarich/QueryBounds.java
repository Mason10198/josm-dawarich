package org.openstreetmap.josm.plugins.dawarich;

import java.time.*;
import java.util.Objects;

public final class QueryBounds {
    private final double minLat, maxLat, minLon, maxLon;
    private final Instant startInclusive, endInclusive;
    private final ZoneId zone;

    /** Bounds-only UI validation uses an explicit recent-year interval, never null instants. */
    public QueryBounds(double minLat, double maxLat, double minLon, double maxLon) {
        this(minLat,maxLat,minLon,maxLon,LocalDate.now().minusMonths(12),LocalDate.now(),ZoneId.systemDefault(),Instant.now());
    }
    public QueryBounds(double minLat, double maxLat, double minLon, double maxLon,
                       LocalDate start, LocalDate end, ZoneId zone, Instant now) {
        if (!Double.isFinite(minLat) || !Double.isFinite(maxLat) || !Double.isFinite(minLon) || !Double.isFinite(maxLon)
                || minLat < -90 || maxLat > 90 || minLat >= maxLat || minLon < -180 || maxLon > 180
                || minLon >= maxLon || maxLon - minLon > 180) throw new IllegalArgumentException("Invalid geographic bounds");
        this.minLat=minLat; this.maxLat=maxLat; this.minLon=minLon; this.maxLon=maxLon;
        this.zone=Objects.requireNonNull(zone); Objects.requireNonNull(now);
        if (start == null || end == null || end.isBefore(start) || start.isAfter(now.atZone(zone).toLocalDate()))
            throw new IllegalArgumentException("Invalid date range");
        startInclusive=start.atStartOfDay(zone).toInstant();
        LocalDate capped=end.isAfter(now.atZone(zone).toLocalDate()) ? now.atZone(zone).toLocalDate() : end;
        Instant exactNow=now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant lastSecond=capped.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1);
        endInclusive=lastSecond.isAfter(exactNow) ? exactNow : lastSecond;
        if (endInclusive.isBefore(startInclusive)) throw new IllegalArgumentException("Invalid date range");
    }
    public double minLat(){return minLat;} public double maxLat(){return maxLat;}
    public double minLon(){return minLon;} public double maxLon(){return maxLon;}
    public Instant startInclusive(){return startInclusive;} public Instant endInclusive(){return endInclusive;}
    public Instant endExclusive(){return endInclusive.plusSeconds(1);} public ZoneId zone(){return zone;}
    public boolean broadAreaNeedsConfirmation(){return haversine(minLat,minLon,maxLat,maxLon)>160934.4;}
    private static double haversine(double a,double b,double c,double d){double x=Math.toRadians(c-a),y=Math.toRadians(d-b);return 6371000*2*Math.asin(Math.sqrt(Math.pow(Math.sin(x/2),2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.pow(Math.sin(y/2),2)));}
}
