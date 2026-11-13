package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.time.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class DialogLayoutTest {
    private static SourceRegistry registry(int count) {
        SourceRegistry r = new SourceRegistry();
        for (int i = 0; i < count; i++) r.add("Source " + (i + 1));
        return r;
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layoutTree(nested);
        }
    }

    @Test void allDefaultDialogButtonsRemainVisibleAtActionSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LoadReferenceDialog dialog = new LoadReferenceDialog(registry(2), LocalDate.now().minusDays(1), LocalDate.now());
            dialog.setSize(620, 430);
            layoutTree(dialog);
            Component buttons = dialog.getComponent(2);
            assertInstanceOf(JPanel.class, buttons);
            for (Component child : ((JPanel) buttons).getComponents()) {
                Rectangle bounds = SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), dialog);
                assertTrue(bounds.width > 0 && bounds.height > 0, child + " has empty geometry");
                assertTrue(new Rectangle(0, 0, dialog.getWidth(), dialog.getHeight()).contains(bounds), child + " is clipped");
            }
            assertEquals("Load this view", dialog.loadButton().getText());
        });
    }

    @Test void longMultiSourceStatusDoesNotExpandFormOrHideButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SourceRegistry r = registry(6);
            LoadReferenceDialog dialog = new LoadReferenceDialog(r, LocalDate.now().minusDays(1), LocalDate.now());
            StringBuilder html = new StringBuilder("<html>Complete — 120 points");
            for (int i = 0; i < 6; i++) html.append(" | Source with a long descriptive name ").append(i).append(": COMPLETE (20 points)");
            dialog.statusLabel().setText(html.append("</html>").toString());
            dialog.setSize(620, 430);
            layoutTree(dialog);
            Component form = dialog.getComponent(0);
            Component table = dialog.getComponent(1);
            Component buttons = dialog.getComponent(2);
            assertTrue(form.getHeight() < dialog.getHeight() / 2, "status must not stretch the form");
            assertTrue(table.getHeight() > 0, "source table must retain visible space");
            for (Component child : ((JPanel) buttons).getComponents()) {
                Rectangle bounds = SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), dialog);
                assertTrue(new Rectangle(0, 0, dialog.getWidth(), dialog.getHeight()).contains(bounds), child + " is clipped");
            }
        });
    }
}
