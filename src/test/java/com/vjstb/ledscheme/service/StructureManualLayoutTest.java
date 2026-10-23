package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.ScreenCurveType;
import com.vjstb.ledscheme.model.ScreenGroup;
import com.vjstb.ledscheme.model.ScreenMountType;
import com.vjstb.ledscheme.model.StructureFrameCell;
import com.vjstb.ledscheme.model.StructureTowerPlacement;
import com.vjstb.ledscheme.service.StructureCurveMath.Placement;
import com.vjstb.ledscheme.service.StructureCurveMath.TowerSpec;
import com.vjstb.ledscheme.service.StructureLayoutMath.Side;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Ручная расстановка башен наземного конструктива и конструктив группы экранов (запрос
 * пользователя 2026-10-22, проект «Жара»): на объекте две башни встык под 90° (и вообще под любым
 * углом), а экраны группы стоят в плане под углом — левая и правая группы под 90°, центральная под
 * 120°. Покрыто: чистая геометрия ({@link StructureLayoutMath}: композиция поз, «встык», стык
 * экранов под углом, предупреждения), операции {@link AppModel} (включение «вручную» от авто-раскладки,
 * перемещение жестом одной записью отмены, добавление/удаление башни со сдвигом номеров), JSON и
 * сводка группы ({@link StructureGroupCalc}).
 */
class StructureManualLayoutTest {

    private static final double EPS = 1e-6;
    private static final TowerSpec SPEC = new TowerSpec(1000, 51, 500, 1000);

    private AppModel model;
    private CabinetType type;

    private Screen screen(Path dir, String name, int rows, int cols) {
        if (model == null) {
            model = new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
            CabinetType ct = new CabinetType();
            ct.setName("Test");
            ct.setWidthMm(500);
            ct.setHeightMm(500);
            ct.setResolutionWidth(128);
            ct.setResolutionHeight(128);
            ct.setWeightKg(10);
            type = model.addCabinetType(ct);
            model.selectProject(model.addProject("P"));
            model.selectScene(model.addScene("S"));
        }
        Screen s = model.addScreen(name, type.getId(), rows, cols, 0, 0, ScreenMountType.STRUCTURE);
        model.selectScreen(s);
        return s;
    }

    /** «Предварительный расчёт» с обычными стартовыми числами (3 сегмента, 2 заднего ряда, 1
     *  уровень перемычек, вынос 500 мм), как в {@code StructureCurvedTowersTest}. */
    private void calc(Screen s, ScreenCurveType curve, double gap, int towers) {
        model.updateScreenStructure(s, 1500, StructureCalc.suggestTowerCount(s, type), 3, 2, 1, 500, 0.6,
                null, null, null, 0, null, curve, 10_000, false, gap, towers);
    }

    private static Set<Integer> posts(Screen s) {
        return s.getStructureFrameCells().stream().filter(c -> !c.isHidden())
                .map(StructureFrameCell::getTowerIndex).collect(Collectors.toCollection(TreeSet::new));
    }

    private static double[] world(Placement p, double x, double z) {
        return p.toWorld(x, z);
    }

    // ---- геометрия ----

    @Test
    void composeAndToFrameAreInverseAndAnglesAdd() {
        Placement frame = new Placement(0, 1200, -800, 35);
        Placement inner = new Placement(3, 300, 450, -110);
        Placement w = StructureLayoutMath.compose(frame, inner);
        assertEquals(3, w.index());
        assertEquals(StructureLayoutMath.normalizeDeg(35 - 110), w.yawDeg(), EPS);
        // точка внутри: идти через inner, затем frame == идти через композицию
        double[] viaTwo = frame.toWorld(inner.toWorld(40, -70)[0], inner.toWorld(40, -70)[1]);
        double[] viaOne = w.toWorld(40, -70);
        assertEquals(viaTwo[0], viaOne[0], 1e-6);
        assertEquals(viaTwo[1], viaOne[1], 1e-6);
        Placement back = StructureLayoutMath.toFrame(frame, w);
        assertEquals(inner.anchorX(), back.anchorX(), 1e-6);
        assertEquals(inner.anchorZ(), back.anchorZ(), 1e-6);
        assertEquals(inner.yawDeg(), back.yawDeg(), 1e-6);
    }

