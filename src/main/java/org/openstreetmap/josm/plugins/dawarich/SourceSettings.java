package org.openstreetmap.josm.plugins.dawarich;
import java.util.*;
public final class SourceSettings {
 public final String id,label,baseUrl,color; public final boolean enabled;
 public SourceSettings(String id,String label,String baseUrl,String color,boolean enabled){this.id=Objects.requireNonNull(id);this.label=label;this.baseUrl=ConnectionSettings.parse(baseUrl,true).baseUrl();this.color=Objects.requireNonNull(color);this.enabled=enabled;}
 @Override public String toString(){return "SourceSettings[id="+id+", label="+label+", baseUrl="+baseUrl+", color="+color+", enabled="+enabled+"]";}
}
