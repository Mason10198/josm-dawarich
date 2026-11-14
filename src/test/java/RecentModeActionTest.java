package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;

class RecentModeActionTest {
 @BeforeAll static void init(){Config.setPreferencesInstance(new MemoryPreferences());}
 @Test void recentIgnoresMalformedDatesAndCarriesFrozenOptions(){
  var r=new SourceRegistry();r.add("x");var panel=new LoadReferenceDialog(r);panel.setLoadHandler(req->{});panel.modeSelector().setSelectedItem(LoadOptions.Mode.MOST_RECENT);panel.startDateField().setText("not-a-date");panel.endDateField().setText("2999-01-01");panel.recentCountSpinner().setValue(73);panel.totalBudgetSpinner().setValue(200);
  var got=new AtomicReference<QueryBounds>();var action=new LoadReferenceAction(()->new LoadReferenceAction.Viewport(10,10,-10,20,100,100),q->{got.set(q);return true;},q->{got.set(q);return null;},()->{},()->false,panel);
  action.load(new LoadReferenceDialog.LoadRequest(null,LocalDate.now(),LocalDate.now(),ZoneId.systemDefault(),panel.loadOptions()));
assertEquals(new LoadOptions(LoadOptions.Mode.MOST_RECENT,73,200),got.get().options());assertEquals(73,got.get().options().recentPointsPerSource());assertFalse(panel.startDateField().isEnabled());assertFalse(panel.endDateField().isEnabled());
 }
}