    @Test
    void normalizeDegKeepsAngleInHalfOpenRange() {
        assertEquals(180, StructureLayoutMath.normalizeDeg(-180), EPS);
        assertEquals(-90, StructureLayoutMath.normalizeDeg(270), EPS);
        assertEquals(10, StructureLayoutMath.normalizeDeg(370), EPS);
        assertEquals(0, StructureLayoutMath.normalizeDeg(720), EPS);
    }

    @Test
    void towerAttachedToSideWithItsFrontMakesAQuarterTurnAndTouchesWithoutOverlap() {
        Placement target = new Placement(0, 0, 0, 0);
        Placement mover = StructureLayoutMath.attachTowerFlush(target, SPEC, Side.RIGHT, SPEC, Side.FRONT, 0, 0);
        assertEquals(-90, mover.yawDeg(), 1e-6, "встык под 90°");
        // передняя плоскость второй башни — по центру правой грани первой: (W/2, −D/2)
        assertEquals(525.5, mover.anchorX(), 1e-6);
        assertEquals(-500, mover.anchorZ(), 1e-6);
        assertFalse(StructureCurveMath.anyIntersect(StructureCurveMath.worldRects(target, SPEC),
                StructureCurveMath.worldRects(mover, SPEC)), "касание гранью — не пересечение");
        Placement into = StructureLayoutMath.attachTowerFlush(target, SPEC, Side.RIGHT, SPEC, Side.FRONT, 0, -10);
        assertTrue(StructureCurveMath.anyIntersect(StructureCurveMath.worldRects(target, SPEC),
                StructureCurveMath.worldRects(into, SPEC)), "заход на 10 мм — пересечение");
    }

    @Test
    void flushAttachWorksForAnyTargetAngleAndSidePair() {
        for (double yaw : new double[]{0, 30, 90, -47.5, 135}) {
            Placement target = new Placement(0, 2000, -700, yaw);
            for (Side ts : Side.values()) {
                for (Side ms : Side.values()) {
                    Placement m = StructureLayoutMath.attachTowerFlush(target, SPEC, ts, SPEC, ms, 120, 0);
                    assertFalse(StructureCurveMath.anyIntersect(StructureCurveMath.worldRects(target, SPEC),
                            StructureCurveMath.worldRects(m, SPEC)), "yaw=" + yaw + " " + ts + "/" + ms);
                    Placement g = StructureLayoutMath.attachTowerFlush(target, SPEC, ts, SPEC, ms, 120, -8);
                    assertTrue(StructureCurveMath.anyIntersect(StructureCurveMath.worldRects(target, SPEC),
                            StructureCurveMath.worldRects(g, SPEC)), "yaw=" + yaw + " " + ts + "/" + ms);
                }
            }
        }
    }

    @Test
    void parallelAttachKeepsTheSameAngle() {
        Placement target = new Placement(0, 0, 0, 25);
        Placement m = StructureLayoutMath.attachTowerFlush(target, SPEC, Side.RIGHT, SPEC, Side.LEFT, 0, 0);
        assertEquals(25, m.yawDeg(), 1e-6);
    }

