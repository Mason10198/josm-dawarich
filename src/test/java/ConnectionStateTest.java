import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.dawarich.*;

class ConnectionStateTest {
 @Test void endpointRetainsPrefixAndRequiresExplicitHttp() {
  assertEquals("https://host/base/api/v1/points", ConnectionSettings.parse("https://host/base", false).pointsUri().toString());
  assertThrows(IllegalArgumentException.class, () -> ConnectionSettings.parse("http://host", false));
  assertEquals("http://host/api/v1/points", ConnectionSettings.parse("http://host", true).pointsUri().toString());
  for (String s : List.of("https://u:p@host", "https://host?x=y", "https://host/#x", "ftp://host", "https://")) assertThrows(IllegalArgumentException.class, () -> ConnectionSettings.parse(s, true));
 }
 @Test void boundsAndInclusiveLocalDates() {
  assertThrows(IllegalArgumentException.class, () -> new QueryBounds(0, 0, 0, 0));
  assertThrows(IllegalArgumentException.class, () -> new QueryBounds(-1, 1, 0, 181));
  ZoneId zone=ZoneId.of("America/New_York"); Instant now=Instant.parse("2024-03-11T12:00:00Z");
  QueryBounds q=new QueryBounds(-1,1,-1,1, LocalDate.parse("2024-03-10"), LocalDate.parse("2024-03-10"),zone,now);
  assertEquals(Duration.ofHours(23),Duration.between(q.startInclusive(),q.endExclusive()));
 }
 @Test void keysRemainEphemeralAndGenerationInvalidates() {
  SourceSession s=new SourceSession("id", "https://host", true); s.setApiKey("secret"); long g=s.generation(); s.setApiKey("replacement");
  assertTrue(s.generation()>g); assertFalse(s.toString().contains("replacement")); assertThrows(IllegalArgumentException.class,()->SourceSession.rejectDuplicateKeys(List.of("a","b","a")));
 }
}
