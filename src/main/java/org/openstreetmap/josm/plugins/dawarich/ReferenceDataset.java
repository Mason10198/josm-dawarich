package org.openstreetmap.josm.plugins.dawarich;

import java.util.*;

/** Immutable values fetched for one server and source. */
public final class ReferenceDataset {
    public static final int MAX_POINTS=50_000;
    private final String serverIdentity,sourceId;
    private final QueryBounds query;
    private final List<ReferencePoint> points;
    public ReferenceDataset(String serverIdentity,String sourceId,QueryBounds query,List<ReferencePoint> points){
        if(serverIdentity==null||serverIdentity.isBlank()||sourceId==null||sourceId.isBlank())throw new IllegalArgumentException("Invalid dataset identity");
        Objects.requireNonNull(query);Objects.requireNonNull(points);if(points.size()>MAX_POINTS)throw new IllegalArgumentException("Dataset point limit exceeded");
        this.serverIdentity=serverIdentity;this.sourceId=sourceId;this.query=query;this.points=List.copyOf(points);
    }
    public String serverIdentity(){return serverIdentity;} public String sourceId(){return sourceId;} public QueryBounds query(){return query;} public List<ReferencePoint> points(){return points;}
    @Override public String toString(){return "ReferenceDataset[sourceId="+sourceId+", points="+points.size()+"]";}
}
