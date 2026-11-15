package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.spi.preferences.*;

class DisplayModeTest {
 @BeforeAll static void init(){Config.setPreferencesInstance(new MemoryPreferences());}
 @Test void viewControlsAreIndependentOfLoadingAndExplainDensity()throws Exception{
  var r=new SourceRegistry();r.add("Synthetic");var panel=new LoadReferenceDialog(r);var selected=new AtomicReference<DisplayMode>();var loads=new AtomicInteger();panel.setLoadHandler(q->loads.incrementAndGet());panel.setDisplayModeHandler(selected::set);
  SwingUtilities.invokeAndWait(()->{assertEquals(DisplayMode.POINTS,panel.getDisplayMode());panel.setLoading(true);assertTrue(panel.displayModeSelector().isEnabled());panel.setDisplayMode(DisplayMode.HEATMAP);assertEquals(DisplayMode.HEATMAP,selected.get());assertTrue(panel.displayLegend().getText().contains("Density is not accuracy"));assertTrue(panel.displayLegend().getText().contains("long stops"));panel.setDisplayMode(DisplayMode.BOTH);assertEquals(DisplayMode.BOTH,selected.get());panel.setLoading(false);panel.setDisplayMode(DisplayMode.POINTS);assertEquals(0,loads.get());});
 }
 @Test void coordinatorUpdatesExistingAndFutureLayersWithoutReloadOrChangingData()throws Exception{
  var r=new SourceRegistry();r.setEndpoint("https://gps.example.org",false);var a=r.add("Synthetic");r.setKey(a.id,"synthetic-key");var worker=Executors.newSingleThreadExecutor();var calls=new AtomicInteger();
  var q=new QueryBounds(-1,1,-1,1);var points=List.of(new ReferencePoint(1,0,0,q.endInclusive().getEpochSecond()));
  try(var task=new LoadReferenceTask(r,new MainLayerManager(),worker,(server,id,key,bounds,quota,budget)->{calls.incrementAndGet();return new ReferenceDataset(server,id,bounds,points);})){task.setDisplayMode(DisplayMode.HEATMAP);assertEquals(LoadReferenceTask.Status.COMPLETE,task.start(q).get(5,TimeUnit.SECONDS).status());var layer=task.layersSnapshot().get(a.id);var original=layer.datasetPoints();assertEquals(DisplayMode.HEATMAP,layer.getDisplayMode());task.setDisplayMode(DisplayMode.BOTH);assertEquals(DisplayMode.BOTH,layer.getDisplayMode());task.setDisplayMode(DisplayMode.POINTS);assertEquals(DisplayMode.POINTS,layer.getDisplayMode());assertSame(original,layer.datasetPoints());assertEquals(points,layer.datasetPoints().points());assertEquals(1,calls.get());}
  finally{worker.shutdownNow();worker.awaitTermination(3,TimeUnit.SECONDS);}
 }
}
