package org.openstreetmap.josm.plugins.dawarich;
import java.util.*;
/** Runtime-only credential holder; Java cannot promise secure erasure of Strings. */
public final class SourceSession {
 private String id,key; private final String url; private long generation;
 public SourceSession(String id,String url,boolean enabled){this.id=id;this.url=ConnectionSettings.parse(url,true).baseUrl();}
 public synchronized void setApiKey(String value){if(!Objects.equals(key,value)){key=value;generation++;}}
 public synchronized String apiKey(){return key;} public synchronized long generation(){return generation;}
 public synchronized void invalidate(){key=null;generation++;}
 public synchronized void disable(){invalidate();} public synchronized void remove(){invalidate();id=null;}
 public static void rejectDuplicateKeys(Collection<String> keys){Set<String>s=new HashSet<>();for(String k:keys)if(k!=null&&!k.isEmpty()&&!s.add(k))throw new IllegalArgumentException("Duplicate API keys");}
 @Override public String toString(){return "SourceSession[id="+id+", generation="+generation+"]";}
}
