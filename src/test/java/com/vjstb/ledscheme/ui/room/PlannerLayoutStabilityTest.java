package com.vjstb.ledscheme.ui.room;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomSelection;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Dimension;
import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Холст плана не должен менять размер от того, что выбрано и какая подсказка показана: иначе при выборе объекта,
 * перетаскивании из библиотеки или смене режима весь план «подпрыгивает» (пересчитывается масштаб). Размер холста
 * снимается при разных выделениях и режимах — он обязан быть один и тот же. Пропускается без дисплея.
 */
class PlannerLayoutStabilityTest {

    @Test
    void planCanvasKeepsItsSizeWhateverIsSelected(@TempDir Path dir) throws Exception {
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SwingUtilities.invokeAndWait(() -> {
            try {
                AppModel m = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
                m.selectProject(m.addProject("P"));
                m.selectScene(m.addScene("S"));
                PlannerSample.Built built = PlannerSample.build(m);
                ControlRoomLayout l = built.layout();
                PlannerEditorPanel panel = new PlannerEditorPanel(m, RoomLibraryActions.NONE);
                JFrame f = new JFrame();
                f.add(panel);
                f.setSize(1380, 860);
                f.addNotify();
                f.validate();
                panel.showLayout(l);
                clickTab(panel, "План");
                settle(f);
                PlanPanel plan = find(panel, PlanPanel.class);

                Map<String, Dimension> sizes = new LinkedHashMap<>();
                sizes.put("ничего не выбрано", plan.getSize());
                panel.session().select(RoomSelection.tower(built.towerA()));
                settle(f);
                sizes.put("башня", plan.getSize());
                panel.session().select(RoomSelection.furniture(l.getFurniture().get(0).getId()));
                settle(f);
                sizes.put("мебель или станок", plan.getSize());
                panel.session().select(RoomSelection.loose(l.getLoose().get(0).getId()));
                settle(f);
                sizes.put("мелочь", plan.getSize());
                panel.session().select(null);
                settle(f);
                sizes.put("снова ничего", plan.getSize());
                plan.setMode(PlanView.Mode.RUNS);
                settle(f);
                sizes.put("режим трасс", plan.getSize());
                plan.setMode(PlanView.Mode.DRAW);
                settle(f);
                sizes.put("рисование трассы", plan.getSize());
                plan.setMode(PlanView.Mode.OBJECTS);
                settle(f);
                sizes.put("объекты", plan.getSize());

                Dimension first = sizes.values().iterator().next();
                for (Map.Entry<String, Dimension> e : sizes.entrySet()) {
                    assertEquals(first, e.getValue(), "размер холста плана изменился: " + e.getKey() + " — " + sizes);
                }
                f.dispose();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private static void settle(JFrame f) {
        java.awt.EventQueue q = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue();
        for (int round = 0; round < 6; round++) {
            f.validate();
            javax.swing.RepaintManager.currentManager(f).validateInvalidComponents();
            try {
                while (q.peekEvent() != null) {
                    java.awt.AWTEvent e = q.getNextEvent();
                    if (e instanceof java.awt.event.InvocationEvent ie) {
                        ie.dispatch();
                    } else if (e.getSource() instanceof java.awt.Component c) {
                        c.dispatchEvent(e);
                    }
                }
            } catch (InterruptedException | RuntimeException ignored) {
                // подготовка к замеру, не проверка
            }
        }
        f.validate();
    }

    private static void clickTab(java.awt.Container root, String text) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.JToggleButton b && text.equals(b.getText())) {
                b.doClick();
                return;
            }
            if (c instanceof java.awt.Container sub) {
                clickTab(sub, text);
            }
        }
    }

    private static <T extends java.awt.Component> T find(java.awt.Container root, Class<T> type) {
        for (java.awt.Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
            if (c instanceof java.awt.Container sub) {
                T found = find(sub, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
