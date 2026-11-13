package org.openstreetmap.josm.plugins.dawarich;

import java.util.*;

/** In-memory orchestration state for connection-wide endpoint and per-source credentials. */
public final class SourceRegistry {
    private String endpoint;
    private boolean allowHttp;
    private long endpointGeneration;
    private final LinkedHashMap<String, SourceSettings> settings=new LinkedHashMap<>();
    private final LinkedHashMap<String, SourceSession> sessions=new LinkedHashMap<>();
    private static final String[] COLORS={"#2E86AB","#F18F01","#6A994E","#9B5DE5","#D1495B","#00A896"};
    public synchronized SourceSettings add(String label){String id=UUID.randomUUID().toString();int n=settings.size();SourceSettings s=new SourceSettings(id,label,COLORS[n%COLORS.length],true);settings.put(id,s);sessions.put(id,new SourceSession(id,"",true));return s;}
    public synchronized void add(SourceSettings row){if(settings.containsKey(row.id))throw new IllegalArgumentException("Duplicate source id");settings.put(row.id,row);sessions.put(row.id,new SourceSession(row.id,"",row.enabled));}
    public synchronized void updateSettings(SourceSettings row){SourceSession session=require(row.id);settings.put(row.id,new SourceSettings(row.id,row.label,row.color,row.enabled));session.setEnabled(row.enabled);}
    public synchronized List<SourceSettings> rows(){return List.copyOf(settings.values());}
    public synchronized SourceSession session(String id){return sessions.get(id);}
    public synchronized void remove(String id){SourceSession s=sessions.remove(id);if(s==null)throw new IllegalArgumentException("Unknown source");s.remove();settings.remove(id);}
    public synchronized void setEnabled(String id,boolean enabled){SourceSession s=require(id);s.setEnabled(enabled);SourceSettings old=settings.get(id);settings.put(id,new SourceSettings(old.id,old.label,old.color,enabled));}
    public synchronized void setKey(String id,String key){SourceSession target=require(id);if(key!=null&&!key.isEmpty())for(var e:sessions.entrySet())if(!e.getKey().equals(id)&&!e.getValue().removed()&&key.equals(e.getValue().snapshot().apiKey()))throw new IllegalArgumentException("Duplicate API keys");target.setApiKey(key);}
    public synchronized void setEndpoint(String url,boolean httpAck){ConnectionSettings parsed=ConnectionSettings.parse(url,httpAck);boolean normalizedAck=parsed.baseUrl().regionMatches(true,0,"http://",0,7)&&httpAck;boolean changed=!Objects.equals(endpoint,parsed.baseUrl());if(changed){for(SourceSession s:sessions.values())s.invalidate();endpointGeneration++;}endpoint=parsed.baseUrl();allowHttp=normalizedAck;}
    public synchronized String endpoint(){return endpoint;} public synchronized boolean allowHttp(){return allowHttp;} public synchronized long endpointGeneration(){return endpointGeneration;}
    public synchronized void clearKeys(){for(SourceSession s:sessions.values())s.invalidate();}
    private SourceSession require(String id){SourceSession s=sessions.get(id);if(s==null)throw new IllegalArgumentException("Unknown source");return s;}
}
