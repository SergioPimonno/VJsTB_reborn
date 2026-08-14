package com.vjstb.ledscheme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vjstb.ledscheme.model.CabinetType;
import com.vjstb.ledscheme.model.HoistType;
import com.vjstb.ledscheme.model.Scene;
import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.store.WorkspaceStore;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Тесты распределения веса подвешенного экрана по точкам подвеса (метод грузовых
 *  площадей, см. class-javadoc {@link RiggingCalc}). */
class RiggingCalcTest {

    private AppModel freshModel(Path dir) {
        return new AppModel(new WorkspaceStore(new File(dir.toFile(), "workspace.json")));
    }

    private CabinetType type(double weightKg) {
        CabinetType ct = new CabinetType();
        ct.setName("Test");
        ct.setWidthMm(500);
        ct.setHeightMm(500);
        ct.setResolutionWidth(128);
        ct.setResolutionHeight(128);
        ct.setWeightKg(weightKg);
        return ct;
    }

    @Test
    void uniformScreen_distributesWeightEvenlyAcrossPoints(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(10));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        // 4 колонки x 2 строки по 10кг/кабинет = 80 кг + 20% крепёж = 96 кг. 4 колонки/
        // 2 точки делятся БЕЗ ничейного исхода привязки к ближайшей точке (граница между
        // левой и правой парой колонок не совпадает ни с одной из точек) — чистый тест
        // симметричного распределения, в отличие от нечётного числа точек/колонок, где
        // возможны ничьи (см. RiggingCalc#compute — при ничьей побеждает МЕНЬШИЙ индекс
        // точки, что при некоторых сочетаниях колонок/точек ломает идеальную симметрию;
        // это легитимное детерминированное поведение, просто не то, что нужно ЭТОМУ тесту).
        Screen screen = model.addScreen("E", t.getId(), 2, 4, 0, 0);

