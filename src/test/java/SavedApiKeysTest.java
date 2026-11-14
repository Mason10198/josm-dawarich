package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.time.LocalDate;
import java.util.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.*;
import org.openstreetmap.josm.io.auth.*;
import org.openstreetmap.josm.spi.preferences.*;

class SavedApiKeysTest {
 MemoryPreferences prefs;SavedApiKeys store;
 @BeforeEach void setup(){prefs=new MemoryPreferences();Config.setPreferencesInstance(prefs);Config.setUrlsProvider(org.openstreetmap.josm.data.preferences.JosmUrls.getInstance());store=new SavedApiKeys(new JosmPreferencesCredentialAgent(),prefs);}
 SourceRegistry registry(){SourceRegistry r=new SourceRegistry(store);r.setEndpoint("https://gps.example.org/prefix",false);return r;}
 void save(SourceRegistry r){ConnectionPreferences.save(prefs,r,LocalDate.now().minusDays(1),LocalDate.now());}
 @Test void optInRoundtripAndReplacementSurviveRamCleanup(){
  var r=registry();var a=r.add("One");var b=r.add("Two");r.setKey(a.id,"synthetic-first");r.setKey(b.id,"synthetic-session-only");assertNull(store.load(r.endpoint(),a.id));
  r.setRemembered(a.id,true);save(r);assertTrue(r.isRemembered(a.id));r.clearKeys();var next=ConnectionPreferences.load(prefs,store).registry();
  assertEquals("synthetic-first",next.session(a.id).snapshot().apiKey());assertTrue(next.isRemembered(a.id));assertNull(next.session(b.id).snapshot().apiKey());assertFalse(next.isRemembered(b.id));
  next.setKey(a.id,"synthetic-replacement");save(next);next.clearKeys();var restart=ConnectionPreferences.load(prefs,store).registry();assertEquals("synthetic-replacement",restart.session(a.id).snapshot().apiKey());
  String metadata=prefs.getListOfMaps("dawarich.sources",new ArrayList<>()).toString();assertFalse(metadata.contains("synthetic-"));assertTrue(metadata.contains("rememberKey=true"));
  String key="server.password."+SavedApiKeys.scope(restart.endpoint(),a.id);assertTrue(prefs.getSensitive().contains(key));assertEquals("synthetic-replacement",prefs.get(key,null));
 }
 @Test void forgetRemoveAndServerChangeDeleteStoredKeys(){
  var r=registry();var a=r.add("One");r.setKey(a.id,"synthetic-first");r.setRemembered(a.id,true);String endpoint=r.endpoint();r.setRemembered(a.id,false);assertNull(store.load(endpoint,a.id));assertEquals("synthetic-first",r.session(a.id).snapshot().apiKey());
  r.setRemembered(a.id,true);r.setEndpoint("https://gps.example.org/another-prefix",false);assertNull(store.load(endpoint,a.id));assertFalse(r.isRemembered(a.id));assertNull(r.session(a.id).snapshot().apiKey());
  r.setKey(a.id,"synthetic-other");r.setRemembered(a.id,true);String destination=r.endpoint();r.remove(a.id);assertNull(store.load(destination,a.id));
 }
 @Test void scopeIsVersionIndependentAndSeparatesServerAndSource(){
  String base=SavedApiKeys.scope("https://gps.example.org/prefix","a");assertEquals(base,SavedApiKeys.scope("https://gps.example.org/prefix/","a"));assertTrue(base.endsWith(".invalid"));
  for(String server:List.of("https://gps.example.org/other","https://gps.example.org:8443/prefix","http://gps.example.org/prefix","https://other.example.org/prefix"))assertNotEquals(base,SavedApiKeys.scope(server,"a"));assertNotEquals(base,SavedApiKeys.scope("https://gps.example.org/prefix","b"));
 }
 @Test void dialogOptInBindsOnlySelectedSourceAndUncheckForgets()throws Exception{
  var r=registry();var a=r.add("One");var b=r.add("Two");r.setEnabled(b.id,false);var panel=new LoadReferenceDialog(r);panel.setLoadHandler(q->{});
  SwingUtilities.invokeAndWait(()->{assertFalse(panel.rememberKeyCheckBox().isSelected());panel.rememberKeyCheckBox().doClick();panel.apiKeyField().setText("synthetic-ui");panel.loadButton().doClick();assertTrue(r.isRemembered(a.id));assertEquals(0,panel.apiKeyField().getPassword().length);assertNull(r.session(b.id).snapshot().apiKey());panel.rememberKeyCheckBox().doClick();assertFalse(r.isRemembered(a.id));});assertNull(store.load(r.endpoint(),a.id));
 }
 @Test void storageFailureDoesNotClaimRememberedOrExposeBackendMessage()throws Exception{
  JosmPreferencesCredentialAgent broken=new JosmPreferencesCredentialAgent(){@Override public void store(Authenticator.RequestorType type,String host,PasswordAuthentication value)throws CredentialsAgentException{throw new CredentialsAgentException("synthetic-private-backend-secret");}};
  var r=new SourceRegistry(new SavedApiKeys(broken,prefs));r.setEndpoint("https://gps.example.org",false);var a=r.add("One");var panel=new LoadReferenceDialog(r);panel.setLoadHandler(q->{});
  SwingUtilities.invokeAndWait(()->{panel.rememberKeyCheckBox().doClick();panel.apiKeyField().setText("synthetic-dont-echo");panel.loadButton().doClick();assertFalse(r.isRemembered(a.id));assertFalse(panel.rememberKeyCheckBox().isSelected());assertTrue(panel.statusLabel().getText().contains("credential store"));assertFalse(panel.statusLabel().getText().contains("synthetic-"));});
 }
}
