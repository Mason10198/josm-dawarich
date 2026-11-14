package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.openstreetmap.josm.spi.preferences.*;

class ProgressActionTest {
 @BeforeAll static void init(){Config.setPreferencesInstance(new MemoryPreferences());}
 static LoadReferenceTask.BatchResult result(LoadReferenceTask.Status status){return new LoadReferenceTask.BatchResult(status,List.of(),0,Set.of(),50000);}
 @Test void timerSamplesProgressAndStopsOnTerminalStatus()throws Exception{
  var registry=new SourceRegistry();var source=registry.add("Synthetic GPS");var active=new AtomicBoolean(true);
  var progress=new AtomicReference<>(new LoadProgress(source.id,1,1,1,4096,0,10000,1000));
  var panel=new LoadReferenceDialog(registry);var action=new LoadReferenceAction(()->null,q->true,q->null,()->active.set(false),active::get,panel);
  try{
   SwingUtilities.invokeAndWait(()->{action.setProgressSource(progress::get);action.showResult(result(LoadReferenceTask.Status.LOADING));assertTrue(action.progressTimerRunning());assertFalse(panel.loadButton().isEnabled());});
   AtomicReference<String> before=new AtomicReference<>();SwingUtilities.invokeAndWait(()->before.set(panel.progressLabel().getText()));
   progress.set(new LoadProgress(source.id,1,1,2,8192,3,10000,2000));Thread.sleep(450);
   SwingUtilities.invokeAndWait(()->{assertNotEquals(before.get(),panel.progressLabel().getText());active.set(false);action.showResult(result(LoadReferenceTask.Status.COMPLETE));assertFalse(action.progressTimerRunning());assertFalse(panel.progressBar().isIndeterminate());});
  }finally{SwingUtilities.invokeAndWait(action::dispose);}
 }
 @Test void disposalStopsTimerAndPreventsLateProgressUpdates()throws Exception{
  var registry=new SourceRegistry();var source=registry.add("Synthetic");var panel=new LoadReferenceDialog(registry);
  var action=new LoadReferenceAction(()->null,q->true,q->null,()->{},()->true,panel);
  SwingUtilities.invokeAndWait(()->{action.setProgressSource(()->new LoadProgress(source.id,1,1,1,4096,0,0,1000));action.showResult(result(LoadReferenceTask.Status.LOADING));assertTrue(action.progressTimerRunning());action.dispose();assertFalse(action.progressTimerRunning());});
 }
 @Test void stopDisablesLoadUntilCancellationCompletesAndClearsEditor()throws Exception{
  var registry=new SourceRegistry();var source=registry.add("Synthetic");var panel=new LoadReferenceDialog(registry);
  var active=new AtomicBoolean(true);var cancels=new AtomicInteger();var starts=new AtomicInteger();
  var action=new LoadReferenceAction(()->new LoadReferenceAction.Viewport(.01,-.01,-.01,.01,640,480),q->true,q->{starts.incrementAndGet();return null;},cancels::incrementAndGet,active::get,panel);
  try{SwingUtilities.invokeAndWait(()->{
   action.setProgressSource(()->new LoadProgress(source.id,1,1,1,512,0,10000,1000));
   action.showResult(result(LoadReferenceTask.Status.LOADING));panel.apiKeyField().setText("synthetic-transient");panel.cancelButton().doClick();
   assertEquals(1,cancels.get());assertEquals(0,panel.apiKeyField().getPassword().length);assertFalse(action.progressTimerRunning());assertFalse(panel.loadButton().isEnabled());
   action.showResult(result(LoadReferenceTask.Status.LOADING));assertFalse(action.progressTimerRunning());assertFalse(panel.loadButton().isEnabled());
   action.load(new LoadReferenceDialog.LoadRequest(ConnectionSettings.parse("https://gps.example.org",false),LocalDate.now(),LocalDate.now(),ZoneId.systemDefault()));assertEquals(0,starts.get());
   active.set(false);action.showResult(result(LoadReferenceTask.Status.CANCELLED));assertTrue(panel.loadButton().isEnabled());assertFalse(action.progressTimerRunning());
  });}finally{SwingUtilities.invokeAndWait(action::dispose);}
 }
 @Test void freshProfilesRecommendRecentButSavedDateModeSurvives(){
  var prefs=new MemoryPreferences();assertEquals(LoadOptions.Mode.MOST_RECENT,ConnectionPreferences.load(prefs).options().mode());
  var registry=new SourceRegistry();registry.add("Synthetic");var options=new LoadOptions(LoadOptions.Mode.DATE_RANGE,10000,100000);
  ConnectionPreferences.save(prefs,registry,LocalDate.now().minusDays(1),LocalDate.now(),options);assertEquals(options,ConnectionPreferences.load(prefs).options());
 }
}
