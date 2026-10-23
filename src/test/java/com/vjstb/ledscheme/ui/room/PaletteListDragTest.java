package com.vjstb.ledscheme.ui.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Карточку библиотеки ведут над окном, а сам список при этом стоит на месте: выделение не переезжает по строкам и список
 * не прокручивается (встроенная реакция JList на перетаскивание делала и то и другое, из-за чего ведение карточки из
 * библиотеки подвисало). Пропускается без дисплея.
 */
class PaletteListDragTest {

    private static JList<?> findList(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JList<?> l) {
                return l;
            }
            if (c instanceof Container sub) {
                JList<?> f = findList(sub);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static void send(Component target, int id, Point p) {
        int mods = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        target.dispatchEvent(new MouseEvent(target, id, System.currentTimeMillis(), mods, p.x, p.y, 1, false, MouseEvent.BUTTON1));
    }

    @Test
    void draggingACardDoesNotMoveTheListSelectionOrScrollIt(@TempDir Path dir) throws Exception {
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SwingUtilities.invokeAndWait(() -> {
            AppModel m = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
            m.selectProject(m.addProject("P"));
            m.selectScene(m.addScene("S"));
            var layout = PlannerSample.build(m).layout();
            PlannerEditorPanel panel = new PlannerEditorPanel(m, RoomLibraryActions.NONE);
            JFrame f = new JFrame();
            f.add(panel);
            f.setSize(1300, 800);
            f.addNotify();
            f.validate();
            panel.showLayout(layout);
            f.validate();
            JList<?> list = findList(panel);
            assertTrue(list.getModel().getSize() > 10, "список длиннее экрана — есть что прокручивать");
            int row = 3;
            while (!(list.getModel().getElementAt(row) instanceof com.vjstb.ledscheme.service.room.RoomItemSpec)) {
                row++;
            }
            Rectangle cell = list.getCellBounds(row, row);
            Point from = new Point(cell.x + 40, cell.y + cell.height / 2);
            int topBefore = list.getVisibleRect().y;
            send(list, MouseEvent.MOUSE_PRESSED, from);
            assertEquals(row, list.getSelectedIndex());
            for (int i = 1; i <= 30; i++) {
                send(list, MouseEvent.MOUSE_DRAGGED, new Point(from.x + i * 5, from.y + i * 40));
            }
            assertEquals(row, list.getSelectedIndex(), "выделение осталось на нажатой карточке");
            assertEquals(topBefore, list.getVisibleRect().y, "список не прокрутился");
            send(list, MouseEvent.MOUSE_RELEASED, new Point(from.x + 150, from.y + 1200));
            assertEquals(topBefore, list.getVisibleRect().y);
            f.dispose();
        });
    }
}
