import java.awt.*;
import java.nio.file.*;
import java.lang.reflect.*;
import javax.swing.*;
import org.openstreetmap.josm.plugins.PluginHandler;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.spi.preferences.Config;

/** Isolated dummy-key two-process persistence acceptance; never contacts a server. */
public class SavedKeyRestartSmoke {
 static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static Object call(Object o,String n,Object...a)throws Exception{for(Method m:o.getClass().getDeclaredMethods())if(m.getName().equals(n)&&m.getParameterCount()==a.length){m.setAccessible(true);return m.invoke(o,a);}throw new NoSuchMethodException(n);}
 static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
 static void warning(Component c){if(c instanceof AbstractButton b&&b.getText().equals("Continue"))b.doClick();if(c instanceof Container p)for(Component x:p.getComponents())warning(x);}
 public static void main(String[] args){Thread driver=new Thread(()->{try{
  Thread.sleep(6000);SwingUtilities.invokeAndWait(()->{for(Window w:Window.getWindows())if(w.isShowing()&&w instanceof Dialog d&&d.getTitle().equals("JOSM may work improperly"))warning(w);});
  Object plugin=null;for(int i=0;i<20&&plugin==null;i++){Thread.sleep(250);plugin=PluginHandler.getPlugin("dawarich");}check(plugin!=null,"Plugin absent");Object p=plugin,r=field(p,"registry"),action=field(p,"action"),panel=call(action,"panel"),task=field(p,"task");
  if(System.getProperty("smoke.stage").equals("seed")){
   SwingUtilities.invokeAndWait(()->{try{
    ((JTextField)call(panel,"serverField")).setText("https://gps.example.org/acceptance-prefix");((JPasswordField)call(panel,"apiKeyField")).setText("synthetic-remembered-dummy");((JCheckBox)call(panel,"rememberKeyCheckBox")).doClick();((JButton)call(panel,"applyKeyButton")).doClick();
    var rows=(java.util.List<?>)call(r,"rows");Object row=rows.get(0);String id=(String)row.getClass().getField("id").get(row);check((boolean)call(r,"isRemembered",id),"Remember not committed");Config.getPref().put("dawarich.acceptance.id",id);
    check(Config.getPref().getListOfMaps("dawarich.sources",new java.util.ArrayList<>()).stream().anyMatch(m->id.equals(m.get("id"))&&"true".equals(m.get("rememberKey"))),"Source metadata not saved immediately");check(((JPasswordField)call(panel,"apiKeyField")).getPassword().length==0,"Editor retained key");
    Config.getPref().put("server.password.gps.example.org","synthetic-unrelated-basic");call(p,"destroy");check(call(call(call(r,"session",id),"snapshot"),"apiKey")==null,"RAM key survived shutdown");Preferences.main().save();
   }catch(Exception e){throw new RuntimeException(e);}});System.out.println("SMOKE_REMEMBER_SEED=true; METADATA_IMMEDIATE=true; EDITOR_EMPTY=true; SHUTDOWN_RAM_CLEARED=true");
  }else{
   String id=Config.getPref().get("dawarich.acceptance.id",null);check(id!=null,"Acceptance metadata absent");check((boolean)call(r,"isRemembered",id),"Remember choice not restored");check("synthetic-remembered-dummy".equals(call(call(call(r,"session",id),"snapshot"),"apiKey")),"Key not restored after process restart");
   check("synthetic-unrelated-basic".equals(Config.getPref().get("server.password.gps.example.org",null)),"Unrelated basic credential modified");check(((JPasswordField)call(panel,"apiKeyField")).getPassword().length==0,"Restored key was put in password editor");check(!((boolean)call(task,"isLoading")),"Startup downloaded automatically");check(Config.getPref().getSensitive().stream().anyMatch(k->k.startsWith("server.password.dawarich-")&&k.endsWith(".invalid")),"Remembered entry not marked sensitive");
   SwingUtilities.invokeAndWait(()->{try{call(p,"destroy");Preferences.main().save();}catch(Exception e){throw new RuntimeException(e);}});System.out.println("SMOKE_REMEMBER_RESTART=true; KEY_RESTORED=true; NO_AUTO_DOWNLOAD=true; UNRELATED_CREDENTIAL_UNCHANGED=true; SENSITIVE_MARKED=true");
  }
  System.exit(0);
 }catch(Throwable e){e.printStackTrace();System.exit(2);}},"saved-key-restart");driver.setDaemon(true);driver.start();MainApplication.main(new String[]{"--offline=ALL"});}
}
