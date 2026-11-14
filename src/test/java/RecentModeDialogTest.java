package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;

class RecentModeDialogTest {
 @BeforeAll static void init(){Config.setPreferencesInstance(new MemoryPreferences());}
 private LoadReferenceDialog dialog(){var r=new SourceRegistry();var s=r.add("Friendly <source>");r.setEndpoint("https://example.test",false);r.setKey(s.id,"dummy-key");return new LoadReferenceDialog(r);}
 @Test void recentModeButtonHandlerAcceptsInvalidDisabledDatesButDateModeRejects(){var p=dialog();var sent=new java.util.concurrent.atomic.AtomicReference<LoadReferenceDialog.LoadRequest>();p.setLoadHandler(sent::set);p.modeSelector().setSelectedItem(LoadOptions.Mode.MOST_RECENT);p.startDateField().setText("invalid");p.endDateField().setText("2999-12-31");p.loadButton().doClick();assertNotNull(sent.get());assertEquals(LoadOptions.Mode.MOST_RECENT,sent.get().options.mode());p.modeSelector().setSelectedItem(LoadOptions.Mode.DATE_RANGE);sent.set(null);p.loadButton().doClick();assertNull(sent.get());assertTrue(p.statusLabel().getText().contains("Invalid"));}
 @Test void rejectsInvalidIntegerOptions(){var p=dialog();assertThrows(IllegalArgumentException.class,()->new LoadOptions(LoadOptions.Mode.MOST_RECENT,0,1));assertThrows(IllegalArgumentException.class,()->new LoadOptions(LoadOptions.Mode.DATE_RANGE,1,250001));}
 @Test void buttonRejectsFractionalOrUnparseableTypedPointCounts(){
  for(String invalid:List.of("1.5","invalid","250001","0")){
   var p=dialog();p.setLoadOptions(new LoadOptions(LoadOptions.Mode.MOST_RECENT,10,100));
   var calls=new java.util.concurrent.atomic.AtomicInteger();p.setLoadHandler(request->calls.incrementAndGet());
   ((javax.swing.JSpinner.DefaultEditor)p.recentCountSpinner().getEditor()).getTextField().setText(invalid);
   p.loadButton().doClick();assertEquals(0,calls.get(),invalid);assertTrue(p.statusLabel().getText().contains("Invalid"));
  }
 }
 @Test void resultShowsRecordedCoverageAndAvailabilityWithoutRawMessage(){var p=dialog();var a=new LoadReferenceAction(()->null,q->true,q->null,()->{},()->false,p);long low=Instant.parse("2024-01-02T03:04:05Z").getEpochSecond(),high=Instant.parse("2024-01-03T04:05:06Z").getEpochSecond();a.showResult(new LoadReferenceTask.BatchResult(LoadReferenceTask.Status.COMPLETE,List.of(new LoadReferenceTask.SourceResult("internal-id",LoadReferenceTask.Status.COMPLETE,"raw secret https://x",false,2,low,high,5),new LoadReferenceTask.SourceResult("internal-id",LoadReferenceTask.Status.COMPLETE,"bad",false,0,0,0,5)),2,Set.of(),5));String html=p.statusLabel().getText();assertTrue(html.contains(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(low))));assertTrue(html.contains(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(high))));assertTrue(html.contains("fewer than"));assertFalse(html.contains("raw secret"));assertFalse(html.contains("https://"));assertFalse(html.contains("internal-id"));}
}
