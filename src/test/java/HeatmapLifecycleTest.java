package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.*;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.projection.*;
import org.openstreetmap.josm.gui.MapViewState;
import org.openstreetmap.josm.spi.preferences.*;

class HeatmapLifecycleTest {
 @BeforeEach void init(){Config.setPreferencesInstance(new MemoryPreferences());ProjectionRegistry.setProjection(Projections.getProjectionByCode("EPSG:3857"));}
 ReferenceDataset data(List<ReferencePoint> points){return new ReferenceDataset("server","source",new QueryBounds(-85,85,-90,90),points);}
 ReferencePoint at(long id,MapViewState state,int x,int y){var ll=state.getForView(x,y).getLatLon();return new ReferencePoint(id,ll.lat(),ll.lon(),id);}
 BufferedImage request(DawarichLayer layer,MapViewState state)throws Exception{var value=new AtomicReference<BufferedImage>();SwingUtilities.invokeAndWait(()->value.set(layer.heatmapFor(state)));return value.get();}
 BufferedImage ready(DawarichLayer layer,MapViewState state)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);BufferedImage image;do{image=request(layer,state);if(image!=null)return image;Thread.sleep(10);}while(System.nanoTime()<until);throw new AssertionError("Heatmap did not become ready");}
 boolean blank(BufferedImage image){for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)!=0)return false;return true;}
 @Test void cacheUsesViewDatasetColorAndReleasesOnPoints()throws Exception{
  var state=MapViewState.createDefaultState(128,96);var original=data(List.of(at(1,state,64,48)));var layer=new DawarichLayer(original,"Synthetic",Color.RED);
  try{layer.setDisplayMode(DisplayMode.HEATMAP);var first=ready(layer,state);int builds=layer.getHeatmapBuildCount();assertSame(first,request(layer,state));assertEquals(builds,layer.getHeatmapBuildCount());
   var moved=state.usingCenter(new EastNorth(state.getCenter().getEastNorth().east()+state.getScale()*10,state.getCenter().getEastNorth().north()));assertNull(request(layer,moved));assertFalse(layer.isHeatmapReady());assertNotSame(first,ready(layer,moved));
   var zoomed=moved.usingScale(state.getScale()*2);assertNull(request(layer,zoomed));ready(layer,zoomed);var resized=MapViewState.createDefaultState(256,128);assertNull(request(layer,resized));ready(layer,resized);
   layer.setColor(Color.BLUE);assertFalse(layer.isHeatmapReady());var blue=ready(layer,state);assertFalse(blank(blue));for(int y=0;y<blue.getHeight();y++)for(int x=0;x<blue.getWidth();x++)if((blue.getRGB(x,y)>>>24)>0)assertEquals(Color.BLUE.getRGB()&0xffffff,blue.getRGB(x,y)&0xffffff);
   assertSame(original,layer.datasetPoints());layer.setDisplayMode(DisplayMode.BOTH);assertSame(blue,request(layer,state));layer.setDisplayMode(DisplayMode.POINTS);assertFalse(layer.isHeatmapReady());assertNull(request(layer,state));assertSame(original,layer.datasetPoints());
  }finally{layer.destroy();}
 }
 @Test void obsoleteLargeWorkCannotResurrectAfterReplaceOrDestroy()throws Exception{
  var state=MapViewState.createDefaultState(128,96);var p=at(1,state,64,48);List<ReferencePoint> many=new ArrayList<>();for(int i=1;i<=250000;i++)many.add(new ReferencePoint(i,p.latitude(),p.longitude(),i));var layer=new DawarichLayer(data(many),"Synthetic",Color.RED);
  layer.setDisplayMode(DisplayMode.HEATMAP);request(layer,state);var empty=data(List.of());layer.replaceDataset(empty);var finalImage=ready(layer,state);assertTrue(blank(finalImage));assertSame(empty,layer.datasetPoints());
  for(int i=0;i<20;i++)request(layer,state.usingScale(state.getScale()*(1+i*.01)));layer.destroy();Thread.sleep(100);assertFalse(layer.isHeatmapReady());assertEquals(0,layer.pointCount());assertNull(request(layer,state));layer.setDisplayMode(DisplayMode.BOTH);assertFalse(layer.isHeatmapReady());
 }
 @Test void rasterAndWorkAreBoundedAndCancelable(){
  var state=MapViewState.createDefaultState(128,96);var empty=data(List.of());var raster=HeatmapRenderer.render(empty,state,Color.RED,100000,100000);assertTrue((long)raster.getWidth()*raster.getHeight()<=HeatmapRenderer.MAX_CELLS);assertTrue(blank(raster));
  assertThrows(CancellationException.class,()->HeatmapRenderer.render(data(List.of(at(1,state,64,48))),state,Color.RED,128,96,()->true));
 }
 @Test void denseAndSparseClustersStaySeparatedAndOutsidePointsDoNotLeak(){
  var state=MapViewState.createDefaultState(128,96);List<ReferencePoint> points=new ArrayList<>();for(int i=1;i<=100;i++)points.add(at(i,state,32,48));points.add(at(101,state,96,48));var source=data(points);var image=HeatmapRenderer.render(source,state,Color.RED,128,96);
  int dense=0,sparse=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int alpha=image.getRGB(x,y)>>>24;if(x<image.getWidth()/2)dense=Math.max(dense,alpha);else sparse=Math.max(sparse,alpha);}assertTrue(dense>sparse&&sparse>0);assertEquals(0,image.getRGB(image.getWidth()/2,image.getHeight()/2)>>>24);assertEquals(points,source.points());
  var outside=data(List.of(at(102,state,-1,48),at(103,state,128,48)));assertTrue(blank(HeatmapRenderer.render(outside,state,Color.RED,128,96)));
 }
}
