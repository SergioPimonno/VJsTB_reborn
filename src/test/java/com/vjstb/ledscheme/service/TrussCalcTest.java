package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.TrussProfile;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Тесты калькулятора фермы подвеса (см. class-javadoc {@link TrussCalc},
 *  RIGGING_CALC_NOTES.md) — геометрия (длина/отступы, разделяемая с {@link RiggingCalc},
 *  см. {@link RiggingCalcTest}) и BOM (минимальный комплект сегментов, см. {@link
 *  MinimalKitCalcTest} за прямым тестом самого DP-алгоритма). */
class TrussCalcTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private CabinetType type500() {
        CabinetType ct = new CabinetType();
        ct.setName("Test");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setWeightKg(10);
        return ct;
    }

    private TrussProfile profile(double... lengths) {
        TrussProfile p = new TrussProfile();
        p.setName("Тестовая ферма");
        List<Double> list = new ArrayList<>();
        for (double l : lengths) {
            list.add(l);
        }
        p.setAvailableLengthsM(list);
        return p;
    }

    @Test
    void suggestTrussLengthMm_usesScreenPhysicalWidth() {
        Screen screen = new Screen();
        screen.setCols(6);
        screen.setRows(1);
        assertEquals(3000.0, TrussCalc.suggestTrussLengthMm(screen, type500()), 1e-6);
    }

    @Test
    void suggestTrussLengthMm_nullTypeReturnsZero() {
        Screen screen = new Screen();
        screen.setCols(6);
        assertEquals(0.0, TrussCalc.suggestTrussLengthMm(screen, null));
    }

    @Test
    void effectiveTrussLengthMm_overridePreferredOverAuto() {
        Screen screen = new Screen();
        screen.setCols(6);
        screen.setRiggingTrussLengthMm(4000.0);
        assertEquals(4000.0, TrussCalc.effectiveTrussLengthMm(screen, type500()), 1e-6);
    }

    @Test
    void effectiveTrussLengthMm_fallsBackToAutoWhenOverrideNullOrNonPositive() {
        Screen screen = new Screen();
        screen.setCols(6);
        assertEquals(3000.0, TrussCalc.effectiveTrussLengthMm(screen, type500()), 1e-6);

        screen.setRiggingTrussLengthMm(0.0);
        assertEquals(3000.0, TrussCalc.effectiveTrussLengthMm(screen, type500()), 1e-6);

        screen.setRiggingTrussLengthMm(-100.0);
        assertEquals(3000.0, TrussCalc.effectiveTrussLengthMm(screen, type500()), 1e-6);
    }

    @Test
    void leftOffsetMm_symmetricSplitsOverhangEvenly() {
        Screen screen = new Screen();
        screen.setCols(6); // 3000мм
        screen.setRiggingTrussLengthMm(5000.0); // overhang 2000
        assertEquals(1000.0, TrussCalc.leftOffsetMm(screen, type500()), 1e-6);
        assertEquals(1000.0, TrussCalc.rightOffsetMm(screen, type500()), 1e-6);
    }

    @Test
    void leftOffsetMm_manualOverridesSymmetricWhenDisabled() {
        Screen screen = new Screen();
        screen.setCols(6);
        screen.setRiggingTrussLengthMm(5000.0);
        screen.setRiggingTrussSymmetricOffset(false);
        screen.setRiggingTrussManualLeftOffsetMm(200.0);
        assertEquals(200.0, TrussCalc.leftOffsetMm(screen, type500()), 1e-6);
        assertEquals(1800.0, TrussCalc.rightOffsetMm(screen, type500()), 1e-6);
    }

    @Test
    void leftOffsetMm_manualFallsBackToSymmetricWhenNotSet() {
        Screen screen = new Screen();
        screen.setCols(6);
        screen.setRiggingTrussLengthMm(5000.0);
        screen.setRiggingTrussSymmetricOffset(false); // но manualLeftOffsetMm не задан
        assertEquals(1000.0, TrussCalc.leftOffsetMm(screen, type500()), 1e-6);
    }

    @Test
    void isShorterThanScreen_trueWhenTrussShorterThanScreenWidth() {
        Screen screen = new Screen();
        screen.setCols(6); // 3000мм
        screen.setRiggingTrussLengthMm(2000.0);
        assertTrue(TrussCalc.isShorterThanScreen(screen, type500()));

        screen.setRiggingTrussLengthMm(3000.0);
        assertFalse(TrussCalc.isShorterThanScreen(screen, type500()));
    }

    @Test
    void compute_profileMissingWhenNoIdSet(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type500());
        model.selectProject(model.addProject("P"));
        var scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 6, 0, 0);

        TrussCalc.Result result = TrussCalc.compute(screen, t, model.getWorkspace());
        assertTrue(result.profileMissing());
        assertFalse(result.catalogEmpty());
        assertNull(result.pieces());
        assertEquals(3000.0, result.targetLengthMm(), 1e-6);
    }

    @Test
    void compute_profileMissingWhenIdDanglingFk(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type500());
        model.selectProject(model.addProject("P"));
        var scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 6, 0, 0);
        screen.setRiggingTrussProfileId("does-not-exist");

        TrussCalc.Result result = TrussCalc.compute(screen, t, model.getWorkspace());
        assertTrue(result.profileMissing());
        assertNull(result.pieces());
    }

    @Test
    void compute_catalogEmptyWhenProfileHasNoLengths(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type500());
        model.selectProject(model.addProject("P"));
        var scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 6, 0, 0);
        TrussProfile empty = model.addTrussProfile(profile());
        screen.setRiggingTrussProfileId(empty.getId());

        TrussCalc.Result result = TrussCalc.compute(screen, t, model.getWorkspace());
        assertFalse(result.profileMissing());
        assertTrue(result.catalogEmpty());
        assertNull(result.pieces());
    }

    @Test
    void compute_example2_5mTargetGivesTwoPlusHalf(@TempDir Path dir) {
        // Прямое воспроизведение примера пользователя: доступны 1/2/0.5м -> для 2.5м
        // комплект 2+0.5 (2 куска).
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type500());
        model.selectProject(model.addProject("P"));
        var scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 5, 0, 0); // 5*500=2500мм=2.5м
        TrussProfile p = model.addTrussProfile(profile(1, 2, 0.5));
        screen.setRiggingTrussProfileId(p.getId());

        TrussCalc.Result result = TrussCalc.compute(screen, t, model.getWorkspace());
        assertFalse(result.profileMissing());
        assertFalse(result.catalogEmpty());
        assertEquals(2500.0, result.targetLengthMm(), 1e-6);
        assertEquals(2, result.pieces().size());
        assertEquals(2.0, result.pieces().get(0).lengthM());
        assertEquals(1, result.pieces().get(0).count());
        assertEquals(0.5, result.pieces().get(1).lengthM());
        assertEquals(1, result.pieces().get(1).count());
        assertEquals(2, result.totalPieceCount());
        assertEquals(2500.0, result.totalKitLengthMm(), 1e-6);
        assertEquals(TrussCalc.CONNECTORS_PER_JOINT, result.connectorCount());
    }

    @Test
    void compute_example1_5mTargetPrefersExactMatchOverSingleOversizedPiece(@TempDir Path dir) {
        // Прямой тест на баг-репорт пользователя (2026-09-14, скриншот "Целевая длина фермы:
        // 1500 мм ... 2,00 м x 1"): для 1.5м экрана ферма НЕ должна собираться из одного
        // куска 2м (33% лишней длины), когда точная комбинация 1+0.5=1.5м доступна в
        // каталоге -- см. MinimalKitCalcTest.solveMinimizingOverage_prefersExactTwoPieceMatch...
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type500());
        model.selectProject(model.addProject("P"));
        var scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 3, 0, 0); // 3*500=1500мм=1.5м
        TrussProfile p = model.addTrussProfile(profile(1, 2, 0.5));
        screen.setRiggingTrussProfileId(p.getId());

        TrussCalc.Result result = TrussCalc.compute(screen, t, model.getWorkspace());
        assertEquals(1500.0, result.targetLengthMm(), 1e-6);
        assertEquals(2, result.pieces().size());
        assertEquals(1.0, result.pieces().get(0).lengthM());
        assertEquals(1, result.pieces().get(0).count());
        assertEquals(0.5, result.pieces().get(1).lengthM());
        assertEquals(1, result.pieces().get(1).count());
        assertEquals(1500.0, result.totalKitLengthMm(), 1e-6, "комплект обязан точно совпасть с целью, без излишка");
    }

    @Test
    void compute_example7mTargetGivesThreeTwosAndOneOne(@TempDir Path dir) {
        // Прямое воспроизведение примера пользователя: доступны 1/2/0.5м -> для 7м
        // комплект 2+2+2+1 (4 куска).
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type500());
        model.selectProject(model.addProject("P"));
        var scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 14, 0, 0); // 14*500=7000мм=7м
        TrussProfile p = model.addTrussProfile(profile(1, 2, 0.5));
        screen.setRiggingTrussProfileId(p.getId());

        TrussCalc.Result result = TrussCalc.compute(screen, t, model.getWorkspace());
        assertEquals(7000.0, result.targetLengthMm(), 1e-6);
        assertEquals(2, result.pieces().size());
        assertEquals(2.0, result.pieces().get(0).lengthM());
        assertEquals(3, result.pieces().get(0).count());
        assertEquals(1.0, result.pieces().get(1).lengthM());
        assertEquals(1, result.pieces().get(1).count());
        assertEquals(4, result.totalPieceCount());
        assertEquals(3 * TrussCalc.CONNECTORS_PER_JOINT, result.connectorCount());
    }

    @Test
    void compute_singlePieceKitHasZeroConnectors(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type500());
        model.selectProject(model.addProject("P"));
        var scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 4, 0, 0); // 4*500=2000мм=2м
        TrussProfile p = model.addTrussProfile(profile(2, 3));
        screen.setRiggingTrussProfileId(p.getId());

        TrussCalc.Result result = TrussCalc.compute(screen, t, model.getWorkspace());
        assertEquals(1, result.totalPieceCount());
        assertEquals(0, result.connectorCount());
    }
}
