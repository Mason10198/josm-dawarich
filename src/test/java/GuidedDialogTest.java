package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;

class GuidedDialogTest {
 @BeforeAll static void prefs(){Config.setPreferencesInstance(new MemoryPreferences());}
 private static <T>T edt(java.util.concurrent.Callable<T> c)throws Exception{AtomicReference<T> r=new AtomicReference<>();AtomicReference<Throwable> x=new AtomicReference<>();SwingUtilities.invokeAndWait(()->{try{r.set(c.call());}catch(Throwable e){x.set(e);}});if(x.get()!=null)throw new AssertionError(x.get());return r.get();}
 @Test void directKeyLoadBindsCurrentSelectedSourceAndKeepsKeyOutOfPreferences()throws Exception{edt(()->{var prefs=Config.getPref();var r=new SourceRegistry();var a=r.add("Alice");var b=r.add("Bob");r.updateSettings(new SourceSettings(a.id,a.label,a.color,false));var d=new LoadReferenceDialog(r);AtomicReference<LoadReferenceDialog.LoadRequest> req=new AtomicReference<>();d.setLoadHandler(req::set);assertEquals(0,d.sourceTable().getSelectedRow());d.sourceTable().setRowSelectionInterval(1,1);d.serverField().setText("https://example.test");d.apiKeyField().setText("secret-test-token");d.loadButton().doClick();assertNotNull(req.get());assertEquals("secret-test-token",r.session(b.id).snapshot().apiKey());assertTrue(a.id!=b.id);assertFalse(prefs.toString().contains("secret-test-token"));assertEquals(0,d.apiKeyField().getPassword().length);return null;});}
 @Test void sourceAndServerChangesClearPendingPassword()throws Exception{edt(()->{var r=new SourceRegistry();r.add("One");r.add("Two");var d=new LoadReferenceDialog(r);d.apiKeyField().setText("pending");d.sourceTable().setRowSelectionInterval(1,1);assertEquals(0,d.apiKeyField().getPassword().length);d.apiKeyField().setText("pending2");d.serverField().setText("https://other.test");assertEquals(0,d.apiKeyField().getPassword().length);return null;});}
 @Test void progressAndValidationAreActionableAndSafe()throws Exception{edt(()->{var r=new SourceRegistry();var s=r.add("Named person");var d=new LoadReferenceDialog(r);d.setLoadHandler(q->{});d.apiKeyField().setText("k");d.serverField().setText("https://example.test");d.startDateField().setText("bad-date");d.loadButton().doClick();assertTrue(d.statusLabel().getText().contains("YYYY-MM-DD"));assertFalse(d.statusLabel().getText().contains("Exception"));d.setLoading(true);assertTrue(d.cancelButton().isEnabled());d.updateProgress(new LoadProgress(s.id,1,1,3,2048,30,100,5000));assertTrue(d.progressLabel().getText().contains("Named person"));assertTrue(d.progressLabel().getText().contains("3 requests"));d.setLoading(false);assertFalse(d.cancelButton().isEnabled());return null;});}
 @Test void keyEntryCommitsToSelectedPersonOnlyAndCloseClears()throws Exception{edt(()->{var r=new SourceRegistry();var s=r.add("Person");var d=new LoadReferenceDialog(r);d.serverField().setText("https://example.test");d.apiKeyField().setText("direct-key");d.applyKeyButton().doClick();assertEquals("direct-key",r.session(s.id).snapshot().apiKey());d.apiKeyField().setText("transient-direct-password");d.closeButton().doClick();assertEquals(0,d.apiKeyField().getPassword().length);assertEquals("direct-key",r.session(s.id).snapshot().apiKey());return null;});}
}
