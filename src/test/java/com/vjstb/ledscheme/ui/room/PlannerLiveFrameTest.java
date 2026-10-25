package com.vjstb.ledscheme.ui.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomEditor;
import com.vjstb.ledscheme.service.room.RoomSelection;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * «Рябь» при перетаскивании: раньше на время жеста выключалось сглаживание фигур, и на захвате и на отпускании вся
 * картинка перерисовывалась иначе (≈5% пикселей плана). Теперь кадр «в жесте» без движения обязан совпадать с кадром
 * в покое пиксель в пиксель, а шаг перетаскивания — менять только область вокруг двигаемого объекта. Пропускается без
 * дисплея.
 */
class PlannerLiveFrameTest {

    private static void click(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JToggleButton b && text.equals(b.getText())) {
                b.doClick();
                return;
            }
            if (c instanceof Container sub) {
                click(sub, text);
            }
        }
    }

    private static <T extends Component> T find(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
            if (c instanceof Container sub) {
                T f = find(sub, type);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static BufferedImage frame(Component c) {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        c.paint(g);
        g.dispose();
        return img;
    }

    /** Число различающихся пикселей и охватывающий их прямоугольник {minX, minY, maxX, maxY}. */
    private static int[] diff(BufferedImage a, BufferedImage b) {
        int[] r = {0, 1 << 30, 1 << 30, -1, -1};
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    r[0]++;
                    r[1] = Math.min(r[1], x);
                    r[2] = Math.min(r[2], y);
                    r[3] = Math.max(r[3], x);
                    r[4] = Math.max(r[4], y);
                }
            }
        }
        return r;
    }

    @Test
    void liveFrameEqualsIdleFrameAndADragStepOnlyChangesTheDraggedArea(@TempDir Path dir) throws Exception {
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SwingUtilities.invokeAndWait(() -> {
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
            click(panel, "План");
            f.validate();
            PlanPanel plan = find(panel, PlanPanel.class);
            panel.session().select(RoomSelection.tower(built.towerA()));
            f.validate();

            BufferedImage idle = frame(plan);
            panel.session().beginLive();
            assertEquals(0, diff(idle, frame(plan))[0], "захват объекта не должен перерисовывать картинку иначе");

            RoomTower t = RoomEditor.tower(l, built.towerA());
            RoomEditor.moveTower(l, t.getId(), t.getXCm() + 20, t.getYCm());
            panel.session().live();
            BufferedImage f1 = frame(plan);
            RoomEditor.moveTower(l, t.getId(), t.getXCm() + 20, t.getYCm());
            panel.session().live();
            int[] d = diff(f1, frame(plan));
            assertTrue(d[0] > 0, "башня сдвинулась — кадр изменился");
            double area = (double) (d[3] - d[1] + 1) * (d[4] - d[2] + 1);
            assertTrue(area < plan.getWidth() * plan.getHeight() * 0.05,
                    "изменилась только область вокруг башни, а не вся картинка: " + area + " из "
                            + (plan.getWidth() * plan.getHeight()));

            panel.session().commitLive();
            f.dispose();
        });
    }
}
