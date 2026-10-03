package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetInstance;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.EdgeRouteMode;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.ScreenEntrySide;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Запрос пользователя 2026-10-02: «чтобы линии старались заходить в блоки экранов снизу — так
 *  визуально красивее» + настройка в предпочтениях. Авто-трассировка: конец-приёмник на узле
 *  экрана — нижняя грань блока, последний отрезок идёт снизу вверх. */
class SchemaScreenBottomEntryTest {

    private static final class Fixture {
        final AppModel model;
        final SettingsManager settings;
        final SchemaNode source;
        final SchemaNode screenNode;
        final Screen screen;

        Fixture(Path dir, double sourceX, double sourceY) {
            model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "w.json")));
            model.selectProject(model.addProject("P"));
            model.selectScene(model.addScene("S"));
            CabinetType type = new CabinetType();
            type.setName("P3");
            type.setWidthMm(500);
            type.setHeightMm(500);
            type.setResolutionWidth(128);
            type.setResolutionHeight(128);
            type = model.addCabinetType(type);
            screen = model.addScreen("Экран", type.getId(), 2, 4, 0, 0);
            model.selectScreen(screen);
            settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
            source = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SOURCE, "Щит", sourceX, sourceY, null);
            screenNode = model.addSchemaNode(SchemaMode.POWER, SchemaNodeType.SCREEN, "Экран", 400, 200,
                    screen.getId());
            screenNode.setWidth(300);
            screenNode.setHeight(160);
        }

        SchemaCanvasPanel canvas() {
            return new SchemaCanvasPanel(model, SchemaMode.POWER, settings);
        }

        double bottom() {
            return screenNode.getY() + screenNode.getHeight();
        }
    }

    private static void assertEntersFromBelow(List<double[]> pts, double bottom, double x0, double x1) {
        double[] last = pts.get(pts.size() - 1);
        double[] prev = pts.get(pts.size() - 2);
        assertEquals(bottom, last[1], 1e-6, "линия кончается на нижней грани блока экрана");
        assertTrue(last[0] >= x0 - 1e-6 && last[0] <= x1 + 1e-6, "точка входа в пределах ширины блока: " + last[0]);
        assertEquals(last[0], prev[0], 1e-6, "последний отрезок вертикальный");
        assertTrue(prev[1] > bottom, "предпоследняя точка ниже блока — линия приходит снизу вверх");
    }

    @Test
    void edgeFromTheLeftEntersTheScreenFromBelowByDefault(@TempDir Path dir) {
        Fixture f = new Fixture(dir, 0, 220);
        SchemaEdge edge = f.model.addSchemaEdge(SchemaMode.POWER, f.source.getId(), f.screenNode.getId(), null);
        f.model.setEdgeRouteMode(edge, EdgeRouteMode.AUTO);

        List<double[]> pts = f.canvas().routePointsForTest(edge);

        assertEntersFromBelow(pts, f.bottom(), f.screenNode.getX(), f.screenNode.getX() + f.screenNode.getWidth());
    }

    @Test
    void edgeFromAboveAlsoGoesAroundToEnterFromBelow(@TempDir Path dir) {
        Fixture f = new Fixture(dir, 480, -200);
        SchemaEdge edge = f.model.addSchemaEdge(SchemaMode.POWER, f.source.getId(), f.screenNode.getId(), null);
        f.model.setEdgeRouteMode(edge, EdgeRouteMode.AUTO);

        List<double[]> pts = f.canvas().routePointsForTest(edge);

        assertEntersFromBelow(pts, f.bottom(), f.screenNode.getX(), f.screenNode.getX() + f.screenNode.getWidth());
    }

    @Test
    void switchedOffKeepsTheNearestSideRouting(@TempDir Path dir) {
        Fixture f = new Fixture(dir, 0, 220);
        f.settings.setSchemaScreenEntrySide(SchemaMode.POWER, ScreenEntrySide.NEAREST);
        SchemaEdge edge = f.model.addSchemaEdge(SchemaMode.POWER, f.source.getId(), f.screenNode.getId(), null);
        f.model.setEdgeRouteMode(edge, EdgeRouteMode.AUTO);

        List<double[]> pts = f.canvas().routePointsForTest(edge);

        double[] last = pts.get(pts.size() - 1);
        assertEquals(f.screenNode.getX(), last[0], 1e-6, "источник слева — вход с левой грани, как раньше");
    }

    @Test
    void settingIsSeparatePerSchemaMode(@TempDir Path dir) {
        Fixture f = new Fixture(dir, 0, 220);
        f.settings.setSchemaScreenEntrySide(SchemaMode.SIGNAL, ScreenEntrySide.NEAREST);
        SchemaEdge edge = f.model.addSchemaEdge(SchemaMode.POWER, f.source.getId(), f.screenNode.getId(), null);
        f.model.setEdgeRouteMode(edge, EdgeRouteMode.AUTO);

        // выключен «сигнал», «питание» (канва Fixture) по-прежнему входит снизу
        assertEntersFromBelow(f.canvas().routePointsForTest(edge), f.bottom(), f.screenNode.getX(),
                f.screenNode.getX() + f.screenNode.getWidth());
        assertEquals(ScreenEntrySide.BOTTOM, f.settings.activeProfile().getSchemaScreenEntrySide(SchemaMode.POWER));
        assertEquals(ScreenEntrySide.NEAREST, f.settings.activeProfile().getSchemaScreenEntrySide(SchemaMode.SIGNAL));
    }

    @Test
    void chosenSideIsHonoredForTopLeftAndRight(@TempDir Path dir) throws Exception {
        // источник справа-снизу от блока: «ближайшая» была бы справа/снизу, а выбранная грань другая
        for (ScreenEntrySide side : new ScreenEntrySide[]{ScreenEntrySide.TOP, ScreenEntrySide.LEFT,
                ScreenEntrySide.RIGHT}) {
            Path sub = java.nio.file.Files.createDirectories(dir.resolve(side.name()));
            Fixture f = new Fixture(sub, 900, 500);
            f.settings.setSchemaScreenEntrySide(SchemaMode.POWER, side);
            SchemaEdge edge = f.model.addSchemaEdge(SchemaMode.POWER, f.source.getId(), f.screenNode.getId(), null);
            f.model.setEdgeRouteMode(edge, EdgeRouteMode.AUTO);

            List<double[]> pts = f.canvas().routePointsForTest(edge);
            double[] last = pts.get(pts.size() - 1);
            double[] prev = pts.get(pts.size() - 2);
            SchemaNode n = f.screenNode;
            switch (side) {
                case TOP -> {
                    assertEquals(n.getY(), last[1], 1e-6);
                    assertTrue(prev[1] < n.getY(), "линия приходит сверху");
                }
                case LEFT -> {
                    assertEquals(n.getX(), last[0], 1e-6);
                    assertTrue(prev[0] < n.getX(), "линия приходит слева");
                }
                default -> {
                    assertEquals(n.getX() + n.getWidth(), last[0], 1e-6);
                    assertTrue(prev[0] > n.getX() + n.getWidth(), "линия приходит справа");
                }
            }
        }
    }

    @Test
    void screenAsTheSourceOfAnEdgeIsNotForcedToTheBottom(@TempDir Path dir) {
        Fixture f = new Fixture(dir, 900, 220);
        SchemaEdge edge = f.model.addSchemaEdge(SchemaMode.POWER, f.screenNode.getId(), f.source.getId(), null);
        f.model.setEdgeRouteMode(edge, EdgeRouteMode.AUTO);

        List<double[]> pts = f.canvas().routePointsForTest(edge);

        double[] first = pts.get(0);
        assertEquals(f.screenNode.getX() + f.screenNode.getWidth(), first[0], 1e-6,
                "выход экрана остаётся на ближайшей к приёмнику грани (справа)");
    }

    @Test
    void cabinetSocketEndEntersFromBelowAndStartsUnderTheSocket(@TempDir Path dir) {
        Fixture f = new Fixture(dir, 0, 220);
        f.settings.setSchemaScreensAsWiringDiagram(true);
        CabinetInstance cab = f.screen.getCabinets().get(0);
        SchemaEdge edge = f.model.addSchemaEdge(SchemaMode.POWER, f.source.getId(), null, null,
                f.screenNode.getId(), null, cab.getId(), null);
        f.model.setEdgeRouteMode(edge, EdgeRouteMode.AUTO);

        List<double[]> pts = f.canvas().routePointsForTest(edge);

        double[] last = pts.get(pts.size() - 1);
        double[] prev = pts.get(pts.size() - 2);
        assertEquals(last[0], prev[0], 1e-6, "последний отрезок вертикальный (идёт снизу вверх к гнезду-кабинету)");
        assertTrue(prev[1] > f.bottom(), "перед гнездом линия проходит ниже нижней грани блока");
        assertTrue(last[1] < f.bottom(), "сама точка гнезда лежит внутри блока");
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] p1 = pts.get(i), p2 = pts.get(i + 1);
            assertTrue(Math.abs(p1[0] - p2[0]) < 1e-6 || Math.abs(p1[1] - p2[1]) < 1e-6,
                    "сегмент " + i + " ортогонален");
        }
    }
}