    @Test
    void screensJoinAtNinetyAndOneTwentyDegreesLikeTheStageWalls() {
        Placement a = new Placement(0, 0, 0, 0);
        // правый экран под 90°, загнут к зрителю: торец A (3000, 0), экран идёт в сторону зрителя
        Placement r90 = StructureLayoutMath.attachScreen(a, 3000, true, 2000, 90, true);
        assertEquals(-90, r90.yawDeg(), 1e-6);
        assertEquals(3000, r90.anchorX(), 1e-6);
        double[] far90 = r90.toWorld(2000, 0);
        assertEquals(3000, far90[0], 1e-6);
        assertEquals(2000, far90[1], 1e-6, "дальний край — ближе к зрителю (z > 0)");
        // 120° между лицевыми поверхностями: угол при общем ребре
        Placement r120 = StructureLayoutMath.attachScreen(a, 3000, true, 2000, 120, true);
        double[] far = r120.toWorld(2000, 0);
        double[] corner = {3000, 0};
        double[] toA = {-3000, 0};
        double[] toB = {far[0] - corner[0], far[1] - corner[1]};
        double cos = (toA[0] * toB[0] + toA[1] * toB[1]) / (Math.hypot(toA[0], toA[1]) * Math.hypot(toB[0], toB[1]));
        assertEquals(120, Math.toDegrees(Math.acos(cos)), 1e-6);
        // выпуклый угол — в другую сторону
        Placement away = StructureLayoutMath.attachScreen(a, 3000, true, 2000, 90, false);
        assertEquals(90, away.yawDeg(), 1e-6);
        assertEquals(-2000, away.toWorld(2000, 0)[1], 1e-6);
        // 180° — продолжение в линию
        Placement line = StructureLayoutMath.attachScreen(a, 3000, true, 2000, 180, true);
        assertEquals(0, line.yawDeg(), 1e-6);
    }

    @Test
    void screenAttachedToTheLeftSharesTheCornerAndFoldsTowardTheViewer() {
        Placement a = new Placement(0, 1000, 500, 20);
        Placement left = StructureLayoutMath.attachScreen(a, 3000, false, 2000, 90, true);
        double[] corner = left.toWorld(2000, 0);
        assertEquals(1000, corner[0], 1e-6, "правый торец левого экрана — в левом торце A");
        assertEquals(500, corner[1], 1e-6);
        // оба экрана загнуты к зрителю: левый край левого экрана — ближе к зрителю, чем общее ребро
        assertTrue(left.toWorld(0, 0)[1] > 500 + 1, "дальний край левого экрана выходит к зрителю");
        double[] toA = {a.toWorld(3000, 0)[0] - 1000, a.toWorld(3000, 0)[1] - 500};
        double[] toB = {left.toWorld(0, 0)[0] - 1000, left.toWorld(0, 0)[1] - 500};
        double cos = (toA[0] * toB[0] + toA[1] * toB[1]) / (Math.hypot(toA[0], toA[1]) * Math.hypot(toB[0], toB[1]));
        assertEquals(90, Math.toDegrees(Math.acos(cos)), 1e-6);
    }

    // ---- модель и операции ----

    @Test
    void manualLayoutStartsFromTheCurrentAutoLayoutAndKeepsCellsAndTotals(@TempDir Path dir) {
        Screen s = screen(dir, "E", 2, 12);
        calc(s, ScreenCurveType.CONCAVE, 500, 0);
        StructureCurveMath.Setup setup = StructureCurveMath.setupOf(s, type, model.getWorkspace());
        List<Placement> auto = StructureCurveMath.placements(setup.curve(), setup.tower(), setup.towerCount(),
                setup.gapMm());
        StructureCalc.Result before = StructureCalc.compute(s, type, model.getWorkspace());
        Set<Integer> postsBefore = posts(s);

        model.setStructureManualLayout(s, true);

        assertTrue(s.isStructureManualLayout());
        assertEquals(auto.size(), s.getStructureTowerPlacements().size());
        for (Placement p : auto) {
            StructureTowerPlacement rec = s.towerPlacementOf(p.index());
            assertNotNull(rec);
            assertEquals(p.anchorX(), rec.getAnchorXMm(), 1e-6);
            assertEquals(p.anchorZ(), rec.getAnchorZMm(), 1e-6);
            assertEquals(p.yawDeg(), rec.getYawDeg(), 1e-6);
        }
        assertEquals(postsBefore, posts(s), "раздельные башни сохраняют ячейки");
        assertEquals(before.totalFrameCount(), StructureCalc.compute(s, type, model.getWorkspace()).totalFrameCount());
    }

