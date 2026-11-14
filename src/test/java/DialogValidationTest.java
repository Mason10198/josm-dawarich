import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.dawarich.*;

class DialogValidationTest {
    private static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }
    private static SourceRegistry readyRegistry() {
        SourceRegistry r=new SourceRegistry();r.setEndpoint("https://old.example",false);
        for(int i=0;i<2;i++){SourceSettings s=r.add("Source "+i);r.setKey(s.id,"old-key-"+i);}return r;
    }
    private static void dates(LoadReferenceDialog d){d.startDateField().setText(LocalDate.now().minusDays(2).toString());d.endDateField().setText(LocalDate.now().minusDays(1).toString());}

    @Test void invalidDateShowsSanitizedStatusWithoutCallbackOrThrow() throws Exception {edt(()->{
        SourceRegistry r=readyRegistry();LoadReferenceDialog d=new LoadReferenceDialog(r);dates(d);d.startDateField().setText("not-a-date");AtomicInteger calls=new AtomicInteger();d.setLoadHandler(x->calls.incrementAndGet());
        assertDoesNotThrow(()->d.loadButton().doClick());assertEquals(0,calls.get());assertEquals("Enter dates in YYYY-MM-DD format.",d.statusLabel().getText());
    });}
    @Test void destinationKeyEditInvalidatesAllOtherKeysBeforeLoad() throws Exception {edt(()->{
        SourceRegistry r=readyRegistry();LoadReferenceDialog d=new LoadReferenceDialog(r);dates(d);d.serverField().setText("https://new.example");
        JTable t=d.sourceTable();assertTrue(t.editCellAt(0,2));((JPasswordField)t.getEditorComponent()).setText("fresh-key-0");assertTrue(t.getCellEditor().stopCellEditing());
        assertTrue(t.editCellAt(1,2));((JPasswordField)t.getEditorComponent()).setText("fresh-key-1");assertTrue(t.getCellEditor().stopCellEditing());
        AtomicInteger calls=new AtomicInteger();d.setLoadHandler(x->calls.incrementAndGet());d.loadButton().doClick();assertEquals(1,calls.get());
        assertEquals("fresh-key-0",r.session(r.rows().get(0).id).snapshot().apiKey());assertEquals("fresh-key-1",r.session(r.rows().get(1).id).snapshot().apiKey());
    });}
    @Test void changedDestinationWithoutFreshKeyCannotLoad() throws Exception {edt(()->{
        SourceRegistry r=readyRegistry();LoadReferenceDialog d=new LoadReferenceDialog(r);dates(d);d.serverField().setText("https://new.example");AtomicInteger calls=new AtomicInteger();d.setLoadHandler(x->calls.incrementAndGet());d.loadButton().doClick();
        assertEquals(0,calls.get());for(SourceSettings s:r.rows())assertNull(r.session(s.id).snapshot().apiKey());assertTrue(d.statusLabel().getText().contains("API key"));
    });}
    @Test void constructorDatesAndCancelClearEditorButKeepSessionKey() throws Exception {edt(()->{
        SourceRegistry r=readyRegistry();r.setEndpoint("https://old.example",false);LocalDate a=LocalDate.of(2024,2,3),b=LocalDate.of(2024,2,4);LoadReferenceDialog d=new LoadReferenceDialog(r,a,b);assertEquals(a.toString(),d.startDateField().getText());assertEquals(b.toString(),d.endDateField().getText());
        JTable t=d.sourceTable();assertTrue(t.editCellAt(0,2));JPasswordField p=(JPasswordField)t.getEditorComponent();p.setText("transient");AtomicInteger calls=new AtomicInteger();d.setCancelHandler(calls::incrementAndGet);
        d.setLoading(true);assertTrue(d.cancelButton().isEnabled());JButton cancel=d.cancelButton();cancel.doClick();assertEquals(1,calls.get());assertFalse(t.isEditing());assertEquals(0,p.getPassword().length);assertEquals("old-key-0",r.session(r.rows().get(0).id).snapshot().apiKey());
    });}
}
