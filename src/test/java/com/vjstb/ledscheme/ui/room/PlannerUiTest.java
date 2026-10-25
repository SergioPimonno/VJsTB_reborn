package com.vjstb.ledscheme.ui.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.ControlRoomLayout;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.RoomEquipmentType;
import com.vjstb.ledscheme.model.RoomFurniture;
import com.vjstb.ledscheme.model.RoomFurnitureType;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.model.RoomLooseItem;
import com.vjstb.ledscheme.model.RoomPoint;
import com.vjstb.ledscheme.model.RoomRun;
import com.vjstb.ledscheme.model.RoomTower;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomEditor;
import com.vjstb.ledscheme.service.room.RoomRackLayout;
import com.vjstb.ledscheme.service.room.RoomRunKind;
import com.vjstb.ledscheme.service.room.RoomSelection;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Редактор Планнера без мыши: отрисовка плана, вида спереди и стоек в картинку (ни одно исключение, на
 * картинке что-то нарисовано), поиск объекта под указателем, геометрия стоек, сессия правок с отменой.
 * Жесты мыши проверяются живой сборкой; тут — всё, что можно проверить кодом.
 */
class PlannerUiTest {

    private AppModel model;
    private ControlRoomLayout layout;
    private PlannerSession session;
    private RoomTower tower;
    private RoomFurniture table;
    private RoomFurniture deck;
    private RoomLooseItem loose;
    private RoomRun run;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));

        CaseType c = new CaseType();
        c.setName("Рэк-кофр 6U");
        c.setWidthMm(600);
        c.setLengthMm(620);
        c.setHeightMm(430);
        c.setWeightKg(20);
        PhysicalSpec cp = new PhysicalSpec();
        cp.setRackUnits(6);
        cp.setDepthMm(450.0);
        c.setPhysical(cp);
        model.addCaseType(c);

        RoomEquipmentType sw = new RoomEquipmentType();
        sw.setName("Коммутатор 24");
        PhysicalSpec sp = new PhysicalSpec();
        sp.setFormFactor(PhysicalSpec.FormFactor.RACK);
        sp.setRackUnits(1);
        sp.setDepthMm(300.0);
        sp.setWeightKg(4.0);
        sp.setPowerW(40.0);
        sw.setPhysical(sp);
        model.addRoomEquipmentType(sw);

        RoomEquipmentType conv = new RoomEquipmentType();
        conv.setName("Конвертер HDMI→SDI");
        PhysicalSpec cvp = new PhysicalSpec();
        cvp.setFormFactor(PhysicalSpec.FormFactor.LOOSE);
        cvp.setWidthMm(110.0);
        cvp.setHeightMm(30.0);
        cvp.setDepthMm(70.0);
        conv.setPhysical(cvp);
        model.addRoomEquipmentType(conv);

        RoomFurnitureType tbl = new RoomFurnitureType();
        tbl.setName("Стол 180");
        tbl.setKind(RoomFurnitureType.Kind.FURNITURE);
        tbl.setRole(RoomFurnitureType.Role.TABLE);
        tbl.setWidthMm(1800.0);
        tbl.setDepthMm(600.0);
        tbl.setHeightMm(750.0);
        model.addRoomFurnitureType(tbl);

        RoomFurnitureType dk = new RoomFurnitureType();
        dk.setName("Станок 2×1");
        dk.setKind(RoomFurnitureType.Kind.DECK);
        dk.setWidthMm(2000.0);
        dk.setDepthMm(1000.0);
        dk.setHeightMm(400.0);
        model.addRoomFurnitureType(dk);

        layout = model.addControlRoomLayout("FOH");
        tower = RoomRackLayout.newTower(layout, new RoomLibRef(RoomLibRef.Kind.CASE, c.getId()), 100, 300);
        RoomRackLayout.autoPlace(session0(), tower, session0().resolve(new RoomLibRef(RoomLibRef.Kind.ROOM_EQUIPMENT, sw.getId())));
        table = new RoomFurniture();
        table.setRef(new RoomLibRef(RoomLibRef.Kind.ROOM_FURNITURE, tbl.getId()));
        table.setXCm(360);
        table.setYCm(240);
        layout.getFurniture().add(table);
        deck = new RoomFurniture();
        deck.setRef(new RoomLibRef(RoomLibRef.Kind.ROOM_FURNITURE, dk.getId()));
        deck.setXCm(500);
        deck.setYCm(350);
        layout.getFurniture().add(deck);
        run = new RoomRun();
        run.setKind("sdi");
        run.setName("SDI");
        run.getPoints().add(new RoomPoint(190, 0));
        run.getPoints().add(new RoomPoint(190, 145));
        run.getPoints().add(new RoomPoint(400, 145));
        layout.getRuns().add(run);
        loose = RoomEditor.placeLoose(layout, new RoomLibRef(RoomLibRef.Kind.ROOM_EQUIPMENT, conv.getId()), 195, 100);

        session = new PlannerSession(model);
        session.setLayout(layout);
    }

    private com.vjstb.ledscheme.service.room.RoomCatalog session0() {
        return com.vjstb.ledscheme.service.room.RoomLibraryAdapter.of(model);
    }

    private static boolean drawn(BufferedImage img) {
        int first = img.getRGB(0, 0);
        for (int y = 0; y < img.getHeight(); y += 7) {
            for (int x = 0; x < img.getWidth(); x += 7) {
                if (img.getRGB(x, y) != first) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------ рисование и поиск

    @Test
    void planPaintsAndFindsObjectsUnderThePointer() {
        PlanView v = new PlanView(session);
        BufferedImage img = new BufferedImage(900, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        v.paint(g, 900, 600);
        g.dispose();
        assertTrue(drawn(img));

        assertEquals(PlanView.Hit.Kind.TOWER, v.hit(100, 300).kind());
        assertEquals(tower.getId(), v.hit(100, 300).id());
        assertEquals(PlanView.Hit.Kind.FURN, v.hit(360, 240).kind(), "стол");
        assertEquals(deck.getId(), v.hit(480, 350).id(), "станок");
        assertEquals(PlanView.Hit.Kind.LOOSE, v.hit(195, 100).kind());
        assertEquals(PlanView.Hit.Kind.RUN, v.hit(300, 146).kind(), "по линии трассы");
        assertEquals(PlanView.Hit.Kind.NONE, v.hit(650, 60).kind());
    }

    @Test
    void routeModeFindsVerticesAndMidpointsOfTheSelectedRun() {
        PlanView v = new PlanView(session);
        v.mode = PlanView.Mode.RUNS;
        session.select(RoomSelection.run(run.getId()));
        assertEquals(PlanView.Hit.Kind.VERTEX, v.hit(190, 145).kind());
        assertEquals(1, v.hit(190, 145).index());
        assertEquals(PlanView.Hit.Kind.MID, v.hit(190, 72).kind(), "середина первого отрезка");
        assertEquals(PlanView.Hit.Kind.NONE, v.hit(100, 300).kind(), "в режиме трасс объекты не хватаются");
        v.mode = PlanView.Mode.DRAW;
        assertEquals(PlanView.Hit.Kind.NONE, v.hit(190, 145).kind());
    }

    @Test
    void elevationPaintsAndPicksTheTowerAndTheDeck() {
        ElevationView v = new ElevationView(session);
        BufferedImage img = new BufferedImage(900, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        v.paint(g, 900, 400);
        g.dispose();
        assertTrue(drawn(img));
        ElevationView.Pick t = v.pickAt(100, 20);
        assertNotNull(t);
        assertTrue(t.tower());
        ElevationView.Pick d = v.pickAt(500, 20);
        assertNotNull(d);
        assertEquals(deck.getId(), d.id());
        assertNull(v.pickAt(660, 200), "пустое место");
    }

    @Test
    void spaceUnderATableIsClickThroughSoThatWhatStandsThereCanBePicked() {
        ElevationView v = new ElevationView(session);
        BufferedImage img = new BufferedImage(900, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        v.paint(g, 900, 400);
        g.dispose();
        // стол f1: x 270…450, высота 75 см, на полу
        ElevationView.Pick top = v.pickAt(360, 73);
        assertNotNull(top, "столешница хватается");
        ElevationView.Pick leg = v.pickAt(274, 30);
        assertNotNull(leg, "ножка хватается");
        assertEquals(top.id(), leg.id());
        ElevationView.Pick hole = v.pickAt(360, 30);
        assertTrue(hole == null || !hole.id().equals(top.id()),
                "под столешницей между ножками клик проходит сквозь стол");
    }

    @Test
    void racksGeometryFindsSlotsStacksAndInsertionPositions() {
        RoomRackLayout.addToStack(tower, new RoomLibRef(RoomLibRef.Kind.ROOM_EQUIPMENT, loose.getRef().getId()));
        session.reload();
        RackGeometry g = RackGeometry.build(session.calc().towers(), session.catalog(), 500);
        assertEquals(1, g.towers.size());
        RackGeometry.TowerBox tb = g.towers.get(0);
        assertEquals(2, tb.segs.size(), "стопка сверху и кофр под ней");
        assertTrue(tb.segs.get(0).seg.isStack());
        RackGeometry.SegBox caseBox = tb.segs.get(1);
        assertEquals(1, caseBox.slotItems.size());
        Point inSlots = new Point(caseBox.slots.x + 10, caseBox.slots.y + 10);
        assertSame(caseBox, g.segAt(tb, inSlots));
        assertSame(tb, g.towerAt(inSlots));
        assertEquals(0, g.segInsertPosition(tb, new Point(tb.card.x + 20, tb.segs.get(0).head.y)));
        assertEquals(2, g.segInsertPosition(tb, new Point(tb.card.x + 20, caseBox.body.y + caseBox.body.height + 5)));
        assertEquals(0, g.stackInsertPosition(tb.segs.get(0), new Point(tb.segs.get(0).slots.x, tb.segs.get(0).slots.y)));
        assertFalse(g.newTowerZone.intersects(tb.card));
    }

    private static void assertSame(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertSame(expected, actual);
    }

    @Test
    void racksPanelPaintsFrontAndTopViews() {
        RacksPanel p = new RacksPanel(session, new RoomDnd.Controller(new javax.swing.JPanel()));
        p.setSize(1000, 700);
        for (boolean top : new boolean[] {false, true}) {
            p.setTopView(top);
            BufferedImage img = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            p.paint(g);
            g.dispose();
            assertTrue(drawn(img), top ? "вид сверху" : "лицевые панели");
        }
    }

    // ------------------------------------------------------------ сессия

    @Test
    void sessionEditUndoRedoAndSelectionSurvival() {
        session.select(RoomSelection.tower(tower.getId()));
        int towers = layout.getTowers().size();
        session.edit(() -> RoomEditor.removeSelection(layout, session.selection()));
        assertEquals(towers - 1, layout.getTowers().size());
        assertNull(session.selection(), "выбор пропал вместе с объектом");
        assertTrue(session.canUndo());

        session.undo();
        assertEquals(towers, layout.getTowers().size());
        assertTrue(session.canRedo());
        session.redo();
        assertEquals(towers - 1, layout.getTowers().size());
    }

    @Test
    void liveGestureMakesOneUndoStepAndSavesOnCommit() {
        double x0 = tower.getXCm();
        session.beginLive();
        RoomEditor.moveTower(layout, tower.getId(), 250, 300);
        session.live();
        RoomEditor.moveTower(layout, tower.getId(), 300, 300);
        session.live();
        session.commitLive();
        assertEquals(300, tower.getXCm(), 1e-9);
        session.undo();
        assertEquals(x0, RoomEditor.tower(layout, tower.getId()).getXCm(), 1e-9, "один шаг отмены на весь жест");
    }

    @Test
    void sessionKeepsSeparateHistoryPerLayout() {
        ControlRoomLayout other = model.addControlRoomLayout("Запасная");
        session.edit(() -> RoomEditor.removeSelection(layout, RoomSelection.tower(tower.getId())));
        assertTrue(session.canUndo());
        session.setLayout(other);
        assertFalse(session.canUndo(), "у другой раскладки своя история");
        session.setLayout(layout);
        assertTrue(session.canUndo());
    }

    @Test
    void drawnRunBecomesPartOfTheLayoutAndIsSelected() {
        session.editSelecting(() -> {
            RoomRun r = RoomEditor.addRun(layout, RoomRunKind.PWR, java.util.List.of(new RoomPoint(610, 0), new RoomPoint(610, 300)));
            return RoomSelection.run(r.getId());
        });
        assertEquals(RoomSelection.Type.RUN, session.selection().type());
        assertEquals(2, layout.getRuns().size());
    }

    // ------------------------------------------------------------ сборка окна

    @Test
    void editorPanelBuildsShowsALayoutAndPaintsEveryTab() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "Нет дисплея — UI-тест пропущен");
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            PlannerEditorPanel panel = new PlannerEditorPanel(model, RoomLibraryActions.NONE);
            javax.swing.JFrame frame = new javax.swing.JFrame();
            frame.add(panel);
            frame.setSize(1300, 780);
            frame.addNotify();
            frame.validate();
            panel.showLayout(layout);
            frame.validate();
            BufferedImage img = new BufferedImage(1300, 780, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            panel.paint(g);
            g.dispose();
            assertTrue(drawn(img));
            panel.reload();
            panel.showLayout(null);
            frame.dispose();
        });
    }
}