    @Test
    void switchingAWallToManualBuildsSeparateTowersAndUndoBringsTheWallBack(@TempDir Path dir) {
        Screen s = screen(dir, "E", 2, 6);
        calc(s, ScreenCurveType.FLAT, 0, 0);
        assertFalse(StructureCurveMath.separateTowers(s));
        int wallPosts = s.getStructureTowerCount();

        model.setStructureManualLayout(s, true);
        assertTrue(StructureCurveMath.separateTowers(s));
        assertEquals(0, s.getStructureTowerCount() % 2, "столбы парами — башни");
        assertEquals(2 * s.getStructureTowerPlacements().size(), s.getStructureTowerCount());
        assertFalse(s.getStructureFrameCells().isEmpty());

        model.undo();
        assertFalse(s.isStructureManualLayout());
        assertTrue(s.getStructureTowerPlacements().isEmpty());
        assertEquals(wallPosts, s.getStructureTowerCount());
        assertFalse(StructureCurveMath.separateTowers(s));
    }

    @Test
    void switchingBackToAutoDropsPlacementsAndRebuildsCells(@TempDir Path dir) {
        Screen s = screen(dir, "E", 2, 12);
        calc(s, ScreenCurveType.FLAT, 600, 2);
        model.setStructureManualLayout(s, true);
        model.addStructureTower(s, 9000, 0, 0);
        assertEquals(3, s.getStructureTowerPlacements().size());

        model.setStructureManualLayout(s, false);
        assertFalse(s.isStructureManualLayout());
        assertTrue(s.getStructureTowerPlacements().isEmpty());
        assertEquals(4, s.getStructureTowerCount(), "авто: 2 башни пользователя = 4 столба");
        assertEquals(Set.of(0, 1, 2, 3), posts(s));
    }

    @Test
    void movingATowerByGestureIsOneUndoStepAndNormalizesTheAngle(@TempDir Path dir) {
        Screen s = screen(dir, "E", 2, 12);
        calc(s, ScreenCurveType.FLAT, 600, 2);
        model.setStructureManualLayout(s, true);
        StructureTowerPlacement start = s.towerPlacementOf(1).copy();
        int depth = model.undoDepth();

        model.setStructureTowerPlacement(s, 1, 4000, -900, 10, true, false);
        model.setStructureTowerPlacement(s, 1, 4100, -950, 200, false, false);
        model.setStructureTowerPlacement(s, 1, 4200, -1000, 370, false, true);
        assertEquals(depth + 1, model.undoDepth(), "весь жест — одна запись отмены");
        StructureTowerPlacement p = s.towerPlacementOf(1);
        assertEquals(4200, p.getAnchorXMm(), EPS);
        assertEquals(-1000, p.getAnchorZMm(), EPS);
        assertEquals(10, p.getYawDeg(), 1e-9, "370° нормализуется в 10°");

        model.undo();
        StructureTowerPlacement back = s.towerPlacementOf(1);
        assertEquals(start.getAnchorXMm(), back.getAnchorXMm(), EPS);
        assertEquals(start.getYawDeg(), back.getYawDeg(), EPS);
    }

    @Test
    void moveIsIgnoredWithoutManualModeOrForUnknownTower(@TempDir Path dir) {
        Screen s = screen(dir, "E", 2, 12);
        calc(s, ScreenCurveType.FLAT, 600, 2);
        int depth = model.undoDepth();
        model.setStructureTowerPlacement(s, 0, 1, 2, 3, true, true);
        assertEquals(depth, model.undoDepth(), "авто-режим: правка положения не принимается");
        model.setStructureManualLayout(s, true);
        depth = model.undoDepth();
        model.setStructureTowerPlacement(s, 99, 1, 2, 3, true, true);
        assertEquals(depth, model.undoDepth());
        assertEquals(-1, new AppModel(new WorkspaceStore(new File(dir.toFile(), "w2.json")))
                .addStructureTower(new Screen(), 0, 0, 0));
    }

