package org.openstreetmap.josm.plugins.dawarich;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory orchestration state for connection-wide endpoint and per-source credentials. */
public final class SourceRegistry {
    public enum ChangeKind { KEY, SERVER, ENABLED, REMOVED, STYLE }
    public record Change(ChangeKind kind, String sourceId) { }
    @FunctionalInterface public interface ChangeListener { void changed(Change change); }
    private final CopyOnWriteArrayList<ChangeListener> listeners = new CopyOnWriteArrayList<>();
    public void addChangeListener(ChangeListener listener) { listeners.add(Objects.requireNonNull(listener)); }
    public void removeChangeListener(ChangeListener listener) { listeners.remove(listener); }
    private void fire(ChangeKind kind, String id) { Change c = new Change(kind, id); for (ChangeListener l : listeners) l.changed(c); }
    private String endpoint;
    private boolean allowHttp;
    private long endpointGeneration;
    private final LinkedHashMap<String, SourceSettings> settings=new LinkedHashMap<>();
    private final LinkedHashMap<String, SourceSession> sessions=new LinkedHashMap<>();
    private static final String[] COLORS={"#2E86AB","#F18F01","#6A994E","#9B5DE5","#D1495B","#00A896"};
    public synchronized SourceSettings add(String label){String id=UUID.randomUUID().toString();int n=settings.size();SourceSettings s=new SourceSettings(id,label,COLORS[n%COLORS.length],true);settings.put(id,s);sessions.put(id,new SourceSession(id,"",true));return s;}
    public synchronized void add(SourceSettings row){if(settings.containsKey(row.id))throw new IllegalArgumentException("Duplicate source id");settings.put(row.id,row);sessions.put(row.id,new SourceSession(row.id,"",row.enabled));}
    public void updateSettings(SourceSettings row){boolean style,enabled; synchronized(this){SourceSession session=require(row.id);SourceSettings old=settings.get(row.id);style=!Objects.equals(old.label,row.label)||!Objects.equals(old.color,row.color);enabled=old.enabled!=row.enabled;settings.put(row.id,new SourceSettings(row.id,row.label,row.color,row.enabled));session.setEnabled(row.enabled);}if(style)fire(ChangeKind.STYLE,row.id);if(enabled)fire(ChangeKind.ENABLED,row.id);}
    public synchronized List<SourceSettings> rows(){return List.copyOf(settings.values());}
    public synchronized SourceSession session(String id){return sessions.get(id);}
    public void remove(String id){synchronized(this){SourceSession s=sessions.remove(id);if(s==null)throw new IllegalArgumentException("Unknown source");s.remove();settings.remove(id);}fire(ChangeKind.REMOVED,id);}
    public void setEnabled(String id,boolean enabled){boolean changed;synchronized(this){SourceSession s=require(id);SourceSettings old=settings.get(id);changed=old.enabled!=enabled;s.setEnabled(enabled);settings.put(id,new SourceSettings(old.id,old.label,old.color,enabled));}if(changed)fire(ChangeKind.ENABLED,id);}
    public void setKey(String id,String key){boolean changed;synchronized(this){SourceSession target=require(id);if(key!=null&&!key.isEmpty())for(var e:sessions.entrySet())if(!e.getKey().equals(id)&&!e.getValue().removed()&&key.equals(e.getValue().snapshot().apiKey()))throw new IllegalArgumentException("Duplicate API keys");String old=target.snapshot().apiKey();target.setApiKey(key);changed=!Objects.equals(old,key);}if(changed)fire(ChangeKind.KEY,id);}
    public void setEndpoint(String url,boolean httpAck){boolean changed;synchronized(this){ConnectionSettings parsed=ConnectionSettings.parse(url,httpAck);boolean normalizedAck=parsed.baseUrl().regionMatches(true,0,"http://",0,7)&&httpAck;changed=!Objects.equals(endpoint,parsed.baseUrl());if(changed){for(SourceSession s:sessions.values())s.invalidate();endpointGeneration++;}endpoint=parsed.baseUrl();allowHttp=normalizedAck;}if(changed)fire(ChangeKind.SERVER,null);}
    public synchronized String endpoint(){return endpoint;} public synchronized boolean allowHttp(){return allowHttp;} public synchronized long endpointGeneration(){return endpointGeneration;}
    public synchronized void clearKeys(){for(SourceSession s:sessions.values())s.invalidate();}
    private SourceSession require(String id){SourceSession s=sessions.get(id);if(s==null)throw new IllegalArgumentException("Unknown source");return s;}
}
