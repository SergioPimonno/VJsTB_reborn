package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.RoomEquipmentType;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomFurnitureType;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomPoint;
import com.vjstb.ledscheme.model.RoomRackItem;
import com.vjstb.ledscheme.model.RoomRun;
import com.vjstb.ledscheme.model.RoomSegment;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomEditor;
import com.vjstb.ledscheme.service.room.RoomRackLayout;
import com.vjstb.ledscheme.service.room.RoomRunKind;
import com.vjstb.ledscheme.service.room.RoomSelection;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Снимки окна Планнера в PNG — не проверка, а способ посмотреть на результат глазами и получить иллюстрации для
 * руководства. Запускается только с {@code -Dplanner.shots=<каталог>} (иначе пропускается): рисует «пример проекта»
 * (три башни, станки, столы, мониторы, трассы) во всех вкладках редактора.
 */
class PlannerShots {

    @Test
    void shots(@TempDir Path tmp) throws Exception {
        String out = System.getProperty("planner.shots");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "Снимки только с -Dplanner.shots=<каталог>");
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
        Path dir = Path.of(out);
        Files.createDirectories(dir);
        // та же тема, что у приложения: FlatLaf + Palette (по умолчанию тёмная, -Dplanner.theme=light — светлая)
        boolean light = "light".equals(System.getProperty("planner.theme"));
        com.vjstb.ledscheme.ui.LafStyle style = light ? com.vjstb.ledscheme.ui.LafStyle.FLAT_LIGHT
                : com.vjstb.ledscheme.ui.LafStyle.FLAT_DARK;
        javax.swing.UIManager.setLookAndFeel(style.createLaf());
        com.vjstb.ledscheme.ui.Palette.applyTheme(style.isDark());

        AppModel m = new AppModel(new WorkspaceStore(new File(tmp.toFile(), "w.json")));
        m.selectProject(m.addProject("P"));
        m.selectScene(m.addScene("S"));
        PlannerSample.Built built = PlannerSample.build(m);
        ControlRoomLayout l = built.layout();
        RoomTower a = RoomEditor.tower(l, built.towerA());

        List<String> done = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                PlannerEditorPanel panel = new PlannerEditorPanel(m, RoomLibraryActions.NONE);
                JFrame f = new JFrame();
                f.add(panel);
                f.setSize(1380, 860);
                f.addNotify();
                settle(f);
                panel.showLayout(l);
                settle(f);
                shot(panel, dir.resolve("1-racks-front.png"), 1380, 860);
                click(panel, "Сверху");
                settle(f);
                shot(panel, dir.resolve("2-racks-top.png"), 1380, 860);
                click(panel, "План");
                settle(f);
                shot(panel, dir.resolve("3-plan-top.png"), 1380, 860);
                click(panel, "Спереди");
                settle(f);
                shot(panel, dir.resolve("4-plan-front.png"), 1380, 860);
                click(panel, "Спецификация");
                click(panel, "Сверху");
                settle(f);
                panel.session().select(RoomSelection.tower(a.getId()));
                settle(f);
                shot(panel, dir.resolve("5-plan-spec.png"), 1380, 860);
                f.dispose();
                done.add("ok");
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    /** Довести раскладку до конца: отложенные {@code revalidate()} обычно выполняет поток интерфейса позже. */
    private static void settle(JFrame f) {
        java.awt.EventQueue q = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue();
        for (int round = 0; round < 6; round++) {
            f.validate();
            javax.swing.RepaintManager.currentManager(f).validateInvalidComponents();
            try {
                // события размера и отложенные задания, которые живой интерфейс обработал бы между кадрами
                while (q.peekEvent() != null) {
                    java.awt.AWTEvent e = q.getNextEvent();
                    if (e instanceof java.awt.event.InvocationEvent ie) {
                        ie.dispatch();
                    } else if (e.getSource() instanceof java.awt.Component c) {
                        c.dispatchEvent(e);
                    }
                }
            } catch (InterruptedException | RuntimeException ignored) {
                // снимок — не проверка: что не обработалось, на картинке просто не будет
            }
        }
        f.validate();
    }

    private static void click(java.awt.Container root, String text) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof JToggleButton b && text.equals(b.getText())) {
                b.doClick();
                return;
            }
            if (c instanceof java.awt.Container sub) {
                click(sub, text);
            }
        }
    }

    private static void shot(java.awt.Component c, Path file, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, w, h);
        c.paint(g);
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
    }
}