        int suggested = RiggingCalc.suggestPointCount(screen, t, model.getWorkspace());
        assertEquals(2, suggested); // Math.max(2, ceil(4/2)) = 2, грузоподъёмность не задана -> без изменений

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), suggested);
        assertEquals(80.0, result.totalCabinetWeightKg(), 1e-6);
        assertEquals(96.0, result.totalWeightWithHardwareKg(), 1e-6);

        double sum = result.points().stream().mapToDouble(RiggingCalc.PointLoad::loadKg).sum();
        assertEquals(96.0, sum, 1e-6);
        // Равномерная нагрузка по ширине -> равное распределение по 2 симметричным точкам.
        for (RiggingCalc.PointLoad p : result.points()) {
            assertEquals(48.0, p.loadKg(), 1e-6);
        }
    }

    @Test
    void hiddenCabinets_dontContributeWeightOrShiftDistribution(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(10));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        // 6x1 экран, правая половина (арка/вырез) скрыта -> вес сосредоточен слева.
        Screen screen = model.addScreen("E", t.getId(), 1, 6, 0, 0);
        for (int col = 3; col < 6; col++) {
            screen.cabinetAt(0, col).setHidden(true);
        }

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), 3);
        assertEquals(30.0, result.totalCabinetWeightKg(), 1e-6); // только 3 видимых кабинета
        double sum = result.points().stream().mapToDouble(RiggingCalc.PointLoad::loadKg).sum();
        assertEquals(36.0, sum, 1e-6); // 30 * 1.2 наценки, весь вес где-то среди точек
        // Крайняя правая точка (x=3000, глубоко в вырезанной/скрытой зоне cols 3..5,
        // x от 1500 до 3000) не должна получить ни грамма веса — все видимые кабинеты
        // (cols 0..2, x от 0 до 1500) физически ближе к левым точкам.
        RiggingCalc.PointLoad rightmost = result.points().get(result.points().size() - 1);
        assertEquals(0.0, rightmost.loadKg(), 1e-6);
    }

    @Test
    void overCapacity_flaggedWhenHoistWllExceeded(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(50));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 2, 0, 0); // 2 кабинета x 50кг = 100кг, +20% = 120кг
        screen.setRiggingHoistCapacityKg(50.0); // одна точка при 2 точках несёт 60кг -> превышение

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), 2);
        assertTrue(result.points().stream().anyMatch(RiggingCalc.PointLoad::overCapacity));
    }

    @Test
    void noCapacitySet_neverFlaggedOverCapacity(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(1000)); // намеренно огромный вес
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 2, 0, 0);
        assertNull(screen.getRiggingHoistCapacityKg());

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), 2);
        assertTrue(result.points().stream().noneMatch(RiggingCalc.PointLoad::overCapacity));
    }

    @Test
    void libraryHoistType_takesPrecedenceOverManualCapacity(@TempDir Path dir) {
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(50));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 2, 0, 0); // 2 x 50кг = 100кг, +20% = 120кг

        HoistType weak = new HoistType();
        weak.setName("Слабая лебёдка");
        weak.setWllKg(50); // одна точка при 2 точках несёт 60кг -> должно флагануть превышение
        model.addHoistType(weak);
        screen.setRiggingHoistTypeId(weak.getId());
        // Ручное число НАМЕРЕННО противоречит библиотечной записи -- FK обязан победить.
        screen.setRiggingHoistCapacityKg(1_000_000.0);

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), 2);
        assertTrue(result.points().stream().anyMatch(RiggingCalc.PointLoad::overCapacity),
                "Библиотечный WLL (50кг) обязан победить ручное число (1_000_000), иначе превышение не найдётся");
    }

    @Test
    void danglingHoistTypeId_fallsBackToManualCapacity(@TempDir Path dir) {
        // FK ссылается на запись, которой больше нет в библиотеке (удалена на сервере) --
        // не должно падать, обязано тихо откатиться на ручное число, а не считать
        // "грузоподъёмность не указана" (иначе реальный fallback-сценарий из
        // RIGGING_CALC_NOTES.md был бы бесполезен).
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(50));
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        Screen screen = model.addScreen("E", t.getId(), 1, 2, 0, 0);
        screen.setRiggingHoistTypeId("does-not-exist");
        screen.setRiggingHoistCapacityKg(50.0);

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), 2);
        assertTrue(result.points().stream().anyMatch(RiggingCalc.PointLoad::overCapacity),
                "Несуществующий FK обязан откатиться на riggingHoistCapacityKg (50), не игнорировать проверку вовсе");
    }

    @Test
    void suggestPointCount_neverFewerThanOldColsBasedFormula() {
        com.vjstb.ledscheme.model.Workspace ws = new com.vjstb.ledscheme.model.Workspace();
        Screen wide = new Screen();
        wide.setCols(11);
        wide.setRows(1);
        assertEquals(6, RiggingCalc.suggestPointCount(wide, null, ws)); // ceil(11/2) = 6, грузоподъёмность не задана

        Screen narrow = new Screen();
        narrow.setCols(1);
        narrow.setRows(1);
        assertEquals(2, RiggingCalc.suggestPointCount(narrow, null, ws)); // минимум 2
    }

    @Test
    void suggestPointCount_increasesWhenLoadExceedsHoistCapacity(@TempDir Path dir) {
        // Прямой тест на баг-репорт «при перерасчёте количество лебёдок не меняется»:
        // формула раньше зависела ТОЛЬКО от ширины экрана в колонках, теперь обязана
        // расти, если реальная нагрузка на точку (метод compute) превышает WLL.
        AppModel model = freshModel(dir);
        CabinetType t = model.addCabinetType(type(100)); // тяжёлые кабинеты, 100кг каждый
        model.selectProject(model.addProject("P"));
        Scene scene = model.addScene("S");
        model.selectScene(scene);
        // 8 колонок x 1 строка -> база ceil(8/2)=4 точки. Суммарный вес 800кг +20% = 960кг.
        Screen screen = model.addScreen("E", t.getId(), 1, 8, 0, 0);

        int withoutCapacity = RiggingCalc.suggestPointCount(screen, t, model.getWorkspace());
        assertEquals(4, withoutCapacity, "без указанной грузоподъёмности -- прежняя формула по ширине");

        screen.setRiggingHoistCapacityKg(150.0); // ниже, чем 960/4=240кг на точку при базовых 4 точках
        int withCapacity = RiggingCalc.suggestPointCount(screen, t, model.getWorkspace());
        assertTrue(withCapacity > 4,
                "заданная грузоподъёмность (150кг) ниже расчётной нагрузки на точку -- количество точек обязано вырасти");

        RiggingCalc.Result result = RiggingCalc.compute(screen, t, model.getWorkspace(), withCapacity);
        assertTrue(result.points().stream().noneMatch(RiggingCalc.PointLoad::overCapacity),
                "предложенное количество точек обязано реально устранять превышение, а не просто вырасти на 1");
    }
}
