package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.Project;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.model.VehicleType;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Тесты подбора минимально достаточной машины под кофры проекта — см.
 *  class-javadoc {@link VehicleCalc} и VEHICLE_CALC_NOTES.md. */
class VehicleCalcTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private CabinetType cabinetType(double weightKg) {
        CabinetType ct = new CabinetType();
        ct.setName("Test");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setWeightKg(weightKg);
        return ct;
    }

    /** clearanceMm=0 намеренно (реальный дефолт CaseType — 50мм) — большинство тестов
     *  этого класса проверяют чистую геометрию площади/штабелирования, запас по
     *  периметру был бы лишней переменной там; см. отдельный
     *  checkFit_addsClearanceAroundEachFootprint для самого запаса. */
    private CaseType caseType(double lengthMm, double widthMm, double heightMm, double weightKg, int maxStack) {
        CaseType c = new CaseType();
        c.setName("Кофр");
        c.setLengthMm(lengthMm);
        c.setWidthMm(widthMm);
        c.setHeightMm(heightMm);
        c.setWeightKg(weightKg);
        c.setMaxStackCount(maxStack);
        c.setClearanceMm(0);
        return c;
    }

    private VehicleType vehicleType(String name, double cargoLengthMm, double cargoWidthMm, double cargoHeightMm,
                                     double payloadKg) {
        VehicleType v = new VehicleType();
        v.setName(name);
        v.setCargoLengthMm(cargoLengthMm);
        v.setCargoWidthMm(cargoWidthMm);
        v.setCargoHeightMm(cargoHeightMm);
        v.setPayloadKg(payloadKg);
        return v;
    }

    @Test
    void checkFit_withoutStacking_sumsFullFootprint() {
        // 5 кофров 1x1м, штабелировать нельзя (maxStack=1) -> площадь = 5 м².
        CaseType c = caseType(1000, 1000, 1000, 20, 1);
        VehicleType v = vehicleType("Газель", 3000, 2000, 2500, 1000); // пол 6 м², высота хватает с запасом
        VehicleCalc.VehicleFitResult result = VehicleCalc.checkFit(v, List.of(new VehicleCalc.CaseRow(c, 5)), false);

        assertTrue(result.fits());
        assertEquals(5.0, result.requiredFloorAreaM2(), 1e-9);
        assertEquals(6.0, result.cargoFloorAreaM2(), 1e-9);
    }

    @Test
    void checkFit_stackingReducesFootprint() {
        // Те же 5 кофров, но maxStack=3 и кузов достаточно высокий для 3 уровней ->
        // 2 "стопки" (ceil(5/3)=2) -> площадь = 2 м², меньше чем без штабелирования.
        CaseType c = caseType(1000, 1000, 1000, 20, 3);
        VehicleType v = vehicleType("Высокий фургон", 3000, 2000, 3200, 1000); // высота хватает на 3 уровня
        VehicleCalc.VehicleFitResult result = VehicleCalc.checkFit(v, List.of(new VehicleCalc.CaseRow(c, 5)), false);

        assertTrue(result.fits());
        assertEquals(2.0, result.requiredFloorAreaM2(), 1e-9);
    }

    @Test
    void checkFit_addsClearanceAroundEachFootprint() {
        // Кофры в машине не стоят впритирку (CaseType.clearanceMm) — каждая занятая
        // позиция пола реально (L+clearance)×(W+clearance), не голое L×W.
        CaseType c = caseType(1000, 1000, 1000, 20, 1);
        c.setClearanceMm(200); // +0.2м с каждой стороны -> отпечаток 1.2×1.2 = 1.44 м²
        VehicleType v = vehicleType("Газель", 3000, 2000, 2500, 1000);
        VehicleCalc.VehicleFitResult result = VehicleCalc.checkFit(v, List.of(new VehicleCalc.CaseRow(c, 3)), false);

        assertEquals(4.32, result.requiredFloorAreaM2(), 1e-9); // 3 × 1.44
    }

    @Test
    void checkFit_caseTallerThanCargo_failsWithReason() {
        CaseType c = caseType(1000, 1000, 2000, 20, 1); // кофр высотой 2м
        VehicleType v = vehicleType("Низкий прицеп", 3000, 2000, 1500, 1000); // кузов высотой 1.5м
        VehicleCalc.VehicleFitResult result = VehicleCalc.checkFit(v, List.of(new VehicleCalc.CaseRow(c, 1)), false);

        assertFalse(result.fits());
        assertTrue(result.failureReason() != null && result.failureReason().contains("выше грузового отсека"));
    }

    @Test
    void checkFit_recomputesStackingPerCandidateVehicle() {
        // Один и тот же набор строк -> у машины с низким кузовом (влезает только 1 уровень)
        // требуется БОЛЬШАЯ площадь, чем у машины с высоким кузовом (влезает maxStack уровней) —
        // подтверждает, что штабелирование считается заново на каждого кандидата, а не фиксируется
        // глобально один раз (см. class-javadoc VehicleCalc).
        CaseType c = caseType(1000, 1000, 1000, 20, 4);
        List<VehicleCalc.CaseRow> rows = List.of(new VehicleCalc.CaseRow(c, 8));

        VehicleType lowRoof = vehicleType("Низкий", 5000, 2000, 1200, 1000); // высота хватает на 1 уровень
        VehicleType highRoof = vehicleType("Высокий", 5000, 2000, 4200, 1000); // высота хватает на все 4 уровня

        double lowRoofArea = VehicleCalc.checkFit(lowRoof, rows, false).requiredFloorAreaM2();
        double highRoofArea = VehicleCalc.checkFit(highRoof, rows, false).requiredFloorAreaM2();

        assertEquals(8.0, lowRoofArea, 1e-9); // 8 кофров, по 1 в стопке -> 8 позиций по 1 м²
        assertEquals(2.0, highRoofArea, 1e-9); // ceil(8/4)=2 стопки -> 2 м²
        assertTrue(highRoofArea < lowRoofArea);
    }

    @Test
    void checkFit_weightToggle_changesOutcome() {
        // Площадь помещается с запасом, но вес превышает грузоподъёмность -> проходит только
        // если учёт веса выключен.
        CaseType c = caseType(500, 500, 500, 300, 1);
        VehicleType v = vehicleType("Лёгкая", 3000, 2000, 2000, 500); // грузоподъёмность 500 кг
        List<VehicleCalc.CaseRow> rows = List.of(new VehicleCalc.CaseRow(c, 5)); // 5 x 300 = 1500 кг

        assertTrue(VehicleCalc.checkFit(v, rows, false).fits(), "без учёта веса площадь помещается");
        VehicleCalc.VehicleFitResult withWeight = VehicleCalc.checkFit(v, rows, true);
        assertFalse(withWeight.fits(), "с учётом веса грузоподъёмность превышена");
        assertEquals(1500.0, withWeight.totalWeightKg(), 1e-9);
    }

    @Test
    void recommend_picksSmallestFittingVehicleByFloorAreaThenHeightThenPayload() {
        CaseType c = caseType(1000, 1000, 1000, 50, 2);
        List<VehicleCalc.CaseRow> rows = List.of(new VehicleCalc.CaseRow(c, 4)); // 2 стопки по 2 -> 2 м²

        VehicleType tooSmall = vehicleType("Слишком маленькая", 1000, 1000, 3000, 1000); // пол 1 м² — не влезет
        VehicleType justRight = vehicleType("Подходящая", 2000, 2000, 3000, 1000); // пол 4 м²
        VehicleType oversized = vehicleType("Избыточная", 5000, 3000, 3000, 5000); // пол 15 м²

        Optional<VehicleType> picked = VehicleCalc.recommend(List.of(oversized, justRight, tooSmall), rows, false);

        assertTrue(picked.isPresent());
        assertEquals("Подходящая", picked.get().getName());
    }

    @Test
    void recommend_returnsEmptyWhenNothingFits() {
        CaseType c = caseType(2000, 2000, 2000, 50, 1);
        List<VehicleCalc.CaseRow> rows = List.of(new VehicleCalc.CaseRow(c, 10));
        VehicleType tiny = vehicleType("Малютка", 1000, 1000, 1000, 100);

        assertTrue(VehicleCalc.recommend(List.of(tiny), rows, false).isEmpty());
    }

    @Test
    void suggestCaseCount_roundsUp() {
        assertEquals(4, VehicleCalc.suggestCaseCount(10, 3));
        assertEquals(3, VehicleCalc.suggestCaseCount(9, 3));
        assertEquals(0, VehicleCalc.suggestCaseCount(0, 3));
        assertEquals(1, VehicleCalc.suggestCaseCount(1, 8));
    }

    @Test
    void sceneCabinetCount_excludesHiddenCabinetsAndOtherScenes(@TempDir Path dir) {
        // Авто-подстановка количества кофров с кабинетами берёт число кабинетов ТЕКУЩЕЙ
        // сцены (ScreenLogic.sceneStats), не всего проекта (см. VehicleCalculatorDialog —
        // исправлено по прямому запросу пользователя: машина обычно возит содержимое
        // одной сцены). Этот тест подтверждает оба свойства сразу: скрытые кабинеты
        // не считаются, и кабинеты ДРУГОЙ сцены не подмешиваются в итог.
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(cabinetType(10));
        model.selectProject(model.addProject("P"));

        Scene scene1 = model.addScene("S1");
        model.selectScene(scene1);
        Screen screen1 = model.addScreen("E1", t.getId(), 2, 2, 0, 0); // 4 кабинета, 1 скрыт
        screen1.getCabinets().iterator().next().setHidden(true);

        Scene scene2 = model.addScene("S2");
        model.selectScene(scene2);
        model.addScreen("E2", t.getId(), 3, 3, 0, 0); // 9 кабинетов — в ДРУГОЙ сцене

        SceneStats stats = ScreenLogic.sceneStats(scene1, model.getWorkspace());

        assertEquals(1, stats.screenCount());
        assertEquals(3, stats.totalCabinetCount()); // 4 - 1 скрытый, без учёта сцены S2
        assertEquals(30.0, stats.totalWeightKg(), 1e-6);
    }

    @Test
    void vehicleCaseCounts_persistAndReloadWithScene(@TempDir Path dir) {
        // Запрос пользователя: строки калькулятора транспорта должны подтягиваться при
        // повторном открытии, а не начинать таблицу заново пустой — см.
        // Scene#getVehicleCaseCounts/AppModel#saveVehicleCaseCounts. Округляет полный
        // цикл: сохранить -> перезагрузить С НУЛЯ ЧЕРЕЗ ФАЙЛ (не тот же AppModel в памяти) ->
        // убедиться, что генерическая Jackson-сериализация Map<String,Integer> не потеряла
        // данные (тот же приём, что legacyPerPlacementMaskColorIsSeededOntoScreenOnLoad в
        // AppModelTest).
        File workspaceFile = new File(dir.toFile(), "workspace.json");
        AppModel model1 = new AppModel(new WorkspaceStore(workspaceFile));
        CaseType type = model1.addCaseType(caseType(1000, 1000, 1000, 20, 1));
        model1.selectProject(model1.addProject("P"));
        Scene scene = model1.addScene("S1");
        model1.selectScene(scene);

        model1.saveVehicleCaseCounts(scene, Map.of(type.getId(), 7));

        AppModel model2 = new AppModel(new WorkspaceStore(workspaceFile));
        Scene reloadedScene = model2.getWorkspace().getProjects().get(0).getScenes().get(0);

        assertEquals(Map.of(type.getId(), 7), reloadedScene.getVehicleCaseCounts());
    }

    @Test
    void vehicleCaseCounts_defaultsToEmptyForNewScene(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S1");

        assertTrue(scene.getVehicleCaseCounts().isEmpty());
    }

    @Test
    void vehicleCaseCountsProject_persistAndReloadWithProject(@TempDir Path dir) {
        // Область расчёта "Весь проект" — строки должны подтягиваться так же, как для
        // одной сцены (баг-репорт: "как будет работать сценарий, если табличка собиралась
        // для нескольких сцен сразу" — до этой правки PROJECT/CUSTOM вообще не сохранялись
        // отдельно от текущей сцены).
        File workspaceFile = new File(dir.toFile(), "workspace.json");
        AppModel model1 = new AppModel(new WorkspaceStore(workspaceFile));
        CaseType type = model1.addCaseType(caseType(1000, 1000, 1000, 20, 1));
        Project project = model1.addProject("P");
        model1.selectProject(project);
        model1.addScene("S1");

        model1.saveVehicleCaseCountsProject(project, Map.of(type.getId(), 12));

        AppModel model2 = new AppModel(new WorkspaceStore(workspaceFile));
        Project reloaded = model2.getWorkspace().getProjects().get(0);

        assertEquals(Map.of(type.getId(), 12), reloaded.getVehicleCaseCountsProject());
    }

    @Test
    void vehicleCaseCountsCustom_persistsSceneIdsAndCountsTogether(@TempDir Path dir) {
        File workspaceFile = new File(dir.toFile(), "workspace.json");
        AppModel model1 = new AppModel(new WorkspaceStore(workspaceFile));
        CaseType type = model1.addCaseType(caseType(1000, 1000, 1000, 20, 1));
        Project project = model1.addProject("P");
        model1.selectProject(project);
        Scene s1 = model1.addScene("S1");
        Scene s2 = model1.addScene("S2");
        model1.addScene("S3"); // намеренно НЕ входит в выбранный набор

        model1.saveVehicleCaseCountsCustom(project, List.of(s1.getId(), s2.getId()), Map.of(type.getId(), 6));

        AppModel model2 = new AppModel(new WorkspaceStore(workspaceFile));
        Project reloaded = model2.getWorkspace().getProjects().get(0);

        assertEquals(List.of(s1.getId(), s2.getId()), reloaded.getVehicleCaseCustomSceneIds());
        assertEquals(Map.of(type.getId(), 6), reloaded.getVehicleCaseCountsCustom());
    }

    @Test
    void vehicleCaseLastScope_persistsAcrossReload(@TempDir Path dir) {
        // Сам выбор области расчёта тоже должен подтягиваться — иначе комбобокс всегда
        // сбрасывается на "Текущая сцена", даже если в прошлый раз использовался
        // "Весь проект"/"Несколько сцен…" (та же жалоба из баг-репорта).
        File workspaceFile = new File(dir.toFile(), "workspace.json");
        AppModel model1 = new AppModel(new WorkspaceStore(workspaceFile));
        Project project = model1.addProject("P");
        model1.selectProject(project);

        model1.saveVehicleCaseLastScope(project, "PROJECT");

        AppModel model2 = new AppModel(new WorkspaceStore(workspaceFile));
        Project reloaded = model2.getWorkspace().getProjects().get(0);

        assertEquals("PROJECT", reloaded.getVehicleCaseLastScope());
    }
}
