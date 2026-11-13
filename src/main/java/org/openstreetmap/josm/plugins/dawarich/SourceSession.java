package org.openstreetmap.josm.plugins.dawarich;

import java.util.Objects;

/** Runtime-only secret holder. Credentials are never included in snapshots or diagnostics. */
public final class SourceSession {
    private final String sourceId;
    private String apiKey;
    private long generation;
    private boolean enabled = true;
    private boolean removed;
    public SourceSession(String sourceId, String ignoredUrl, boolean enabled) {
        this.sourceId=Objects.requireNonNull(sourceId); this.enabled=enabled;
    }
    public synchronized String sourceId(){return sourceId;}
    public synchronized long generation(){return generation;}
    public synchronized boolean enabled(){return enabled;}
    public synchronized boolean removed(){return removed;}
    public synchronized void setApiKey(String key){ensureActive(); if(!Objects.equals(apiKey,key)){apiKey=key;generation++;}}
    public synchronized KeySnapshot snapshot(){return new KeySnapshot(sourceId,generation,enabled,removed,apiKey);}
    public synchronized void setEnabled(boolean value){ensureActive();if(enabled!=value){enabled=value;generation++;}}
    public synchronized void invalidate(){ensureActive();apiKey=null;generation++;}
    public synchronized void remove(){if(!removed){removed=true;enabled=false;apiKey=null;generation++;}}
    private void ensureActive(){if(removed)throw new IllegalStateException("Source removed");}
    public static void rejectDuplicateKeys(Iterable<String> keys){java.util.HashSet<String> seen=new java.util.HashSet<>();for(String k:keys)if(k!=null&&!k.isEmpty()&&!seen.add(k))throw new IllegalArgumentException("Duplicate API keys");}
    @Override public synchronized String toString(){return "SourceSession[sourceId="+sourceId+", generation="+generation+", enabled="+enabled+", removed="+removed+"]";}
    public static final class KeySnapshot {
        private final String sourceId,key; private final long generation; private final boolean enabled,removed;
        private KeySnapshot(String id,long generation,boolean enabled,boolean removed,String key){this.sourceId=id;this.generation=generation;this.enabled=enabled;this.removed=removed;this.key=key;}
        public String sourceId(){return sourceId;} public long generation(){return generation;} public boolean enabled(){return enabled;} public boolean removed(){return removed;}
        public String apiKey(){return key;}
        @Override public String toString(){return "KeySnapshot[sourceId="+sourceId+", generation="+generation+"]";}
    }
}
