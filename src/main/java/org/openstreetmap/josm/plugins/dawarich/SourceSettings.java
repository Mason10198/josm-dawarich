package org.openstreetmap.josm.plugins.dawarich;

import java.util.Objects;

/** Non-secret, immutable row configuration; server endpoint is connection-wide. */
public final class SourceSettings {
    public final String id, label, color;
    public final boolean enabled;
    public SourceSettings(String id,String label,String color,boolean enabled){
        this.id=Objects.requireNonNull(id);this.label=Objects.requireNonNull(label);this.color=Objects.requireNonNull(color);this.enabled=enabled;
    }
    /** Compatibility constructor; the legacy endpoint argument is intentionally ignored. */
    public SourceSettings(String id,String label,String ignoredBaseUrl,String color,boolean enabled){this(id,label,color,enabled);}
    @Override public String toString(){return "SourceSettings[id="+id+", label="+label+", color="+color+", enabled="+enabled+"]";}
}
