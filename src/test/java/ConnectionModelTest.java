import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.spi.preferences.IPreferences;
import org.openstreetmap.josm.plugins.dawarich.*;

class ConnectionModelTest {
 @Test void validatesAndNormalizesEndpoints() {
  assertEquals("https://host/base%20x",ConnectionSettings.parse("HTTPS://host/base%20x///",false).baseUrl());
  for(String url:List.of("https://host:99999","https://host:abc","https://host/%zz","https://host?x","https://host/#f","http://host")) assertThrows(IllegalArgumentException.class,()->ConnectionSettings.parse(url,false));
 }
 @Test void lifecycleAndSettingsUpdatesKeepSecretsScoped() {
  SourceRegistry r=new SourceRegistry(); SourceSettings a=r.add("A"),b=r.add("B"),c=r.add("C");
  assertEquals(3,r.rows().stream().map(s->s.color).distinct().count());
  r.setKey(a.id,"alpha"); r.setKey(b.id,"beta"); assertThrows(IllegalArgumentException.class,()->r.setKey(c.id,"alpha"));
  long ga=r.session(a.id).generation(), gb=r.session(b.id).generation();
  r.setEnabled(a.id,false); assertEquals("alpha",r.session(a.id).snapshot().apiKey()); assertTrue(r.session(a.id).generation()>ga); assertEquals(gb,r.session(b.id).generation());
  r.setEndpoint("https://one.test",false); r.setKey(a.id,"alpha"); r.setKey(b.id,"beta"); long before=r.endpointGeneration();
  r.setEndpoint("https://one.test",true); assertEquals(before,r.endpointGeneration()); assertEquals("alpha",r.session(a.id).snapshot().apiKey());
  r.updateSettings(new SourceSettings(a.id,"renamed","#ABCDEF",false)); assertFalse(r.session(a.id).enabled()); assertEquals("alpha",r.session(a.id).snapshot().apiKey());
  r.setEndpoint("https://two.test",false); assertTrue(r.endpointGeneration()>before); assertNull(r.session(a.id).snapshot().apiKey());
  SourceSession removed=r.session(c.id); r.remove(c.id); assertTrue(removed.removed()); assertThrows(IllegalStateException.class,()->removed.setApiKey("x"));
 }
 @Test void persistsNonSecretsAndLoadsEmptyCredentials() {
  Map<String,Object> data=new HashMap<>(); IPreferences p=(IPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IPreferences.class},(o,m,a)->{
   String k=a!=null&&a.length>0&&a[0] instanceof String?(String)a[0]:"";
   switch(m.getName()) {case "put": case "putBoolean": case "putInt": case "putLong": case "putDouble": case "putList": case "putListOfLists": case "putListOfMaps": data.put(k,a[1]); return true;
   case "get": return data.getOrDefault(k,a.length>1?a[1]:null); case "getBoolean":case "getInt":return data.getOrDefault(k,a[1]); case "getList":case "getListOfLists":case "getListOfMaps":return data.getOrDefault(k,a[1]); case "getKeySet":return data.keySet(); case "toString":return data.toString(); default:return null; }
  });
  SourceRegistry r=new SourceRegistry(); SourceSettings a=r.add("One"),b=r.add("Two"),c=r.add("Three"); r.setEndpoint("https://host/base",false); r.setKey(a.id,"secret-a");r.setKey(b.id,"secret-b");r.setKey(c.id,"secret-c");
  assertEquals("secret-a",r.session(a.id).snapshot().apiKey()); assertEquals("secret-b",r.session(b.id).snapshot().apiKey()); assertEquals("secret-c",r.session(c.id).snapshot().apiKey());
  LocalDate start=LocalDate.parse("2025-01-02"),end=LocalDate.parse("2025-03-04"); ConnectionPreferences.save(p,r,start,end);
  ConnectionPreferences.save(p,r,start,end,new LoadOptions(LoadOptions.Mode.MOST_RECENT,123,456));
  assertEquals(new LoadOptions(LoadOptions.Mode.MOST_RECENT,123,456),ConnectionPreferences.load(p).options());
  assertFalse(data.toString().contains("secret-")); var loaded=ConnectionPreferences.load(p); assertEquals(3,loaded.registry().rows().size()); assertEquals(start,loaded.startDate());assertEquals(end,loaded.endDate());
  for(SourceSettings row:loaded.registry().rows())assertNull(loaded.registry().session(row.id).snapshot().apiKey());
  LocalDate today=LocalDate.now(), fallback=today.minusMonths(12);
  data.put("dawarich.startDate","bad-date"); data.put("dawarich.endDate",today.toString());
  var invalid=ConnectionPreferences.load(p); assertEquals(fallback,invalid.startDate()); assertEquals(today,invalid.endDate());
  data.put("dawarich.startDate",today.plusDays(1).toString()); data.put("dawarich.endDate",today.plusDays(2).toString());
  var future=ConnectionPreferences.load(p); assertEquals(fallback,future.startDate()); assertEquals(today,future.endDate());
  data.put("dawarich.startDate",today.toString()); data.put("dawarich.endDate",today.minusDays(1).toString());
  var reversed=ConnectionPreferences.load(p); assertEquals(fallback,reversed.startDate()); assertEquals(today,reversed.endDate());
  assertEquals(3,future.registry().rows().size()); for(SourceSettings row:future.registry().rows())assertNull(future.registry().session(row.id).snapshot().apiKey());
 }
 @Test void dstInclusiveAndFutureCapped() {
  ZoneId z=ZoneId.of("America/New_York"); Instant now=Instant.parse("2025-11-02T12:00:00Z");
  QueryBounds q=new QueryBounds(-1,1,-1,1,LocalDate.parse("2025-11-02"),LocalDate.parse("2025-11-02"),z,Instant.parse("2025-11-03T12:00:00Z"));
  assertEquals(Duration.ofHours(25),Duration.between(q.startInclusive(),q.endExclusive()));
  QueryBounds capped=new QueryBounds(-1,1,-1,1,LocalDate.parse("2025-11-02"),LocalDate.parse("2025-11-03"),z,now);
  assertEquals(now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS),capped.endInclusive());
  assertThrows(IllegalArgumentException.class,()->new QueryBounds(-90,90,-180,180,LocalDate.parse("2025-01-01"),LocalDate.parse("2025-01-02"),z,now));
  assertTrue(new QueryBounds(-1,1,-2,2,LocalDate.parse("2025-01-01"),LocalDate.parse("2025-01-02"),z,now).broadAreaNeedsConfirmation());
 }
}
