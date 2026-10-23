package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomEditor;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.LafStyle;
import com.vjstb.ledscheme.ui.Palette;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Замер скорости редактора под настоящей темой приложения (не проверка, а инструмент для оптимизации):
 * {@code -Dplanner.perf=<файл>}. Печатает миллисекунды на шаг: «живой» шаг перетаскивания (оповещение панелей),
 * отрисовка каждой вкладки целиком, зафиксированная правка с записью проекта.
 */
class PlannerPerf {

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

    private static <T extends java.awt.Component> T find(java.awt.Container root, Class<T> type) {
        for (java.awt.Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
            if (c instanceof java.awt.Container sub) {
                T f = find(sub, type);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static double avgMs(int n, Runnable r) {
        for (int i = 0; i < 5; i++) {
            r.run();
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            r.run();
        }
        return (System.nanoTime() - t0) / 1e6 / n;
    }

    @Test
    void perf(@TempDir Path tmp) throws Exception {
        String out = System.getProperty("planner.perf");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "Замер только с -Dplanner.perf=<файл>");
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
        javax.swing.UIManager.setLookAndFeel(LafStyle.FLAT_DARK.createLaf());
        Palette.applyTheme(true);
        StringBuilder log = new StringBuilder();
        SwingUtilities.invokeAndWait(() -> {
            try {
                AppModel m = new AppModel(new WorkspaceStore(new File(tmp.toFile(), "w.json")));
                m.selectProject(m.addProject("P"));
                m.selectScene(m.addScene("S"));
                PlannerSample.Built built = PlannerSample.build(m);
                ControlRoomLayout l = built.layout();
                PlannerEditorPanel panel = new PlannerEditorPanel(m, RoomLibraryActions.NONE);
                JFrame f = new JFrame();
                f.add(panel);
                f.setSize(1900, 1100);
                f.addNotify();
                f.validate();
                panel.showLayout(l);
                f.validate();
                BufferedImage img = new BufferedImage(1900, 1100, BufferedImage.TYPE_INT_ARGB);
                RoomTower t = RoomEditor.tower(l, built.towerA());
                PlannerSession s = panel.session();
                log.append("sun.java2d.d3d=").append(System.getProperty("sun.java2d.d3d")).append('\n');
                log.append("accelerated=").append(f.getGraphicsConfiguration().getImageCapabilities().isAccelerated()).append('\n');

                for (String tab : new String[] {"Стойки и башни", "План"}) {
                    click(panel, tab);
                    f.validate();
                    final boolean plan = tab.equals("План");
                    double[] x = {t.getXCm()};
                    log.append("== вкладка ").append(tab).append('\n');
                    log.append(String.format("  отрисовка вкладки целиком: %.1f мс%n", avgMs(20, () -> {
                        Graphics2D g = img.createGraphics();
                        panel.paint(g);
                        g.dispose();
                    })));
                    java.awt.Component canvas = plan ? find(panel, PlanPanel.class) : find(panel, RacksPanel.class);
                    log.append(String.format("  холст отдельно: %.1f мс%n", avgMs(20, () -> {
                        Graphics2D g = img.createGraphics();
                        canvas.paint(g);
                        g.dispose();
                    })));
                    log.append(String.format("  палитра отдельно: %.1f мс%n", avgMs(20, () -> {
                        Graphics2D g = img.createGraphics();
                        find(panel, LibraryPalette.class).paint(g);
                        g.dispose();
                    })));
                    s.beginLive();
                    log.append(String.format("  живой шаг (live): %.1f мс%n", avgMs(40, () -> {
                        x[0] += 0.5;
                        RoomEditor.moveTower(l, t.getId(), 70 + (x[0] % 100), 261);
                        s.live();
                    })));
                    log.append(String.format("  живой шаг + отрисовка: %.1f мс%n", avgMs(30, () -> {
                        x[0] += 0.5;
                        RoomEditor.moveTower(l, t.getId(), 70 + (x[0] % 100), 261);
                        s.live();
                        Graphics2D g = img.createGraphics();
                        panel.paint(g);
                        g.dispose();
                    })));
                    s.commitLive();
                    if (plan) {
                        log.append(String.format("  фиксация правки (edit + запись проекта): %.1f мс%n", avgMs(10, () ->
                                s.edit(() -> RoomEditor.moveTower(l, t.getId(), 80, 261)))));
                    }
                }
                f.dispose();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
        Files.writeString(Path.of(out), log.toString());
        System.out.println(log);
    }
}
