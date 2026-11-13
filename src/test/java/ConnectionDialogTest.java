import static org.junit.jupiter.api.Assertions.*;
import java.awt.Component;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.dawarich.*;

class ConnectionDialogTest {
    private static <T> T onEdt(java.util.concurrent.Callable<T> action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return action.call();
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { result.set(action.call()); } catch (Throwable t) { failure.set(t); }
        });
        if (failure.get() != null) {
            Throwable t = failure.get();
            if (t instanceof Exception e) throw e;
            if (t instanceof Error e) throw e;
            throw new AssertionError(t);
        }
        return result.get();
    }

    private static SourceRegistry registryWithThreeKeys() {
        SourceRegistry r = new SourceRegistry();
        for (int i = 1; i <= 3; i++) {
            SourceSettings s = r.add("Source " + i);
            r.setKey(s.id, "secret-key-" + i);
        }
        return r;
    }

    private static JTextField serverField(LoadReferenceDialog dialog) throws Exception {
        Field f = LoadReferenceDialog.class.getDeclaredField("server");
        f.setAccessible(true);
        return (JTextField) f.get(dialog);
    }

    @Test void emptyKeyCellsRenderAsBooleanWithoutNullFailure() throws Exception {
        onEdt(() -> {
            SourceRegistry r = new SourceRegistry();
            for (int i = 1; i <= 3; i++) r.add("Source " + i);
            LoadReferenceDialog d = new LoadReferenceDialog(r);
            assertEquals(Boolean.class, d.sourceTable().getColumnClass(2));
            for (int row = 0; row < 3; row++) assertEquals(Boolean.FALSE, d.sourceTable().getValueAt(row, 2));
            return null;
        });
    }

    @Test void populatedKeysAreMaskedInTableValuesAndRenderer() throws Exception {
        onEdt(() -> {
            SourceRegistry r = registryWithThreeKeys();
            LoadReferenceDialog d = new LoadReferenceDialog(r);
            JTable t = d.sourceTable();
            for (int row = 0; row < 3; row++) {
                assertEquals(Boolean.TRUE, t.getValueAt(row, 2));
                Component c = t.prepareRenderer(t.getCellRenderer(row, 2), row, 2);
                assertInstanceOf(JLabel.class, c);
                assertFalse(((JLabel)c).getText().contains("secret-key"));
            }
            return null;
        });
    }

    @Test void editingKeyUsesBlankPasswordFieldAndChangesOnlySelectedSource() throws Exception {
        onEdt(() -> {
            SourceRegistry r = registryWithThreeKeys();
            LoadReferenceDialog d = new LoadReferenceDialog(r);
            JTable t = d.sourceTable();
            assertTrue(t.editCellAt(0, 2));
            assertInstanceOf(JPasswordField.class, t.getEditorComponent());
            JPasswordField editor = (JPasswordField)t.getEditorComponent();
            assertEquals(0, editor.getPassword().length);
            editor.setText("fresh-key");
            assertTrue(t.getCellEditor().stopCellEditing());
            assertEquals("fresh-key", r.session(r.rows().get(0).id).snapshot().apiKey());
            assertEquals("secret-key-2", r.session(r.rows().get(1).id).snapshot().apiKey());
            assertEquals("secret-key-3", r.session(r.rows().get(2).id).snapshot().apiKey());
            return null;
        });
    }

    @Test void cancellingKeyEditRetainsExistingCredential() throws Exception {
        onEdt(() -> {
            SourceRegistry r = registryWithThreeKeys();
            LoadReferenceDialog d = new LoadReferenceDialog(r);
            JTable t = d.sourceTable();
            assertTrue(t.editCellAt(1, 2));
            ((JPasswordField)t.getEditorComponent()).setText("discard-this");
            t.getCellEditor().cancelCellEditing();
            assertEquals("secret-key-2", r.session(r.rows().get(1).id).snapshot().apiKey());
            assertEquals("secret-key-1", r.session(r.rows().get(0).id).snapshot().apiKey());
            assertEquals("secret-key-3", r.session(r.rows().get(2).id).snapshot().apiKey());
            return null;
        });
    }

    @Test void endpointChangeInvalidatesKeysAndAdvancesGeneration() throws Exception {
        onEdt(() -> {
            SourceRegistry r = registryWithThreeKeys();
            r.setEndpoint("https://old.example", false);
            for (SourceSettings s : r.rows()) r.setKey(s.id, "key-" + s.label);
            long before = r.endpointGeneration();
            LoadReferenceDialog d = new LoadReferenceDialog(r);
            serverField(d).setText("https://new.example");
            assertThrows(IllegalArgumentException.class, d::validateAndCommit);
            assertEquals("https://new.example", r.endpoint());
            assertTrue(r.endpointGeneration() > before);
            for (SourceSettings s : r.rows()) assertNull(r.session(s.id).snapshot().apiKey());
            return null;
        });
    }

    @Test void loadButtonRequiresHandlerAndDeliversValidatedRequestOnce() throws Exception {
        onEdt(() -> {
            SourceRegistry r = registryWithThreeKeys();
            r.setEndpoint("https://api.example/base", false);
            for (SourceSettings s : r.rows()) r.setKey(s.id, "key-" + s.label);
            LoadReferenceDialog d = new LoadReferenceDialog(r);
            assertFalse(d.loadButton().isEnabled());
            LocalDate start = LocalDate.now().minusDays(9);
            LocalDate end = LocalDate.now().minusDays(3);
            d.startDateField().setText(start.toString());
            d.endDateField().setText(end.toString());
            AtomicReference<LoadReferenceDialog.LoadRequest> delivered = new AtomicReference<>();
            int[] calls = {0};
            d.setLoadHandler(request -> { calls[0]++; delivered.set(request); });
            assertTrue(d.loadButton().isEnabled());
            d.loadButton().doClick();
            assertEquals(1, calls[0]);
            assertEquals(start, delivered.get().startDate);
            assertEquals(end, delivered.get().endDate);
            assertEquals(ZoneId.systemDefault(), delivered.get().timezone);
            assertEquals("https://api.example/base", delivered.get().settings.baseUrl());
            return null;
        });
    }

    @Test void clearingSecretEditorErasesTransientTextButPreservesSessionKey() throws Exception {
        onEdt(() -> {
            SourceRegistry r = registryWithThreeKeys();
            LoadReferenceDialog d = new LoadReferenceDialog(r);
            JTable t = d.sourceTable();
            assertTrue(t.editCellAt(0, 2));
            JPasswordField editor = (JPasswordField)t.getEditorComponent();
            editor.setText("transient-secret");
            d.clearSecretEditors();
            assertFalse(t.isEditing());
            assertEquals(0, editor.getPassword().length);
            assertEquals("secret-key-1", r.session(r.rows().get(0).id).snapshot().apiKey());
            return null;
        });
    }
}
