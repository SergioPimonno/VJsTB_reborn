package com.vjstb.ledscheme.ui.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.RoomEquipmentType;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomRackLayout;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Жесты мыши в настоящем окне синтетическими событиями: карточка библиотеки → план, → слот стойки, блок стойки →
 * корзина (вынос за пределы башни — удаление, запрос пользователя 2026-10-23), перенос башни по плану с отменой.
 * Проверяется сквозной путь «нажатие → призрак → приёмник → правка раскладки → пересчёт». Пропускается без дисплея.
 */
class PlannerGestureTest {

    private AppModel model;
    private ControlRoomLayout layout;
    private CaseType rackCase;
    private RoomEquipmentType switchEq;
    private PlannerEditorPanel panel;
    private JFrame frame;

    private void setUp(Path dir, boolean withTower) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        rackCase = new CaseType();
        rackCase.setName("Рэк-кофр 6U");
        rackCase.setWidthMm(600);
        rackCase.setLengthMm(620);
        rackCase.setHeightMm(430);
        PhysicalSpec cp = new PhysicalSpec();
        cp.setRackUnits(6);
        cp.setDepthMm(450.0);
        rackCase.setPhysical(cp);
        model.addCaseType(rackCase);
        switchEq = new RoomEquipmentType();
        switchEq.setName("Коммутатор 24");
        PhysicalSpec sp = new PhysicalSpec();
        sp.setFormFactor(PhysicalSpec.FormFactor.RACK);
        sp.setRackUnits(1);
        sp.setDepthMm(300.0);
        switchEq.setPhysical(sp);
        model.addRoomEquipmentType(switchEq);
        layout = model.addControlRoomLayout("FOH");
        if (withTower) {
            RoomRackLayout.newTower(layout, new RoomLibRef(RoomLibRef.Kind.CASE, rackCase.getId()), 200, 300);
        }
        panel = new PlannerEditorPanel(model, RoomLibraryActions.NONE);
        frame = new JFrame();
        frame.add(panel);
        frame.setSize(1380, 860);
        frame.addNotify();
        frame.validate();
        panel.showLayout(layout);
        frame.validate();
        repaint();
    }

    private void repaint() {
        BufferedImage img = new BufferedImage(1380, 860, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        panel.paint(g);
        g.dispose();
    }

    private static <T extends Component> T find(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
            if (c instanceof Container sub) {
                T found = find(sub, type);
                if (found != null) {
                    return found;
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

    private static void send(Component target, int id, Point p, int clicks) {
        int mods = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        target.dispatchEvent(new MouseEvent(target, id, System.currentTimeMillis(), mods, p.x, p.y, clicks, false,
                MouseEvent.BUTTON1));
    }

    /** Нажать на источнике, провести по нескольким промежуточным точкам и отпустить в точке {@code to} (координаты источника). */
    private static void drag(Component source, Point from, Point to) {
        send(source, MouseEvent.MOUSE_PRESSED, from, 1);
        for (int i = 1; i <= 6; i++) {
            Point p = new Point(from.x + (to.x - from.x) * i / 6, from.y + (to.y - from.y) * i / 6);
            send(source, MouseEvent.MOUSE_DRAGGED, p, 0);
        }
        send(source, MouseEvent.MOUSE_RELEASED, to, 1);
    }

    private JList<?> paletteList() {
        return find(panel, JList.class) instanceof JList<?> l ? l : null;
    }

    private int rowOf(JList<?> list, String name) {
        for (int i = 0; i < list.getModel().getSize(); i++) {
            Object e = list.getModel().getElementAt(i);
            if (e instanceof com.vjstb.ledscheme.service.room.RoomItemSpec s && s.name().equals(name)) {
                return i;
            }
        }
        throw new AssertionError("нет карточки " + name);
    }

    @Test
    void libraryCardDroppedOnThePlanMakesATower(@TempDir Path dir) throws Exception {
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SwingUtilities.invokeAndWait(() -> {
            setUp(dir, false);
            click(panel, "План");
            frame.validate();
            repaint();
            JList<?> list = paletteList();
            PlanPanel plan = find(panel, PlanPanel.class);
            Rectangle cell = list.getCellBounds(rowOf(list, "Рэк-кофр 6U"), rowOf(list, "Рэк-кофр 6U"));
            Point from = new Point(cell.x + 40, cell.y + cell.height / 2);
            Point onPlan = SwingUtilities.convertPoint(plan, new Point(plan.getWidth() / 2, plan.getHeight() / 2), list);
            drag(list, from, onPlan);
            assertEquals(1, layout.getTowers().size(), "карточка кофра на плане собрала башню");
            assertEquals(RoomLibRef.Kind.CASE, layout.getTowers().get(0).getSegments().get(0).getCaseRef().getKind());
            frame.dispose();
        });
    }

    @Test
    void rackBlockDroppedIntoASlotThenDraggedOutIsRemoved(@TempDir Path dir) throws Exception {
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SwingUtilities.invokeAndWait(() -> {
            setUp(dir, true);
            repaint();
            JList<?> list = paletteList();
            RacksPanel racks = find(panel, RacksPanel.class);
            racks.sessionChanged();
            RackGeometry g = racks.geometry();
            RackGeometry.SegBox seg = g.towers.get(0).segs.get(0);
            Point slot = new Point(seg.slots.x + 60, seg.slots.y + 3 * RackGeometry.UH + 6);
            int row = rowOf(list, "Коммутатор 24");
            Rectangle cell = list.getCellBounds(row, row);
            drag(list, new Point(cell.x + 40, cell.y + cell.height / 2), SwingUtilities.convertPoint(racks, slot, list));
            RoomTower t = layout.getTowers().get(0);
            assertEquals(1, t.getSegments().get(0).getItems().size(), "блок лёг в слот кофра");
            assertEquals(3, t.getSegments().get(0).getItems().get(0).getU0(), "под указателем — четвёртый ряд");

            // теперь вытаскиваем блок на пустое место панели — удаление
            racks.sessionChanged();
            RackGeometry g2 = racks.geometry();
            RackGeometry.SlotItem it = g2.towers.get(0).segs.get(0).slotItems.get(0);
            Point from = new Point(it.rect().x + 20, it.rect().y + 8);
            Point empty = new Point(g2.newTowerZone.x + 60, g2.newTowerZone.y + g2.newTowerZone.height + 300);
            drag(racks, from, empty);
            assertTrue(layout.getTowers().get(0).getSegments().get(0).getItems().isEmpty(),
                    "блок, выброшенный в пустое место, удалён");
            frame.dispose();
        });
    }

    @Test
    void towerDraggedOnThePlanMovesAndUndoBringsItBack(@TempDir Path dir) throws Exception {
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        SwingUtilities.invokeAndWait(() -> {
            setUp(dir, true);
            click(panel, "План");
            frame.validate();
            repaint();
            PlanPanel plan = find(panel, PlanPanel.class);
            RoomTower t = layout.getTowers().get(0);
            double sc = plan.view().scale();
            var origin = plan.view().toWorld(0, 0);
            Point from = new Point((int) Math.round((t.getXCm() - origin.getX()) * sc),
                    (int) Math.round((t.getYCm() - origin.getY()) * sc));
            Point to = new Point(from.x + (int) Math.round(150 * sc), from.y);
            drag(plan, from, to);
            assertEquals(350, t.getXCm(), 10, "башня уехала на 150 см вправо");
            assertEquals(0.0, t.getXCm() % 5, 1e-9, "шаг 5 см");
            assertNotNull(plan);
            JToggleButton unused = null;
            panel.session().undo();
            assertEquals(200, layout.getTowers().get(0).getXCm(), 1e-9, "один шаг отмены на весь жест");
            frame.dispose();
        });
    }
}