    @Test
    void addedTowerGetsAFullSetOfFramesEvenFarFromTheScreen(@TempDir Path dir) {
        Screen s = screen(dir, "E", 2, 12);
        calc(s, ScreenCurveType.FLAT, 600, 2);
        model.setStructureManualLayout(s, true);

        int k = model.addStructureTower(s, 30_000, -4000, 90);
        assertEquals(2, k);
        assertEquals(Set.of(0, 1, 2, 3, 4, 5), posts(s), "башня стоит, хотя под ней нет кабинетов");
        assertEquals(6, s.getStructureTowerCount());
        long newTowerFrames = s.getStructureFrameCells().stream()
                .filter(c -> c.getTowerIndex() >= 4 && !c.isHidden()).count();
        assertEquals(10, newTowerFrames, "2 столба × (3 переднего + 2 заднего ряда)");
        assertTrue(s.getStructurePeremychkaCells().stream().anyMatch(c -> c.getTowerIndex() == 4));
        assertEquals(90, s.towerPlacementOf(2).getYawDeg(), EPS);
    }

    @Test
    void removingATowerShiftsTheNumbersOfTheOthersWithTheirCells(@TempDir Path dir) {
        Screen s = screen(dir, "E", 2, 12);
        calc(s, ScreenCurveType.FLAT, 600, 3);
        model.setStructureManualLayout(s, true);
        StructureTowerPlacement second = s.towerPlacementOf(1).copy();
        StructureTowerPlacement third = s.towerPlacementOf(2).copy();
        // метка на третьей башне: убранная вручную верхняя рама заднего ряда
        s.getStructureFrameCells().stream().filter(c -> c.getTowerIndex() == 4 && c.getRow() == 1
                && c.getSegmentIndex() == 1).forEach(c -> c.setHidden(true));

        assertTrue(model.removeStructureTower(s, 0));

        assertEquals(2, s.getStructureTowerPlacements().size());
        assertEquals(Set.of(0, 1, 2, 3), posts(s).stream().filter(p -> p < 4).collect(Collectors.toSet()));
        assertEquals(4, s.getStructureTowerCount());
        assertEquals(second.getAnchorXMm(), s.towerPlacementOf(0).getAnchorXMm(), EPS);
        assertEquals(third.getAnchorXMm(), s.towerPlacementOf(1).getAnchorXMm(), EPS);
        assertNull(s.towerPlacementOf(2));
        assertTrue(s.getStructureFrameCells().stream().anyMatch(c -> c.getTowerIndex() == 2 && c.getRow() == 1
                && c.getSegmentIndex() == 1 && c.isHidden()), "правка третьей башни переехала в её новый номер");
        assertFalse(model.removeStructureTower(s, 7));
    }

    @Test
    void recalcInManualModeKeepsPlacementsAndHandEditedCells(@TempDir Path dir) {
        Screen s = screen(dir, "E", 2, 12);
        calc(s, ScreenCurveType.FLAT, 600, 2);
        model.setStructureManualLayout(s, true);
        model.addStructureTower(s, 20_000, 0, 45);
        model.setStructureTowerPlacement(s, 0, 777, -555, 33, true, true);
        s.getStructureFrameCells().stream().filter(c -> c.getTowerIndex() == 5 && c.getRow() == 0
                && c.getSegmentIndex() == 2).forEach(c -> c.setHidden(true));

        calc(s, ScreenCurveType.FLAT, 600, 2); // авто-параметры формы тут не должны сбросить ручной режим

        assertTrue(s.isStructureManualLayout());
        assertEquals(3, s.getStructureTowerPlacements().size());
        assertEquals(777, s.towerPlacementOf(0).getAnchorXMm(), EPS);
        assertEquals(45, s.towerPlacementOf(2).getYawDeg(), EPS);
        assertEquals(6, s.getStructureTowerCount());
        assertTrue(s.getStructureFrameCells().stream().anyMatch(c -> c.getTowerIndex() == 5 && c.getRow() == 0
                && c.getSegmentIndex() == 2 && c.isHidden()), "ручные правки рам при пересчёте сохраняются");
    }

