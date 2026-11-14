package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class LoadReferenceActionTest {
    @org.junit.jupiter.api.BeforeAll static void initializePreferences(){
        org.openstreetmap.josm.spi.preferences.Config.setPreferencesInstance(new org.openstreetmap.josm.spi.preferences.MemoryPreferences());
    }
    private static SourceRegistry registry(){SourceRegistry r=new SourceRegistry();r.add("test");return r;}
    private static LoadReferenceDialog.LoadRequest request(){return new LoadReferenceDialog.LoadRequest(null,LocalDate.now().minusDays(1),LocalDate.now(),ZoneId.of("UTC"));}
    private static LoadReferenceAction action(AtomicReference<LoadReferenceAction.Viewport> view, AtomicInteger starts, AtomicBoolean loading){
        var panel=new LoadReferenceDialog(registry());
        return new LoadReferenceAction(view::get,q->{starts.incrementAndGet();return false;},q->{starts.incrementAndGet();return null;},()->{},loading::get,panel);
    }
    @Test void capturesViewportOnceAndUsesOrderedBounds(){var v=new AtomicReference<>(new LoadReferenceAction.Viewport(20,10,5,30,100,100));var captured=new AtomicReference<QueryBounds>();var n=new AtomicInteger();var a=new LoadReferenceAction(v::get,q->{n.incrementAndGet();return true;},q->{captured.set(q);return null;},()->{},()->false,new LoadReferenceDialog(registry()));a.load(request());v.set(new LoadReferenceAction.Viewport(0,0,1,1,100,100));assertEquals(1,n.get());assertEquals(5,captured.get().minLat());assertEquals(20,captured.get().maxLat());assertEquals(10,captured.get().minLon());assertEquals(30,captured.get().maxLon());}
    @Test void decliningBroadAreaDoesNotStart(){var calls=new AtomicInteger();var a=new LoadReferenceAction(()->new LoadReferenceAction.Viewport(80,-80,-80,80,500,500),q->false,q->{calls.incrementAndGet();return null;},()->{},()->false,new LoadReferenceDialog(registry()));a.load(request());assertEquals(0,calls.get());assertTrue(a.panel().statusLabel().getText().contains("cancelled"));}
    @Test void rejectsDatelineAndMissingViewport(){var calls=new AtomicInteger();var v=new AtomicReference<>(new LoadReferenceAction.Viewport(10,170,-10,-170,100,100));var a=new LoadReferenceAction(v::get,q->{fail("invalid bounds must not confirm");return false;},q->{calls.incrementAndGet();return null;},()->{},()->false,new LoadReferenceDialog(registry()));a.load(request());assertEquals(0,calls.get());v.set(null);a.load(request());assertEquals(0,calls.get());}
    @Test void activeBatchBlocksSecondRequestAndCancelRuns(){var calls=new AtomicInteger();var cancels=new AtomicInteger();var loading=new AtomicBoolean(true);var panel=new LoadReferenceDialog(registry());var a=new LoadReferenceAction(()->new LoadReferenceAction.Viewport(10,10,-10,20,100,100),q->true,q->{calls.incrementAndGet();return null;},cancels::incrementAndGet,loading::get,panel);a.load(request());assertEquals(0,calls.get());assertFalse(panel.loadButton().isEnabled());panel.setCancelHandler(cancels::incrementAndGet);for(var c:panel.getComponents())if(c instanceof javax.swing.JPanel p)for(var b:p.getComponents())if(b instanceof javax.swing.JButton button&&button.getText().equals("Cancel"))button.doClick();assertEquals(1,cancels.get());}
    @Test void statusEscapesUntrustedSourceLabelAndOnlyShowsSafeSummary(){var r=registry();var row=r.rows().get(0);r.updateSettings(new SourceSettings(row.id,"<img onerror=boom>",row.color,row.enabled));var p=new LoadReferenceDialog(r);var a=new LoadReferenceAction(()->null,q->true,q->null,()->{},()->false,p);a.showResult(new LoadReferenceTask.BatchResult(LoadReferenceTask.Status.COMPLETE,java.util.List.of(new LoadReferenceTask.SourceResult(row.id,LoadReferenceTask.Status.COMPLETE,"secret raw detail",true,2)),2,java.util.Set.of("overlap-id"),5));assertTrue(p.statusLabel().getText().contains("&lt;img"));assertFalse(p.statusLabel().getText().contains(row.id));assertFalse(p.statusLabel().getText().contains("secret raw detail"));assertFalse(p.statusLabel().getText().contains("overlap-id"));assertTrue(p.statusLabel().getText().contains("old layer retained"));}
    @Test void failedSourcesShowAllowlistedReasonWithoutLeakingRawDetails(){
        var r=registry();var row=r.rows().get(0);var p=new LoadReferenceDialog(r);
        var a=new LoadReferenceAction(()->null,q->true,q->null,()->{},()->false,p);
        for(String message:java.util.List.of("Request timed out","Source authorization failed","Service connection failed","Invalid point data","Source exceeds its fair point capacity")){
            a.showResult(new LoadReferenceTask.BatchResult(LoadReferenceTask.Status.FAILED,java.util.List.of(new LoadReferenceTask.SourceResult(row.id,LoadReferenceTask.Status.FAILED,message,false,0)),0,java.util.Set.of(),50000));
            assertTrue(p.statusLabel().getText().contains(message),message);
            assertTrue(p.loadButton().isEnabled());
        }
        String unsafe="<img src=https://private.example/trace?key=DO_NOT_SHOW>";
        a.showResult(new LoadReferenceTask.BatchResult(LoadReferenceTask.Status.FAILED,java.util.List.of(new LoadReferenceTask.SourceResult(row.id,LoadReferenceTask.Status.FAILED,unsafe,true,12)),12,java.util.Set.of(),50000));
        assertFalse(p.statusLabel().getText().contains("DO_NOT_SHOW"));
        assertFalse(p.statusLabel().getText().contains("private.example"));
        assertTrue(p.statusLabel().getText().contains("Source load failed"));
        assertTrue(p.statusLabel().getText().contains("old layer retained"));
    }
    @Test void overlapWarningShowsOnlySafeCountAndIsOmittedWhenEmpty(){var r=registry();var p=new LoadReferenceDialog(r);var a=new LoadReferenceAction(()->null,q->true,q->null,()->{},()->false,p);var row=r.rows().get(0);var ids=java.util.Set.of("private-point-id-one","private-point-id-two");a.showResult(new LoadReferenceTask.BatchResult(LoadReferenceTask.Status.COMPLETE,java.util.List.of(new LoadReferenceTask.SourceResult(row.id,LoadReferenceTask.Status.COMPLETE,"ignored",false,2)),2,ids,5));String html=p.statusLabel().getText();assertTrue(html.contains("2 overlapping"));assertTrue(html.contains("keys may refer to the same account"));assertTrue(html.contains("Layers kept separate"));assertFalse(html.contains("private-point-id-one"));assertFalse(html.contains("private-point-id-two"));assertFalse(html.contains(row.id));a.showResult(new LoadReferenceTask.BatchResult(LoadReferenceTask.Status.COMPLETE,java.util.List.of(),0,java.util.Set.of(),5));assertFalse(p.statusLabel().getText().contains("overlapping"));assertFalse(p.statusLabel().getText().contains("same account"));}
}
