package org.openstreetmap.josm.plugins.dawarich;

import java.time.LocalDate;
import java.util.*;
import org.openstreetmap.josm.spi.preferences.IPreferences;

/** Persistence for connection configuration. API keys are deliberately never serialized. */
public final class ConnectionPreferences {
    private static final String ENDPOINT="dawarich.endpoint", HTTP="dawarich.httpAck", SOURCES="dawarich.sources", START="dawarich.startDate", END="dawarich.endDate";
    private ConnectionPreferences() { }
    public static void save(IPreferences prefs, SourceRegistry registry, LocalDate start, LocalDate end) {
        Objects.requireNonNull(prefs); Objects.requireNonNull(registry); Objects.requireNonNull(start); Objects.requireNonNull(end);
        prefs.put(ENDPOINT, registry.endpoint()==null ? "" : registry.endpoint());
        prefs.putBoolean(HTTP, registry.allowHttp());
        List<Map<String,String>> rows=new ArrayList<>();
        for(SourceSettings s:registry.rows()) { Map<String,String> m=new LinkedHashMap<>(); m.put("id",s.id);m.put("label",s.label);m.put("color",s.color);m.put("enabled",Boolean.toString(s.enabled));rows.add(m); }
        prefs.putListOfMaps(SOURCES,rows); prefs.put(START,start.toString());prefs.put(END,end.toString());
    }
    public static Settings load(IPreferences prefs) {
        Objects.requireNonNull(prefs); SourceRegistry registry=new SourceRegistry();
        try { String endpoint=prefs.get(ENDPOINT,""); boolean http=prefs.getBoolean(HTTP,false); if(endpoint!=null&&!endpoint.isBlank())registry.setEndpoint(endpoint,http); } catch(RuntimeException ignored) { }
        try { Set<String> ids=new HashSet<>(); for(Map<String,String> m:prefs.getListOfMaps(SOURCES,List.of())) { try {
            String id=m.get("id"), label=m.get("label"), color=m.get("color"); boolean enabled=Boolean.parseBoolean(m.get("enabled"));
            SourceSettings row=new SourceSettings(id,label,color,enabled); if(ids.add(id))registry.add(row);
        } catch(RuntimeException ignored) { } } } catch(RuntimeException ignored) { }
        LocalDate today=LocalDate.now(), start=date(prefs,START,today.minusMonths(12)), end=date(prefs,END,today);
        if(start.isAfter(today) || end.isAfter(today) || end.isBefore(start)) { start=today.minusMonths(12); end=today; }
        return new Settings(registry,start,end);
    }
    private static LocalDate date(IPreferences p,String key,LocalDate fallback) { try { return LocalDate.parse(p.get(key,fallback.toString())); } catch(RuntimeException e) { return fallback; } }
    public static final class Settings {
        private final SourceRegistry registry; private final LocalDate startDate,endDate;
        private Settings(SourceRegistry r,LocalDate s,LocalDate e){registry=r;startDate=s;endDate=e;}
        public SourceRegistry registry(){return registry;} public LocalDate startDate(){return startDate;} public LocalDate endDate(){return endDate;}
    }
}