    @Test
    void manualLayoutWarnsAboutOverlapsAndAboutATowerInsideTheScreenButNotAboutTouching(@TempDir Path dir) {
        Screen s = screen(dir, "E", 2, 12);
        calc(s, ScreenCurveType.FLAT, 600, 2);
        model.setStructureManualLayout(s, true);
        assertTrue(StructureLayoutMath.analyzeManual(s, type, model.getWorkspace()).isEmpty(),
                "авто-раскладка, скопированная как стартовая, чистая");

        // вторая башня встык к первой под 90° — касание, предупреждений нет
        TowerSpec spec0 = StructureCurveMath.towerSpecOf(s, model.getWorkspace(), 0);
        StructureCurveMath.Setup setup = StructureCurveMath.setupOf(s, type, model.getWorkspace());
        Placement first = StructureLayoutMath.towerPlacementInScreen(s, setup, 0);
        Placement flush = StructureLayoutMath.attachTowerFlush(first, spec0, Side.RIGHT,
                StructureCurveMath.towerSpecOf(s, model.getWorkspace(), 1), Side.FRONT, 0, 0);
        model.setStructureTowerPlacement(s, 1, flush.anchorX(), flush.anchorZ(), flush.yawDeg(), true, true);
        assertTrue(StructureLayoutMath.analyzeManual(s, type, model.getWorkspace()).isEmpty(),
                "башни встык под 90° — это штатная расстановка, не ошибка");

        // заход одной на другую — предупреждение
        model.setStructureTowerPlacement(s, 1, first.anchorX() + 100, first.anchorZ(), 0, true, true);
        assertTrue(StructureLayoutMath.analyzeManual(s, type, model.getWorkspace()).stream()
                .anyMatch(w -> w.contains("1 и 2") && w.contains("пересекаются")));
        assertTrue(StructureCalc.compute(s, type, model.getWorkspace()).curveWarnings().stream()
                .anyMatch(w -> w.contains("пересекаются")), "предупреждение доходит до ведомости");

        // башня внутри объёма экрана
        model.setStructureTowerPlacement(s, 1, 3000, -50, 0, true, true);
        assertTrue(StructureLayoutMath.analyzeManual(s, type, model.getWorkspace()).stream()
                .anyMatch(w -> w.contains("заходит в объём экрана")));
    }

    @Test
    void towerWorldPlacementIsScreenPoseComposedWithTheTowerPlacement(@TempDir Path dir) {
        Screen s = screen(dir, "E", 2, 12);
        calc(s, ScreenCurveType.FLAT, 600, 1);
        model.setStructureManualLayout(s, true);
        model.setStructureTowerPlacement(s, 0, 100, -400, 15, true, true);
        model.setStructureScreenPose(s, 1000, 2000, 90, true, true);
        StructureCurveMath.Setup setup = StructureCurveMath.setupOf(s, type, model.getWorkspace());

        Placement w = StructureLayoutMath.towerPlacementInGroup(s, setup, 0);
        double[] expected = new Placement(0, 1000, 2000, 90).toWorld(100, -400);
        assertEquals(expected[0], w.anchorX(), 1e-6);
        assertEquals(expected[1], w.anchorZ(), 1e-6);
        assertEquals(105, w.yawDeg(), 1e-6);
    }

    @Test
    void poseAndManualLayoutSurviveUndoAndJson(@TempDir Path dir) throws Exception {
        Screen s = screen(dir, "E", 2, 12);
        calc(s, ScreenCurveType.FLAT, 600, 2);
        model.setStructureManualLayout(s, true);
        model.setStructureScreenPose(s, 1500, -300, 120, true, true);

        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Screen back = mapper.readValue(mapper.writeValueAsString(s), Screen.class);
        assertTrue(back.isStructureManualLayout());
        assertEquals(2, back.getStructureTowerPlacements().size());
        assertEquals(s.towerPlacementOf(1).getAnchorXMm(), back.towerPlacementOf(1).getAnchorXMm(), EPS);
        assertEquals(1500, back.getStructurePoseXMm(), EPS);
        assertEquals(-300, back.getStructurePoseZMm(), EPS);
        assertEquals(120, back.getStructurePoseYawDeg(), EPS);

        Screen old = mapper.readValue("{\"id\":\"s1\",\"mountType\":\"STRUCTURE\",\"structureTowerCount\":4}",
                Screen.class);
        assertFalse(old.isStructureManualLayout(), "старый проект — авто");
        assertTrue(old.getStructureTowerPlacements().isEmpty());
        assertEquals(0, old.getStructurePoseXMm(), EPS);

        model.undo();
        assertEquals(0, s.getStructurePoseXMm(), EPS, "отмена возвращает позу");
        model.undo();
        assertFalse(s.isStructureManualLayout());
    }

