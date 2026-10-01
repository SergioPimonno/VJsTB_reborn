package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureFrameType;
import com.vjstb.ledscheme.service.FloorPickMath.Ray;
import com.vjstb.ledscheme.service.FloorPickMath.Vec3;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Picking 3D-редактора пола ({@link FloorPickMath}, запрос 2026-10-01) — единственная часть
 * {@code ui.FloorPlan3DPanel}, которую можно проверить без GL. Проверяется то, что решает,
 * ПО КАКОЙ раме пришёлся клик: без Ctrl — видимая рама под точкой (и в зазоре стакана тоже,
 * иначе узкий зазор был бы «мёртвой зоной»), с Ctrl — призрак с ближайшим центром (так раму
 * можно сдвинуть на кабинет), призраки на месте видимых рам не выбираются (урок Round 24
 * конструктива — клик не должен «промахиваться» в призрак).
 */
class FloorPickMathTest {

    private AppModel model;
    private Screen screen;

    private FloorCalc.Layout layout13(Path dir) {
        model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "ws.json")));
        model.selectProject(model.addProject("P"));
        model.selectScene(model.addScene("S"));
        CabinetType t = new CabinetType();
        t.setName("500");
        t.setWidthMm(500);
        t.setHeightMm(500);
        t.setResolutionWidth(128);
        t.setResolutionHeight(128);
        model.addCabinetType(t);
        StructureFrameType f = new StructureFrameType();
        f.setName("Рама");
        f.setKind(StructureFrameType.Kind.FRAME);
        f.setHeightMm(950.0);
        f.setWidthMm(500.0);
        f.setDepthMm(51.0);
        model.addStructureFrameType(f);
        screen = model.addScreen("Пол", t.getId(), 1, 13, 0, 0, ScreenMountType.FLOOR);
        model.selectScreen(screen);
        screen.setStructureFrameTypeId(f.getId());
        return FloorCalc.layout(screen, t, model.getWorkspace());
    }

    @Test
    void cameraRayThroughViewportCenterHitsOrbitTargetOnFloorPlane() {
        Vec3 eye = new Vec3(3000, 4000, 9000);
        Vec3 center = new Vec3(3000, 151, 1000);
        Ray ray = FloorPickMath.cameraRay(eye, center, new Vec3(0, 1, 0), 45, 1.6, 400, 250, 800, 500);
        double[] hit = FloorPickMath.intersectHorizontalPlane(ray, 151);
        assertNotNull(hit);
        assertArrayEquals(new double[]{3000, 1000}, hit, 1e-6);

        Ray up = new Ray(eye, new Vec3(0, 1, 0));
        assertNull(FloorPickMath.intersectHorizontalPlane(up, 0), "плоскость позади луча — промах");
        Ray flat = new Ray(eye, new Vec3(1, 0, 0));
        assertNull(FloorPickMath.intersectHorizontalPlane(flat, 0), "луч параллелен полу — промах");
    }

    @Test
    void plainClickPicksVisibleFrameUnderPointIncludingCupGap(@TempDir Path dir) {
        FloorCalc.Layout l = layout13(dir);
        var cells = FloorCalc.effectiveCells(screen, l);

        assertEquals(l.placementAt(0, 2), FloorPickMath.pickTarget(l, cells, 3 * 500 + 250, 250, false),
                "кабинет 3 лежит на раме [2,4)");
        assertEquals(l.placementAt(0, 2), FloorPickMath.pickTarget(l, cells, 1010, 250, false),
                "точка в зазоре стакана (рама 950 в отрезке 1000) — всё равно эта рама");
        assertNull(FloorPickMath.pickTarget(l, cells, 12 * 500 + 250, 250, false),
                "хвостовой неопёртый кабинет — рамы под ним нет");
        assertNull(FloorPickMath.pickTarget(l, cells, -10, 250, false), "мимо пола");
    }

    @Test
    void ctrlClickPicksGhostWithNearestCenterAndNeverOverlapsVisibleFrame(@TempDir Path dir) {
        FloorCalc.Layout l = layout13(dir);
        assertNull(FloorPickMath.pickTarget(l, FloorCalc.effectiveCells(screen, l), 1250, 250, true),
                "на месте видимой рамы призрака нет");

        model.toggleFloorFrameCell(screen, 0, 10); // убрали предпоследнюю раму: свободны колонки 10..12
        var cells = FloorCalc.effectiveCells(screen, l);
        assertEquals(l.placementAt(0, 10), FloorPickMath.pickTarget(l, cells, 11 * 500 + 100, 250, true),
                "левая половина кабинета 11 — рама с колонки 10 (вернуть на место)");
        assertEquals(l.placementAt(0, 11), FloorPickMath.pickTarget(l, cells, 11 * 500 + 400, 250, true),
                "правая половина кабинета 11 — рама с колонки 11 (сдвиг на кабинет)");
        assertNull(FloorPickMath.pickTarget(l, cells, 10 * 500 + 100, 250, false),
                "без Ctrl по месту убранной рамы кликать нечего — призраки без Ctrl не выбираются");
    }

    @Test
    void frameFootprintCentersLibraryLengthInItsCabinetSpanAndSurfaceHeightsStack(@TempDir Path dir) {
        FloorCalc.Layout l = layout13(dir);
        assertArrayEquals(new double[]{1025, 0, 950, 500}, FloorPickMath.frameFootprint(l, l.placementAt(0, 2)), 1e-9,
                "рама 950 в отрезке 1000 — по 25 мм зазора с каждой стороны (вместе — зазор стакана 50)");
        assertEquals(FloorCalc.DISPLAY_LEG_HEIGHT_MM + 51, FloorPickMath.topSurfaceY(l, false), 1e-9);
        assertEquals(FloorCalc.DISPLAY_LEG_HEIGHT_MM + 51 + FloorPickMath.DISPLAY_CABINET_THICKNESS_MM,
                FloorPickMath.topSurfaceY(l, true), 1e-9);
    }
}
