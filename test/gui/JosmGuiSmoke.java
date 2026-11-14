import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import com.sun.net.httpserver.*;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.PluginHandler;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

/** Isolated synthetic-only GUI acceptance driver, never uses real GPS. */
public class JosmGuiSmoke {
 static Object field(Object object,String name) throws Exception { Field f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
 static Object call(Object object,String name,Class<?>[] types,Object... args) throws Exception {Method m=object.getClass().getMethod(name,types); return m.invoke(object,args);}
 static void edt(Runnable r) throws Exception {SwingUtilities.invokeAndWait(r);}
 static void acceptPlatformWarning(Component c) {if(c instanceof AbstractButton b && "Continue".equals(b.getText())) b.doClick(); if(c instanceof Container p) for(Component x:p.getComponents()) acceptPlatformWarning(x);}
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args) throws Exception {
  AtomicInteger requests=new AtomicInteger(); java.util.List<String> auth=Collections.synchronizedList(new ArrayList<>());
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/v1/points",x->{requests.incrementAndGet();String key=x.getRequestHeaders().getFirst("Authorization");auth.add(key);boolean second="Bearer synthetic-B".equals(key);long now=java.time.Instant.now().minusSeconds(60).getEpochSecond();StringBuilder body=new StringBuilder("[");for(int i=0;i<10;i++){if(i>0)body.append(',');body.append("{\"id\":").append(i+1).append(",\"latitude\":").append((second?0.003:0.001)+i*0.0001).append(",\"longitude\":").append(-0.002+i*0.0004).append(",\"timestamp\":").append(now).append('}');}body.append(']');byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.getResponseHeaders().set("X-Current-Page","1");x.getResponseHeaders().set("X-Total-Pages","1");x.sendResponseHeaders(200,bytes.length);try(var out=x.getResponseBody()){out.write(bytes);}});
  server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r);t.setDaemon(true);return t;}));server.start();
  Thread driver=new Thread(()->{try{
   Thread.sleep(6000);edt(()->{for(Window w:Window.getWindows())if(w.isShowing()&&w instanceof Dialog d&&d.getTitle().equals("JOSM may work improperly"))acceptPlatformWarning(w);});
   Object plugin=null;for(int i=0;i<20&&plugin==null;i++){Thread.sleep(500);plugin=PluginHandler.getPlugin("dawarich");}require(plugin!=null,"Plugin was not loaded");require(requests.get()==0,"Plugin performed startup HTTP");System.out.println("SMOKE_PLUGIN_LOADED=true; STARTUP_REQUESTS=0");
   runLoaded(server,requests,auth);
  }catch(Throwable t){t.printStackTrace();System.exit(2);}},"synthetic-gui-driver");driver.setDaemon(true);driver.start();MainApplication.main(new String[]{"--offline=ALL","--debug"});
 }
 static void runLoaded(HttpServer server,AtomicInteger requests,java.util.List<String> auth) throws Exception {
  Object plugin=PluginHandler.getPlugin("dawarich");Object registry=field(plugin,"registry"), task=field(plugin,"task"), action=field(plugin,"action");Method pm=action.getClass().getDeclaredMethod("panel");pm.setAccessible(true);Object panel=pm.invoke(action);
  edt(()->{try{
   var rows=(java.util.List<?>)call(registry,"rows",new Class<?>[]{});Object a=rows.get(0);String idA=(String)a.getClass().getField("id").get(a);Object b=call(registry,"add",new Class<?>[]{String.class},"Synthetic B");String idB=(String)b.getClass().getField("id").get(b);
   String endpoint="http://127.0.0.1:"+server.getAddress().getPort();call(registry,"setEndpoint",new Class<?>[]{String.class,boolean.class},endpoint,true);call(registry,"setKey",new Class<?>[]{String.class,String.class},idA,"synthetic-A");call(registry,"setKey",new Class<?>[]{String.class,String.class},idB,"synthetic-B");
   ((JTextField)call(panel,"serverField",new Class<?>[]{})).setText(endpoint);((JCheckBox)call(panel,"httpCheckBox",new Class<?>[]{})).setSelected(true);
   MainApplication.getLayerManager().addLayer(new OsmDataLayer(new DataSet(),"Synthetic empty editing layer",null));MainApplication.getMap().mapView.zoomTo(new Bounds(-0.01,-0.01,0.01,0.01));
   ((javax.swing.Action)action).actionPerformed(new ActionEvent(action,0,"smoke"));
  }catch(Exception e){throw new RuntimeException(e);}});
  Thread.sleep(1000);edt(()->{try{((JButton)call(panel,"loadButton",new Class<?>[]{})).doClick();}catch(Exception e){throw new RuntimeException(e);}});
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while((boolean)call(task,"isLoading",new Class<?>[]{})&&System.nanoTime()<deadline)Thread.sleep(50);
  Map<?,?> owned=(Map<?,?>)call(task,"layersSnapshot",new Class<?>[]{});require(owned.size()==2,"Expected two source layers, got "+owned.size());for(Object layer:owned.values())require((int)call(layer,"pointCount",new Class<?>[]{})==10,"Source point count mismatch");
  require(requests.get()==2,"Expected two requests, got "+requests.get());require(auth.equals(java.util.List.of("Bearer synthetic-A","Bearer synthetic-B")),"Per-source auth mismatch");
  AtomicBoolean enabled=new AtomicBoolean();edt(()->{try{enabled.set(((JButton)call(panel,"loadButton",new Class<?>[]{})).isEnabled());}catch(Exception e){throw new RuntimeException(e);}});require(enabled.get(),"Load did not re-enable after completion");
  System.out.println("SMOKE_GUI_LOAD=true; SOURCES=2; POINTS_PER_SOURCE=10; HTTP_REQUESTS=2; AUTH_ISOLATED=true; LOAD_REENABLED=true");
  javax.imageio.ImageIO.write(new Robot().createScreenCapture(new Rectangle(0,0,1280,900)),"png",Path.of(System.getProperty("smoke.out"),"synthetic-load.png").toFile());

  // Switch the real dialog to newest-N. All fixtures predate the displayed dates by years.
  server.removeContext("/api/v1/points");
  AtomicBoolean descendingSeen=new AtomicBoolean(true);
  server.createContext("/api/v1/points",exchange->{
   requests.incrementAndGet();String key=exchange.getRequestHeaders().getFirst("Authorization");auth.add(key);
   var parameters=new HashMap<String,String>();for(String item:exchange.getRequestURI().getRawQuery().split("&")){String[] pair=item.split("=",2);parameters.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}
   if(!"desc".equals(parameters.get("order"))||!"1".equals(parameters.get("page")))descendingSeen.set(false);
   int available="Bearer synthetic-B".equals(key)?2:7;long recorded=java.time.Instant.parse("2010-01-01T00:00:00Z").getEpochSecond();
   long from=Long.parseLong(parameters.get("start_at")),to=Long.parseLong(parameters.get("end_at"));
   StringBuilder json=new StringBuilder("[");int count=0;
   for(int i=available;i>0;i--)if(recorded+i>=from&&recorded+i<=to){if(count++>0)json.append(',');json.append("{\"id\":").append(i).append(",\"latitude\":0.001,\"longitude\":0.001,\"timestamp\":").append(recorded+i).append('}');}
   byte[] bytes=json.append(']').toString().getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().set("Content-Type","application/json");exchange.getResponseHeaders().set("X-Current-Page","1");exchange.getResponseHeaders().set("X-Total-Pages",count==0?"0":"1");exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
  });
  ClassLoader pluginLoader=plugin.getClass().getClassLoader();
  Class<?> optionsType=pluginLoader.loadClass("org.openstreetmap.josm.plugins.dawarich.LoadOptions");
  Class<?> modeType=pluginLoader.loadClass("org.openstreetmap.josm.plugins.dawarich.LoadOptions$Mode");
  Object recentMode=modeType.getField("MOST_RECENT").get(null);
  Object recentOptions=optionsType.getConstructor(modeType,int.class,int.class).newInstance(recentMode,3,100000);
  edt(()->{try{
   call(panel,"setLoadOptions",new Class<?>[]{optionsType},recentOptions);
   MainApplication.getMap().mapView.zoomTo(new Bounds(-0.01,-0.01,0.01,0.01));
   ((JTextField)call(panel,"startDateField",new Class<?>[]{})).setText("ignored-invalid-date");
   ((JTextField)call(panel,"endDateField",new Class<?>[]{})).setText("ignored-invalid-date");
   ((JButton)call(panel,"loadButton",new Class<?>[]{})).doClick();
  }catch(Exception e){throw new RuntimeException(e);}});
  deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while((boolean)call(task,"isLoading",new Class<?>[]{})&&System.nanoTime()<deadline)Thread.sleep(50);
  System.out.println("SMOKE_RECENT_DIAGNOSTIC="+((JLabel)call(panel,"statusLabel",new Class<?>[]{})).getText()+"; REQUESTS="+requests.get());
  Map<?,?> recentOwned=(Map<?,?>)call(task,"layersSnapshot",new Class<?>[]{});
  java.util.List<Integer> recentCounts=new ArrayList<>();java.util.Set<Long> firstSourceIds=new HashSet<>();
  for(Object layer:recentOwned.values()){
   int count=(int)call(layer,"pointCount",new Class<?>[]{});recentCounts.add(count);
   Object dataset=field(layer,"dataset");
   for(Object point:(java.util.List<?>)call(dataset,"points",new Class<?>[]{})){
    long timestamp=(long)call(point,"timestamp",new Class<?>[]{});require(timestamp>=1262304001L&&timestamp<=1262304007L,"Recent mode did not use original historical timestamps");
    if(count==3)firstSourceIds.add((long)call(point,"id",new Class<?>[]{}));
   }
  }
  Collections.sort(recentCounts);require(recentCounts.equals(java.util.List.of(2,3)),"Newest per-source counts incorrect");
  require(firstSourceIds.equals(java.util.Set.of(5L,6L,7L)),"Newest source IDs differ from independent oracle");
  require(requests.get()==4&&descendingSeen.get(),"Recent mode did not use descending page-1 requests");
  AtomicReference<String> recentStatus=new AtomicReference<>();edt(()->{try{recentStatus.set(((JLabel)call(panel,"statusLabel",new Class<?>[]{})).getText());}catch(Exception e){throw new RuntimeException(e);}});
  require(!recentStatus.get().contains("overlapping point IDs"),"Overlap warning incorrectly included replaced old source datasets");
  require(recentStatus.get().contains("reached requested 3")&&recentStatus.get().contains("fewer than requested 3"),"Recent count availability not reported");
  String expectedCoverageDate=java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.parse("2010-01-01T00:00:01Z"));
  require(recentStatus.get().contains(expectedCoverageDate),"Actual recorded coverage absent from status");
  System.out.println("SMOKE_RECENT_LOAD=true; REQUESTED_PER_SOURCE=3; RETURNED_COUNTS=3,2; ORIGINAL_NEWEST_IDS=true; ALL_HISTORY=true; DATES_IGNORED=true; COVERAGE_VISIBLE=true");
  javax.imageio.ImageIO.write(new Robot().createScreenCapture(new Rectangle(0,0,1280,900)),"png",Path.of(System.getProperty("smoke.out"),"synthetic-recent.png").toFile());

  if(Boolean.getBoolean("smoke.render")){
  int measuredPoints=Integer.getInteger("smoke.points",50000);
  // Measure actual MapView pan/zoom paints, not just the dot-painter seam.
  ClassLoader pcl=plugin.getClass().getClassLoader();
  Class<?> pointType=pcl.loadClass("org.openstreetmap.josm.plugins.dawarich.ReferencePoint");
  Class<?> datasetType=pcl.loadClass("org.openstreetmap.josm.plugins.dawarich.ReferenceDataset");
  Class<?> layerType=pcl.loadClass("org.openstreetmap.josm.plugins.dawarich.DawarichLayer");
  Object exampleLayer=owned.values().iterator().next();
  Object query=call(field(exampleLayer,"dataset"),"query",new Class<?>[]{});
  Constructor<?> pointCtor=pointType.getConstructor(long.class,double.class,double.class,long.class);
  Constructor<?> datasetCtor=datasetType.getConstructor(String.class,String.class,query.getClass(),java.util.List.class);
  Constructor<?> layerCtor=layerType.getConstructor(datasetType,String.class,Color.class);
  edt(()->{for(Object l:owned.values())MainApplication.getLayerManager().removeLayer((org.openstreetmap.josm.gui.layer.Layer)l);});
  for(int sourceCount:new int[]{1,2,4}){
   java.util.List<org.openstreetmap.josm.gui.layer.Layer> measured=new ArrayList<>();
   for(int source=0;source<sourceCount;source++){
    java.util.List<Object> points=new ArrayList<>();
    for(int i=0;i<measuredPoints/sourceCount;i++)points.add(pointCtor.newInstance((long)i+1,((i%200)-100)*0.00004,(((i/200)%200)-100)*0.00004,java.time.Instant.now().minusSeconds(60).getEpochSecond()));
    Object dataset=datasetCtor.newInstance("https://example.test","synthetic-render-"+source,query,points);
    measured.add((org.openstreetmap.josm.gui.layer.Layer)layerCtor.newInstance(dataset,"Synthetic renderer "+source,Color.getHSBColor(source/(float)sourceCount,0.8f,0.9f)));
   }
   edt(()->{for(var layer:measured)MainApplication.getLayerManager().addLayer(layer);});
   long[] elapsed=new long[21];
   edt(()->{var view=MainApplication.getMap().mapView; java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(view.getWidth(),view.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();graphics.setClip(0,0,image.getWidth(),image.getHeight());try{for(int frame=0;frame<elapsed.length;frame++){view.zoomTo(view.getCenter().add(1,0),view.getScale()*1.0002);for(var layer:measured)layer.invalidate();long started=System.nanoTime();view.paint(graphics);elapsed[frame]=System.nanoTime()-started;}}finally{graphics.dispose();}});
   long[] warmed=java.util.Arrays.copyOfRange(elapsed,1,elapsed.length);java.util.Arrays.sort(warmed);long sum=0;for(long v:warmed)sum+=v;
   System.out.println("SMOKE_MAPVIEW_POINTS="+measuredPoints+"; SOURCES="+sourceCount+"; WARM_FRAMES="+warmed.length+"; MEAN_MS="+(sum/(double)warmed.length/1e6)+"; P95_MS="+(warmed[18]/1e6)+"; MAX_MS="+(warmed[19]/1e6)+"; FIRST_MS="+(elapsed[0]/1e6));
   edt(()->{for(var layer:measured)MainApplication.getLayerManager().removeLayer(layer);});
  }

  }
  // Reference rendering must leave the empty editable dataset untouched.
  require(MainApplication.getLayerManager().getEditDataSet().allPrimitives().isEmpty(),"Reference loading modified OSM data");
  edt(()->{try{call(plugin,"destroy",new Class<?>[]{});}catch(Exception e){throw new RuntimeException(e);}});
  require(((Map<?,?>)call(task,"layersSnapshot",new Class<?>[]{})).isEmpty(),"Shutdown did not release reference layers");System.out.println("SMOKE_OSM_UNCHANGED=true; SHUTDOWN_RELEASED=true");server.stop(0);System.exit(0);
 }
}
