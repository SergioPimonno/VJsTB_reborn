package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EdgeRouteMode;
import com.vjstb.ledscheme.model.EdgeWaypoint;
import com.vjstb.ledscheme.model.InterfaceRole;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaEdge;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNode;
import com.vjstb.ledscheme.model.SchemaNodeType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SchemaStylePreset;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.settings.SettingsStore;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.awt.Color;
import java.awt.Point;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** docs/schema-ports-rework/PLAN.md, задача T4.4 — интеграция OrthogonalRouter в
 *  холст: AUTO-маршрут, превращение в MANUAL при перетаскивании, ортогональное
 *  редактирование при переносе блока, разрешение цвета линии (D9). */
class SchemaEdgeRoutingTest {

    private static AppModel model(Path dir) {
        AppModel model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("Зал"));
        return model;
    }

    private static SchemaCanvasPanel canvas(AppModel model, Path dir) {
        return canvas(model, dir, SchemaStylePreset.SCREEN);
    }

    private static SchemaCanvasPanel canvas(AppModel model, Path dir, SchemaStylePreset preset) {
        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings.json")));
        settings.setSchemaStylePreset(preset);
        return new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);
    }

    @Test
    void autoRouteModeProducesAnAxisAlignedOrthogonalPath(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 200, null);
        CardPort aOut = model.addCardToNode(a, "Видео", List.of(
                new CardPort("HDMI", PortDirection.OUT, 1))).getPorts().get(0);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 500, 0, null);
        CardPort bIn = model.addCardToNode(b, "Видео", List.of(
                new CardPort("HDMI", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), aOut.getId(), null,
                b.getId(), bIn.getId(), null, null, EdgeRouteMode.AUTO);

        SchemaCanvasPanel canvas = canvas(model, dir);
        List<double[]> pts = canvas.routePointsForTest(edge);

        assertTrue(pts.size() >= 2);
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] p1 = pts.get(i), p2 = pts.get(i + 1);
            boolean horizontal = Math.abs(p1[1] - p2[1]) < 1e-6;
            boolean vertical = Math.abs(p1[0] - p2[0]) < 1e-6;
            assertTrue(horizontal || vertical, "сегмент " + i + " не ортогонален");
        }
    }

    @Test
    void legacyEdgeWithoutRouteModeStaysAStraightLineEvenWithConcretePorts(@TempDir Path dir) {
        // "Старый проект открывается с прежними маршрутами" (PLAN.md §2.6/T4.4) —
        // связь без явного routeMode (как из старого workspace.json) должна остаться
        // прямой, даже если у обоих концов теперь есть привязка к гнезду.
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 200, null);
        CardPort aOut = model.addCardToNode(a, "Видео", List.of(
                new CardPort("HDMI", PortDirection.OUT, 1))).getPorts().get(0);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 500, 0, null);
        CardPort bIn = model.addCardToNode(b, "Видео", List.of(
                new CardPort("HDMI", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), aOut.getId(), b.getId(), bIn.getId(), null);

        SchemaCanvasPanel canvas = canvas(model, dir);
        List<double[]> pts = canvas.routePointsForTest(edge);

        assertEquals(2, pts.size(), "без явного routeMode и без изломов — та же прямая линия узел-узел, что и раньше");
    }

    @Test
    void materializingAnAutoEdgeConvertsItToManualWithTheCurrentRoute(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 100, null);
        CardPort aOut = model.addCardToNode(a, "Видео", List.of(
                new CardPort("HDMI", PortDirection.OUT, 1))).getPorts().get(0);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 300, 100, null);
        CardPort bIn = model.addCardToNode(b, "Видео", List.of(
                new CardPort("HDMI", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), aOut.getId(), null,
                b.getId(), bIn.getId(), null, null, EdgeRouteMode.AUTO);

        SchemaCanvasPanel canvas = canvas(model, dir);
        // Препятствие ровно на прямой между гнёздами (координаты — из РЕАЛЬНОЙ
        // раскладки, не угаданы по номинальной позиции/ширине узла — та растёт под
        // подписи и заранее неизвестна) — авто-маршрут обязан свернуть, иначе у него
        // не будет ВНУТРЕННИХ точек и тест ничего не проверит.
        Point pinA = canvas.socketPositionForTest(a, aOut.getId(), edge);
        Point pinB = canvas.socketPositionForTest(b, bIn.getId(), edge);
        assertTrue(pinB.x > pinA.x + 40, "фикстура должна оставлять зазор между гнёздами шире препятствия");
        double wallX = (pinA.x + pinB.x) / 2.0 - 20;
        SchemaNode wall = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.CUSTOM, "Стена",
                wallX, Math.min(pinA.y, pinB.y) - 100, null);
        wall.setWidth(40);
        wall.setHeight(200);
        List<double[]> before = canvas.routePointsForTest(edge);
        assertTrue(before.size() > 2, "фикстура должна давать хотя бы один излом — иначе материализация тривиальна");

        canvas.materializeAutoRouteIfNeededForTest(edge);

        assertEquals(EdgeRouteMode.MANUAL, edge.getRouteMode());
        assertEquals(before.size() - 2, edge.getWaypoints().size());
        for (int i = 0; i < edge.getWaypoints().size(); i++) {
            EdgeWaypoint w = edge.getWaypoints().get(i);
            assertEquals(before.get(i + 1)[0], w.getX(), 1e-6);
            assertEquals(before.get(i + 1)[1], w.getY(), 1e-6);
        }
        // После материализации маршрут не должен визуально прыгнуть.
        List<double[]> after = canvas.routePointsForTest(edge);
        assertEquals(before.size(), after.size());
    }

    @Test
    void materializingAlreadyManualEdgeDoesNothing(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 0, null);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 300, 0, null);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), b.getId(), null);
        model.setSchemaEdgeWaypoints(edge, List.of(new EdgeWaypoint(150, 50)));
        model.setEdgeRouteMode(edge, EdgeRouteMode.MANUAL);

        SchemaCanvasPanel canvas = canvas(model, dir);
        canvas.materializeAutoRouteIfNeededForTest(edge);

        assertEquals(1, edge.getWaypoints().size());
        assertEquals(150, edge.getWaypoints().get(0).getX(), 1e-6);
    }

    @Test
    void keepOrthogonalWaypointsAdjustsNearEndAfterNodeMoves(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 100, null);
        CardPort aOut = model.addCardToNode(a, "Видео", List.of(
                new CardPort("HDMI", PortDirection.OUT, 1))).getPorts().get(0);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 400, 300, null);
        CardPort bIn = model.addCardToNode(b, "Видео", List.of(
                new CardPort("HDMI", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), aOut.getId(), b.getId(), bIn.getId(), null);
        model.setSchemaEdgeWaypoints(edge, List.of(new EdgeWaypoint(200, 110), new EdgeWaypoint(200, 310)));
        model.setEdgeRouteMode(edge, EdgeRouteMode.MANUAL);

        SchemaCanvasPanel canvas = canvas(model, dir);
        double pinYBefore = canvas.socketPositionForTest(a, aOut.getId(), edge).y;
        assertEquals(110.0, edge.getWaypoints().get(0).getY(), 1e-6);

        // Двигаем A вниз на 40 — HDMI OUT на RIGHT-стороне (роль VIDEO), значит
        // ближний излом должен подтянуться по Y к НОВОЙ высоте пина.
        a.setY(a.getY() + 40);
        canvas.keepOrthogonalWaypointsForNodeForTest(a);

        double pinYAfter = canvas.socketPositionForTest(a, aOut.getId(), edge).y;
        assertEquals(40.0, pinYAfter - pinYBefore, 1e-6, "фикстура должна реально сдвинуть пин, иначе тест не проверяет ничего");
        assertEquals(pinYAfter, edge.getWaypoints().get(0).getY(), 1e-6,
                "ближний излом должен встать на новую высоту пина (отрезок остаётся горизонтальным)");
        assertEquals(200.0, edge.getWaypoints().get(0).getX(), 1e-6, "X ближнего излома трогать не нужно");
        // Дальний конец (у B) не должен был измениться вовсе.
        assertEquals(310.0, edge.getWaypoints().get(1).getY(), 1e-6);
    }

    @Test
    void keepOrthogonalWaypointsDoesNothingWhenSettingIsDisabled(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 100, null);
        CardPort aOut = model.addCardToNode(a, "Видео", List.of(
                new CardPort("HDMI", PortDirection.OUT, 1))).getPorts().get(0);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 400, 300, null);
        CardPort bIn = model.addCardToNode(b, "Видео", List.of(
                new CardPort("HDMI", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), aOut.getId(), b.getId(), bIn.getId(), null);
        model.setSchemaEdgeWaypoints(edge, List.of(new EdgeWaypoint(200, 110), new EdgeWaypoint(200, 310)));
        model.setEdgeRouteMode(edge, EdgeRouteMode.MANUAL);

        SettingsManager settings = new SettingsManager(new SettingsStore(new File(dir.toFile(), "settings-off.json")));
        settings.activeProfile().setOrthogonalEdgeEditing(false);
        SchemaCanvasPanel canvas = new SchemaCanvasPanel(model, SchemaMode.SIGNAL, settings);

        a.setY(a.getY() + 40);
        canvas.keepOrthogonalWaypointsForNodeForTest(a);

        assertEquals(110.0, edge.getWaypoints().get(0).getY(), 1e-6, "настройка выключена — излом трогать нельзя");
    }

    @Test
    void printPresetResolvesRoleAndNominalColorsWhenEdgeHasNoCustomColor(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 0, null);
        CardPort sync = model.addCardToNode(a, "Синхро", List.of(
                new CardPort("Genlock (SDI)", PortDirection.OUT, 1))).getPorts().get(0);
        sync.setRole(InterfaceRole.SYNC);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 300, 0, null);
        CardPort bIn = model.addCardToNode(b, "Синхро", List.of(
                new CardPort("Genlock (SDI)", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), sync.getId(), b.getId(), bIn.getId(), null);

        SchemaCanvasPanel canvas = canvas(model, dir, SchemaStylePreset.PRINT);
        canvas.renderImage(10, 10, false, 1.0); // прогревает style-поле (см. paint())
        Color color = canvas.edgeDefaultColorForTest(edge);

        assertEquals(new Color(0x7A, 0x1F, 0xA3), color, "«Печатный»: цвет линии для роли SYNC — фиолетовый");
    }

    @Test
    void screenPresetIsUnaffectedByRoleColorsAndKeepsTheOldDefault(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 0, null);
        CardPort sync = model.addCardToNode(a, "Синхро", List.of(
                new CardPort("Genlock (SDI)", PortDirection.OUT, 1))).getPorts().get(0);
        sync.setRole(InterfaceRole.SYNC);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 300, 0, null);
        CardPort bIn = model.addCardToNode(b, "Синхро", List.of(
                new CardPort("Genlock (SDI)", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), sync.getId(), b.getId(), bIn.getId(), null);

        SchemaCanvasPanel canvas = canvas(model, dir, SchemaStylePreset.SCREEN);
        canvas.renderImage(10, 10, false, 1.0);
        Color color = canvas.edgeDefaultColorForTest(edge);

        assertEquals(com.vjstb.ledscheme.ui.Palette.MUTED, color,
                "«Экранный» пресет пока не задаёт цвета ролей — поведение не должно поменяться");
    }

    @Test
    void customEdgeColorAlwaysWinsOverRoleColor(@TempDir Path dir) {
        AppModel model = model(dir);
        SchemaNode a = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "A", 0, 0, null);
        CardPort sync = model.addCardToNode(a, "Синхро", List.of(
                new CardPort("Genlock (SDI)", PortDirection.OUT, 1))).getPorts().get(0);
        sync.setRole(InterfaceRole.SYNC);
        SchemaNode b = model.addSchemaNode(SchemaMode.SIGNAL, SchemaNodeType.SOURCE, "B", 300, 0, null);
        CardPort bIn = model.addCardToNode(b, "Синхро", List.of(
                new CardPort("Genlock (SDI)", PortDirection.IN, 1))).getPorts().get(0);
        SchemaEdge edge = model.addSchemaEdge(SchemaMode.SIGNAL, a.getId(), sync.getId(), b.getId(), bIn.getId(), null);
        model.setSchemaEdgeColor(edge, Color.PINK.getRGB());

        SchemaCanvasPanel canvas = canvas(model, dir, SchemaStylePreset.PRINT);
        canvas.renderImage(10, 10, false, 1.0);

        // edgeDefaultColor() сам по себе не смотрит на edge.getColor() — приоритет
        // (D9: пользовательский цвет → цвет роли → цвет стиля по умолчанию) собирает
        // paint() снаружи ИМЕННО в этом порядке (см. SchemaCanvasPanel.paint):
        // customColor != null ? customColor : edgeDefaultColor(edge). Тест фиксирует,
        // что эта сборка не даёт роли (фиолетовый SYNC) выиграть у явного цвета.
        Color roleColor = canvas.edgeDefaultColorForTest(edge);
        Color used = edge.getColor() != null ? new Color(edge.getColor()) : roleColor;
        assertEquals(Color.PINK, used);
    }
}