    // ---- группа ----

    @Test
    void groupSumsTheScreensAndIsEligibleOnlyWhenEveryScreenIsOnStructure(@TempDir Path dir) {
        Screen a = screen(dir, "L1", 2, 12);
        Screen b = screen(dir, "L2", 2, 8);
        calc(a, ScreenCurveType.FLAT, 600, 2);
        calc(b, ScreenCurveType.FLAT, 600, 1);
        Scene scene = model.getCurrentScene();
        ScreenGroup g = new ScreenGroup("Left");
        scene.getScreenGroups().add(g);
        a.setGroupId(g.getId());
        b.setGroupId(g.getId());
        assertTrue(StructureGroupCalc.eligible(scene, g));

        StructureGroupCalc.Result r = StructureGroupCalc.compute(StructureGroupCalc.members(scene, g), model::typeOf,
                model.getWorkspace());
        StructureCalc.Result ra = StructureCalc.compute(a, type, model.getWorkspace());
        StructureCalc.Result rb = StructureCalc.compute(b, type, model.getWorkspace());
        assertEquals(ra.totalFrameCount() + rb.totalFrameCount(), r.total().totalFrameCount());
        assertEquals(ra.cupCount() + rb.cupCount(), r.total().cupCount());
        assertEquals(ra.requiredBallastKg() + rb.requiredBallastKg(), r.total().requiredBallastKg(), 1e-9);
        assertEquals(2, r.perScreen().size());

        b.setMountType(ScreenMountType.RIGGED);
        assertFalse(StructureGroupCalc.eligible(scene, g), "в группе есть экран на подвесе");
        assertFalse(StructureGroupCalc.eligible(scene, new ScreenGroup("Пустая")));
    }

    @Test
    void groupWarnsWhenATowerOfOneScreenStandsInsideAnotherScreenOfTheGroup(@TempDir Path dir) {
        Screen a = screen(dir, "L1", 2, 12);
        Screen b = screen(dir, "L2", 2, 12);
        calc(a, ScreenCurveType.FLAT, 600, 2);
        calc(b, ScreenCurveType.FLAT, 600, 2);
        model.setStructureManualLayout(a, true);
        // экраны под 90°: b — справа от a, загнут к зрителю
        Placement poseB = StructureLayoutMath.attachScreen(StructureLayoutMath.screenPose(a), 6000, true, 6000,
                90, true);
        model.setStructureScreenPose(b, poseB.anchorX(), poseB.anchorZ(), poseB.yawDeg(), true, true);
        List<Screen> pair = List.of(a, b);
        assertTrue(StructureLayoutMath.analyzeGroup(pair, model::typeOf, model.getWorkspace()).isEmpty(),
                "штатная авто-раскладка двух экранов под 90° чистая");

        // башня a уезжает в объём b: точка (6000 + 300, +2000) мира лежит на теле b
        model.setStructureTowerPlacement(a, 0, 6200, 2000, 0, true, true);
        List<String> warnings = StructureLayoutMath.analyzeGroup(pair, model::typeOf, model.getWorkspace());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("«L1»") && w.contains("«L2»")
                && w.contains("заходит в объём экрана")), warnings.toString());
        assertTrue(StructureGroupCalc.compute(pair, model::typeOf, model.getWorkspace()).groupWarnings().size() > 0);
    }

    @Test
    void cabinetDepthComesFromTheLibraryWithADefaultWhenUnset() {
        CabinetType ct = new CabinetType();
        assertEquals(StructureCurveMath.DEFAULT_CABINET_DEPTH_MM, StructureCurveMath.cabinetDepthOf(ct), EPS);
        ct.setDepthMm(65.0);
        assertEquals(65, StructureCurveMath.cabinetDepthOf(ct), EPS);
        assertEquals(StructureCurveMath.DEFAULT_CABINET_DEPTH_MM, StructureCurveMath.cabinetDepthOf(null), EPS);
    }
}
