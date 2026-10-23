package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.store.WorkspaceStore;
import com.vjstb.ledscheme.ui.LafStyle;
import com.vjstb.ledscheme.ui.Palette;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Замер на живом экране (не проверка): показывает настоящее окно с редактором на реальном дисплее — со штатным
 * масштабом и конвейером отрисовки Java2D — и гоняет синтетическое перетаскивание, засекая время от события мыши до
 * конца перерисовки. {@code -Dplanner.liveperf=<файл>}. Нужен, чтобы видеть то, чего не видно при рисовании в картинку:
 * масштаб экрана, ускорение, стоимость полноэкранных перерисовок.
 */
class PlannerLivePerf {

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

    private static void send(Component target, int id, Point p) {
        int mods = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        target.dispatchEvent(new MouseEvent(target, id, System.currentTimeMillis(), mods, p.x, p.y, 1, false, MouseEvent.BUTTON1));
    }

    /** Дождаться, пока Swing выполнит накопившиеся перерисовки. */
    private static void flush() throws Exception {
        for (int i = 0; i < 2; i++) {
            SwingUtilities.invokeAndWait(() -> { });
        }
        java.awt.Toolkit.getDefaultToolkit().sync();
    }

    private static String stats(List<Double> ms) {
        List<Double> s = new ArrayList<>(ms);
        Collections.sort(s);
        double sum = 0;
        for (double v : s) {
            sum += v;
        }
        return String.format("шагов %d · среднее %.1f мс · медиана %.1f · 95%% %.1f · максимум %.1f", s.size(), sum / s.size(),
                s.get(s.size() / 2), s.get((int) (s.size() * 0.95)), s.get(s.size() - 1));
    }

    @Test
    void live(@TempDir Path tmp) throws Exception {
        String out = System.getProperty("planner.liveperf");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "Замер только с -Dplanner.liveperf=<файл>");
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
        javax.swing.UIManager.setLookAndFeel(LafStyle.FLAT_DARK.createLaf());
        Palette.applyTheme(true);
        StringBuilder log = new StringBuilder();
        JFrame[] frame = new JFrame[1];
        PlannerEditorPanel[] panel = new PlannerEditorPanel[1];
        ControlRoomLayout[] layout = new ControlRoomLayout[1];
        SwingUtilities.invokeAndWait(() -> {
            AppModel m = new AppModel(new WorkspaceStore(new File(tmp.toFile(), "w.json")));
            m.selectProject(m.addProject("P"));
            m.selectScene(m.addScene("S"));
            layout[0] = PlannerSample.build(m).layout();
            panel[0] = new PlannerEditorPanel(m, RoomLibraryActions.NONE);
            frame[0] = new JFrame("perf");
            frame[0].add(panel[0]);
            frame[0].setSize(1900, 1050);
            frame[0].setLocation(0, 0);
            frame[0].setVisible(true);
            panel[0].showLayout(layout[0]);
            log.append("масштаб экрана: ").append(frame[0].getGraphicsConfiguration().getDefaultTransform().getScaleX()).append('\n');
            log.append("ускоренный буфер: ").append(frame[0].getGraphicsConfiguration().getImageCapabilities().isAccelerated()).append('\n');
            log.append("sun.java2d.d3d=").append(System.getProperty("sun.java2d.d3d")).append(" opengl=")
                    .append(System.getProperty("sun.java2d.opengl")).append('\n');
        });
        Thread.sleep(1500);
        flush();

        // 1) карточка библиотеки тянется над стойками
        for (String tab : new String[] {"Стойки и башни", "План"}) {
            SwingUtilities.invokeAndWait(() -> click(panel[0], tab));
            flush();
            Thread.sleep(300);
            List<Double> times = new ArrayList<>();
            boolean racks = tab.startsWith("Стойки");
            JList<?>[] list = new JList<?>[1];
            Component[] target = new Component[1];
            Point[] from = new Point[1];
            SwingUtilities.invokeAndWait(() -> {
                list[0] = find(panel[0], JList.class);
                target[0] = racks ? find(panel[0], RacksPanel.class) : find(panel[0], PlanPanel.class);
                Rectangle cell = list[0].getCellBounds(3, 3);
                from[0] = new Point(cell.x + 40, cell.y + cell.height / 2);
            });
            SwingUtilities.invokeAndWait(() -> send(list[0], MouseEvent.MOUSE_PRESSED, from[0]));
            for (int i = 0; i < 90; i++) {
                final int k = i;
                Point[] p = new Point[1];
                SwingUtilities.invokeAndWait(() -> {
                    Point inTarget = new Point(60 + (k * 9) % 520, 80 + (k * 5) % 280);
                    p[0] = SwingUtilities.convertPoint(target[0], inTarget, list[0]);
                });
                long t0 = System.nanoTime();
                SwingUtilities.invokeAndWait(() -> send(list[0], MouseEvent.MOUSE_DRAGGED, p[0]));
                flush();
                times.add((System.nanoTime() - t0) / 1e6);
            }
            SwingUtilities.invokeAndWait(() -> send(list[0], MouseEvent.MOUSE_RELEASED, from[0]));
            flush();
            log.append("перетаскивание карточки над вкладкой «").append(tab).append("»: ").append(stats(times)).append('\n');
        }

        // 2) перенос башни по плану
        List<Double> moves = new ArrayList<>();
        PlanPanel[] plan = new PlanPanel[1];
        SwingUtilities.invokeAndWait(() -> plan[0] = find(panel[0], PlanPanel.class));
        Point[] start = new Point[1];
        SwingUtilities.invokeAndWait(() -> {
            var o = plan[0].view().toWorld(0, 0);
            double sc = plan[0].view().scale();
            var t = layout[0].getTowers().get(0);
            start[0] = new Point((int) ((t.getXCm() - o.getX()) * sc), (int) ((t.getYCm() - o.getY()) * sc));
        });
        SwingUtilities.invokeAndWait(() -> send(plan[0], MouseEvent.MOUSE_PRESSED, start[0]));
        for (int i = 1; i <= 90; i++) {
            final int k = i;
            long t0 = System.nanoTime();
            SwingUtilities.invokeAndWait(() -> send(plan[0], MouseEvent.MOUSE_DRAGGED, new Point(start[0].x + k * 4, start[0].y + (k % 30))));
            flush();
            moves.add((System.nanoTime() - t0) / 1e6);
        }
        SwingUtilities.invokeAndWait(() -> send(plan[0], MouseEvent.MOUSE_RELEASED, new Point(start[0].x + 360, start[0].y)));
        flush();
        log.append("перенос башни по плану: ").append(stats(moves)).append('\n');

        SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        Files.writeString(Path.of(out), log.toString());
        System.out.println(log);
    }
}
